package com.example.banksmstracker.service

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.banksmstracker.BankSmsTrackerApp
import com.example.banksmstracker.BuildConfig
import com.example.banksmstracker.R
import com.example.banksmstracker.data.MessageProcessResult
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.database.IncomeEntity
import com.example.banksmstracker.processor.PaymentProcessor
import com.example.banksmstracker.processor.UnparsedMessageException
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.util.HashUtil
import com.example.banksmstracker.util.SmsAddressMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SmsProcessingService : Service() {

    companion object {
        const val CHANNEL_ID = "sms_monitor_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "SmsProcessingService"

        @Volatile
        private var isRunning = false

        fun isRunning(): Boolean = isRunning

        fun start(context: Context) {
            val intent = Intent(context, SmsProcessingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SmsProcessingService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent?.action != "android.provider.Telephony.SMS_RECEIVED") return
            val bundle = intent.extras ?: return

            val pdus: Array<*>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getSerializable("pdus", Array<Any>::class.java)
            } else {
                @Suppress("DEPRECATION")
                bundle.getSerializable("pdus") as? Array<*>
            }

            val format = bundle.getString("format")
            val messages = pdus?.mapNotNull {
                SmsMessage.createFromPdu(it as? ByteArray, format)
            } ?: return

            val receivedAt = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            serviceScope.launch {
                for (message in messages) {
                    val sender = message.originatingAddress ?: continue
                    val body = message.messageBody
                    handleIncomingSms(sender, body, receivedAt)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ConfigRepository.load(applicationContext as Application)
        registerSmsReceiver()
        isRunning = true
        Log.d(TAG, "Service created and SMS receiver registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsReceiver)
        serviceScope.cancel()
        isRunning = false
        Log.d(TAG, "Service destroyed and SMS receiver unregistered")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerSmsReceiver() {
        val filter = IntentFilter("android.provider.Telephony.SMS_RECEIVED").apply {
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY - 1
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smsReceiver, filter)
        }
    }

    private suspend fun handleIncomingSms(sender: String, body: String, receivedAt: Long) {
        val configuredAddresses = ConfigRepository.config.senders
            .flatMap { it.addresses }
            .toSet()

        if (!SmsAddressMatcher.matchesAny(sender, configuredAddresses)) {
            Log.d(TAG, "SMS from unknown sender '$sender' — skipping parse")
            return
        }

        val processor = ConfigRepository.getPaymentProcessor()
        processWithProcessor(processor, sender, body, receivedAt)
    }

    private suspend fun processWithProcessor(
        processor: PaymentProcessor,
        sender: String,
        body: String,
        receivedAt: Long
    ) {
        try {
            val result = processor.processMessageFull(body, sender, receivedAt)
            logProcessingResult(result, sender, body)
            if (result is MessageProcessResult.IncomeResult) {
                saveIncome(result, body, sender)
            }
        } catch (e: UnparsedMessageException) {
            Log.d(TAG, "SMS from $sender could not be parsed — sending unmatched notification")
            val prefs = applicationContext.getSharedPreferences(
                BankSmsTrackerApp.PREFS_NAME,
                MODE_PRIVATE
            )
            if (prefs.getBoolean(NotificationHelper.KEY_NOTIFICATIONS_UNMATCHED_SMS, true)) {
                NotificationHelper.sendUnmatchedSmsNotification(applicationContext, sender, body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS from $sender: ${e.message}")
        }
    }

    private fun logProcessingResult(result: MessageProcessResult, sender: String, body: String) {
        if (BuildConfig.DEBUG) {
            when (result) {
                is MessageProcessResult.PaymentResult ->
                    Log.d(TAG, "SMS from $sender processed as payment: ${result.payment}")
                is MessageProcessResult.IncomeResult ->
                    Log.d(TAG, "SMS from $sender processed as income: ${result.income}")
                is MessageProcessResult.Ignored ->
                    Log.d(TAG, "SMS from $sender ignored by rule '${result.ruleName}': $body")
            }
        }
    }

    private suspend fun saveIncome(result: MessageProcessResult.IncomeResult, body: String, sender: String) {
        val income = result.income
        val incomeEntity = IncomeEntity(
            amount = income.amount,
            currency = income.currency,
            source = income.source,
            timestamp = income.timestamp,
            balance = income.balance,
            messageHash = HashUtil.computeMessageHash(body, sender),
            senderAddress = sender,
            receivedAt = System.currentTimeMillis(),
            ruleId = income.ruleId
        )
        val db = BankSmsDatabase.getInstance(applicationContext)
        val insertedId = db.incomeDao().insertIncome(incomeEntity)
        if (insertedId == -1L) {
            Log.d(TAG, "Duplicate income skipped for sender $sender")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.sms_monitor_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.sms_monitor_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.sms_monitor_notification_text))
        .setSmallIcon(R.mipmap.ic_launcher)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()
}
