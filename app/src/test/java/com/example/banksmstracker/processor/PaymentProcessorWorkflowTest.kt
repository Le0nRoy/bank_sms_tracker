package com.example.banksmstracker.processor

import com.example.banksmstracker.data.MessageProcessResult
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.data.Rule
import com.example.banksmstracker.data.RuleType
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.repository.PaymentRepository
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for the message processing workflow:
 * 1. Try payment rules -> if match, return Payment
 * 2. Try income rules -> if match, return Income
 * 3. Try ignore rules -> if match, signal ignored
 * 4. If no match, throw UnparsedMessageException
 */
class PaymentProcessorWorkflowTest {

    private lateinit var mockRepository: TestPaymentRepository
    private val testAddress = "TestBank"

    // Simple regex that captures named groups: amount, currency, card, merchant, date, balance
    private val paymentRegex =
        """(?<amount>\d+\.\d+) (?<currency>\w+) card:(?<card>\d+)""" +
            """ at:(?<merchant>\w+) time:(?<date>\S+) bal:(?<balance>\d+\.\d+)"""
    private val paymentMessage = "100.50 USD card:1234 at:Amazon time:2024-01-15 bal:500.00"

    // Income regex with named groups
    private val incomeRegex =
        """INCOME: (?<amount>\d+\.\d+) (?<currency>\w+) from:(?<card>\w+)""" +
            """ via:(?<merchant>\w+) time:(?<date>\S+) bal:(?<balance>\d+\.\d+)"""
    private val incomeMessage = "INCOME: 1000.00 USD from:1234 via:Employer time:2024-01-15 bal:1500.00"

    // Ignore regex - simple patterns without capture groups
    private val ignoreRegex = """OTP|verification|balance inquiry|promo"""
    private val ignoreMessage = "Your OTP is 123456"

    @BeforeEach
    fun setup() {
        mockRepository = TestPaymentRepository()
    }

    @Nested
    inner class IgnoreRulesWithoutGroups {

        @Test
        fun `ignore rule should match without capture groups`() {
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(pattern = ignoreRegex, ruleType = RuleType.IGNORE, enabled = true)
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            val result = processor.getMessageResult(ignoreMessage, testAddress)

            assertTrue(result is MessageProcessResult.Ignored)
        }

        @Test
        fun `ignore rule with simple pattern should work`() {
            val simpleIgnorePattern = "balance inquiry"
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(pattern = simpleIgnorePattern, ruleType = RuleType.IGNORE, enabled = true)
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            val result = processor.getMessageResult("Your balance inquiry result is X", testAddress)

            assertTrue(result is MessageProcessResult.Ignored)
        }

        @Test
        fun `ignore rule should store rule description in result`() {
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(
                            pattern = ignoreRegex,
                            ruleType = RuleType.IGNORE,
                            enabled = true,
                            description = "OTP and promo filter"
                        )
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            val result = processor.getMessageResult(ignoreMessage, testAddress)

            assertTrue(result is MessageProcessResult.Ignored)
            assertEquals("OTP and promo filter", (result as MessageProcessResult.Ignored).ruleName)
        }

        @Test
        fun `disabled ignore rule should not match`() {
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(pattern = ignoreRegex, ruleType = RuleType.IGNORE, enabled = false)
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            assertFailsWith<UnparsedMessageException> {
                processor.getMessageResult(ignoreMessage, testAddress)
            }
        }
    }

