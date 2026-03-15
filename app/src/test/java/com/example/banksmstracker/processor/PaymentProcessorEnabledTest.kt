package com.example.banksmstracker.processor

import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Merchant
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.data.Rule
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.repository.PaymentRepository
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PaymentProcessorEnabledTest {

    private lateinit var mockRepository: TestPaymentRepository
    private val testAddress = "TestBank"

    // Simple regex that captures named groups: amount, currency, card, merchant, date, balance
    private val testRegex =
        """(?<amount>\d+\.\d+) (?<currency>\w+) card:(?<card>\d+)""" +
            """ at:(?<merchant>\w+) time:(?<date>\S+) bal:(?<balance>\d+\.\d+)"""

    private val testMessage = "100.50 USD card:1234 at:Amazon time:2024-01-15 bal:500.00"

    @BeforeEach
    fun setup() {
        mockRepository = TestPaymentRepository()
    }

    @Test
    fun `disabled sender should not be matched`() {
        val senders = listOf(
            Sender(
                id = 1,
                name = "Test Bank",
                addresses = mutableListOf(testAddress),
                rules = mutableListOf(Rule(pattern = testRegex)),
                enabled = false
            )
        )
        val categories = emptyList<Category>()

        val processor = PaymentProcessor(senders, categories, mockRepository)

        assertFailsWith<UnparsedMessageException> {
            processor.getPaymentFromMessage(testMessage, testAddress)
        }
    }

    @Test
    fun `enabled sender should be matched`() {
        val senders = listOf(
            Sender(
                id = 1,
                name = "Test Bank",
                addresses = mutableListOf(testAddress),
                rules = mutableListOf(Rule(pattern = testRegex)),
                enabled = true
            )
        )
        val categories = emptyList<Category>()

        val processor = PaymentProcessor(senders, categories, mockRepository)
        val payment = processor.getPaymentFromMessage(testMessage, testAddress)

        assertEquals(100.50, payment?.amount)
        assertEquals("USD", payment?.currency)
    }

    @Test
    fun `disabled rule should not be matched even with enabled sender`() {
        val senders = listOf(
            Sender(
                id = 1,
                name = "Test Bank",
                addresses = mutableListOf(testAddress),
                rules = mutableListOf(Rule(pattern = testRegex, enabled = false)),
                enabled = true
            )
        )
        val categories = emptyList<Category>()

        val processor = PaymentProcessor(senders, categories, mockRepository)

        assertFailsWith<UnparsedMessageException> {
            processor.getPaymentFromMessage(testMessage, testAddress)
        }
    }

    @Test
    fun `enabled rule with enabled sender should match`() {
        val senders = listOf(
            Sender(
                id = 1,
                name = "Test Bank",
                addresses = mutableListOf(testAddress),
                rules = mutableListOf(Rule(pattern = testRegex, enabled = true)),
                enabled = true
            )
        )
        val categories = emptyList<Category>()

        val processor = PaymentProcessor(senders, categories, mockRepository)
        val payment = processor.getPaymentFromMessage(testMessage, testAddress)

        assertEquals(100.50, payment?.amount)
    }

    @Test
    fun `disabled category should not be assigned`() = runBlocking {
        val senders = listOf(
            Sender(
                id = 1,
                name = "Test Bank",
                addresses = mutableListOf(testAddress),
                rules = mutableListOf(Rule(pattern = testRegex, enabled = true)),
                enabled = true
            )
        )
        val categories = listOf(
            Category(
                id = 1,
                name = "Shopping",
                merchants = mutableListOf(Merchant("Amazon")),
                enabled = false
            )
        )

        val processor = PaymentProcessor(senders, categories, mockRepository)
        val payment = processor.processMessage(testMessage, testAddress)

        assertNull(payment.categoryId)
    }

    @Test
    fun `enabled category should be assigned`() = runBlocking {
        val senders = listOf(
            Sender(
                id = 1,
                name = "Test Bank",
                addresses = mutableListOf(testAddress),
                rules = mutableListOf(Rule(pattern = testRegex, enabled = true)),
                enabled = true
            )
        )
        val categories = listOf(
            Category(
                id = 1,
                name = "Shopping",
                merchants = mutableListOf(Merchant("Amazon")),
                enabled = true
            )
        )

        val processor = PaymentProcessor(senders, categories, mockRepository)
        val payment = processor.processMessage(testMessage, testAddress)

        assertEquals("Shopping", payment.categoryId)
    }

    @Test
    fun `multiple rules with only one enabled should match correct one`() {
        val nonMatchingRegex = """NOMATCH (?<amount>\d+)"""
        val senders = listOf(
            Sender(
                id = 1,
                name = "Test Bank",
                addresses = mutableListOf(testAddress),
                rules = mutableListOf(
                    Rule(pattern = nonMatchingRegex, enabled = true),
                    Rule(pattern = testRegex, enabled = true)
                ),
                enabled = true
            )
        )
        val categories = emptyList<Category>()

        val processor = PaymentProcessor(senders, categories, mockRepository)
        val payment = processor.getPaymentFromMessage(testMessage, testAddress)

        assertEquals(100.50, payment?.amount)
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
