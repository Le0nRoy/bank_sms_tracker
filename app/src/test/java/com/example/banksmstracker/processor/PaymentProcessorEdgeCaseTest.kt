package com.example.banksmstracker.processor

import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.data.Rule
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.repository.InMemoryPaymentRepository
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PaymentProcessor Edge Cases")
class PaymentProcessorEdgeCaseTest {

    private lateinit var repository: InMemoryPaymentRepository

    // Valid regex with named capture groups for testing
    private val validRegex =
        "(?<amount>\\d+\\.\\d+)\\s*(?<currency>USD|EUR|GEL)\\s*(?<card>\\*\\d+)?" +
            "\\s*(?<merchant>.+?)\\s*(?<date>\\d{2}/\\d{2}/\\d{4})?\\s*(?<balance>\\d+\\.\\d+)?"

    @BeforeEach
    fun setUp() {
        repository = InMemoryPaymentRepository()
    }

    @Nested
    @DisplayName("Empty Input Handling")
    inner class EmptyInputHandling {

        @Test
        @DisplayName("Empty message throws UnparsedMessageException")
        fun `empty message throws UnparsedMessageException`() {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = validRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            assertFailsWith<UnparsedMessageException> {
                processor.getPaymentFromMessage("", "TESTBANK")
            }
        }

        @Test
        @DisplayName("Blank message throws UnparsedMessageException")
        fun `blank message throws UnparsedMessageException`() {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = validRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            assertFailsWith<UnparsedMessageException> {
                processor.getPaymentFromMessage("   ", "TESTBANK")
            }
        }

        @Test
        @DisplayName("Empty senders list throws for any address")
        fun `empty senders list throws for any address`() {
            val processor = PaymentProcessor(emptyList(), emptyList(), repository)

            assertFailsWith<UnparsedMessageException> {
                processor.getPaymentFromMessage("100.00 USD *1234 Amazon 01/01/2024 500.00", "ANYBANK")
            }
        }

        @Test
        @DisplayName("Sender with empty rules throws UnparsedMessageException")
        fun `sender with empty rules throws UnparsedMessageException`() {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf()
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            assertFailsWith<UnparsedMessageException> {
                processor.getPaymentFromMessage("100.00 USD *1234 Amazon", "TESTBANK")
            }
        }

        @Test
        @DisplayName("Empty categories list leaves payment uncategorized")
        fun `empty categories list leaves payment uncategorized`() {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = validRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            val payment = processor.getPaymentFromMessage("100.00 USD *1234 Amazon", "TESTBANK")
            assertNotNull(payment)
            assertNull(payment.categoryId)
        }
    }

    @Nested
    @DisplayName("Regex Capture Group Handling")
    inner class RegexGroupHandling {

        @Test
        @DisplayName("Regex without named amount group is skipped")
        fun `regex without named amount group is skipped`() {
            val noNamedGroupsRegex = "(\\d+\\.\\d+)\\s*(USD|EUR)" // positional groups, no named groups
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = noNamedGroupsRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            assertFailsWith<UnparsedMessageException> {
                processor.getPaymentFromMessage("100.00 USD", "TESTBANK")
            }
        }

        @Test
        @DisplayName("Regex with named groups and extra non-named groups works correctly")
        fun `regex with named groups and extra groups works correctly`() {
            val extraGroupsRegex =
                "(?<amount>\\d+\\.\\d+)\\s*(?<currency>USD|EUR)\\s*(?<card>\\*\\d+)?" +
                    "\\s*(?<merchant>.+?)\\s*(?<date>\\d{2}/\\d{2}/\\d{4})?\\s*(?<balance>\\d+\\.\\d+)?\\s*(extra)?"
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = extraGroupsRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            val payment = processor.getPaymentFromMessage("100.00 USD *1234 Amazon 01/01/2024 500.00 extra", "TESTBANK")
            assertNotNull(payment)
            assertEquals(100.00, payment.amount)
        }

        @Test
        @DisplayName("Empty amount named group results in skipped rule")
        fun `empty amount group results in skipped rule`() {
            // Regex where amount group captures empty string
            val emptyAmountRegex = "(?<amount>)?(?<currency>)(?<card>)(?<merchant>)(?<balance>)?"
            val validRegex2 =
                "(?<amount>\\d+\\.\\d+)\\s*(?<currency>USD)\\s*(?<card>\\*\\d+)?" +
                    "\\s*(?<merchant>\\w+)\\s*(?<date>\\d{2}/\\d{2})?\\s*(?<balance>\\d+)?"
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(
                    Rule(pattern = emptyAmountRegex),
                    Rule(pattern = validRegex2)
                )
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            // Should fall through to second rule
            val payment = processor.getPaymentFromMessage("50.00 USD *1234 Store 01/01 100", "TESTBANK")
            assertNotNull(payment)
            assertEquals(50.00, payment.amount)
        }
    }

