package com.example.banksmstracker

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Merchant
import com.example.banksmstracker.data.Rule
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.parser.SmsReceiver
import com.example.banksmstracker.processor.PaymentProcessor
import com.example.banksmstracker.repository.InMemoryPaymentRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Full SMS Processing Pipeline E2E Tests (Phase 4.2)
 *
 * Tests the complete flow: SmsReceiver -> PaymentProcessor -> PaymentRepository
 * Including multi-message sequences, config changes, and edge cases.
 */
@DisplayName("SMS Processing Pipeline E2E")
class SmsProcessingPipelineE2ETest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val TAG = "SmsProcessingPipelineE2ETest"

    private lateinit var repository: InMemoryPaymentRepository
    private lateinit var smsReceiver: SmsReceiver

    // Standard regex pattern with 6 named capture groups
    private val standardRegex =
        "Payment (?<amount>\\d+\\.\\d{2}) (?<currency>[A-Z]{3}) card (?<card>\\d+)" +
            " (?<merchant>.+) at (?<date>\\d+) bal (?<balance>\\d+\\.\\d{2})"

    private fun buildSmsIntent(sender: String, body: String): Intent =
        Intent("android.provider.Telephony.SMS_RECEIVED").apply {
            putExtra(SmsReceiver.EXTRA_TEST_SENDER, sender)
            putExtra(SmsReceiver.EXTRA_TEST_BODY, body)
        }

    private fun createProcessor(senders: List<Sender>, categories: List<Category>): PaymentProcessor =
        PaymentProcessor(senders, categories, repository)

    @BeforeEach
    fun setUp() {
        repository = InMemoryPaymentRepository()
        smsReceiver = SmsReceiver()
    }

    @Nested
    @DisplayName("Multi-SMS Sequence Processing")
    inner class MultiSmsSequenceProcessing {

        @Test
        @DisplayName("Multiple SMS from same sender are all processed")
        fun multipleSmsFromSameSenderAreAllProcessed() {
            val sender = Sender(
                name = "TestBank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = standardRegex))
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            // Send 5 different SMS
            for (i in 1..5) {
                val body = "Payment ${i}00.00 USD card 1234 Store$i at 20230901 bal 1000.00"
                smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", body))
            }

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(5, payments.size)
            assertTrue(payments.any { it.amount == 100.0 })
            assertTrue(payments.any { it.amount == 500.0 })
        }

        @Test
        @DisplayName("Multiple SMS from different senders are all processed")
        fun multipleSmsFromDifferentSendersAreAllProcessed() {
            val senderA = Sender(
                name = "BankA",
                addresses = mutableListOf("BANKA"),
                rules = mutableListOf(Rule(pattern = standardRegex))
            )
            val senderB = Sender(
                name = "BankB",
                addresses = mutableListOf("BANKB"),
                rules = mutableListOf(Rule(pattern = standardRegex))
            )
            val processor = createProcessor(listOf(senderA, senderB), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(
                context,
                buildSmsIntent("BANKA", "Payment 100.00 USD card 1234 StoreA at 20230901 bal 1000.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("BANKB", "Payment 200.00 USD card 5678 StoreB at 20230901 bal 2000.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("BANKA", "Payment 300.00 USD card 1234 StoreC at 20230901 bal 700.00")
            )

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(3, payments.size)

            // Check sender addresses are recorded correctly
            assertEquals(2, payments.count { it.senderAddress == "BANKA" })
            assertEquals(1, payments.count { it.senderAddress == "BANKB" })
        }

        @Test
        @DisplayName("Mixed valid and invalid SMS - valid ones are saved")
        fun mixedValidAndInvalidSmsValidOnesAreSaved() {
            val sender = Sender(
                name = "TestBank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = standardRegex))
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            // Valid
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Store1 at 20230901 bal 1000.00")
            )
            // Invalid format
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Your OTP is 123456"))
            // Valid
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 200.00 USD card 1234 Store2 at 20230901 bal 800.00")
            )
            // Invalid - unknown sender
            smsReceiver.onReceive(
                context,
                buildSmsIntent("UNKNOWN", "Payment 300.00 USD card 1234 Store3 at 20230901 bal 500.00")
            )
            // Valid
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 400.00 USD card 1234 Store4 at 20230901 bal 400.00")
            )

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(3, payments.size)
            assertTrue(payments.all { it.senderAddress == "TESTBANK" })
        }
    }

    @Nested
    @DisplayName("Category Assignment Integration")
    inner class CategoryAssignmentIntegration {

        @Test
        @DisplayName("Payments are categorized based on merchant")
        fun paymentsAreCategorizedBasedOnMerchant() {
            val sender = Sender(
                name = "TestBank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = standardRegex))
            )
            val categories = listOf(
                Category(name = "Shopping", merchants = mutableListOf(Merchant("Amazon"), Merchant("Walmart"))),
                Category(name = "Food", merchants = mutableListOf(Merchant("KFC"), Merchant("McDonalds")))
            )
            val processor = createProcessor(listOf(sender), categories)
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Amazon at 20230901 bal 1000.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 50.00 USD card 1234 KFC at 20230901 bal 950.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 75.00 USD card 1234 UnknownStore at 20230901 bal 875.00")
            )

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(3, payments.size)

            val amazonPayment = payments.find { it.merchant == "Amazon" }
            val kfcPayment = payments.find { it.merchant == "KFC" }
            val unknownPayment = payments.find { it.merchant == "UnknownStore" }

            assertEquals("Shopping", amazonPayment?.categoryId)
            assertEquals("Food", kfcPayment?.categoryId)
            assertNull(unknownPayment?.categoryId)
        }

        @Test
        @DisplayName("Category matching is case-insensitive")
        fun categoryMatchingIsCaseInsensitive() {
            val sender = Sender(
                name = "TestBank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = standardRegex))
            )
            val categories = listOf(
                Category(name = "Shopping", merchants = mutableListOf(Merchant("Amazon")))
            )
            val processor = createProcessor(listOf(sender), categories)
            smsReceiver.setPaymentProcessorForTest(processor)

            // Different case variations
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 AMAZON at 20230901 bal 1000.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 amazon at 20230901 bal 900.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 AmAzOn at 20230901 bal 800.00")
            )

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(3, payments.size)
            assertTrue(payments.all { it.categoryId == "Shopping" })
        }
    }

    @Nested
    @DisplayName("Deduplication Integration")
    inner class DeduplicationIntegration {

        @Test
        @DisplayName("Duplicate SMS is not saved twice")
        fun duplicateSmsIsNotSavedTwice() {
            val sender = Sender(
                name = "TestBank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = standardRegex))
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            val body = "Payment 100.00 USD card 1234 Store at 20230901 bal 1000.00"
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", body))
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", body))
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", body))

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(1, payments.size)
        }

        @Test
        @DisplayName("Same payment amount different message is saved")
        fun samePaymentAmountDifferentMessageIsSaved() {
            val sender = Sender(
                name = "TestBank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = standardRegex))
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            // Same amount, different merchants (different messages)
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Store1 at 20230901 bal 1000.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Store2 at 20230901 bal 900.00")
            )

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(2, payments.size)
        }

        @Test
        @DisplayName("Same message from different senders is saved")
        fun sameMessageFromDifferentSendersIsSaved() {
            val senderA =
                Sender(
                    name = "BankA",
                    addresses = mutableListOf("BANKA"),
                    rules = mutableListOf(Rule(pattern = standardRegex))
                )
            val senderB =
                Sender(
                    name = "BankB",
                    addresses = mutableListOf("BANKB"),
                    rules = mutableListOf(Rule(pattern = standardRegex))
                )
            val processor = createProcessor(listOf(senderA, senderB), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            val body = "Payment 100.00 USD card 1234 Store at 20230901 bal 1000.00"
            smsReceiver.onReceive(context, buildSmsIntent("BANKA", body))
            smsReceiver.onReceive(context, buildSmsIntent("BANKB", body))

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(2, payments.size)
        }
    }

    @Nested
    @DisplayName("Enabled/Disabled Filtering Integration")
    inner class EnabledDisabledIntegration {

        @Test
        @DisplayName("Disabled sender messages are ignored")
        fun disabledSenderMessagesAreIgnored() {
            val enabledSender =
                Sender(
                    name = "EnabledBank",
                    addresses = mutableListOf("ENABLED"),
                    rules = mutableListOf(Rule(pattern = standardRegex)),
                    enabled = true
                )
            val disabledSender =
                Sender(
                    name = "DisabledBank",
                    addresses = mutableListOf("DISABLED"),
                    rules = mutableListOf(Rule(pattern = standardRegex)),
                    enabled = false
                )
            val processor = createProcessor(listOf(enabledSender, disabledSender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(
                context,
                buildSmsIntent("ENABLED", "Payment 100.00 USD card 1234 Store at 20230901 bal 1000.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("DISABLED", "Payment 200.00 USD card 5678 Store at 20230901 bal 2000.00")
            )

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(1, payments.size)
            assertEquals("ENABLED", payments[0].senderAddress)
        }

        @Test
        @DisplayName("Disabled rule messages are ignored")
        fun disabledRuleMessagesAreIgnored() {
            val sender = Sender(
                name = "TestBank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(
                    Rule(pattern = "Disabled: (\\d+) (\\w+) (\\d+) (\\w+) (\\d+) (\\d+)", enabled = false),
                    Rule(pattern = standardRegex, enabled = true)
                )
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            // This matches only the disabled rule
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Disabled: 100 USD 1234 Store 20230901 1000"))
            // This matches the enabled rule
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 200.00 USD card 5678 Store at 20230902 bal 800.00")
            )

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(1, payments.size)
            assertEquals(200.0, payments[0].amount)
        }

        @Test
        @DisplayName("Disabled category does not assign categoryId")
        fun disabledCategoryDoesNotAssignCategoryId() {
            val sender =
                Sender(
                    name = "TestBank",
                    addresses = mutableListOf("TESTBANK"),
                    rules = mutableListOf(Rule(pattern = standardRegex))
                )
            val categories = listOf(
                Category(name = "Shopping", merchants = mutableListOf(Merchant("Amazon")), enabled = false),
                Category(name = "Food", merchants = mutableListOf(Merchant("KFC")), enabled = true)
            )
            val processor = createProcessor(listOf(sender), categories)
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Amazon at 20230901 bal 1000.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 50.00 USD card 1234 KFC at 20230901 bal 950.00")
            )

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(2, payments.size)

            val amazonPayment = payments.find { it.merchant == "Amazon" }
            val kfcPayment = payments.find { it.merchant == "KFC" }

            assertNull(amazonPayment?.categoryId) // Disabled category
            assertEquals("Food", kfcPayment?.categoryId) // Enabled category
        }
    }

    @Nested
    @DisplayName("Multiple Rules Processing")
    inner class MultipleRulesProcessing {

        @Test
        @DisplayName("First matching rule is used")
        fun firstMatchingRuleIsUsed() {
            val sender = Sender(
                name = "TestBank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(
                    // Specific rule for GEL currency
                    Rule(
                        pattern = "GEL (?<amount>\\d+\\.\\d{2}) (?<currency>GEL) card (?<card>\\d+)" +
                            " (?<merchant>.+) at (?<date>\\d+) bal (?<balance>\\d+\\.\\d{2})"
                    ),
                    // General rule for any currency
                    Rule(pattern = standardRegex)
                )
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "GEL 100.00 GEL card 1234 Store at 20230901 bal 1000.00")
            )

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(1, payments.size)
            assertEquals("GEL", payments[0].currency)
        }

        @Test
        @DisplayName("Falls through to next rule when first does not match")
        fun fallsThroughToNextRuleWhenFirstDoesNotMatch() {
            val sender = Sender(
                name = "TestBank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(
                    // Rule that won't match
                    Rule(pattern = "NOMATCH (\\d+) (\\w+) (\\d+) (\\w+) (\\d+) (\\d+)"),
                    // Rule that will match
                    Rule(pattern = standardRegex)
                )
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Store at 20230901 bal 1000.00")
            )

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(1, payments.size)
            assertEquals(100.0, payments[0].amount)
        }
    }

    @Nested
    @DisplayName("Address Matching Integration")
    inner class AddressMatchingIntegration {

        @Test
        @DisplayName("Address matching is case-insensitive")
        fun addressMatchingIsCaseInsensitive() {
            val sender = Sender(
                name = "TestBank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = standardRegex))
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            // Different case variations of sender address
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Store1 at 20230901 bal 1000.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("testbank", "Payment 200.00 USD card 1234 Store2 at 20230901 bal 800.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TestBank", "Payment 300.00 USD card 1234 Store3 at 20230901 bal 500.00")
            )

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(3, payments.size)
        }

        @Test
        @DisplayName("Sender with multiple addresses all work")
        fun senderWithMultipleAddressesAllWork() {
            val sender = Sender(
                name = "TestBank",
                addresses = mutableListOf("BANK-SMS", "BANK-NOTIFY", "+1234567890"),
                rules = mutableListOf(Rule(pattern = standardRegex))
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(
                context,
                buildSmsIntent("BANK-SMS", "Payment 100.00 USD card 1234 Store1 at 20230901 bal 1000.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("BANK-NOTIFY", "Payment 200.00 USD card 1234 Store2 at 20230901 bal 800.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("+1234567890", "Payment 300.00 USD card 1234 Store3 at 20230901 bal 500.00")
            )

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(3, payments.size)
        }
    }

    @Nested
    @DisplayName("Payment Data Integrity")
    inner class PaymentDataIntegrity {

        @Test
        @DisplayName("All payment fields are correctly extracted and stored")
        fun allPaymentFieldsAreCorrectlyExtractedAndStored() {
            val sender = Sender(
                name = "TestBank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = standardRegex))
            )
            val categories = listOf(Category(name = "Shopping", merchants = mutableListOf(Merchant("Amazon"))))
            val processor = createProcessor(listOf(sender), categories)
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 123.45 EUR card 9876 Amazon at 20231225 bal 5000.50")
            )

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(1, payments.size)

            val payment = payments[0]
            assertEquals(123.45, payment.amount)
            assertEquals("EUR", payment.currency)
            assertEquals("9876", payment.card)
            assertEquals("Amazon", payment.merchant)
            assertEquals("20231225", payment.timestamp)
            assertEquals(5000.50, payment.balance)
            assertEquals("Shopping", payment.categoryId)
            assertEquals("TESTBANK", payment.senderAddress)
            assertNotNull(payment.id)
        }

        @Test
        @DisplayName("Payments are stored with non-blank timestamp")
        fun paymentsAreStoredWithNonBlankTimestamp() {
            val sender =
                Sender(
                    name = "TestBank",
                    addresses = mutableListOf("TESTBANK"),
                    rules = mutableListOf(Rule(pattern = standardRegex))
                )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Store at 20230901 bal 1000.00")
            )

            val payment = runBlocking { repository.getAllPayments() }[0]
            // timestamp comes from the SMS body regex group (captured as "20230901" in this case)
            assertTrue(payment.timestamp.isNotBlank())
        }

        @Test
        @DisplayName("Payments can be filtered by sender after storage")
        fun paymentsCanBeFilteredBySenderAfterStorage() {
            val senderA =
                Sender(
                    name = "BankA",
                    addresses = mutableListOf("BANKA"),
                    rules = mutableListOf(Rule(pattern = standardRegex))
                )
            val senderB =
                Sender(
                    name = "BankB",
                    addresses = mutableListOf("BANKB"),
                    rules = mutableListOf(Rule(pattern = standardRegex))
                )
            val processor = createProcessor(listOf(senderA, senderB), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(
                context,
                buildSmsIntent("BANKA", "Payment 100.00 USD card 1234 Store1 at 20230901 bal 1000.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("BANKA", "Payment 200.00 USD card 1234 Store2 at 20230901 bal 800.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("BANKB", "Payment 300.00 USD card 1234 Store3 at 20230901 bal 500.00")
            )

            val bankAPayments = runBlocking { repository.getPaymentsBySender("BANKA") }
            val bankBPayments = runBlocking { repository.getPaymentsBySender("BANKB") }

            assertEquals(2, bankAPayments.size)
            assertEquals(1, bankBPayments.size)
        }

        @Test
        @DisplayName("Payments can be filtered by category after storage")
        fun paymentsCanBeFilteredByCategoryAfterStorage() {
            val sender =
                Sender(
                    name = "TestBank",
                    addresses = mutableListOf("TESTBANK"),
                    rules = mutableListOf(Rule(pattern = standardRegex))
                )
            val categories = listOf(
                Category(name = "Shopping", merchants = mutableListOf(Merchant("Amazon"))),
                Category(name = "Food", merchants = mutableListOf(Merchant("KFC")))
            )
            val processor = createProcessor(listOf(sender), categories)
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Amazon at 20230901 bal 1000.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 50.00 USD card 1234 KFC at 20230901 bal 950.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 75.00 USD card 1234 Unknown at 20230901 bal 875.00")
            )

            val shoppingPayments = runBlocking { repository.getPaymentsByCategory("Shopping") }
            val foodPayments = runBlocking { repository.getPaymentsByCategory("Food") }
            val uncategorized = runBlocking { repository.getUncategorizedPayments() }

            assertEquals(1, shoppingPayments.size)
            assertEquals(1, foodPayments.size)
            assertEquals(1, uncategorized.size)
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {

        @Test
        @DisplayName("Empty intent extras are handled gracefully")
        fun emptyIntentExtrasAreHandledGracefully() {
            val sender =
                Sender(
                    name = "TestBank",
                    addresses = mutableListOf("TESTBANK"),
                    rules = mutableListOf(Rule(pattern = standardRegex))
                )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            // Intent with no extras
            val emptyIntent = Intent("android.provider.Telephony.SMS_RECEIVED")
            smsReceiver.onReceive(context, emptyIntent)

            assertEquals(0, runBlocking { repository.getAllPayments() }.size)
        }

        @Test
        @DisplayName("Null body is handled gracefully")
        fun nullBodyIsHandledGracefully() {
            val sender =
                Sender(
                    name = "TestBank",
                    addresses = mutableListOf("TESTBANK"),
                    rules = mutableListOf(Rule(pattern = standardRegex))
                )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            val intent = Intent("android.provider.Telephony.SMS_RECEIVED").apply {
                putExtra(SmsReceiver.EXTRA_TEST_SENDER, "TESTBANK")
                // No body extra
            }
            smsReceiver.onReceive(context, intent)

            assertEquals(0, runBlocking { repository.getAllPayments() }.size)
        }

        @Test
        @DisplayName("Unicode in message is preserved")
        fun unicodeInMessageIsPreserved() {
            val unicodeRegex =
                "Payment (?<amount>\\d+\\.\\d{2}) (?<currency>[A-Z]{3}) card (?<card>\\d+)" +
                    " (?<merchant>.+) at (?<date>\\d+) bal (?<balance>\\d+\\.\\d{2})"
            val sender =
                Sender(
                    name = "TestBank",
                    addresses = mutableListOf("TESTBANK"),
                    rules = mutableListOf(Rule(pattern = unicodeRegex))
                )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 100.00 GEL card 1234 საქართველო at 20230901 bal 1000.00")
            )

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(1, payments.size)
            assertEquals("საქართველო", payments[0].merchant)
        }

        @Test
        @DisplayName("Special characters in merchant are preserved")
        fun specialCharactersInMerchantArePreserved() {
            val sender =
                Sender(
                    name = "TestBank",
                    addresses = mutableListOf("TESTBANK"),
                    rules = mutableListOf(Rule(pattern = standardRegex))
                )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Store-Name_123 at 20230901 bal 1000.00")
            )

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(1, payments.size)
            assertEquals("Store-Name_123", payments[0].merchant)
        }
    }

    @Nested
    @DisplayName("Ordering and Retrieval")
    inner class OrderingAndRetrieval {

        @Test
        @DisplayName("Payments are returned in descending order by ID")
        fun paymentsAreReturnedInDescendingOrderById() {
            val sender =
                Sender(
                    name = "TestBank",
                    addresses = mutableListOf("TESTBANK"),
                    rules = mutableListOf(Rule(pattern = standardRegex))
                )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 First at 20230901 bal 1000.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 200.00 USD card 1234 Second at 20230902 bal 800.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("TESTBANK", "Payment 300.00 USD card 1234 Third at 20230903 bal 500.00")
            )

            val payments = runBlocking { repository.getAllPayments() }
            assertEquals(3, payments.size)
            // Newest first
            assertEquals("Third", payments[0].merchant)
            assertEquals("Second", payments[1].merchant)
            assertEquals("First", payments[2].merchant)
        }

        @Test
        @DisplayName("Distinct senders are retrievable")
        fun distinctSendersAreRetrievable() {
            val senderA =
                Sender(
                    name = "BankA",
                    addresses = mutableListOf("BANKA"),
                    rules = mutableListOf(Rule(pattern = standardRegex))
                )
            val senderB =
                Sender(
                    name = "BankB",
                    addresses = mutableListOf("BANKB"),
                    rules = mutableListOf(Rule(pattern = standardRegex))
                )
            val senderC =
                Sender(
                    name = "BankC",
                    addresses = mutableListOf("BANKC"),
                    rules = mutableListOf(Rule(pattern = standardRegex))
                )
            val processor = createProcessor(listOf(senderA, senderB, senderC), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(
                context,
                buildSmsIntent("BANKA", "Payment 100.00 USD card 1234 Store1 at 20230901 bal 1000.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("BANKC", "Payment 200.00 USD card 1234 Store2 at 20230901 bal 800.00")
            )
            smsReceiver.onReceive(
                context,
                buildSmsIntent("BANKA", "Payment 300.00 USD card 1234 Store3 at 20230901 bal 500.00")
            )

            val senders = runBlocking { repository.getDistinctSenderAddresses() }
            assertEquals(2, senders.size)
            assertTrue(senders.contains("BANKA"))
            assertTrue(senders.contains("BANKC"))
            assertFalse(senders.contains("BANKB")) // No messages from this sender
        }
    }
}
