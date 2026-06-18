package com.example.banksmstracker.repository

import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Merchant
import com.example.banksmstracker.data.Payment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CategoryConcurrencyTest {

    private lateinit var repository: InMemoryPaymentRepository

    @BeforeEach
    fun setUp() {
        repository = InMemoryPaymentRepository()
    }

    /** Core recategorize logic extracted for unit-testing without ConfigRepository singleton. */
    private suspend fun recategorizeAll(repo: PaymentRepository, categories: List<Category>): Int {
        var count = 0
        val allPayments = repo.getAllPayments()
        for (payment in allPayments) {
            val merchant = payment.merchant ?: continue
            val newCategory = categories
                .filter { it.enabled }
                .firstOrNull { cat ->
                    cat.merchants.any { m ->
                        if (m.isRegex) {
                            Regex(m.pattern, setOf(RegexOption.IGNORE_CASE)).containsMatchIn(merchant)
                        } else {
                            m.pattern.equals(merchant, ignoreCase = true)
                        }
                    }
                }?.name
            if (newCategory != payment.categoryId) {
                val paymentId = payment.id ?: continue
                repo.updatePaymentCategory(paymentId, newCategory)
                count++
            }
        }
        return count
    }

    @Test
    fun `rapidRecategorizeAssignsAllPayments`() = runBlocking {
        // Insert 50 payments with merchant "Amazon"
        repeat(50) { i ->
            repository.savePayment(
                Payment(
                    amount = 1.0,
                    currency = "USD",
                    card = null,
                    merchant = "Amazon",
                    timestamp = "",
                    balance = null
                ),
                rawMessage = "msg-$i",
                senderAddress = "BANK"
            )
        }

        val categories = listOf(
            Category(id = 1L, name = "Shopping", merchants = mutableListOf(Merchant("Amazon")))
        )

        // Run recategorize in 10 concurrent coroutines
        (1..10).map {
            async(Dispatchers.Default) { recategorizeAll(repository, categories) }
        }.awaitAll()

        val allPayments = repository.getAllPayments()
        assertEquals(50, allPayments.size)
        allPayments.forEach { payment ->
            assertEquals("Shopping", payment.categoryId, "Payment ${payment.id} should be categorized as Shopping")
        }
    }

    @Test
    fun `recategorizeIsIdempotent`() = runBlocking {
        repeat(20) { i ->
            repository.savePayment(
                Payment(
                    amount = 2.0,
                    currency = "USD",
                    card = null,
                    merchant = "Supermarket",
                    timestamp = "",
                    balance = null
                ),
                rawMessage = "msg-$i",
                senderAddress = "BANK"
            )
        }

        val categories = listOf(
            Category(id = 1L, name = "Groceries", merchants = mutableListOf(Merchant("Supermarket")))
        )

        // First pass
        recategorizeAll(repository, categories)
        val afterFirst = repository.getAllPayments().map { it.categoryId }

        // Second pass
        recategorizeAll(repository, categories)
        val afterSecond = repository.getAllPayments().map { it.categoryId }

        assertEquals(afterFirst, afterSecond)
        afterSecond.forEach { categoryId ->
            assertEquals("Groceries", categoryId)
        }
    }

    @Test
    fun `unknownMerchantStaysUncategorized`() = runBlocking {
        repeat(10) { i ->
            repository.savePayment(
                Payment(
                    amount = 5.0,
                    currency = "USD",
                    card = null,
                    merchant = "UnknownShop",
                    timestamp = "",
                    balance = null
                ),
                rawMessage = "msg-$i",
                senderAddress = "BANK"
            )
        }

        val categories = listOf(
            Category(id = 1L, name = "Shopping", merchants = mutableListOf(Merchant("Amazon")))
        )

        recategorizeAll(repository, categories)

        repository.getAllPayments().forEach { payment ->
            assertNull(payment.categoryId, "Payment with unmatched merchant should stay uncategorized")
        }
    }
}
