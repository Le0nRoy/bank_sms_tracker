package com.example.banksmstracker

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Merchant
import com.example.banksmstracker.data.Rule
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.parser.SmsReceiver
import com.example.banksmstracker.processor.PaymentProcessor
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.InMemoryPaymentRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * E2E tests for enabled/disabled functionality.
 * Tests that disabled senders, rules, and categories are properly filtered.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnabledDisabledE2ETest {

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

    private fun buildSmsIntent(sender: String, body: String): Intent =
        Intent("android.provider.Telephony.SMS_RECEIVED").apply {
            putExtra(SmsReceiver.EXTRA_TEST_SENDER, sender)
            putExtra(SmsReceiver.EXTRA_TEST_BODY, body)
        }

    @Test
    @DisplayName("disabledSender_smsIsNotProcessed")
    fun disabledSenderSmsIsNotProcessed() {
        val repository = InMemoryPaymentRepository()

        val sender = Sender(
            name = "Disabled Bank",
            addresses = mutableListOf("DISABLED"),
            rules = mutableListOf(
                Rule(
                    pattern = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
                )
            ),
            enabled = false // Disabled sender
        )

        val processor = PaymentProcessor(
            senders = listOf(sender),
            categories = emptyList(),
            paymentRepository = repository
        )

        val smsReceiver = SmsReceiver()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body = "Payment 123.45 USD card 1234 Amazon at 20230905 bal 500.00"
        val intent = buildSmsIntent("DISABLED", body)

        smsReceiver.onReceive(context, intent)

        // Should not save payment from disabled sender
        assertTrue(runBlocking { repository.getAllPayments() }.isEmpty())
    }

    @Test
    @DisplayName("enabledSender_smsIsProcessed")
    fun enabledSenderSmsIsProcessed() {
        val repository = InMemoryPaymentRepository()

        val sender = Sender(
            name = "Enabled Bank",
            addresses = mutableListOf("ENABLED"),
            rules = mutableListOf(
                Rule(
                    pattern = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
                )
            ),
            enabled = true // Enabled sender
        )

        val processor = PaymentProcessor(
            senders = listOf(sender),
            categories = emptyList(),
            paymentRepository = repository
        )

        val smsReceiver = SmsReceiver()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body = "Payment 123.45 USD card 1234 Amazon at 20230905 bal 500.00"
        val intent = buildSmsIntent("ENABLED", body)

        smsReceiver.onReceive(context, intent)

        assertEquals(1, runBlocking { repository.getAllPayments() }.size)
    }

    @Test
    @DisplayName("disabledRule_smsIsNotMatched")
    fun disabledRuleSmsIsNotMatched() {
        val repository = InMemoryPaymentRepository()

        val sender = Sender(
            name = "Test Bank",
            addresses = mutableListOf("TESTBANK"),
            rules = mutableListOf(
                Rule(
                    pattern = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})",
                    enabled = false // Disabled rule
                )
            ),
            enabled = true
        )

        val processor = PaymentProcessor(
            senders = listOf(sender),
            categories = emptyList(),
            paymentRepository = repository
        )

        val smsReceiver = SmsReceiver()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body = "Payment 123.45 USD card 1234 Amazon at 20230905 bal 500.00"
        val intent = buildSmsIntent("TESTBANK", body)

        smsReceiver.onReceive(context, intent)

        // Should not match with disabled rule
        assertTrue(runBlocking { repository.getAllPayments() }.isEmpty())
    }

    @Test
    @DisplayName("enabledRule_smsIsMatched")
    fun enabledRuleSmsIsMatched() {
        val repository = InMemoryPaymentRepository()

        val sender = Sender(
            name = "Test Bank",
            addresses = mutableListOf("TESTBANK"),
            rules = mutableListOf(
                Rule(
                    pattern = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})",
                    enabled = true // Enabled rule
                )
            ),
            enabled = true
        )

        val processor = PaymentProcessor(
            senders = listOf(sender),
            categories = emptyList(),
            paymentRepository = repository
        )

        val smsReceiver = SmsReceiver()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body = "Payment 123.45 USD card 1234 Amazon at 20230905 bal 500.00"
        val intent = buildSmsIntent("TESTBANK", body)

        smsReceiver.onReceive(context, intent)

        assertEquals(1, runBlocking { repository.getAllPayments() }.size)
    }

    @Test
    @DisplayName("disabledCategory_paymentIsNotCategorized")
    fun disabledCategoryPaymentIsNotCategorized() {
        val repository = InMemoryPaymentRepository()

        val sender = Sender(
            name = "Test Bank",
            addresses = mutableListOf("TESTBANK"),
            rules = mutableListOf(
                Rule(
                    pattern = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
                )
            ),
            enabled = true
        )

        val category = Category(
            name = "Disabled Shopping",
            merchants = mutableListOf(Merchant("Amazon")),
            enabled = false // Disabled category
        )

        val processor = PaymentProcessor(
            senders = listOf(sender),
            categories = listOf(category),
            paymentRepository = repository
        )

        val smsReceiver = SmsReceiver()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body = "Payment 123.45 USD card 1234 Amazon at 20230905 bal 500.00"
        val intent = buildSmsIntent("TESTBANK", body)

        smsReceiver.onReceive(context, intent)

        val payments = runBlocking { repository.getAllPayments() }
        assertEquals(1, payments.size)
        // Payment should NOT be categorized because category is disabled
        assertNull(payments[0].categoryId)
    }

    @Test
    @DisplayName("enabledCategory_paymentIsCategorized")
    fun enabledCategoryPaymentIsCategorized() {
        val repository = InMemoryPaymentRepository()

        val sender = Sender(
            name = "Test Bank",
            addresses = mutableListOf("TESTBANK"),
            rules = mutableListOf(
                Rule(
                    pattern = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
                )
            ),
            enabled = true
        )

        val category = Category(
            name = "Shopping",
            merchants = mutableListOf(Merchant("Amazon")),
            enabled = true // Enabled category
        )

        val processor = PaymentProcessor(
            senders = listOf(sender),
            categories = listOf(category),
            paymentRepository = repository
        )

        val smsReceiver = SmsReceiver()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body = "Payment 123.45 USD card 1234 Amazon at 20230905 bal 500.00"
        val intent = buildSmsIntent("TESTBANK", body)

        smsReceiver.onReceive(context, intent)

        val payments = runBlocking { repository.getAllPayments() }
        assertEquals(1, payments.size)
        assertEquals("Shopping", payments[0].categoryId)
    }

    @Test
    @DisplayName("multipleRules_onlyEnabledRulesAreUsed")
    fun multipleRulesOnlyEnabledRulesAreUsed() {
        val repository = InMemoryPaymentRepository()

        val sender = Sender(
            name = "Multi Rule Bank",
            addresses = mutableListOf("MULTIRULE"),
            rules = mutableListOf(
                // Disabled rule that would match
                Rule(
                    pattern = "NOMATCH (\\d+)",
                    enabled = false
                ),
                // Enabled rule that will match
                Rule(
                    pattern = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})",
                    enabled = true
                )
            ),
            enabled = true
        )

        val processor = PaymentProcessor(
            senders = listOf(sender),
            categories = emptyList(),
            paymentRepository = repository
        )

        val smsReceiver = SmsReceiver()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body = "Payment 99.99 USD card 5678 Store at 20230906 bal 100.00"
        val intent = buildSmsIntent("MULTIRULE", body)

        smsReceiver.onReceive(context, intent)

        val payments = runBlocking { repository.getAllPayments() }
        assertEquals(1, payments.size)
        assertEquals(99.99, payments[0].amount)
    }

    @Test
    @DisplayName("multipleSenders_onlyEnabledSendersAreUsed")
    fun multipleSendersOnlyEnabledSendersAreUsed() {
        val repository = InMemoryPaymentRepository()

        val disabledSender = Sender(
            name = "Disabled Bank",
            addresses = mutableListOf("BANK123"),
            rules = mutableListOf(
                Rule(
                    pattern = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
                )
            ),
            enabled = false
        )

        val enabledSender = Sender(
            name = "Enabled Bank",
            addresses = mutableListOf("BANK123"), // Same address
            rules = mutableListOf(
                Rule(
                    pattern = "Payment (\\d+\\.\\d{2}) (USD) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
                )
            ),
            enabled = true
        )

        val processor = PaymentProcessor(
            senders = listOf(disabledSender, enabledSender),
            categories = emptyList(),
            paymentRepository = repository
        )

        val smsReceiver = SmsReceiver()
        smsReceiver.setPaymentProcessorForTest(processor)

        val body = "Payment 50.00 USD card 1111 Test at 20230907 bal 200.00"
        val intent = buildSmsIntent("BANK123", body)

        smsReceiver.onReceive(context, intent)

        // Should process because enabled sender matches
        assertEquals(1, runBlocking { repository.getAllPayments() }.size)
    }

    @Test
    @DisplayName("configRepository_persistsEnabledState")
    fun configRepositoryPersistsEnabledState() = runBlocking {
        // Add sender with enabled=false
        val sender = ConfigRepository.addSender()
        sender.name = "Persist Test Bank"
        sender.addresses = mutableListOf("PERSIST")
        sender.enabled = false
        ConfigRepository.updateSender(sender)

        // Add category with enabled=false
        val category = ConfigRepository.addCategory()
        category.name = "Persist Test Category"
        category.merchants = mutableListOf(Merchant("TestMerchant"))
        category.enabled = false
        ConfigRepository.updateCategory(category)

        // Reload from database
        ConfigRepository.reset()
        ConfigRepository.load(context.applicationContext as android.app.Application)

        val senders = ConfigRepository.getSenders()
        val categories = ConfigRepository.getCategories()

        val persistedSender = senders.first { it.name == "Persist Test Bank" }
        val persistedCategory = categories.first { it.name == "Persist Test Category" }

        assertTrue(!persistedSender.enabled)
        assertTrue(!persistedCategory.enabled)
    }

    @Test
    @DisplayName("configRepository_toggleEnabledState")
    fun configRepositoryToggleEnabledState() = runBlocking {
        // Add sender with enabled=true
        val sender = ConfigRepository.addSender()
        sender.name = "Toggle Test Bank"
        sender.addresses = mutableListOf("TOGGLE")
        sender.enabled = true
        ConfigRepository.updateSender(sender)

        // Toggle to false
        val senders = ConfigRepository.getSenders()
        val toggleSender = senders.first { it.name == "Toggle Test Bank" }
        toggleSender.enabled = false
        ConfigRepository.updateSender(toggleSender)

        // Verify
        val updatedSenders = ConfigRepository.getSenders()
        val updated = updatedSenders.first { it.name == "Toggle Test Bank" }
        assertTrue(!updated.enabled)

        // Toggle back to true
        updated.enabled = true
        ConfigRepository.updateSender(updated)

        val finalSenders = ConfigRepository.getSenders()
        val final = finalSenders.first { it.name == "Toggle Test Bank" }
        assertTrue(final.enabled)
    }
}