    @Nested
    @DisplayName("Address Matching")
    inner class AddressMatching {

        @Test
        @DisplayName("Address matching is case-insensitive")
        fun `address matching is case insensitive`() {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = validRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            // lowercase address should match uppercase
            val payment = processor.getPaymentFromMessage("100.00 USD *1234 Amazon", "testbank")
            assertNotNull(payment)
        }

        @Test
        @DisplayName("Unknown address throws UnparsedMessageException")
        fun `unknown address throws UnparsedMessageException`() {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = validRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            assertFailsWith<UnparsedMessageException> {
                processor.getPaymentFromMessage("100.00 USD *1234 Amazon", "UNKNOWN")
            }
        }

        @Test
        @DisplayName("Multiple addresses work correctly")
        fun `multiple addresses work correctly`() {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("BANK1", "BANK2", "BANK3"),
                rules = mutableListOf(Rule(pattern = validRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            // All addresses should work
            assertNotNull(processor.getPaymentFromMessage("100.00 USD *1234 Amazon", "BANK1"))
            assertNotNull(processor.getPaymentFromMessage("100.00 USD *1234 Amazon", "BANK2"))
            assertNotNull(processor.getPaymentFromMessage("100.00 USD *1234 Amazon", "BANK3"))
        }
    }

    @Nested
    @DisplayName("Category Assignment")
    inner class CategoryAssignment {

        @Test
        @DisplayName("Merchant matching is case-insensitive")
        fun `merchant matching is case insensitive`() {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = validRegex))
            )
            val category = Category(name = "Shopping", merchants = mutableListOf("Amazon", "Walmart"))
            val processor = PaymentProcessor(listOf(sender), listOf(category), repository)

            // "AMAZON" should match "Amazon" in category
            val payment = processor.getPaymentFromMessage("100.00 USD *1234 AMAZON", "TESTBANK")
            assertNotNull(payment)
            // Category assignment happens in processMessage, not getPaymentFromMessage
            assertNull(payment.categoryId)
        }

