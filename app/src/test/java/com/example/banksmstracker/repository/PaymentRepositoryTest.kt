package com.example.banksmstracker.repository

import com.example.banksmstracker.data.Payment
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PaymentRepositoryTest {

    private lateinit var repository: InMemoryPaymentRepository

    @BeforeEach
    fun setUp() {
        repository = InMemoryPaymentRepository()
    }

    private fun createTestPayment(
        amount: Double,
        currency: String = "USD",
        card: String? = null,
        merchant: String?,
        timestamp: String? = "2023-01-01T12:00:00Z",
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

    @Test
    fun `savePayment_addsPaymentToRepository`() = runBlocking {
        val payment = createTestPayment(amount = 10.0, merchant = "Test Merchant", categoryId = "cat1")
        repository.savePayment(payment, "message-1", "sender-1")
        assertEquals(1, repository.getAllPayments().size)
        assertEquals(payment, repository.getAllPayments()[0])
    }

    @Test
    fun `getAllPayments_returnsAllSavedPayments`() = runBlocking {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Test 1", categoryId = "cat1")
        val payment2 = createTestPayment(amount = 20.0, merchant = "Test 2", categoryId = "cat2")
        repository.savePayment(payment1, "message-1", "sender-1")
        repository.savePayment(payment2, "message-2", "sender-2")
        val allPayments = repository.getAllPayments()
        assertEquals(2, allPayments.size)
        assertTrue(allPayments.contains(payment1))
        assertTrue(allPayments.contains(payment2))
    }

    @Test
    fun `getAllPayments_returnsEmptyList_whenNoPaymentsSaved`() = runBlocking {
        assertTrue(repository.getAllPayments().isEmpty())
    }

    @Test
    fun `getPaymentsByCategory_returnsCorrectPayments`() = runBlocking {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Groceries", categoryId = "cat_grocery")
        val payment2 = createTestPayment(amount = 20.0, merchant = "Transport", categoryId = "cat_transport")
        val payment3 = createTestPayment(amount = 30.0, merchant = "More Groceries", categoryId = "cat_grocery")
        repository.savePayment(payment1, "message-1", "sender-1")
        repository.savePayment(payment2, "message-2", "sender-2")
        repository.savePayment(payment3, "message-3", "sender-3")

        val groceryPayments = repository.getPaymentsByCategory("cat_grocery")
        assertEquals(2, groceryPayments.size)
        assertTrue(groceryPayments.contains(payment1))
        assertTrue(groceryPayments.contains(payment3))
    }

    @Test
    fun `getPaymentsByCategory_returnsEmptyList_forUnknownCategory`() = runBlocking {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Groceries", categoryId = "cat_grocery")
        repository.savePayment(payment1, "message-1", "sender-1")
        val utilityPayments = repository.getPaymentsByCategory("cat_utility")
        assertTrue(utilityPayments.isEmpty())
    }

    @Test
    fun `getUncategorizedPayments_returnsOnlyPaymentsWithNullCategoryId`() = runBlocking {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Categorized", categoryId = "cat_misc")
        val payment2 = createTestPayment(amount = 20.0, merchant = "Uncategorized 1", categoryId = null)
        val payment3 = createTestPayment(amount = 30.0, merchant = "Uncategorized 2", categoryId = null)
        repository.savePayment(payment1, "message-1", "sender-1")
        repository.savePayment(payment2, "message-2", "sender-2")
        repository.savePayment(payment3, "message-3", "sender-3")

        val uncategorized = repository.getUncategorizedPayments()
        assertEquals(2, uncategorized.size)
        assertTrue(uncategorized.contains(payment2))
        assertTrue(uncategorized.contains(payment3))
    }

    @Test
    fun `getUncategorizedPayments_returnsEmptyList_whenAllAreCategorized`() = runBlocking {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Categorized", categoryId = "cat_misc")
        repository.savePayment(payment1, "message-1", "sender-1")
        assertTrue(repository.getUncategorizedPayments().isEmpty())
    }

    @Test
    fun `savePayment_returnsFalseWhenDuplicate`() = runBlocking {
        val payment = createTestPayment(amount = 10.0, merchant = "Test Merchant", categoryId = "cat1")
        val inserted = repository.savePayment(payment, "duplicate-message", "sender-1")
        val duplicateInsert = repository.savePayment(payment, "duplicate-message", "sender-1")
        assertTrue(inserted)
        assertTrue(repository.getAllPayments().size == 1)
        assertFalse(duplicateInsert)
    }
}
