package com.example.banksmstracker.parser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import android.util.Log
import com.example.banksmstracker.processor.PaymentProcessor
import com.example.banksmstracker.repository.ConfigRepository

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        const val EXTRA_TEST_SENDER =
            "com.example.banksmstracker.parser.SmsReceiver.EXTRA_TEST_SENDER"
        const val EXTRA_TEST_BODY =
            "com.example.banksmstracker.parser.SmsReceiver.EXTRA_TEST_BODY"
    }

    private lateinit var paymentProcessor: PaymentProcessor

    // For testing only TODO remove it from the prod version
    fun setPaymentProcessorForTest(processor: PaymentProcessor) {
        this.paymentProcessor = processor
    }

    private fun initializePaymentProcessor(context: Context) {
        if (!::paymentProcessor.isInitialized) {
            ConfigRepository.load(context.applicationContext as android.app.Application)
            paymentProcessor = ConfigRepository.getPaymentProcessor()
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != "android.provider.Telephony.SMS_RECEIVED") return

        initializePaymentProcessor(context)

        val testSender = intent.getStringExtra(EXTRA_TEST_SENDER)
        val testBody = intent.getStringExtra(EXTRA_TEST_BODY)

        if (!testSender.isNullOrBlank() && !testBody.isNullOrBlank()) {
            handleMessage(testSender, testBody)
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

        for (message in messages) {
            val sender = message.originatingAddress ?: continue
            val body = message.messageBody

            handleMessage(sender, body)
        }
    }

    private fun handleMessage(sender: String, body: String) {
        try {
            val payment = paymentProcessor.processMessage(body, sender)
            Log.d(
                TAG,
                "SMS from $sender processed successfully.\nMessage: $body\nParsed payment: $payment"
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error processing SMS from $sender:\n${e.message}\nMessage: $body"
            )
        }
    }
}
