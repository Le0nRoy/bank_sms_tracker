package com.example.banksmstracker.processor

import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.data.PaymentRegexRule
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

    // Simple regex that captures: (amount)(currency)(card)(merchant)(timestamp)(balance)
    private val testRegex = """(\d+\.\d+) (\w+) card:(\d+) at:(\w+) time:(\S+) bal:(\d+\.\d+)"""

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
                rules = mutableListOf(PaymentRegexRule(regex = testRegex)),
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
                rules = mutableListOf(PaymentRegexRule(regex = testRegex)),
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
                rules = mutableListOf(PaymentRegexRule(regex = testRegex, enabled = false)),
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
                rules = mutableListOf(PaymentRegexRule(regex = testRegex, enabled = true)),
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
                rules = mutableListOf(PaymentRegexRule(regex = testRegex, enabled = true)),
                enabled = true
            )
        )
        val categories = listOf(
            Category(
                id = 1,
                name = "Shopping",
                merchants = mutableListOf("Amazon"),
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
                rules = mutableListOf(PaymentRegexRule(regex = testRegex, enabled = true)),
                enabled = true
            )
        )
        val categories = listOf(
            Category(
                id = 1,
                name = "Shopping",
                merchants = mutableListOf("Amazon"),
                enabled = true
            )
        )

        val processor = PaymentProcessor(senders, categories, mockRepository)
        val payment = processor.processMessage(testMessage, testAddress)

        assertEquals("Shopping", payment.categoryId)
    }

    @Test
    fun `multiple rules with only one enabled should match correct one`() {
        val nonMatchingRegex = """NOMATCH (\d+)"""
        val senders = listOf(
            Sender(
                id = 1,
                name = "Test Bank",
                addresses = mutableListOf(testAddress),
                rules = mutableListOf(
                    PaymentRegexRule(regex = nonMatchingRegex, enabled = true),
                    PaymentRegexRule(regex = testRegex, enabled = true)
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
    }
}
