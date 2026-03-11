package com.example.banksmstracker.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.data.Rule
import com.example.banksmstracker.database.BankSmsDatabase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Instrumented tests for ConfigRepository database operations.
 * Tests adding, updating, and retrieving categories and senders from Room database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigRepositoryRoomTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @BeforeEach
    fun setUp() {
        // First reset to close any existing database connection
        ConfigRepository.reset()
        // Load fresh instance
        ConfigRepository.load(context.applicationContext as android.app.Application)
        // Clear all data to ensure test isolation
        runBlocking {
            ConfigRepository.clearAllData()
        }
        // Reload after clearing to have a fresh empty config (without seeding)
        ConfigRepository.reset()
        ConfigRepository.load(context.applicationContext as android.app.Application, seedIfEmpty = false)
    }

    @AfterEach
    fun tearDown() {
        ConfigRepository.reset()
    }

    @Test
    @DisplayName("addCategory_createsNewCategoryInDatabase")
    fun addCategoryCreatesNewCategoryInDatabase() = runBlocking {
        ConfigRepository.load(context.applicationContext as android.app.Application)

        val category = ConfigRepository.addCategory()

        assertNotNull(category.id)
        val categories = ConfigRepository.getCategories()
        assertEquals(1, categories.size)
        assertEquals(category.id, categories[0].id)
    }

    @Test
    @DisplayName("updateCategory_persistsChangesToDatabase")
    fun updateCategoryPersistsChangesToDatabase() = runBlocking {
        ConfigRepository.load(context.applicationContext as android.app.Application)

        val category = ConfigRepository.addCategory()
        category.name = "Groceries"
        category.merchants = mutableListOf("Supermarket", "Grocery Store")

        ConfigRepository.updateCategory(category)

        val categories = ConfigRepository.getCategories()
        assertEquals(1, categories.size)
        assertEquals("Groceries", categories[0].name)
        assertEquals(2, categories[0].merchants.size)
        assertTrue(categories[0].merchants.contains("Supermarket"))
        assertTrue(categories[0].merchants.contains("Grocery Store"))
    }

    @Test
    @DisplayName("updateCategory_filtersEmptyMerchants")
    fun updateCategoryFiltersEmptyMerchants() = runBlocking {
        ConfigRepository.load(context.applicationContext as android.app.Application)

        val category = ConfigRepository.addCategory()
        category.name = "Test Category"
        category.merchants = mutableListOf("Merchant1", "", "   ", "Merchant2")

        ConfigRepository.updateCategory(category)

        val categories = ConfigRepository.getCategories()
        assertEquals(2, categories[0].merchants.size)
        assertTrue(categories[0].merchants.contains("Merchant1"))
        assertTrue(categories[0].merchants.contains("Merchant2"))
    }

    @Test
    @DisplayName("updateCategory_replacesExistingMerchants")
    fun updateCategoryReplacesExistingMerchants() = runBlocking {
        ConfigRepository.load(context.applicationContext as android.app.Application)

        val category = ConfigRepository.addCategory()
        category.name = "Category"
        category.merchants = mutableListOf("Old Merchant")
        ConfigRepository.updateCategory(category)

        category.merchants = mutableListOf("New Merchant 1", "New Merchant 2")
        ConfigRepository.updateCategory(category)

        val categories = ConfigRepository.getCategories()
        assertEquals(2, categories[0].merchants.size)
        assertTrue(categories[0].merchants.contains("New Merchant 1"))
        assertTrue(categories[0].merchants.contains("New Merchant 2"))
        assertTrue(!categories[0].merchants.contains("Old Merchant"))
    }

    @Test
    @DisplayName("addSender_createsNewSenderInDatabase")
    fun addSenderCreatesNewSenderInDatabase() = runBlocking {
        ConfigRepository.load(context.applicationContext as android.app.Application)

        val sender = ConfigRepository.addSender()

        assertNotNull(sender.id)
        val senders = ConfigRepository.getSenders()
        assertEquals(1, senders.size)
        assertEquals(sender.id, senders[0].id)
    }

    @Test
    @DisplayName("updateSender_persistsChangesToDatabase")
    fun updateSenderPersistsChangesToDatabase() = runBlocking {
        ConfigRepository.load(context.applicationContext as android.app.Application)

        val sender = ConfigRepository.addSender()
        sender.name = "My Bank"
        sender.addresses = mutableListOf("12345", "67890")
        sender.rules = mutableListOf(
            Rule(pattern = "Payment (\\d+\\.\\d{2})")
        )

        ConfigRepository.updateSender(sender)

        val senders = ConfigRepository.getSenders()
        assertEquals(1, senders.size)
        assertEquals("My Bank", senders[0].name)
        assertEquals(2, senders[0].addresses.size)
        assertTrue(senders[0].addresses.contains("12345"))
        assertTrue(senders[0].addresses.contains("67890"))
        assertEquals(1, senders[0].rules.size)
        assertEquals("Payment (\\d+\\.\\d{2})", senders[0].rules[0].pattern)
    }

    @Test
    @DisplayName("updateSender_filtersEmptyAddressesAndRules")
    fun updateSenderFiltersEmptyAddressesAndRules() = runBlocking {
        ConfigRepository.load(context.applicationContext as android.app.Application)

        val sender = ConfigRepository.addSender()
        sender.name = "Test Bank"
        sender.addresses = mutableListOf("12345", "", "   ", "67890")
        sender.rules = mutableListOf(
            Rule(pattern = "Rule1"),
            Rule(pattern = ""),
            Rule(pattern = "   "),
            Rule(pattern = "Rule2")
        )

        ConfigRepository.updateSender(sender)

        val senders = ConfigRepository.getSenders()
        assertEquals(2, senders[0].addresses.size)
        assertTrue(senders[0].addresses.contains("12345"))
        assertTrue(senders[0].addresses.contains("67890"))
        assertEquals(2, senders[0].rules.size)
        assertEquals("Rule1", senders[0].rules[0].pattern)
        assertEquals("Rule2", senders[0].rules[1].pattern)
    }

    @Test
    @DisplayName("updateSender_replacesExistingAddressesAndRules")
    fun updateSenderReplacesExistingAddressesAndRules() = runBlocking {
        ConfigRepository.load(context.applicationContext as android.app.Application)

        val sender = ConfigRepository.addSender()
        sender.name = "Bank"
        sender.addresses = mutableListOf("Old Address")
        sender.rules = mutableListOf(Rule(pattern = "Old Rule"))
        ConfigRepository.updateSender(sender)

        sender.addresses = mutableListOf("New Address 1", "New Address 2")
        sender.rules = mutableListOf(
            Rule(pattern = "New Rule 1"),
            Rule(pattern = "New Rule 2")
        )
        ConfigRepository.updateSender(sender)

        val senders = ConfigRepository.getSenders()
        assertEquals(2, senders[0].addresses.size)
        assertTrue(senders[0].addresses.contains("New Address 1"))
        assertTrue(senders[0].addresses.contains("New Address 2"))
        assertTrue(!senders[0].addresses.contains("Old Address"))
        assertEquals(2, senders[0].rules.size)
        assertEquals("New Rule 1", senders[0].rules[0].pattern)
        assertEquals("New Rule 2", senders[0].rules[1].pattern)
    }

    @Test
    @DisplayName("getCategories_returnsMutableCopies")
    fun getCategoriesReturnsMutableCopies() = runBlocking {
        ConfigRepository.load(context.applicationContext as android.app.Application)

        val category = ConfigRepository.addCategory()
        category.name = "Test"
        category.merchants = mutableListOf("Merchant1")
        ConfigRepository.updateCategory(category)

        val categories1 = ConfigRepository.getCategories()
        val categories2 = ConfigRepository.getCategories()

        // Modifying one list should not affect the other
        categories1[0].merchants.add("Merchant2")
        assertEquals(1, categories2[0].merchants.size)
        assertEquals(2, categories1[0].merchants.size)
    }

    @Test
    @DisplayName("getSenders_returnsMutableCopies")
    fun getSendersReturnsMutableCopies() = runBlocking {
        ConfigRepository.load(context.applicationContext as android.app.Application)

        val sender = ConfigRepository.addSender()
        sender.name = "Bank"
        sender.addresses = mutableListOf("12345")
        ConfigRepository.updateSender(sender)

        val senders1 = ConfigRepository.getSenders()
        val senders2 = ConfigRepository.getSenders()

        // Modifying one list should not affect the other
        senders1[0].addresses.add("67890")
        assertEquals(1, senders2[0].addresses.size)
        assertEquals(2, senders1[0].addresses.size)
    }

    @Test
    @DisplayName("getPaymentProcessor_usesCurrentConfig")
    fun getPaymentProcessorUsesCurrentConfig() = runBlocking {
        ConfigRepository.load(context.applicationContext as android.app.Application)

        val category = ConfigRepository.addCategory()
        category.name = "Groceries"
        category.merchants = mutableListOf("Supermarket")
        ConfigRepository.updateCategory(category)

        val processor = ConfigRepository.getPaymentProcessor()
        assertNotNull(processor)
        // Processor should have access to categories from config
        assertEquals(1, ConfigRepository.config.categories.size)
    }

    @Test
    @DisplayName("multipleCategoriesAndSenders_persistCorrectly")
    fun multipleCategoriesAndSendersPersistCorrectly() = runBlocking {
        ConfigRepository.load(context.applicationContext as android.app.Application)

        // Add multiple categories
        val cat1 = ConfigRepository.addCategory()
        cat1.name = "Category 1"
        cat1.merchants = mutableListOf("Merchant 1")
        ConfigRepository.updateCategory(cat1)

        val cat2 = ConfigRepository.addCategory()
        cat2.name = "Category 2"
        cat2.merchants = mutableListOf("Merchant 2", "Merchant 3")
        ConfigRepository.updateCategory(cat2)

        // Add multiple senders
        val sender1 = ConfigRepository.addSender()
        sender1.name = "Sender 1"
        sender1.addresses = mutableListOf("11111")
        sender1.rules = mutableListOf(Rule(pattern = "Rule1"))
        ConfigRepository.updateSender(sender1)

        val sender2 = ConfigRepository.addSender()
        sender2.name = "Sender 2"
        sender2.addresses = mutableListOf("22222", "33333")
        sender2.rules = mutableListOf(
            Rule(pattern = "Rule2a"),
            Rule(pattern = "Rule2b")
        )
        ConfigRepository.updateSender(sender2)

        val categories = ConfigRepository.getCategories()
        val senders = ConfigRepository.getSenders()

        assertEquals(2, categories.size)
        assertEquals(2, senders.size)
        assertEquals("Category 1", categories[0].name)
        assertEquals("Category 2", categories[1].name)
        assertEquals("Sender 1", senders[0].name)
        assertEquals("Sender 2", senders[1].name)
    }

    @Test
    @DisplayName("updateCategory_triggersRecategorizeAllPayments")
    fun updateCategoryTriggersRecategorizeAllPayments() = runBlocking {
        ConfigRepository.load(context.applicationContext as Application)

        // Save a payment with merchant "Amazon" via the shared Room database
        val db = BankSmsDatabase.getInstance(context.applicationContext)
        val repo = RoomPaymentRepository(db.paymentDao())
        repo.savePayment(
            Payment(
                amount = 10.0,
                currency = "USD",
                card = null,
                merchant = "Amazon",
                timestamp = null,
                balance = null
            ),
            rawMessage = "amazon-trigger-test",
            senderAddress = "BANK"
        )

        // Create a category that maps "Amazon" and call updateCategory
        val cat = ConfigRepository.addCategory()
        ConfigRepository.updateCategory(cat.copy(name = "Shopping", merchants = mutableListOf("Amazon")))

        // updateCategory() must have called recategorizeAllPayments() — verify the payment is categorized
        val payments = repo.getAllPayments()
        assertEquals(1, payments.size)
        assertEquals(
            "Shopping",
            payments[0].categoryId,
            "updateCategory() must recategorize existing payments with matching merchant"
        )
    }
}
