package com.example.banksmstracker

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.data.Merchant
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.data.Rule
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.parser.SmsReceiver
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.RoomPaymentRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
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
                Rule(
                    pattern = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
                )
            )
            ConfigRepository.updateSender(sender)
        }

        paymentRepository = RoomPaymentRepository(
            BankSmsDatabase.getInstance(context).paymentDao()
        )
    }

    @Test
    @DisplayName("updatePaymentCategory_updatesExistingPayment")
    fun updatePaymentCategoryUpdatesExistingPayment() = runBlocking {
        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK", "Payment 100.00 USD card 1111 Store at 20230901 bal 500.00")
        )

        val payments = waitForPayments(1)
        assertEquals(1, payments.size)
        val paymentId = payments[0].id!!

        // Update category
        paymentRepository.updatePaymentCategory(paymentId, "NewCategory")

        val updatedPayments = paymentRepository.getAllPayments()
        assertEquals("NewCategory", updatedPayments[0].categoryId)
    }

    @Test
    @DisplayName("updatePaymentCategory_canSetCategoryToNull")
    fun updatePaymentCategoryCanSetCategoryToNull() = runBlocking {
        // Create a category first
        val category = ConfigRepository.addCategory()
        category.name = "TestCat"
        category.merchants = mutableListOf(Merchant("Store"))
        ConfigRepository.updateCategory(category)

        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK", "Payment 100.00 USD card 1111 Store at 20230901 bal 500.00")
        )

        val payments = waitForPayments(1)
        val paymentId = payments[0].id!!
        assertEquals("TestCat", payments[0].categoryId)

        // Set category to null
        paymentRepository.updatePaymentCategory(paymentId, null)

        val updatedPayments = paymentRepository.getAllPayments()
        assertNull(updatedPayments[0].categoryId)
    }

    @Test
    @DisplayName("recategorizeAllPayments_updatesCategoriesBasedOnCurrentConfig")
    fun recategorizeAllPaymentsUpdatesCategoriesBasedOnCurrentConfig() = runBlocking {
        // Create initial category
        val category = ConfigRepository.addCategory()
        category.name = "Groceries"
        category.merchants = mutableListOf(Merchant("SuperMarket"))
        ConfigRepository.updateCategory(category)

        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        // Create payments with merchant names
        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK", "Payment 100.00 USD card 1111 SuperMarket at 20230901 bal 500.00")
        )

        // Wait for first payment before sending second
        waitForPayments(1)

        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK", "Payment 200.00 USD card 2222 Electronics at 20230902 bal 300.00")
        )

        val paymentsBeforeUpdate = waitForPayments(2)
        val superMarketPayment = paymentsBeforeUpdate.find { it.merchant == "SuperMarket" }
        assertEquals("Groceries", superMarketPayment?.categoryId)

        // Now add a new category for Electronics
        val techCategory = ConfigRepository.addCategory()
        techCategory.name = "Tech"
        techCategory.merchants = mutableListOf(Merchant("Electronics"))
        ConfigRepository.updateCategory(techCategory)

        // Re-categorize all payments
        val updateCount = ConfigRepository.recategorizeAllPayments()

        val paymentsAfterUpdate = paymentRepository.getAllPayments()
        val electronicsPayment = paymentsAfterUpdate.find { it.merchant == "Electronics" }
        assertEquals("Tech", electronicsPayment?.categoryId)
        assertEquals(1, updateCount) // Only Electronics should be updated
    }

    @Test
    @DisplayName("recategorizeAllPayments_preservesExistingCorrectCategories")
    fun recategorizeAllPaymentsPreservesExistingCorrectCategories() = runBlocking {
        // Create category
        val category = ConfigRepository.addCategory()
        category.name = "Shops"
        category.merchants = mutableListOf(Merchant("Store"))
        ConfigRepository.updateCategory(category)

        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK", "Payment 100.00 USD card 1111 Store at 20230901 bal 500.00")
        )

        val paymentsBefore = waitForPayments(1)
        assertEquals("Shops", paymentsBefore[0].categoryId)

        // Re-categorize (should not change anything since category is already correct)
        val updateCount = ConfigRepository.recategorizeAllPayments()

        val paymentsAfter = paymentRepository.getAllPayments()
        assertEquals("Shops", paymentsAfter[0].categoryId)
        assertEquals(0, updateCount) // Nothing should change
    }

    @Test
    @DisplayName("recategorizeAllPayments_handlesEmptyPaymentList")
    fun recategorizeAllPaymentsHandlesEmptyPaymentList() = runBlocking {
        // No payments in database
        val updateCount = ConfigRepository.recategorizeAllPayments()
        assertEquals(0, updateCount)
    }

    @Test
    @DisplayName("recategorizeAllPayments_handlesMissingMerchant")
    fun recategorizeAllPaymentsHandlesMissingMerchant() = runBlocking {
        // Add a category
        val category = ConfigRepository.addCategory()
        category.name = "TestCat"
        category.merchants = mutableListOf(Merchant("SomeMerchant"))
        ConfigRepository.updateCategory(category)

        val smsReceiver = SmsReceiver()
        val processor = ConfigRepository.getPaymentProcessor()
        smsReceiver.setPaymentProcessorForTest(processor)

        // Create payment with a merchant not in any category
        smsReceiver.onReceive(
            context,
            buildSmsIntent("BANK", "Payment 100.00 USD card 1111 UnknownStore at 20230901 bal 500.00")
        )

        val paymentsBefore = waitForPayments(1)
        assertNull(paymentsBefore[0].categoryId)

        // Re-categorize should not crash and should not change the category
        val updateCount = ConfigRepository.recategorizeAllPayments()
        assertEquals(0, updateCount)

        val paymentsAfter = paymentRepository.getAllPayments()
        assertNull(paymentsAfter[0].categoryId)
    }
}
