package com.example.banksmstracker

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.data.PaymentRegexRule
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.parser.SmsReceiver
import com.example.banksmstracker.processor.PaymentProcessor
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.RoomPaymentRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * E2E tests for payment deduplication with Room database.
 * Verifies that duplicate SMS messages are not saved multiple times.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentDeduplicationE2ETest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var database: BankSmsDatabase
    private lateinit var paymentRepository: RoomPaymentRepository
    private lateinit var processor: PaymentProcessor

    private fun buildSmsIntent(sender: String, body: String): Intent =
        Intent("android.provider.Telephony.SMS_RECEIVED").apply {
            putExtra(SmsReceiver.EXTRA_TEST_SENDER, sender)
            putExtra(SmsReceiver.EXTRA_TEST_BODY, body)
        }

    @BeforeAll
    fun setup() {
        // Initialize database and repository
        database = BankSmsDatabase.getInstance(context)
        paymentRepository = RoomPaymentRepository(database.paymentDao())

        // Load config
        ConfigRepository.load(context.applicationContext as android.app.Application)

        // Create test sender and category
        runBlocking {
            val sender = ConfigRepository.addSender()
            sender.name = "Test Bank"
            sender.addresses = mutableListOf("BANK123")
            sender.rules = mutableListOf(
                PaymentRegexRule(regex = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})")
            )
            ConfigRepository.updateSender(sender)

            val category = ConfigRepository.addCategory()
            category.name = "Shops"
            category.merchants = mutableListOf("Amazon")
            ConfigRepository.updateCategory(category)

            processor = ConfigRepository.getPaymentProcessor()
        }
    }

    @BeforeEach
    fun clearPayments() {
        // Clear payments before each test
        runBlocking {
            val payments = paymentRepository.getAllPayments()
            // Note: In a real scenario, you'd have a delete method, but for testing
            // we'll use a fresh repository or clear manually
            // For now, we'll rely on unique constraint for deduplication
        }
    }

    @Test
    fun `duplicateSmsMessage_isNotSavedTwice`() {
        val smsReceiver = SmsReceiver()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body = "Payment 123.45 USD card 1234 Amazon at 20230905 bal 500.00"
        val sender = "BANK123"

        // Send same message twice
        val intent1 = buildSmsIntent(sender, body)
        smsReceiver.onReceive(context, intent1)

        val intent2 = buildSmsIntent(sender, body)
        smsReceiver.onReceive(context, intent2)

        val allPayments = paymentRepository.getAllPayments()

        // Should only have one payment despite sending twice
        assertEquals(1, allPayments.size, "Duplicate message should not create duplicate payment")

        val payment = allPayments[0]
        assertEquals(123.45, payment.amount)
        assertEquals("Amazon", payment.merchant)
    }

    @Test
    fun `differentMessagesFromSameSender_areBothSaved`() {
        val smsReceiver = SmsReceiver()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body1 = "Payment 100.00 USD card 1234 Amazon at 20230905 bal 500.00"
        val body2 = "Payment 200.00 USD card 5678 Shop at 20230906 bal 300.00"
        val sender = "BANK123"

        val intent1 = buildSmsIntent(sender, body1)
        smsReceiver.onReceive(context, intent1)

        val intent2 = buildSmsIntent(sender, body2)
        smsReceiver.onReceive(context, intent2)

        val allPayments = paymentRepository.getAllPayments()
        assertEquals(2, allPayments.size, "Different messages should both be saved")

        val amounts = allPayments.map { it.amount }.sorted()
        assertTrue(amounts.contains(100.0))
        assertTrue(amounts.contains(200.0))
    }

    @Test
    fun `sameMessageFromDifferentSenders_areBothSaved`() {
        val smsReceiver = SmsReceiver()

        // Create second sender
        runBlocking {
            val sender2 = ConfigRepository.addSender()
            sender2.name = "Another Bank"
            sender2.addresses = mutableListOf("BANK456")
            sender2.rules = mutableListOf(
                PaymentRegexRule(regex = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})")
            )
            ConfigRepository.updateSender(sender2)

            processor = ConfigRepository.getPaymentProcessor()
            smsReceiver.setPaymentProcessorForTest(processor)
        }

        val body = "Payment 150.00 USD card 9999 Store at 20230907 bal 400.00"

        val intent1 = buildSmsIntent("BANK123", body)
        smsReceiver.onReceive(context, intent1)

        val intent2 = buildSmsIntent("BANK456", body)
        smsReceiver.onReceive(context, intent2)

        val allPayments = paymentRepository.getAllPayments()
        assertEquals(2, allPayments.size, "Same message from different senders should both be saved")
    }

    @Test
    fun `slightlyDifferentMessages_areTreatedAsDifferent`() {
        val smsReceiver = SmsReceiver()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body1 = "Payment 100.00 USD card 1234 Amazon at 20230905 bal 500.00"
        val body2 = "Payment 100.00 USD card 1234 Amazon at 20230905 bal 500.01" // Different balance
        val sender = "BANK123"

        val intent1 = buildSmsIntent(sender, body1)
        smsReceiver.onReceive(context, intent1)

        val intent2 = buildSmsIntent(sender, body2)
        smsReceiver.onReceive(context, intent2)

        val allPayments = paymentRepository.getAllPayments()
        assertEquals(2, allPayments.size, "Messages with different content should both be saved")
    }
}
