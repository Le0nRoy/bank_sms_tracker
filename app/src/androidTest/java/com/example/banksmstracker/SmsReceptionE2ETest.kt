package com.example.banksmstracker

import android.content.Intent
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.data.PaymentRegexRule
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.parser.SmsReceiver
import com.example.banksmstracker.processor.PaymentProcessor
import com.example.banksmstracker.repository.InMemoryPaymentRepository
import com.example.banksmstracker.repository.ConfigRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.collections.listOf

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmsReceiverE2ETest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val TAG = "SmsReceiverE2ETest"

    private fun buildSmsIntent(sender: String, body: String): Intent {
        return Intent("android.provider.Telephony.SMS_RECEIVED").apply {
            putExtra(SmsReceiver.EXTRA_TEST_SENDER, sender)
            putExtra(SmsReceiver.EXTRA_TEST_BODY, body)
        }
    }

    @BeforeAll
    fun setup() {
        Log.i(TAG, "Setting up tests, loading config repository…")
        ConfigRepository.load(
            context.applicationContext as android.app.Application
        )
    }

    @Test
    fun testValidSms_isParsedAndSaved() {
        val repository = InMemoryPaymentRepository()

        val sender = Sender(
            name = "MyBank",
            addresses = listOf("BANK"),
            rules = listOf(
                PaymentRegexRule(
                    regex = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
                )
            )
        )

        val categories = listOf(
            Category(
                name = "Shops",
                merchants = listOf("Amazon").toMutableList()
            )
        )

        val processor = PaymentProcessor(
            senders = listOf(sender),
            categories = categories,
            paymentRepository = repository
        )

        val smsReceiver = SmsReceiver()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body = "Payment 123.45 USD card 1234 Amazon at 20230905 bal 500.00"
        val intent = buildSmsIntent("BANK", body)

        smsReceiver.onReceive(context, intent)

        val allPayments = repository.getAllPayments()
        assertEquals(1, allPayments.size)
        val payment: Payment = allPayments[0]

        assertEquals(123.45, payment.amount)
        assertEquals("USD", payment.currency)
        assertEquals("1234", payment.card)
        assertEquals("Amazon", payment.merchant)
        assertEquals("Shops", payment.categoryId)
    }

    @Test
    fun testSmsFromUnknownSender_isIgnored() {
        val repository = InMemoryPaymentRepository()

        val processor = PaymentProcessor(
            senders = emptyList(),
            categories = emptyList(),
            paymentRepository = repository
        )

        val smsReceiver = SmsReceiver()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body = "Payment 123.45 USD card 1234 Amazon at 20230905 bal 500.00"
        val intent = buildSmsIntent("SPAMMER", body)

        smsReceiver.onReceive(context, intent)

        val allPayments = repository.getAllPayments()
        assertTrue(allPayments.isEmpty())
    }

    @Test
    fun testInvalidSmsFromValidSender_isNotSaved() {
        val repository = InMemoryPaymentRepository()

        val sender = Sender(
            name = "MyBank",
            addresses = listOf("BANK"),
            rules = listOf(
                PaymentRegexRule(
                    regex = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
                )
            )
        )

        val processor = PaymentProcessor(
            senders = listOf(sender),
            categories = emptyList(),
            paymentRepository = repository
        )

        val smsReceiver = SmsReceiver()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body = "This is not a valid payment format"
        val intent = buildSmsIntent("BANK", body)

        smsReceiver.onReceive(context, intent)

        val allPayments = repository.getAllPayments()
        assertTrue(allPayments.isEmpty())
    }
}
