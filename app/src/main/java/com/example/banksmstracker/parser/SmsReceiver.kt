package com.example.banksmstracker.parser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import android.util.Log
import com.example.banksmstracker.processor.PaymentProcessor
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.serializer.ConfigLoader

class SmsReceiver : BroadcastReceiver() {

    private lateinit var paymentProcessor: PaymentProcessor

    private fun initializePaymentProcessor(context: Context) {
        if (!::paymentProcessor.isInitialized) {
            ConfigRepository.load(context.applicationContext as android.app.Application)
            paymentProcessor = ConfigLoader.createPaymentProcessor(ConfigRepository.config)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != "android.provider.Telephony.SMS_RECEIVED") return

        initializePaymentProcessor(context)

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

        for (message in messages) {
            val sender = message.originatingAddress ?: continue
            val body = message.messageBody

            try {
                val payment = paymentProcessor.processMessage(body, sender)
                Log.d(
                    "SmsReceiver", "SMS from $sender processed successfully." +
                        "\nMessage: $body" +
                        "\nParsed payment: $payment"
                )
            } catch (e: Exception) {
                Log.e(
                    "SmsReceiver", "Error processing SMS from $sender:\n" +
                        "${e.message}" +
                        "\nMessage: $body"
                )
            }
        }
    }
}
