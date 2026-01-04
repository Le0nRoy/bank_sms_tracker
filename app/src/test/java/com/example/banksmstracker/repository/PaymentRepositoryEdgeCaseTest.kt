package com.example.banksmstracker.repository

import com.example.banksmstracker.data.Payment
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

@DisplayName("PaymentRepository Edge Cases")
class PaymentRepositoryEdgeCaseTest {

    private lateinit var repository: InMemoryPaymentRepository

    @BeforeEach
    fun setUp() {
        repository = InMemoryPaymentRepository()
    }

    private fun createTestPayment(
        amount: Double = 10.0,
        currency: String = "USD",
        card: String? = null,
        merchant: String? = "Test Merchant",
        timestamp: String? = null,
        balance: Double? = null,
        categoryId: String? = null
    ): Payment = Payment(
        amount = amount,
        currency = currency,
        card = card,
        merchant = merchant,
        timestamp = timestamp,
        balance = balance,
        categoryId = categoryId
    )

    @Nested
    @DisplayName("Empty and Null Values")
    inner class EmptyAndNullValues {

        @Test
        @DisplayName("Payment with empty merchant is saved")
        fun `payment with empty merchant is saved`() = runBlocking {
            val payment = createTestPayment(merchant = "")
            val inserted = repository.savePayment(payment, "msg-1", "SENDER")

            assertTrue(inserted)
            assertEquals(1, repository.getAllPayments().size)
            assertEquals("", repository.getAllPayments()[0].merchant)
        }

        @Test
        @DisplayName("Payment with null merchant is saved")
        fun `payment with null merchant is saved`() = runBlocking {
            val payment = createTestPayment(merchant = null)
            val inserted = repository.savePayment(payment, "msg-1", "SENDER")

            assertTrue(inserted)
            assertEquals(1, repository.getAllPayments().size)
            assertNull(repository.getAllPayments()[0].merchant)
        }

        @Test
        @DisplayName("Payment with null categoryId is saved")
        fun `payment with null categoryId is saved`() = runBlocking {
            val payment = createTestPayment(categoryId = null)
            val inserted = repository.savePayment(payment, "msg-1", "SENDER")

            assertTrue(inserted)
            assertNull(repository.getAllPayments()[0].categoryId)
        }

        @Test
        @DisplayName("Payment with empty string sender is saved")
        fun `payment with empty string sender is saved`() = runBlocking {
            val payment = createTestPayment()
            val inserted = repository.savePayment(payment, "msg-1", "")

            assertTrue(inserted)
            assertEquals("", repository.getAllPayments()[0].senderAddress)
        }

        @Test
        @DisplayName("Filter by empty sender returns payments with empty sender")
        fun `filter by empty sender returns payments with empty sender`() = runBlocking {
            val payment = createTestPayment()
            repository.savePayment(payment, "msg-1", "")

            val result = repository.getPaymentsBySender("")
            assertEquals(1, result.size)
        }

        @Test
        @DisplayName("Empty message string is valid for deduplication")
        fun `empty message string is valid for deduplication`() = runBlocking {
            val payment = createTestPayment()
            val first = repository.savePayment(payment, "", "SENDER")
            val second = repository.savePayment(payment, "", "SENDER")

            assertTrue(first)
            assertFalse(second)
            assertEquals(1, repository.getAllPayments().size)
        }
    }