    @Nested
    inner class WorkflowPriority {

        @Test
        fun `payment rule should take priority over income and ignore`() {
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(pattern = paymentRegex, ruleType = RuleType.PAYMENT, enabled = true),
                        Rule(pattern = incomeRegex, ruleType = RuleType.INCOME, enabled = true),
                        Rule(pattern = ".*", ruleType = RuleType.IGNORE, enabled = true) // matches everything
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            val result = processor.getMessageResult(paymentMessage, testAddress)

            assertTrue(result is MessageProcessResult.PaymentResult)
            assertEquals(100.50, (result as MessageProcessResult.PaymentResult).payment.amount)
        }

        @Test
        fun `income rule should take priority over ignore when no payment match`() {
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(pattern = paymentRegex, ruleType = RuleType.PAYMENT, enabled = true),
                        Rule(pattern = incomeRegex, ruleType = RuleType.INCOME, enabled = true),
                        Rule(pattern = ".*", ruleType = RuleType.IGNORE, enabled = true) // matches everything
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            val result = processor.getMessageResult(incomeMessage, testAddress)

            assertTrue(result is MessageProcessResult.IncomeResult)
            assertEquals(1000.00, (result as MessageProcessResult.IncomeResult).income.amount)
        }

        @Test
        fun `ignore rule should match when no payment or income match`() {
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(pattern = paymentRegex, ruleType = RuleType.PAYMENT, enabled = true),
                        Rule(pattern = incomeRegex, ruleType = RuleType.INCOME, enabled = true),
                        Rule(pattern = ignoreRegex, ruleType = RuleType.IGNORE, enabled = true)
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            val result = processor.getMessageResult(ignoreMessage, testAddress)

            assertTrue(result is MessageProcessResult.Ignored)
        }

        @Test
        fun `should throw exception when no rules match`() {
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(pattern = paymentRegex, ruleType = RuleType.PAYMENT, enabled = true),
                        Rule(pattern = incomeRegex, ruleType = RuleType.INCOME, enabled = true),
                        Rule(pattern = ignoreRegex, ruleType = RuleType.IGNORE, enabled = true)
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            assertFailsWith<UnparsedMessageException> {
                processor.getMessageResult("Random unmatched message", testAddress)
            }
        }
    }

    @Nested
    inner class IncomeRules {

        @Test
        fun `income rule should extract amount and currency`() {
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(pattern = incomeRegex, ruleType = RuleType.INCOME, enabled = true)
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            val result = processor.getMessageResult(incomeMessage, testAddress)

            assertTrue(result is MessageProcessResult.IncomeResult)
            val income = (result as MessageProcessResult.IncomeResult).income
            assertEquals(1000.00, income.amount)
            assertEquals("USD", income.currency)
        }

        @Test
        fun `income rule should extract source and timestamp`() {
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(pattern = incomeRegex, ruleType = RuleType.INCOME, enabled = true)
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            val result = processor.getMessageResult(incomeMessage, testAddress)

            assertTrue(result is MessageProcessResult.IncomeResult)
            val income = (result as MessageProcessResult.IncomeResult).income
            assertEquals("Employer", income.source) // source is in group 4 (merchant position)
            assertEquals("2024-01-15", income.timestamp)
        }

        @Test
        fun `income should include sender address`() {
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(pattern = incomeRegex, ruleType = RuleType.INCOME, enabled = true)
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            val result = processor.getMessageResult(incomeMessage, testAddress)

            assertTrue(result is MessageProcessResult.IncomeResult)
            val income = (result as MessageProcessResult.IncomeResult).income
            assertEquals(testAddress, income.senderAddress)
        }
    }

    @Nested
    inner class ProcessMessageFull {

        @Test
        fun `processMessageFull should save payment and return result`() = runBlocking {
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(pattern = paymentRegex, ruleType = RuleType.PAYMENT, enabled = true)
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            val result = processor.processMessageFull(paymentMessage, testAddress)

            assertTrue(result is MessageProcessResult.PaymentResult)
            assertTrue(mockRepository.getAllPayments().isNotEmpty())
        }

        @Test
        fun `processMessageFull should return income result without saving to payment repository`() = runBlocking {
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(pattern = incomeRegex, ruleType = RuleType.INCOME, enabled = true)
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            val result = processor.processMessageFull(incomeMessage, testAddress)

            assertTrue(result is MessageProcessResult.IncomeResult)
            assertTrue(mockRepository.getAllPayments().isEmpty()) // Income not saved to payment repo
        }

        @Test
        fun `processMessageFull should return ignored result`() = runBlocking {
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(pattern = ignoreRegex, ruleType = RuleType.IGNORE, enabled = true)
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            val result = processor.processMessageFull(ignoreMessage, testAddress)

            assertTrue(result is MessageProcessResult.Ignored)
            assertTrue(mockRepository.getAllPayments().isEmpty()) // Nothing saved
        }
    }

