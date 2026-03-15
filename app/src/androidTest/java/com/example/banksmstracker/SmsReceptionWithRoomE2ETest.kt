package com.example.banksmstracker

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.data.Merchant
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.parser.SmsReceiver
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.RoomPaymentRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * E2E test for SMS reception using Room database for persistence.
 * Tests the complete flow from SMS reception to payment storage in database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmsReceptionWithRoomE2ETest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var paymentRepository: RoomPaymentRepository

    private fun buildSmsIntent(sender: String, body: String): Intent =
        Intent("android.provider.Telephony.SMS_RECEIVED").apply {
            putExtra(SmsReceiver.EXTRA_TEST_SENDER, sender)
            putExtra(SmsReceiver.EXTRA_TEST_BODY, body)
        }

    /**
     * Wait for payments to appear in repository with polling.
     * onReceive() spawns async coroutine, so we need to wait for it to complete.
     */
    private suspend fun waitForPayments(
        expectedCount: Int,
        timeoutMs: Long = 5000,
        pollIntervalMs: Long = 100
    ): List<Payment> {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val payments = paymentRepository.getAllPayments()
            if (payments.size >= expectedCount) return payments
            delay(pollIntervalMs)
        }
        return paymentRepository.getAllPayments()
    }

    @BeforeEach
    fun setup() {
        // Reset and reload config repository
        ConfigRepository.reset()
        ConfigRepository.load(context.applicationContext as android.app.Application)

        // Clear all data to ensure test isolation
        runBlocking {
            ConfigRepository.clearAllData()
        }

        // Reload after clearing (without seeding)
        ConfigRepository.reset()
        ConfigRepository.load(context.applicationContext as android.app.Application, seedIfEmpty = false)

        // Set up test sender and category in config
        runBlocking {
            val sender = ConfigRepository.addSender()
            sender.name = "Test Bank"
            sender.addresses = mutableListOf("BANK")
            sender.rules = mutableListOf(
                com.example.banksmstracker.data.Rule(
                    pattern = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
                )
            )
            ConfigRepository.updateSender(sender)

            val category = ConfigRepository.addCategory()
            category.name = "Shops"
            category.merchants = mutableListOf(Merchant("Amazon"))
            ConfigRepository.updateCategory(category)
        }

        paymentRepository = RoomPaymentRepository(
            BankSmsDatabase.getInstance(context).paymentDao()
        )
    }

    @Test
    @DisplayName("validSms_isParsedAndSavedToDatabase")
    fun validSmsIsParsedAndSavedToDatabase() = runBlocking {
        val smsReceiver = SmsReceiver()
        // Use the actual processor from ConfigRepository
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body = "Payment 123.45 USD card 1234 Amazon at 20230905 bal 500.00"
        val intent = buildSmsIntent("BANK", body)

        smsReceiver.onReceive(context, intent)

        // Wait for async processing to complete
        val allPayments = waitForPayments(1)

        assertEquals(1, allPayments.size)
        val payment: Payment = allPayments[0]

        assertEquals(123.45, payment.amount)
        assertEquals("USD", payment.currency)
        assertEquals("1234", payment.card)
        assertEquals("Amazon", payment.merchant)
        assertEquals("Shops", payment.categoryId)
        assertNotNull(payment.id)
    }

    @Test
    @DisplayName("duplicateSms_isNotSavedTwice")
    fun duplicateSmsIsNotSavedTwice() = runBlocking {
        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body = "Payment 200.00 USD card 5678 Shop at 20230906 bal 300.00"
        val intent = buildSmsIntent("BANK", body)

        // Send same message twice
        smsReceiver.onReceive(context, intent)
        waitForPayments(1)
        smsReceiver.onReceive(context, intent)
        delay(500) // Give time for second processing attempt

        val allPayments = paymentRepository.getAllPayments()

        // Should only have one payment despite sending twice
        assertEquals(1, allPayments.size, "Duplicate SMS should not create duplicate payment")
    }

    @Test
    @DisplayName("multipleValidSms_areAllSavedToDatabase")
    fun multipleValidSmsAreAllSavedToDatabase() = runBlocking {
        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body1 = "Payment 100.00 USD card 1111 Store1 at 20230907 bal 400.00"
        val body2 = "Payment 200.00 USD card 2222 Store2 at 20230908 bal 200.00"

        smsReceiver.onReceive(context, buildSmsIntent("BANK", body1))
        waitForPayments(1)
        smsReceiver.onReceive(context, buildSmsIntent("BANK", body2))

        val allPayments = waitForPayments(2)

        assertEquals(2, allPayments.size)
        val amounts = allPayments.map { it.amount }.sorted()
        assert(amounts.contains(100.0) || amounts.contains(200.0))
    }

    @Test
    @DisplayName("paymentIsCategorizedCorrectly")
    fun paymentIsCategorizedCorrectly() = runBlocking {
        // Add TestStore to Shops category
        val category = ConfigRepository.getCategories().firstOrNull { it.name == "Shops" }
        if (category != null) {
            category.merchants.add(Merchant("TestStore"))
            ConfigRepository.updateCategory(category)
        }

        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body = "Payment 150.00 USD card 9999 TestStore at 20230909 bal 250.00"
        smsReceiver.onReceive(context, buildSmsIntent("BANK", body))

        val allPayments = waitForPayments(1)

        val testStorePayment = allPayments.find { it.merchant == "TestStore" }
        assertNotNull(testStorePayment)
        assertEquals("Shops", testStorePayment?.categoryId)
    }
}
