package com.example.banksmstracker.parser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import android.util.Log
import android.widget.Toast

val allowedSenders = listOf("YourBank1", "YourBank2")
val allowedKeywords = listOf("BankName", "VISA")


class SmsReceiver : BroadcastReceiver() {

    fun parseMessage(context: Context?, sender: String, body: String) {
        Log.d("SmsReceiver", "SMS received from $sender: '$body'")
        Toast.makeText(context, "SMS from $sender: '$body'", Toast.LENGTH_LONG).show()
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != "android.provider.Telephony.SMS_RECEIVED") return

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

            val allowedSenders = listOf("YourBank1", "YourBank2")
            val allowedKeywords = listOf("BankName", "VISA")

            if (sender in allowedSenders || allowedKeywords.any { body.contains(it, ignoreCase = true) }) {
                // TODO: parse and categorize
                parseMessage(context, sender, body)
            }
        }
    }
}
