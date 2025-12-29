package com.example.banksmstracker

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.parser.SmsReceiver
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.RoomPaymentRepository
import com.example.banksmstracker.database.BankSmsDatabase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit5.android.core.J5SuiteExtension

/**
 * E2E test for SMS reception using Room database for persistence.
 * Tests the complete flow from SMS reception to payment storage in database.
 */
@ExtendWith(J5SuiteExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmsReceptionWithRoomE2ETest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun buildSmsIntent(sender: String, body: String): Intent {
        return Intent("android.provider.Telephony.SMS_RECEIVED").apply {
            putExtra(SmsReceiver.EXTRA_TEST_SENDER, sender)
            putExtra(SmsReceiver.EXTRA_TEST_BODY, body)
        }
    }

    @BeforeAll
    fun setup() {
        // Load config repository
        ConfigRepository.load(context.applicationContext as android.app.Application)
        
        // Set up test sender and category in config
        runBlocking {
            val sender = ConfigRepository.addSender()
            sender.name = "Test Bank"
            sender.addresses = mutableListOf("BANK")
            sender.rules = mutableListOf(
                com.example.banksmstracker.data.PaymentRegexRule(
                    regex = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
                )
            )
            ConfigRepository.updateSender(sender)
            
            val category = ConfigRepository.addCategory()
            category.name = "Shops"
            category.merchants = mutableListOf("Amazon")
            ConfigRepository.updateCategory(category)
        }
    }

    @Test
    fun `validSms_isParsedAndSavedToDatabase`() {
        val smsReceiver = SmsReceiver()
        // Use the actual processor from ConfigRepository
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)
        
        val body = "Payment 123.45 USD card 1234 Amazon at 20230905 bal 500.00"
        val intent = buildSmsIntent("BANK", body)
        
        smsReceiver.onReceive(context, intent)
        
        // Verify payment was saved to database
        val paymentRepository = RoomPaymentRepository(
            BankSmsDatabase.getInstance(context).paymentDao()
        )
        val allPayments = paymentRepository.getAllPayments()
        
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
    fun `duplicateSms_isNotSavedTwice`() {
        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)
        
        val body = "Payment 200.00 USD card 5678 Shop at 20230906 bal 300.00"
        val intent = buildSmsIntent("BANK", body)
        
        // Send same message twice
        smsReceiver.onReceive(context, intent)
        smsReceiver.onReceive(context, intent)
        
        val paymentRepository = RoomPaymentRepository(
            BankSmsDatabase.getInstance(context).paymentDao()
        )
        val allPayments = paymentRepository.getAllPayments()
        
        // Should only have one payment despite sending twice
        assertEquals(1, allPayments.size, "Duplicate SMS should not create duplicate payment")
    }

    @Test
    fun `multipleValidSms_areAllSavedToDatabase`() {
        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)
        
        val body1 = "Payment 100.00 USD card 1111 Store1 at 20230907 bal 400.00"
        val body2 = "Payment 200.00 USD card 2222 Store2 at 20230908 bal 200.00"
        
        smsReceiver.onReceive(context, buildSmsIntent("BANK", body1))
        smsReceiver.onReceive(context, buildSmsIntent("BANK", body2))
        
        val paymentRepository = RoomPaymentRepository(
            BankSmsDatabase.getInstance(context).paymentDao()
        )
        val allPayments = paymentRepository.getAllPayments()
        
        assertEquals(2, allPayments.size)
        val amounts = allPayments.map { it.amount }.sorted()
        assert(amounts.contains(100.0) || amounts.contains(200.0))
    }

    @Test
    fun `paymentIsCategorizedCorrectly`() {
        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)
        
        // Add more merchants to test categorization
        runBlocking {
            val category = ConfigRepository.getCategories().firstOrNull { it.name == "Shops" }
            if (category != null) {
                category.merchants.add("TestStore")
                ConfigRepository.updateCategory(category)
            }
        }
        
        // Reload processor to get updated config
        ConfigRepository.reset()
        ConfigRepository.load(context.applicationContext as android.app.Application)
        val updatedProcessor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(updatedProcessor)
        
        val body = "Payment 150.00 USD card 9999 TestStore at 20230909 bal 250.00"
        smsReceiver.onReceive(context, buildSmsIntent("BANK", body))
        
        val paymentRepository = RoomPaymentRepository(
            BankSmsDatabase.getInstance(context).paymentDao()
        )
        val allPayments = paymentRepository.getAllPayments()
        
        val testStorePayment = allPayments.find { it.merchant == "TestStore" }
        assertNotNull(testStorePayment)
        assertEquals("Shops", testStorePayment?.categoryId)
    }
}