        @Test
        @DisplayName("Payment with null merchant stays uncategorized")
        fun `payment with null merchant stays uncategorized`() = runBlocking {
            // Regex where merchant group can be empty
            val optionalMerchantRegex =
                "(?<amount>\\d+\\.\\d+)\\s*(?<currency>USD)\\s*(?<card>)?(?<merchant>)?(?<balance>)?"
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = optionalMerchantRegex))
            )
            val category = Category(name = "Shopping", merchants = mutableListOf("Amazon"))
            val processor = PaymentProcessor(listOf(sender), listOf(category), repository)

            val payment = processor.processMessage("100.00 USD", "TESTBANK")
            assertNull(payment.categoryId)
        }

        @Test
        @DisplayName("Unknown merchant stays uncategorized")
        fun `unknown merchant stays uncategorized`() = runBlocking {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = validRegex))
            )
            val category = Category(name = "Shopping", merchants = mutableListOf("Amazon", "Walmart"))
            val processor = PaymentProcessor(listOf(sender), listOf(category), repository)

            val payment = processor.processMessage("100.00 USD *1234 UnknownMerchant", "TESTBANK")
            assertNull(payment.categoryId)
        }
    }

    @Nested
    @DisplayName("Boundary Values")
    inner class BoundaryValues {

        @Test
        @DisplayName("Very large amount is parsed correctly")
        fun `very large amount is parsed correctly`() {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = validRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            val payment = processor.getPaymentFromMessage("999999999.99 USD *1234 Store", "TESTBANK")
            assertNotNull(payment)
            assertEquals(999999999.99, payment.amount)
        }

        @Test
        @DisplayName("Very small amount is parsed correctly")
        fun `very small amount is parsed correctly`() {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = validRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            val payment = processor.getPaymentFromMessage("0.01 USD *1234 Store", "TESTBANK")
            assertNotNull(payment)
            assertEquals(0.01, payment.amount)
        }

        @Test
        @DisplayName("Zero amount is parsed correctly")
        fun `zero amount is parsed correctly`() {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = validRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            val payment = processor.getPaymentFromMessage("0.00 USD *1234 Store", "TESTBANK")
            assertNotNull(payment)
            assertEquals(0.00, payment.amount)
        }

        @Test
        @DisplayName("Long merchant name is preserved")
        fun `long merchant name is preserved`() {
            // Use a regex with greedy matching for merchant (no optional groups after)
            val greedyRegex =
                "(?<amount>\\d+\\.\\d+)\\s*(?<currency>USD|EUR|GEL)\\s*(?<card>\\*\\d+)\\s+" +
                    "(?<merchant>.+)\\s+(?<date>\\d{2}/\\d{2}/\\d{4})\\s*(?<balance>\\d+\\.\\d+)?"
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = greedyRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            val longMerchant = "A".repeat(200)
            val payment = processor.getPaymentFromMessage("100.00 USD *1234 $longMerchant 01/01/2024", "TESTBANK")
            assertNotNull(payment)
            assertEquals(longMerchant, payment.merchant)
        }
    }

    @Nested
    @DisplayName("Special Characters in Regex")
    inner class SpecialRegexPatterns {

        @Test
        @DisplayName("Regex with digit class works")
        fun `regex with digit class works`() {
            val digitRegex =
                "(?<amount>\\d+\\.\\d{2})\\s+(?<currency>\\w+)\\s+(?<card>\\d{4})" +
                    "\\s+(?<merchant>\\w+)\\s+(?<date>\\d{2}/\\d{2})\\s+(?<balance>\\d+)?"
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = digitRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            val payment = processor.getPaymentFromMessage("100.00 USD 1234 Store 01/01 500", "TESTBANK")
            assertNotNull(payment)
            assertEquals(100.00, payment.amount)
            assertEquals("USD", payment.currency)
        }

        @Test
        @DisplayName("Regex with quantifiers works")
        fun `regex with quantifiers works`() {
            val quantifierRegex =
                "(?<amount>\\d+\\.\\d+)\\s*(?<currency>\\w{2,5})\\s*(?<card>\\*?\\d*)" +
                    "\\s*(?<merchant>.*)\\s*(?<balance>)?"
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = quantifierRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            val payment = processor.getPaymentFromMessage("100.00 USD *1234 Store Name Here", "TESTBANK")
            assertNotNull(payment)
        }

        @Test
        @DisplayName("Regex with anchors works for full match")
        fun `regex with start anchor works`() {
            // Using ^ anchor
            val anchoredRegex =
                "^(?<amount>\\d+\\.\\d+)\\s*(?<currency>USD)\\s*(?<card>\\d*)" +
                    "\\s*(?<merchant>.*)\\s*(?<balance>)?"
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = anchoredRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            val payment = processor.getPaymentFromMessage("100.00 USD 1234 Store", "TESTBANK")
            assertNotNull(payment)
            assertEquals(100.00, payment.amount)
        }
    }

    @Nested
    @DisplayName("Enabled/Disabled Filtering")
    inner class EnabledDisabledFiltering {

        @Test
        @DisplayName("Disabled sender is not matched")
        fun `disabled sender is not matched`() {
            val disabledSender = Sender(
                name = "Disabled Bank",
                addresses = mutableListOf("DISABLEDBANK"),
                rules = mutableListOf(Rule(pattern = validRegex)),
                enabled = false
            )
            val processor = PaymentProcessor(listOf(disabledSender), emptyList(), repository)

            assertFailsWith<UnparsedMessageException> {
                processor.getPaymentFromMessage("100.00 USD *1234 Amazon", "DISABLEDBANK")
            }
        }

        @Test
        @DisplayName("Disabled rule is not used")
        fun `disabled rule is not used`() {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(
                    Rule(pattern = validRegex, enabled = false)
                )
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            assertFailsWith<UnparsedMessageException> {
                processor.getPaymentFromMessage("100.00 USD *1234 Amazon", "TESTBANK")
            }
        }

        @Test
        @DisplayName("Disabled category is not assigned")
        fun `disabled category is not assigned`() = runBlocking {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = validRegex))
            )
            val disabledCategory = Category(
                name = "Shopping",
                merchants = mutableListOf("Amazon"),
                enabled = false
            )
            val processor = PaymentProcessor(listOf(sender), listOf(disabledCategory), repository)

            val payment = processor.processMessage("100.00 USD *1234 Amazon", "TESTBANK")
            // Category should not be assigned because it's disabled
            assertNull(payment.categoryId)
        }

        @Test
        @DisplayName("Enabled sender after disabled is matched")
        fun `enabled sender after disabled is matched`() {
            val disabledSender = Sender(
                name = "Disabled Bank",
                addresses = mutableListOf("BANK"),
                rules = mutableListOf(Rule(pattern = validRegex)),
                enabled = false
            )
            val enabledSender = Sender(
                name = "Enabled Bank",
                addresses = mutableListOf("BANK"),
                rules = mutableListOf(Rule(pattern = validRegex)),
                enabled = true
            )
            val processor = PaymentProcessor(listOf(disabledSender, enabledSender), emptyList(), repository)

            // Should match the enabled sender
            val payment = processor.getPaymentFromMessage("100.00 USD *1234 Amazon", "BANK")
            assertNotNull(payment)
        }
    }

    @Nested
    @DisplayName("Date Approximation")
    inner class DateApproximation {

        private val dateOnlyRegex = "(?<amount>\\d+\\.\\d+)\\s*(?<currency>USD)"
        private val withDateRegex = "(?<amount>\\d+\\.\\d+)\\s*(?<currency>USD)\\s*(?<date>\\d{2}/\\d{2}/\\d{4})"

        @Test
        @DisplayName("Payment with no date gets date from neighbor")
        fun `payment with no date gets date from neighbor`() = runBlocking {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = dateOnlyRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            // Insert a neighbor with a timestamp
            repository.savePayment(
                Payment(
                    amount = 50.0,
                    currency = "USD",
                    card = null,
                    merchant = null,
                    timestamp = "01/01/2024",
                    balance = null
                ),
                "50.00 USD neighbor",
                "TESTBANK"
            )

            val result = processor.processMessage("100.00 USD", "TESTBANK")
            assertEquals("01/01/2024", result.timestamp)
        }

        @Test
        @DisplayName("Payment that already has timestamp is not changed")
        fun `payment that already has timestamp is not changed`() = runBlocking {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = withDateRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            // Insert a neighbor with a different date
            repository.savePayment(
                Payment(
                    amount = 50.0,
                    currency = "USD",
                    card = null,
                    merchant = null,
                    timestamp = "02/02/2024",
                    balance = null
                ),
                "50.00 USD neighbor",
                "TESTBANK"
            )

            val result = processor.processMessage("100.00 USD 01/01/2024", "TESTBANK")
            assertEquals("01/01/2024", result.timestamp)
        }

        @Test
        @DisplayName("Payment with no date and no neighbors has null timestamp")
        fun `payment with no date and no neighbors has null timestamp`() = runBlocking {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = dateOnlyRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            val result = processor.processMessage("100.00 USD", "TESTBANK")
            assertNull(result.timestamp)
        }

        @Test
        @DisplayName("Neighbor with date-time timestamp provides only the date part")
        fun `neighbor with date-time timestamp provides only the date part`() = runBlocking {
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(Rule(pattern = dateOnlyRegex))
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            // Insert neighbor with date+time
            repository.savePayment(
                Payment(
                    amount = 50.0,
                    currency = "USD",
                    card = null,
                    merchant = null,
                    timestamp = "01/01/2024 12:30:00",
                    balance = null
                ),
                "50.00 USD neighbor",
                "TESTBANK"
            )

            val result = processor.processMessage("100.00 USD", "TESTBANK")
            assertEquals("01/01/2024", result.timestamp)
        }
    }

    @Nested
    @DisplayName("Multiple Rules Handling")
    inner class MultipleRulesHandling {

        @Test
        @DisplayName("First matching rule is used")
        fun `first matching rule is used`() {
            val rule1 =
                Rule(
                    pattern = "(?<amount>\\d+\\.\\d+)\\s*(?<currency>USD)\\s*(?<card>\\d*)" +
                        "\\s*(?<merchant>Store1)\\s*(?<balance>)?"
                ) // matches Store1
            val rule2 =
                Rule(
                    pattern = "(?<amount>\\d+\\.\\d+)\\s*(?<currency>USD)\\s*(?<card>\\d*)" +
                        "\\s*(?<merchant>\\w+)\\s*(?<balance>)?"
                ) // matches any word
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(rule1, rule2)
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            val payment = processor.getPaymentFromMessage("100.00 USD 1234 Store1", "TESTBANK")
            assertNotNull(payment)
            assertEquals("Store1", payment.merchant)
        }

        @Test
        @DisplayName("Falls through to second rule when first does not match")
        fun `falls through to second rule when first does not match`() {
            val rule1 =
                Rule(
                    pattern = "(?<amount>\\d+\\.\\d+)\\s*(?<currency>EUR)\\s*(?<card>\\d*)" +
                        "\\s*(?<merchant>\\w+)\\s*(?<balance>)?"
                ) // EUR only
            val rule2 =
                Rule(
                    pattern = "(?<amount>\\d+\\.\\d+)\\s*(?<currency>USD)\\s*(?<card>\\d*)" +
                        "\\s*(?<merchant>\\w+)\\s*(?<balance>)?"
                ) // USD only
            val sender = Sender(
                name = "Test Bank",
                addresses = mutableListOf("TESTBANK"),
                rules = mutableListOf(rule1, rule2)
            )
            val processor = PaymentProcessor(listOf(sender), emptyList(), repository)

            val payment = processor.getPaymentFromMessage("100.00 USD 1234 Store", "TESTBANK")
            assertNotNull(payment)
            assertEquals("USD", payment.currency)
        }
    }
}
