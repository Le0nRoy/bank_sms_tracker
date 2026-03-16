package com.example.banksmstracker.parser

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import android.util.Log
import com.example.banksmstracker.data.MessageProcessResult
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.database.IncomeEntity
import com.example.banksmstracker.processor.PaymentProcessor
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.service.SmsProcessingService
import com.example.banksmstracker.util.HashUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        const val EXTRA_TEST_SENDER =
            "com.example.banksmstracker.parser.SmsReceiver.EXTRA_TEST_SENDER"
        const val EXTRA_TEST_BODY =
            "com.example.banksmstracker.parser.SmsReceiver.EXTRA_TEST_BODY"
    }

    private lateinit var paymentProcessor: PaymentProcessor
    private var applicationContext: Context? = null

    // For testing only
    fun setPaymentProcessorForTest(processor: PaymentProcessor) {
        this.paymentProcessor = processor
    }

    private fun initializePaymentProcessor(context: Context) {
        if (!::paymentProcessor.isInitialized) {
            ConfigRepository.load(context.applicationContext as Application)
            paymentProcessor = ConfigRepository.getPaymentProcessor()
        }
        applicationContext = context.applicationContext
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != "android.provider.Telephony.SMS_RECEIVED") return

        // If the foreground service is running it handles real-time SMS — skip here to avoid
        // double-processing. Hash deduplication provides a safety net even if both run.
        if (SmsProcessingService.isRunning()) {
            Log.d(TAG, "SmsProcessingService is running — delegating real-time SMS to service")
            return
        }

        initializePaymentProcessor(context)

        val testSender = intent.getStringExtra(EXTRA_TEST_SENDER)
        val testBody = intent.getStringExtra(EXTRA_TEST_BODY)

        if (!testSender.isNullOrBlank() && !testBody.isNullOrBlank()) {
            val pendingResult = goAsync()
            if (pendingResult != null) {
                // Async mode - running from actual broadcast
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        handleMessage(testSender, testBody, System.currentTimeMillis())
                    } finally {
                        pendingResult.finish()
                    }
                }
            } else {
                // Sync mode - running from tests (goAsync() returns null)
                runBlocking { handleMessage(testSender, testBody, System.currentTimeMillis()) }
            }
            return
        }

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

        val smsReceivedAt = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

        val pendingResult = goAsync()
        if (pendingResult != null) {
            // Async mode - running from actual broadcast
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    for (message in messages) {
                        val sender = message.originatingAddress ?: continue
                        val body = message.messageBody
                        handleMessage(sender, body, smsReceivedAt)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            // Sync mode - should not happen for real SMS, but handle gracefully
            runBlocking {
                for (message in messages) {
                    val sender = message.originatingAddress ?: continue
                    val body = message.messageBody
                    handleMessage(sender, body, smsReceivedAt)
                }
            }
        }
    }

    private suspend fun handleMessage(sender: String, body: String, smsReceivedAt: Long) {
        try {
            val result = paymentProcessor.processMessageFull(body, sender, smsReceivedAt)
            when (result) {
                is MessageProcessResult.PaymentResult -> {
                    Log.d(
                        TAG,
                        "SMS from $sender processed as payment.\nMessage: $body\nParsed: ${result.payment}"
                    )
                }
                is MessageProcessResult.IncomeResult -> {
                    saveIncome(result, body, sender)
                    Log.d(
                        TAG,
                        "SMS from $sender processed as income.\nMessage: $body\nParsed: ${result.income}"
                    )
                }
                is MessageProcessResult.Ignored -> {
                    Log.d(
                        TAG,
                        "SMS from $sender ignored by rule: ${result.ruleName}\nMessage: $body"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error processing SMS from $sender:\n${e.message}\nMessage: $body"
            )
        }
    }

    private suspend fun saveIncome(result: MessageProcessResult.IncomeResult, body: String, sender: String) {
        val context = applicationContext ?: return
        val income = result.income
        val messageHash = HashUtil.computeMessageHash(body, sender)

        val incomeEntity = IncomeEntity(
            amount = income.amount,
            currency = income.currency,
            source = income.source,
            timestamp = income.timestamp,
            balance = income.balance,
            messageHash = messageHash,
            senderAddress = sender,
            receivedAt = System.currentTimeMillis(),
            ruleId = income.ruleId
        )

        val db = BankSmsDatabase.getInstance(context)
        val insertedId = db.incomeDao().insertIncome(incomeEntity)
        if (insertedId == -1L) {
            Log.d(TAG, "Duplicate income skipped for sender $sender")
        }
    }
}