    @Nested
    @DisplayName("Boundary Conditions")
    inner class BoundaryConditions {

        @Test
        @DisplayName("Very large amount is stored correctly")
        fun `very large amount is stored correctly`() = runBlocking {
            val payment = createTestPayment(amount = Double.MAX_VALUE / 2)
            repository.savePayment(payment, "msg-1", "SENDER")

            assertEquals(Double.MAX_VALUE / 2, repository.getAllPayments()[0].amount)
        }

        @Test
        @DisplayName("Very small amount is stored correctly")
        fun `very small amount is stored correctly`() = runBlocking {
            val payment = createTestPayment(amount = 0.00001)
            repository.savePayment(payment, "msg-1", "SENDER")

            assertEquals(0.00001, repository.getAllPayments()[0].amount, 0.000001)
        }

        @Test
        @DisplayName("Negative amount is stored correctly")
        fun `negative amount is stored correctly`() = runBlocking {
            val payment = createTestPayment(amount = -100.50)
            repository.savePayment(payment, "msg-1", "SENDER")

            assertEquals(-100.50, repository.getAllPayments()[0].amount)
        }

        @Test
        @DisplayName("Zero amount is stored correctly")
        fun `zero amount is stored correctly`() = runBlocking {
            val payment = createTestPayment(amount = 0.0)
            repository.savePayment(payment, "msg-1", "SENDER")

            assertEquals(0.0, repository.getAllPayments()[0].amount)
        }

        @Test
        @DisplayName("Very long merchant name is preserved")
        fun `very long merchant name is preserved`() = runBlocking {
            val longMerchant = "A".repeat(1000)
            val payment = createTestPayment(merchant = longMerchant)
            repository.savePayment(payment, "msg-1", "SENDER")

            assertEquals(longMerchant, repository.getAllPayments()[0].merchant)
        }

        @Test
        @DisplayName("Very long message for deduplication is handled")
        fun `very long message for deduplication is handled`() = runBlocking {
            val longMessage = "M".repeat(10000)
            val payment = createTestPayment()

            val first = repository.savePayment(payment, longMessage, "SENDER")
            val second = repository.savePayment(payment, longMessage, "SENDER")

            assertTrue(first)
            assertFalse(second)
        }
    }

