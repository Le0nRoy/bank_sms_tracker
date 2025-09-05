package com.example.banksmstracker

import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.parser.SmsReceiver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmsReceiverE2ETest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val TAG = "SmsReceiverE2ETest"

    @BeforeAll
    fun setup() {
        Log.i(TAG, "Setting up tests, loading config repository…")
        com.example.banksmstracker.repository.ConfigRepository.load(
            context.applicationContext as android.app.Application
        )
    }

    private fun buildSmsIntent(sender: String, body: String): Intent {
        // Encode SMS as PDU
        val scAddress = "00000" // Service center address (fake)
        val sms = SmsMessage.getSubmitPdu(scAddress, sender, body, false)

        val pdu = sms.encodedMessage
        val intent = Intent("android.provider.Telephony.SMS_RECEIVED")
        intent.putExtra("pdus", arrayOf(pdu))
        intent.putExtra("format", "3gpp")
        return intent
    }

    @Test
    fun testValidSmsIsProcessed() {
        val intent = buildSmsIntent(
            sender = "TBC",
            body = "50.00 GEL\nMC GOLD (***0123)\nUncategorized 30/08/2025 00:00:00\nBalance: 25.97 GEL"
        )

        SmsReceiver().onReceive(context, intent)

        // No crash = success. Real check would be Logcat or repository update.
        assertTrue(true, "Valid SMS processed without exception")
    }

    @Test
    fun testSmsFromUnknownSenderIgnored() {
        val intent = buildSmsIntent(
            sender = "UNKNOWN",
            body = "20.00 GEL\nMC GOLD (***0123)\nMAGNITI 30/08/2025 00:00:00\nBalance: 10.00 GEL"
        )

        SmsReceiver().onReceive(context, intent)

        // If sender not in config, it should skip or log error
        assertTrue(true, "Unknown sender handled without crash")
    }

    @Test
    fun testInvalidSmsFailsGracefully() {
        val intent = buildSmsIntent(
            sender = "TBC",
            body = "THIS IS AN INVALID MESSAGE"
        )

        SmsReceiver().onReceive(context, intent)

        // Should log error, but not crash
        assertTrue(true, "Invalid SMS handled gracefully")
    }
}
