package com.example.banksmstracker.repository

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.database.BankSmsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Instrumented mirror of CategoryConcurrencyTest — runs on real Room/SQLite storage.
 * The performance test verifies that 1000 payments can be recategorized in < 10 s.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryConcurrencyInstrumentedTest {

    private lateinit var database: BankSmsDatabase
    private lateinit var repository: RoomPaymentRepository

    @BeforeEach
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BankSmsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomPaymentRepository(database.paymentDao())
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    /** Same recategorize logic as the unit test, standalone — no ConfigRepository singleton. */
    private suspend fun recategorizeAll(repo: PaymentRepository, categories: List<Category>): Int {
        var count = 0
        val allPayments = repo.getAllPayments()
        for (payment in allPayments) {
            val merchant = payment.merchant ?: continue
            val newCategory = categories
                .filter { it.enabled }
                .firstOrNull { cat ->
                    cat.merchants.any { it.equals(merchant, ignoreCase = true) }
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
    fun rapidRecategorizeAssignsAllPaymentsOnRealStorage() = runBlocking {
        repeat(50) { i ->
            repository.savePayment(
                Payment(amount = 1.0, currency = "USD", card = null, merchant = "Amazon",
                    timestamp = null, balance = null),
                rawMessage = "msg-$i",
                senderAddress = "BANK"
            )
        }

        val categories = listOf(Category(id = 1L, name = "Shopping", merchants = mutableListOf("Amazon")))

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
    fun recategorizeIsIdempotentOnRealStorage() = runBlocking {
        repeat(20) { i ->
            repository.savePayment(
                Payment(amount = 2.0, currency = "USD", card = null, merchant = "Supermarket",
                    timestamp = null, balance = null),
                rawMessage = "msg-$i",
                senderAddress = "BANK"
            )
        }

        val categories = listOf(Category(id = 1L, name = "Groceries", merchants = mutableListOf("Supermarket")))

        recategorizeAll(repository, categories)
        val afterFirst = repository.getAllPayments().map { it.categoryId }

        recategorizeAll(repository, categories)
        val afterSecond = repository.getAllPayments().map { it.categoryId }

        assertEquals(afterFirst, afterSecond)
        afterSecond.forEach { categoryId ->
            assertEquals("Groceries", categoryId)
        }
    }

    @Test
    fun unknownMerchantStaysUncategorizedOnRealStorage() = runBlocking {
        repeat(10) { i ->
            repository.savePayment(
                Payment(amount = 5.0, currency = "USD", card = null, merchant = "UnknownShop",
                    timestamp = null, balance = null),
                rawMessage = "msg-$i",
                senderAddress = "BANK"
            )
        }

        val categories = listOf(Category(id = 1L, name = "Shopping", merchants = mutableListOf("Amazon")))

        recategorizeAll(repository, categories)

        repository.getAllPayments().forEach { payment ->
            assertNull(payment.categoryId, "Payment with unmatched merchant should stay uncategorized")
        }
    }

    @Test
    @Tag("performance")
    fun recategorizePerformanceWith1000Payments() = runBlocking {
        // Insert on IO dispatcher inside a single transaction to avoid blocking the main thread
        // and to keep insert time reasonable (1000 individual commits is ~10x slower than batched).
        withContext(Dispatchers.IO) {
            database.withTransaction {
                repeat(1000) { i ->
                    repository.savePayment(
                        Payment(amount = 1.0, currency = "USD", card = null, merchant = "Amazon",
                            timestamp = null, balance = null),
                        rawMessage = "msg-$i",
                        senderAddress = "BANK"
                    )
                }
            }
        }

        val categories = listOf(Category(id = 1L, name = "Shopping", merchants = mutableListOf("Amazon")))

        val start = System.currentTimeMillis()
        withContext(Dispatchers.IO) { recategorizeAll(repository, categories) }
        val elapsed = System.currentTimeMillis() - start

        assertTrue(elapsed < 10_000L, "Expected < 10 s, got ${elapsed} ms for 1000 payments")
        assertEquals(1000, repository.getAllPayments().count { it.categoryId == "Shopping" })
    }
}