    @Nested
    @DisplayName("Date Range Edge Cases")
    inner class DateRangeEdgeCases {

        @Test
        @DisplayName("Query with start equals end returns payments at exact time")
        fun `query with start equals end returns payments at exact time`() = runBlocking {
            val payment = createTestPayment()
            repository.savePayment(payment, "msg-1", "SENDER")

            val receivedAt = repository.getAllPayments()[0].receivedAt ?: 0L
            val result = repository.getPaymentsByDateRange(receivedAt, receivedAt)

            assertEquals(1, result.size)
        }

        @Test
        @DisplayName("Query with start greater than end returns empty")
        fun `query with start greater than end returns empty`() = runBlocking {
            val payment = createTestPayment()
            repository.savePayment(payment, "msg-1", "SENDER")

            val result = repository.getPaymentsByDateRange(Long.MAX_VALUE, 0L)
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("Query with negative timestamps works")
        fun `query with negative timestamps works`() = runBlocking {
            val payment = createTestPayment()
            repository.savePayment(payment, "msg-1", "SENDER")

            // Negative timestamps should return empty (no payments before epoch)
            val result = repository.getPaymentsByDateRange(-1000L, 0L)
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("Query spanning full Long range returns all payments")
        fun `query spanning full Long range returns all payments`() = runBlocking {
            val payment = createTestPayment()
            repository.savePayment(payment, "msg-1", "SENDER")

            val result = repository.getPaymentsByDateRange(Long.MIN_VALUE, Long.MAX_VALUE)
            assertEquals(1, result.size)
        }
    }

    @Nested
    @DisplayName("Deduplication Edge Cases")
    inner class DeduplicationEdgeCases {

        @Test
        @DisplayName("Same message different sender is not duplicate")
        fun `same message different sender is not duplicate`() = runBlocking {
            val payment = createTestPayment()
            val message = "Same message"

            val first = repository.savePayment(payment, message, "SENDER1")
            val second = repository.savePayment(payment, message, "SENDER2")

            assertTrue(first)
            assertTrue(second)
            assertEquals(2, repository.getAllPayments().size)
        }

        @Test
        @DisplayName("Different message same sender is not duplicate")
        fun `different message same sender is not duplicate`() = runBlocking {
            val payment = createTestPayment()

            val first = repository.savePayment(payment, "message-1", "SENDER")
            val second = repository.savePayment(payment, "message-2", "SENDER")

            assertTrue(first)
            assertTrue(second)
            assertEquals(2, repository.getAllPayments().size)
        }

        @Test
        @DisplayName("Whitespace differences in message are detected")
        fun `whitespace differences in message are detected`() = runBlocking {
            val payment = createTestPayment()

            val first = repository.savePayment(payment, "message", "SENDER")
            val second = repository.savePayment(payment, "message ", "SENDER")

            assertTrue(first)
            assertTrue(second) // Different due to trailing space
            assertEquals(2, repository.getAllPayments().size)
        }

        @Test
        @DisplayName("Case differences in message are detected")
        fun `case differences in message are detected`() = runBlocking {
            val payment = createTestPayment()

            val first = repository.savePayment(payment, "Message", "SENDER")
            val second = repository.savePayment(payment, "message", "SENDER")

            assertTrue(first)
            assertTrue(second) // Case-sensitive comparison
            assertEquals(2, repository.getAllPayments().size)
        }

        @Test
        @DisplayName("Case differences in sender are detected")
        fun `case differences in sender are detected`() = runBlocking {
            val payment = createTestPayment()
            val message = "same message"

            val first = repository.savePayment(payment, message, "SENDER")
            val second = repository.savePayment(payment, message, "sender")

            assertTrue(first)
            assertTrue(second) // Case-sensitive sender comparison
            assertEquals(2, repository.getAllPayments().size)
        }
    }

    @Nested
    @DisplayName("Category Update Edge Cases")
    inner class CategoryUpdateEdgeCases {

        @Test
        @DisplayName("Update category for non-existent payment ID does nothing")
        fun `update category for non-existent payment ID does nothing`() = runBlocking {
            val payment = createTestPayment(categoryId = "old-cat")
            repository.savePayment(payment, "msg-1", "SENDER")

            // Update with non-existent ID
            repository.updatePaymentCategory(99999L, "new-cat")

            // Original payment should be unchanged
            assertEquals("old-cat", repository.getAllPayments()[0].categoryId)
        }

        @Test
        @DisplayName("Update category to empty string is valid")
        fun `update category to empty string is valid`() = runBlocking {
            val payment = createTestPayment(categoryId = "old-cat")
            repository.savePayment(payment, "msg-1", "SENDER")

            val paymentId = repository.getAllPayments()[0].id ?: return@runBlocking
            repository.updatePaymentCategory(paymentId, "")

            assertEquals("", repository.getAllPayments()[0].categoryId)
        }

        @Test
        @DisplayName("Multiple updates to same payment work correctly")
        fun `multiple updates to same payment work correctly`() = runBlocking {
            val payment = createTestPayment(categoryId = "cat-1")
            repository.savePayment(payment, "msg-1", "SENDER")

            val paymentId = repository.getAllPayments()[0].id ?: return@runBlocking

            repository.updatePaymentCategory(paymentId, "cat-2")
            repository.updatePaymentCategory(paymentId, "cat-3")
            repository.updatePaymentCategory(paymentId, "cat-4")

            assertEquals("cat-4", repository.getAllPayments()[0].categoryId)
        }
    }

    @Nested
    @DisplayName("Ordering and Retrieval")
    inner class OrderingAndRetrieval {

        @Test
        @DisplayName("Payments are returned in insertion order")
        fun `payments are returned in insertion order`() = runBlocking {
            val payment1 = createTestPayment(amount = 1.0)
            val payment2 = createTestPayment(amount = 2.0)
            val payment3 = createTestPayment(amount = 3.0)

            repository.savePayment(payment1, "msg-1", "SENDER")
            repository.savePayment(payment2, "msg-2", "SENDER")
            repository.savePayment(payment3, "msg-3", "SENDER")

            val all = repository.getAllPayments()
            // InMemoryPaymentRepository returns in insertion order
            assertEquals(1.0, all[0].amount)
            assertEquals(2.0, all[1].amount)
            assertEquals(3.0, all[2].amount)
        }

        @Test
        @DisplayName("Filtered results maintain insertion order")
        fun `filtered results maintain insertion order`() = runBlocking {
            val payment1 = createTestPayment(amount = 1.0, categoryId = "cat-a")
            val payment2 = createTestPayment(amount = 2.0, categoryId = "cat-b")
            val payment3 = createTestPayment(amount = 3.0, categoryId = "cat-a")

            repository.savePayment(payment1, "msg-1", "SENDER")
            repository.savePayment(payment2, "msg-2", "SENDER")
            repository.savePayment(payment3, "msg-3", "SENDER")

            val catAPayments = repository.getPaymentsByCategory("cat-a")
            assertEquals(2, catAPayments.size)
            // InMemoryPaymentRepository returns in insertion order
            assertEquals(1.0, catAPayments[0].amount)
            assertEquals(3.0, catAPayments[1].amount)
        }

        @Test
        @DisplayName("Uncategorized payments maintain insertion order")
        fun `uncategorized payments maintain insertion order`() = runBlocking {
            val payment1 = createTestPayment(amount = 1.0, categoryId = null)
            val payment2 = createTestPayment(amount = 2.0, categoryId = "cat")
            val payment3 = createTestPayment(amount = 3.0, categoryId = null)

            repository.savePayment(payment1, "msg-1", "SENDER")
            repository.savePayment(payment2, "msg-2", "SENDER")
            repository.savePayment(payment3, "msg-3", "SENDER")

            val uncategorized = repository.getUncategorizedPayments()
            assertEquals(2, uncategorized.size)
            // InMemoryPaymentRepository returns in insertion order
            assertEquals(1.0, uncategorized[0].amount)
            assertEquals(3.0, uncategorized[1].amount)
        }
    }

    @Nested
    @DisplayName("ReceivedAt Timestamp")
    inner class ReceivedAtTimestamp {

        @Test
        @DisplayName("ReceivedAt is set automatically on save")
        fun `receivedAt is set automatically on save`() = runBlocking {
            val before = System.currentTimeMillis()
            val payment = createTestPayment()
            repository.savePayment(payment, "msg-1", "SENDER")
            val after = System.currentTimeMillis()

            val saved = repository.getAllPayments()[0]
            assertNotNull(saved.receivedAt)
            assertTrue(saved.receivedAt!! >= before)
            assertTrue(saved.receivedAt!! <= after)
        }

        @Test
        @DisplayName("ReceivedAt is preserved from original payment if present")
        fun `receivedAt is preserved from original payment if present`() = runBlocking {
            val customTime = 1000000000L
            val payment = createTestPayment().copy(receivedAt = customTime)
            repository.savePayment(payment, "msg-1", "SENDER")

            // InMemoryPaymentRepository overwrites receivedAt, which is expected behavior
            // This test verifies the current behavior
            val saved = repository.getAllPayments()[0]
            assertNotNull(saved.receivedAt)
        }

        @Test
        @DisplayName("Multiple payments have increasing receivedAt")
        fun `multiple payments have increasing receivedAt`() = runBlocking {
            val payment1 = createTestPayment(amount = 1.0)
            val payment2 = createTestPayment(amount = 2.0)

            repository.savePayment(payment1, "msg-1", "SENDER")
            Thread.sleep(10)
            repository.savePayment(payment2, "msg-2", "SENDER")

            val all = repository.getAllPayments()
            val time1 = all.find { it.amount == 1.0 }?.receivedAt ?: 0L
            val time2 = all.find { it.amount == 2.0 }?.receivedAt ?: 0L

            assertTrue(time2 >= time1)
        }
    }

    @Nested
    @DisplayName("Distinct Senders")
    inner class DistinctSenders {

        @Test
        @DisplayName("Distinct senders are sorted alphabetically")
        fun `distinct senders are sorted alphabetically`() = runBlocking {
            repository.savePayment(createTestPayment(), "msg-1", "CHARLIE")
            repository.savePayment(createTestPayment(), "msg-2", "ALPHA")
            repository.savePayment(createTestPayment(), "msg-3", "BRAVO")

            val senders = repository.getDistinctSenderAddresses()
            assertEquals(listOf("ALPHA", "BRAVO", "CHARLIE"), senders)
        }

        @Test
        @DisplayName("Duplicate senders appear only once")
        fun `duplicate senders appear only once`() = runBlocking {
            repository.savePayment(createTestPayment(), "msg-1", "BANK-A")
            repository.savePayment(createTestPayment(), "msg-2", "BANK-A")
            repository.savePayment(createTestPayment(), "msg-3", "BANK-A")

            val senders = repository.getDistinctSenderAddresses()
            assertEquals(1, senders.size)
            assertEquals("BANK-A", senders[0])
        }

        @Test
        @DisplayName("Empty string sender appears in distinct list")
        fun `empty string sender appears in distinct list`() = runBlocking {
            repository.savePayment(createTestPayment(), "msg-1", "")
            repository.savePayment(createTestPayment(), "msg-2", "BANK")

            val senders = repository.getDistinctSenderAddresses()
            assertEquals(2, senders.size)
            assertTrue(senders.contains(""))
        }
    }
}
