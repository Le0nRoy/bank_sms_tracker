package com.example.banksmstracker

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.data.PaymentRegexRule
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.parser.SmsReceiver
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.RoomPaymentRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * E2E tests for category cascade and re-categorization functionality.
 * Tests updating payment categories when category configuration changes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryCascadeE2ETest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var paymentRepository: RoomPaymentRepository

    private fun buildSmsIntent(sender: String, body: String): Intent =
        Intent("android.provider.Telephony.SMS_RECEIVED").apply {
            putExtra(SmsReceiver.EXTRA_TEST_SENDER, sender)
            putExtra(SmsReceiver.EXTRA_TEST_BODY, body)
        }

    @BeforeEach
    fun setup() {
        ConfigRepository.reset()
        ConfigRepository.load(context.applicationContext as android.app.Application)

        runBlocking {
            ConfigRepository.clearAllData()
        }

        ConfigRepository.reset()
        ConfigRepository.load(context.applicationContext as android.app.Application, seedIfEmpty = false)

        runBlocking {
            val sender = ConfigRepository.addSender()
            sender.name = "Test Bank"
            sender.addresses = mutableListOf("BANK")
            sender.rules = mutableListOf(
                PaymentRegexRule(
                    regex = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
                )
            )
            ConfigRepository.updateSender(sender)
        }

        paymentRepository = RoomPaymentRepository(
            BankSmsDatabase.getInstance(context).paymentDao()
        )
    }

    @Test
    fun `updatePaymentCategory_updatesExistingPayment`() {
        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK", "Payment 100.00 USD card 1111 Store at 20230901 bal 500.00")
        )

        val payments = runBlocking { paymentRepository.getAllPayments() }
        assertEquals(1, payments.size)
        val paymentId = payments[0].id!!

        // Update category
        runBlocking { paymentRepository.updatePaymentCategory(paymentId, "NewCategory") }

        val updatedPayments = runBlocking { paymentRepository.getAllPayments() }
        assertEquals("NewCategory", updatedPayments[0].categoryId)
    }

    @Test
    fun `updatePaymentCategory_canSetCategoryToNull`() {
        // Create a category first
        runBlocking {
            val category = ConfigRepository.addCategory()
            category.name = "TestCat"
            category.merchants = mutableListOf("Store")
            ConfigRepository.updateCategory(category)
        }

        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK", "Payment 100.00 USD card 1111 Store at 20230901 bal 500.00")
        )

        val payments = runBlocking { paymentRepository.getAllPayments() }
        val paymentId = payments[0].id!!
        assertEquals("TestCat", payments[0].categoryId)

        // Set category to null
        runBlocking { paymentRepository.updatePaymentCategory(paymentId, null) }

        val updatedPayments = runBlocking { paymentRepository.getAllPayments() }
        assertNull(updatedPayments[0].categoryId)
    }

    @Test
    fun `recategorizeAllPayments_updatesCategoriesBasedOnCurrentConfig`() {
        // Create initial category
        runBlocking {
            val category = ConfigRepository.addCategory()
            category.name = "Groceries"
            category.merchants = mutableListOf("SuperMarket")
            ConfigRepository.updateCategory(category)
        }

        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        // Create payments with merchant names
        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK", "Payment 100.00 USD card 1111 SuperMarket at 20230901 bal 500.00")
        )
        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK", "Payment 200.00 USD card 2222 Electronics at 20230902 bal 300.00")
        )

        val paymentsBeforeUpdate = runBlocking { paymentRepository.getAllPayments() }
        val superMarketPayment = paymentsBeforeUpdate.find { it.merchant == "SuperMarket" }
        assertEquals("Groceries", superMarketPayment?.categoryId)

        // Now add a new category for Electronics
        runBlocking {
            val category = ConfigRepository.addCategory()
            category.name = "Tech"
            category.merchants = mutableListOf("Electronics")
            ConfigRepository.updateCategory(category)
        }

        // Re-categorize all payments
        val updateCount = runBlocking { ConfigRepository.recategorizeAllPayments() }

        val paymentsAfterUpdate = runBlocking { paymentRepository.getAllPayments() }
        val electronicsPayment = paymentsAfterUpdate.find { it.merchant == "Electronics" }
        assertEquals("Tech", electronicsPayment?.categoryId)
        assertEquals(1, updateCount) // Only Electronics should be updated
    }

    @Test
    fun `recategorizeAllPayments_preservesExistingCorrectCategories`() {
        // Create category
        runBlocking {
            val category = ConfigRepository.addCategory()
            category.name = "Shops"
            category.merchants = mutableListOf("Store")
            ConfigRepository.updateCategory(category)
        }

        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK", "Payment 100.00 USD card 1111 Store at 20230901 bal 500.00")
        )

        val paymentsBefore = runBlocking { paymentRepository.getAllPayments() }
        assertEquals("Shops", paymentsBefore[0].categoryId)

        // Re-categorize (should not change anything since category is already correct)
        val updateCount = runBlocking { ConfigRepository.recategorizeAllPayments() }

        val paymentsAfter = runBlocking { paymentRepository.getAllPayments() }
        assertEquals("Shops", paymentsAfter[0].categoryId)
        assertEquals(0, updateCount) // Nothing should change
    }

    @Test
    fun `recategorizeAllPayments_handlesEmptyPaymentList`() {
        // No payments in database
        val updateCount = runBlocking { ConfigRepository.recategorizeAllPayments() }
        assertEquals(0, updateCount)
    }

    @Test
    fun `recategorizeAllPayments_handlesMissingMerchant`() {
        // Add a category
        runBlocking {
            val category = ConfigRepository.addCategory()
            category.name = "TestCat"
            category.merchants = mutableListOf("SomeMerchant")
            ConfigRepository.updateCategory(category)
        }

        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        // Create payment with a merchant not in any category
        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK", "Payment 100.00 USD card 1111 UnknownStore at 20230901 bal 500.00")
        )

        val paymentsBefore = runBlocking { paymentRepository.getAllPayments() }
        assertNull(paymentsBefore[0].categoryId)

        // Re-categorize should not crash and should not change the category
        val updateCount = runBlocking { ConfigRepository.recategorizeAllPayments() }
        assertEquals(0, updateCount)

        val paymentsAfter = runBlocking { paymentRepository.getAllPayments() }
        assertNull(paymentsAfter[0].categoryId)
    }
}
