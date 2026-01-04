package com.example.banksmstracker

import android.content.Intent
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.PaymentRegexRule
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.parser.SmsReceiver
import com.example.banksmstracker.processor.PaymentProcessor
import com.example.banksmstracker.repository.InMemoryPaymentRepository
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

    // Standard regex pattern with 6 capture groups
    private val standardRegex = "Payment (\\d+\\.\\d{2}) (\\w+) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"

    private fun buildSmsIntent(sender: String, body: String): Intent =
        Intent("android.provider.Telephony.SMS_RECEIVED").apply {
            putExtra(SmsReceiver.EXTRA_TEST_SENDER, sender)
            putExtra(SmsReceiver.EXTRA_TEST_BODY, body)
        }

    private fun createProcessor(
        senders: List<Sender>,
        categories: List<Category>
    ): PaymentProcessor {
        return PaymentProcessor(senders, categories, repository)
    }

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
        fun `multiple sms from same sender are all processed`() {
            val sender = Sender(
                name = "TestBank",
                addresses = listOf("TESTBANK"),
                rules = listOf(PaymentRegexRule(regex = standardRegex))
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            // Send 5 different SMS
            for (i in 1..5) {
                val body = "Payment ${i}00.00 USD card 1234 Store$i at 20230901 bal 1000.00"
                smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", body))
            }

            val payments = repository.getAllPayments()
            assertEquals(5, payments.size)
            assertTrue(payments.any { it.amount == 100.0 })
            assertTrue(payments.any { it.amount == 500.0 })
        }

        @Test
        @DisplayName("Multiple SMS from different senders are all processed")
        fun `multiple sms from different senders are all processed`() {
            val senderA = Sender(
                name = "BankA",
                addresses = listOf("BANKA"),
                rules = listOf(PaymentRegexRule(regex = standardRegex))
            )
            val senderB = Sender(
                name = "BankB",
                addresses = listOf("BANKB"),
                rules = listOf(PaymentRegexRule(regex = standardRegex))
            )
            val processor = createProcessor(listOf(senderA, senderB), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(context, buildSmsIntent("BANKA", "Payment 100.00 USD card 1234 StoreA at 20230901 bal 1000.00"))
            smsReceiver.onReceive(context, buildSmsIntent("BANKB", "Payment 200.00 USD card 5678 StoreB at 20230901 bal 2000.00"))
            smsReceiver.onReceive(context, buildSmsIntent("BANKA", "Payment 300.00 USD card 1234 StoreC at 20230901 bal 700.00"))

            val payments = repository.getAllPayments()
            assertEquals(3, payments.size)

            // Check sender addresses are recorded correctly
            assertEquals(2, payments.count { it.senderAddress == "BANKA" })
            assertEquals(1, payments.count { it.senderAddress == "BANKB" })
        }

        @Test
        @DisplayName("Mixed valid and invalid SMS - valid ones are saved")
        fun `mixed valid and invalid sms - valid ones are saved`() {
            val sender = Sender(
                name = "TestBank",
                addresses = listOf("TESTBANK"),
                rules = listOf(PaymentRegexRule(regex = standardRegex))
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            // Valid
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Store1 at 20230901 bal 1000.00"))
            // Invalid format
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Your OTP is 123456"))
            // Valid
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 200.00 USD card 1234 Store2 at 20230901 bal 800.00"))
            // Invalid - unknown sender
            smsReceiver.onReceive(context, buildSmsIntent("UNKNOWN", "Payment 300.00 USD card 1234 Store3 at 20230901 bal 500.00"))
            // Valid
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 400.00 USD card 1234 Store4 at 20230901 bal 400.00"))

            val payments = repository.getAllPayments()
            assertEquals(3, payments.size)
            assertTrue(payments.all { it.senderAddress == "TESTBANK" })
        }
    }

    @Nested
    @DisplayName("Category Assignment Integration")
    inner class CategoryAssignmentIntegration {

        @Test
        @DisplayName("Payments are categorized based on merchant")
        fun `payments are categorized based on merchant`() {
            val sender = Sender(
                name = "TestBank",
                addresses = listOf("TESTBANK"),
                rules = listOf(PaymentRegexRule(regex = standardRegex))
            )
            val categories = listOf(
                Category(name = "Shopping", merchants = listOf("Amazon", "Walmart")),
                Category(name = "Food", merchants = listOf("KFC", "McDonalds"))
            )
            val processor = createProcessor(listOf(sender), categories)
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Amazon at 20230901 bal 1000.00"))
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 50.00 USD card 1234 KFC at 20230901 bal 950.00"))
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 75.00 USD card 1234 UnknownStore at 20230901 bal 875.00"))

            val payments = repository.getAllPayments()
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
        fun `category matching is case insensitive`() {
            val sender = Sender(
                name = "TestBank",
                addresses = listOf("TESTBANK"),
                rules = listOf(PaymentRegexRule(regex = standardRegex))
            )
            val categories = listOf(
                Category(name = "Shopping", merchants = listOf("Amazon"))
            )
            val processor = createProcessor(listOf(sender), categories)
            smsReceiver.setPaymentProcessorForTest(processor)

            // Different case variations
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 AMAZON at 20230901 bal 1000.00"))
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 amazon at 20230901 bal 900.00"))
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 AmAzOn at 20230901 bal 800.00"))

            val payments = repository.getAllPayments()
            assertEquals(3, payments.size)
            assertTrue(payments.all { it.categoryId == "Shopping" })
        }
    }

    @Nested
    @DisplayName("Deduplication Integration")
    inner class DeduplicationIntegration {

        @Test
        @DisplayName("Duplicate SMS is not saved twice")
        fun `duplicate sms is not saved twice`() {
            val sender = Sender(
                name = "TestBank",
                addresses = listOf("TESTBANK"),
                rules = listOf(PaymentRegexRule(regex = standardRegex))
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            val body = "Payment 100.00 USD card 1234 Store at 20230901 bal 1000.00"
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", body))
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", body))
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", body))

            val payments = repository.getAllPayments()
            assertEquals(1, payments.size)
        }

        @Test
        @DisplayName("Same payment amount different message is saved")
        fun `same payment amount different message is saved`() {
            val sender = Sender(
                name = "TestBank",
                addresses = listOf("TESTBANK"),
                rules = listOf(PaymentRegexRule(regex = standardRegex))
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            // Same amount, different merchants (different messages)
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Store1 at 20230901 bal 1000.00"))
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Store2 at 20230901 bal 900.00"))

            val payments = repository.getAllPayments()
            assertEquals(2, payments.size)
        }

        @Test
        @DisplayName("Same message from different senders is saved")
        fun `same message from different senders is saved`() {
            val senderA = Sender(name = "BankA", addresses = listOf("BANKA"), rules = listOf(PaymentRegexRule(regex = standardRegex)))
            val senderB = Sender(name = "BankB", addresses = listOf("BANKB"), rules = listOf(PaymentRegexRule(regex = standardRegex)))
            val processor = createProcessor(listOf(senderA, senderB), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            val body = "Payment 100.00 USD card 1234 Store at 20230901 bal 1000.00"
            smsReceiver.onReceive(context, buildSmsIntent("BANKA", body))
            smsReceiver.onReceive(context, buildSmsIntent("BANKB", body))

            val payments = repository.getAllPayments()
            assertEquals(2, payments.size)
        }
    }

    @Nested
    @DisplayName("Enabled/Disabled Filtering Integration")
    inner class EnabledDisabledIntegration {

        @Test
        @DisplayName("Disabled sender messages are ignored")
        fun `disabled sender messages are ignored`() {
            val enabledSender = Sender(name = "EnabledBank", addresses = listOf("ENABLED"), rules = listOf(PaymentRegexRule(regex = standardRegex)), enabled = true)
            val disabledSender = Sender(name = "DisabledBank", addresses = listOf("DISABLED"), rules = listOf(PaymentRegexRule(regex = standardRegex)), enabled = false)
            val processor = createProcessor(listOf(enabledSender, disabledSender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(context, buildSmsIntent("ENABLED", "Payment 100.00 USD card 1234 Store at 20230901 bal 1000.00"))
            smsReceiver.onReceive(context, buildSmsIntent("DISABLED", "Payment 200.00 USD card 5678 Store at 20230901 bal 2000.00"))

            val payments = repository.getAllPayments()
            assertEquals(1, payments.size)
            assertEquals("ENABLED", payments[0].senderAddress)
        }

        @Test
        @DisplayName("Disabled rule messages are ignored")
        fun `disabled rule messages are ignored`() {
            val sender = Sender(
                name = "TestBank",
                addresses = listOf("TESTBANK"),
                rules = listOf(
                    PaymentRegexRule(regex = "Disabled: (\\d+) (\\w+) (\\d+) (\\w+) (\\d+) (\\d+)", enabled = false),
                    PaymentRegexRule(regex = standardRegex, enabled = true)
                )
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            // This matches only the disabled rule
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Disabled: 100 USD 1234 Store 20230901 1000"))
            // This matches the enabled rule
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 200.00 USD card 5678 Store at 20230902 bal 800.00"))

            val payments = repository.getAllPayments()
            assertEquals(1, payments.size)
            assertEquals(200.0, payments[0].amount)
        }

        @Test
        @DisplayName("Disabled category does not assign categoryId")
        fun `disabled category does not assign categoryId`() {
            val sender = Sender(name = "TestBank", addresses = listOf("TESTBANK"), rules = listOf(PaymentRegexRule(regex = standardRegex)))
            val categories = listOf(
                Category(name = "Shopping", merchants = listOf("Amazon"), enabled = false),
                Category(name = "Food", merchants = listOf("KFC"), enabled = true)
            )
            val processor = createProcessor(listOf(sender), categories)
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Amazon at 20230901 bal 1000.00"))
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 50.00 USD card 1234 KFC at 20230901 bal 950.00"))

            val payments = repository.getAllPayments()
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
        fun `first matching rule is used`() {
            val sender = Sender(
                name = "TestBank",
                addresses = listOf("TESTBANK"),
                rules = listOf(
                    // Specific rule for GEL currency
                    PaymentRegexRule(regex = "GEL (\\d+\\.\\d{2}) (GEL) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"),
                    // General rule for any currency
                    PaymentRegexRule(regex = standardRegex)
                )
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "GEL 100.00 GEL card 1234 Store at 20230901 bal 1000.00"))

            val payments = repository.getAllPayments()
            assertEquals(1, payments.size)
            assertEquals("GEL", payments[0].currency)
        }

        @Test
        @DisplayName("Falls through to next rule when first does not match")
        fun `falls through to next rule when first does not match`() {
            val sender = Sender(
                name = "TestBank",
                addresses = listOf("TESTBANK"),
                rules = listOf(
                    // Rule that won't match
                    PaymentRegexRule(regex = "NOMATCH (\\d+) (\\w+) (\\d+) (\\w+) (\\d+) (\\d+)"),
                    // Rule that will match
                    PaymentRegexRule(regex = standardRegex)
                )
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Store at 20230901 bal 1000.00"))

            val payments = repository.getAllPayments()
            assertEquals(1, payments.size)
            assertEquals(100.0, payments[0].amount)
        }
    }

    @Nested
    @DisplayName("Address Matching Integration")
    inner class AddressMatchingIntegration {

        @Test
        @DisplayName("Address matching is case-insensitive")
        fun `address matching is case insensitive`() {
            val sender = Sender(
                name = "TestBank",
                addresses = listOf("TESTBANK"),
                rules = listOf(PaymentRegexRule(regex = standardRegex))
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            // Different case variations of sender address
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Store1 at 20230901 bal 1000.00"))
            smsReceiver.onReceive(context, buildSmsIntent("testbank", "Payment 200.00 USD card 1234 Store2 at 20230901 bal 800.00"))
            smsReceiver.onReceive(context, buildSmsIntent("TestBank", "Payment 300.00 USD card 1234 Store3 at 20230901 bal 500.00"))

            val payments = repository.getAllPayments()
            assertEquals(3, payments.size)
        }

        @Test
        @DisplayName("Sender with multiple addresses all work")
        fun `sender with multiple addresses all work`() {
            val sender = Sender(
                name = "TestBank",
                addresses = listOf("BANK-SMS", "BANK-NOTIFY", "+1234567890"),
                rules = listOf(PaymentRegexRule(regex = standardRegex))
            )
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(context, buildSmsIntent("BANK-SMS", "Payment 100.00 USD card 1234 Store1 at 20230901 bal 1000.00"))
            smsReceiver.onReceive(context, buildSmsIntent("BANK-NOTIFY", "Payment 200.00 USD card 1234 Store2 at 20230901 bal 800.00"))
            smsReceiver.onReceive(context, buildSmsIntent("+1234567890", "Payment 300.00 USD card 1234 Store3 at 20230901 bal 500.00"))

            val payments = repository.getAllPayments()
            assertEquals(3, payments.size)
        }
    }

    @Nested
    @DisplayName("Payment Data Integrity")
    inner class PaymentDataIntegrity {

        @Test
        @DisplayName("All payment fields are correctly extracted and stored")
        fun `all payment fields are correctly extracted and stored`() {
            val sender = Sender(
                name = "TestBank",
                addresses = listOf("TESTBANK"),
                rules = listOf(PaymentRegexRule(regex = standardRegex))
            )
            val categories = listOf(Category(name = "Shopping", merchants = listOf("Amazon")))
            val processor = createProcessor(listOf(sender), categories)
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 123.45 EUR card 9876 Amazon at 20231225 bal 5000.50"))

            val payments = repository.getAllPayments()
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
            assertNotNull(payment.receivedAt)
            assertNotNull(payment.id)
        }

        @Test
        @DisplayName("Payments are stored with receivedAt timestamp")
        fun `payments are stored with receivedAt timestamp`() {
            val sender = Sender(name = "TestBank", addresses = listOf("TESTBANK"), rules = listOf(PaymentRegexRule(regex = standardRegex)))
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            val before = System.currentTimeMillis()
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Store at 20230901 bal 1000.00"))
            val after = System.currentTimeMillis()

            val payment = repository.getAllPayments()[0]
            assertNotNull(payment.receivedAt)
            assertTrue(payment.receivedAt!! >= before)
            assertTrue(payment.receivedAt!! <= after)
        }

        @Test
        @DisplayName("Payments can be filtered by sender after storage")
        fun `payments can be filtered by sender after storage`() {
            val senderA = Sender(name = "BankA", addresses = listOf("BANKA"), rules = listOf(PaymentRegexRule(regex = standardRegex)))
            val senderB = Sender(name = "BankB", addresses = listOf("BANKB"), rules = listOf(PaymentRegexRule(regex = standardRegex)))
            val processor = createProcessor(listOf(senderA, senderB), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(context, buildSmsIntent("BANKA", "Payment 100.00 USD card 1234 Store1 at 20230901 bal 1000.00"))
            smsReceiver.onReceive(context, buildSmsIntent("BANKA", "Payment 200.00 USD card 1234 Store2 at 20230901 bal 800.00"))
            smsReceiver.onReceive(context, buildSmsIntent("BANKB", "Payment 300.00 USD card 1234 Store3 at 20230901 bal 500.00"))

            val bankAPayments = repository.getPaymentsBySender("BANKA")
            val bankBPayments = repository.getPaymentsBySender("BANKB")

            assertEquals(2, bankAPayments.size)
            assertEquals(1, bankBPayments.size)
        }

        @Test
        @DisplayName("Payments can be filtered by category after storage")
        fun `payments can be filtered by category after storage`() {
            val sender = Sender(name = "TestBank", addresses = listOf("TESTBANK"), rules = listOf(PaymentRegexRule(regex = standardRegex)))
            val categories = listOf(
                Category(name = "Shopping", merchants = listOf("Amazon")),
                Category(name = "Food", merchants = listOf("KFC"))
            )
            val processor = createProcessor(listOf(sender), categories)
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Amazon at 20230901 bal 1000.00"))
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 50.00 USD card 1234 KFC at 20230901 bal 950.00"))
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 75.00 USD card 1234 Unknown at 20230901 bal 875.00"))

            val shoppingPayments = repository.getPaymentsByCategory("Shopping")
            val foodPayments = repository.getPaymentsByCategory("Food")
            val uncategorized = repository.getUncategorizedPayments()

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
        fun `empty intent extras are handled gracefully`() {
            val sender = Sender(name = "TestBank", addresses = listOf("TESTBANK"), rules = listOf(PaymentRegexRule(regex = standardRegex)))
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            // Intent with no extras
            val emptyIntent = Intent("android.provider.Telephony.SMS_RECEIVED")
            smsReceiver.onReceive(context, emptyIntent)

            assertEquals(0, repository.getAllPayments().size)
        }

        @Test
        @DisplayName("Null body is handled gracefully")
        fun `null body is handled gracefully`() {
            val sender = Sender(name = "TestBank", addresses = listOf("TESTBANK"), rules = listOf(PaymentRegexRule(regex = standardRegex)))
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            val intent = Intent("android.provider.Telephony.SMS_RECEIVED").apply {
                putExtra(SmsReceiver.EXTRA_TEST_SENDER, "TESTBANK")
                // No body extra
            }
            smsReceiver.onReceive(context, intent)

            assertEquals(0, repository.getAllPayments().size)
        }

        @Test
        @DisplayName("Unicode in message is preserved")
        fun `unicode in message is preserved`() {
            val unicodeRegex = "Payment (\\d+\\.\\d{2}) (\\w+) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
            val sender = Sender(name = "TestBank", addresses = listOf("TESTBANK"), rules = listOf(PaymentRegexRule(regex = unicodeRegex)))
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 100.00 GEL card 1234 საქართველო at 20230901 bal 1000.00"))

            val payments = repository.getAllPayments()
            assertEquals(1, payments.size)
            assertEquals("საქართველო", payments[0].merchant)
        }

        @Test
        @DisplayName("Special characters in merchant are preserved")
        fun `special characters in merchant are preserved`() {
            val sender = Sender(name = "TestBank", addresses = listOf("TESTBANK"), rules = listOf(PaymentRegexRule(regex = standardRegex)))
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 Store-Name_123 at 20230901 bal 1000.00"))

            val payments = repository.getAllPayments()
            assertEquals(1, payments.size)
            assertEquals("Store-Name_123", payments[0].merchant)
        }
    }

    @Nested
    @DisplayName("Ordering and Retrieval")
    inner class OrderingAndRetrieval {

        @Test
        @DisplayName("Payments are returned in descending order by ID")
        fun `payments are returned in descending order by ID`() {
            val sender = Sender(name = "TestBank", addresses = listOf("TESTBANK"), rules = listOf(PaymentRegexRule(regex = standardRegex)))
            val processor = createProcessor(listOf(sender), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 100.00 USD card 1234 First at 20230901 bal 1000.00"))
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 200.00 USD card 1234 Second at 20230902 bal 800.00"))
            smsReceiver.onReceive(context, buildSmsIntent("TESTBANK", "Payment 300.00 USD card 1234 Third at 20230903 bal 500.00"))

            val payments = repository.getAllPayments()
            assertEquals(3, payments.size)
            // Newest first
            assertEquals("Third", payments[0].merchant)
            assertEquals("Second", payments[1].merchant)
            assertEquals("First", payments[2].merchant)
        }

        @Test
        @DisplayName("Distinct senders are retrievable")
        fun `distinct senders are retrievable`() {
            val senderA = Sender(name = "BankA", addresses = listOf("BANKA"), rules = listOf(PaymentRegexRule(regex = standardRegex)))
            val senderB = Sender(name = "BankB", addresses = listOf("BANKB"), rules = listOf(PaymentRegexRule(regex = standardRegex)))
            val senderC = Sender(name = "BankC", addresses = listOf("BANKC"), rules = listOf(PaymentRegexRule(regex = standardRegex)))
            val processor = createProcessor(listOf(senderA, senderB, senderC), emptyList())
            smsReceiver.setPaymentProcessorForTest(processor)

            smsReceiver.onReceive(context, buildSmsIntent("BANKA", "Payment 100.00 USD card 1234 Store1 at 20230901 bal 1000.00"))
            smsReceiver.onReceive(context, buildSmsIntent("BANKC", "Payment 200.00 USD card 1234 Store2 at 20230901 bal 800.00"))
            smsReceiver.onReceive(context, buildSmsIntent("BANKA", "Payment 300.00 USD card 1234 Store3 at 20230901 bal 500.00"))

            val senders = repository.getDistinctSenderAddresses()
            assertEquals(2, senders.size)
            assertTrue(senders.contains("BANKA"))
            assertTrue(senders.contains("BANKC"))
            assertFalse(senders.contains("BANKB")) // No messages from this sender
        }
    }
}