    @Nested
    inner class MessageIgnoredException {

        @Test
        fun `processMessage should throw MessageIgnoredException for ignored messages`() = runBlocking {
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(pattern = ignoreRegex, ruleType = RuleType.IGNORE, enabled = true)
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            val exception = assertFailsWith<com.example.banksmstracker.processor.MessageIgnoredException> {
                processor.processMessage(ignoreMessage, testAddress)
            }
            assertNotNull(exception.ruleName)
        }

        @Test
        fun `getMessageResult should return Ignored for ignored messages`() {
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(pattern = ignoreRegex, ruleType = RuleType.IGNORE, enabled = true)
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            assertTrue(processor.getMessageResult(ignoreMessage, testAddress) is MessageProcessResult.Ignored)
        }
    }

    @Nested
    inner class RuleIdTracking {

        @Test
        fun `payment should include rule id`() {
            val ruleId = 42L
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(id = ruleId, pattern = paymentRegex, ruleType = RuleType.PAYMENT, enabled = true)
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            val result = processor.getMessageResult(paymentMessage, testAddress)

            assertTrue(result is MessageProcessResult.PaymentResult)
            assertEquals(ruleId, (result as MessageProcessResult.PaymentResult).payment.ruleId)
        }

        @Test
        fun `income should include rule id`() {
            val ruleId = 99L
            val senders = listOf(
                Sender(
                    id = 1,
                    name = "Test Bank",
                    addresses = mutableListOf(testAddress),
                    rules = mutableListOf(
                        Rule(id = ruleId, pattern = incomeRegex, ruleType = RuleType.INCOME, enabled = true)
                    ),
                    enabled = true
                )
            )
            val processor = PaymentProcessor(senders, emptyList(), mockRepository)

            val result = processor.getMessageResult(incomeMessage, testAddress)

            assertTrue(result is MessageProcessResult.IncomeResult)
            assertEquals(ruleId, (result as MessageProcessResult.IncomeResult).income.ruleId)
        }
    }

    // Simple test repository implementation
    class TestPaymentRepository : PaymentRepository {
        private val payments = mutableListOf<Payment>()

        override suspend fun savePayment(payment: Payment, message: String, address: String): Boolean {
            payments.add(payment)
            return true
        }

        override suspend fun getAllPayments(): List<Payment> = payments.toList()

        override suspend fun getPaymentsByCategory(categoryId: String): List<Payment> =
            payments.filter { it.categoryId == categoryId }

        override suspend fun getUncategorizedPayments(): List<Payment> = payments.filter { it.categoryId == null }

        override suspend fun getPaymentsBySender(senderAddress: String): List<Payment> =
            payments.filter { it.senderAddress == senderAddress }

        override suspend fun getDistinctSenderAddresses(): List<String> =
            payments.mapNotNull { it.senderAddress }.distinct().sorted()

        override suspend fun updatePaymentCategory(paymentId: Long, categoryName: String?) {
            val index = payments.indexOfFirst { it.id == paymentId }
            if (index >= 0) {
                payments[index] = payments[index].copy(categoryId = categoryName)
            }
        }

        override suspend fun getPaymentsByRule(ruleId: Long): List<Payment> = payments.filter { it.ruleId == ruleId }

        override suspend fun updateCategoryForRule(ruleId: Long, categoryName: String?) {
            payments.replaceAll { payment ->
                if (payment.ruleId == ruleId) {
                    payment.copy(categoryId = categoryName)
                } else {
                    payment
                }
            }
        }

        override suspend fun updateCategoryForMerchant(merchant: String, categoryName: String?) {
            payments.replaceAll { payment ->
                if (payment.merchant?.equals(merchant, ignoreCase = true) == true) {
                    payment.copy(categoryId = categoryName)
                } else {
                    payment
                }
            }
        }
    }
}
