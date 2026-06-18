package com.example.banksmstracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.banksmstracker.R
import com.example.banksmstracker.ui.ApplyRulesActivity
import com.example.banksmstracker.ui.RegexBuilderActivity

object NotificationHelper {

    const val UNMATCHED_SMS_CHANNEL_ID = "unmatched_sms_channel"
    private const val UNMATCHED_CHANNEL_NAME = "Unmatched SMS"
    private const val BODY_PREVIEW_LENGTH = 100

    const val EXCHANGE_RATE_CHANNEL_ID = "exchange_rate_channel"
    private const val EXCHANGE_RATE_CHANNEL_NAME = "Exchange Rate Errors"
    private const val EXCHANGE_RATE_NOTIFICATION_ID = 2001

    fun createUnmatchedSmsChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val prefs = context.getSharedPreferences(
                com.example.banksmstracker.BankSmsTrackerApp.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val soundEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_SOUND, true)
            val vibrationEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_VIBRATION, true)

            val channel = NotificationChannel(
                UNMATCHED_SMS_CHANNEL_ID,
                UNMATCHED_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.unmatched_sms_channel_desc)
                if (!soundEnabled) {
                    setSound(null, null)
                }
                enableVibration(vibrationEnabled)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun sendUnmatchedSmsNotification(context: Context, senderAddress: String, messageBody: String) {
        val prefs = context.getSharedPreferences(
            com.example.banksmstracker.BankSmsTrackerApp.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        if (!prefs.getBoolean(KEY_NOTIFICATIONS_UNMATCHED_SMS, true)) return

        createUnmatchedSmsChannel(context)

        val intent = Intent(context, RegexBuilderActivity::class.java).apply {
            putExtra(ApplyRulesActivity.EXTRA_SAMPLE_SMS, messageBody)
            putExtra(ApplyRulesActivity.EXTRA_SENDER_ADDRESS, senderAddress)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            senderAddress.hashCode() xor messageBody.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val preview = if (messageBody.length > BODY_PREVIEW_LENGTH) {
            messageBody.substring(0, BODY_PREVIEW_LENGTH)
        } else {
            messageBody
        }

        val notification = NotificationCompat.Builder(context, UNMATCHED_SMS_CHANNEL_ID)
            .setContentTitle(
                context.getString(R.string.unmatched_sms_notification_title, senderAddress)
            )
            .setContentText(preview)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        val notificationId = senderAddress.hashCode() xor messageBody.hashCode()
        manager.notify(notificationId, notification)
    }

    fun sendExchangeRateErrorNotification(context: Context, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                EXCHANGE_RATE_CHANNEL_ID,
                EXCHANGE_RATE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.exchange_rate_channel_desc)
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, EXCHANGE_RATE_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.exchange_rate_error_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(EXCHANGE_RATE_NOTIFICATION_ID, notification)
    }

    const val KEY_NOTIFICATIONS_UNMATCHED_SMS = "notifications_unmatched_sms"
    const val KEY_NOTIFICATIONS_SOUND = "notifications_sound"
    const val KEY_NOTIFICATIONS_VIBRATION = "notifications_vibration"
}
