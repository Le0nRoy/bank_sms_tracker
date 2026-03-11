package com.example.banksmstracker.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.database.PaymentDao
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Instrumented tests for RoomPaymentRepository.
 * Tests payment persistence and deduplication using Room database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoomPaymentRepositoryTest {

    private lateinit var database: BankSmsDatabase
    private lateinit var paymentDao: PaymentDao
    private lateinit var repository: RoomPaymentRepository

    @BeforeEach
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            BankSmsDatabase::class.java
        ).allowMainThreadQueries().build()
        paymentDao = database.paymentDao()
        repository = RoomPaymentRepository(paymentDao)
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    private fun createTestPayment(
        amount: Double,
        currency: String = "USD",
        card: String? = null,
        merchant: String? = null,
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
    @DisplayName("savePayment_addsPaymentToRepository")
    fun savePaymentAddsPaymentToRepository() = runBlocking {
        val payment = createTestPayment(amount = 10.0, merchant = "Test Merchant", categoryId = "cat1")
        val inserted = repository.savePayment(payment, "message-1", "sender-1")

        assertTrue(inserted)
        val allPayments = repository.getAllPayments()
        assertEquals(1, allPayments.size)
        assertEquals(payment.amount, allPayments[0].amount)
        assertEquals(payment.merchant, allPayments[0].merchant)
        assertEquals(payment.categoryId, allPayments[0].categoryId)
    }

    @Test
    @DisplayName("getAllPayments_returnsAllSavedPayments")
    fun getAllPaymentsReturnsAllSavedPayments() = runBlocking {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Test 1", categoryId = "cat1")
        val payment2 = createTestPayment(amount = 20.0, merchant = "Test 2", categoryId = "cat2")
        repository.savePayment(payment1, "message-1", "sender-1")
        repository.savePayment(payment2, "message-2", "sender-2")

        val allPayments = repository.getAllPayments()
        assertEquals(2, allPayments.size)
    }

    @Test
    @DisplayName("getAllPayments_returnsEmptyList_whenNoPaymentsSaved")
    fun getAllPaymentsReturnsEmptyListWhenNoPaymentsSaved() = runBlocking {
        assertTrue(repository.getAllPayments().isEmpty())
    }

    @Test
    @DisplayName("getPaymentsByCategory_returnsCorrectPayments")
    fun getPaymentsByCategoryReturnsCorrectPayments() = runBlocking {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Groceries", categoryId = "cat_grocery")
        val payment2 = createTestPayment(amount = 20.0, merchant = "Transport", categoryId = "cat_transport")
        val payment3 = createTestPayment(amount = 30.0, merchant = "More Groceries", categoryId = "cat_grocery")
        repository.savePayment(payment1, "message-1", "sender-1")
        repository.savePayment(payment2, "message-2", "sender-2")
        repository.savePayment(payment3, "message-3", "sender-3")

        val groceryPayments = repository.getPaymentsByCategory("cat_grocery")
        assertEquals(2, groceryPayments.size)
        assertTrue(groceryPayments.all { it.categoryId == "cat_grocery" })
    }

    @Test
    @DisplayName("getPaymentsByCategory_returnsEmptyList_forUnknownCategory")
    fun getPaymentsByCategoryReturnsEmptyListForUnknownCategory() = runBlocking {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Groceries", categoryId = "cat_grocery")
        repository.savePayment(payment1, "message-1", "sender-1")
        val utilityPayments = repository.getPaymentsByCategory("cat_utility")
        assertTrue(utilityPayments.isEmpty())
    }

    @Test
    @DisplayName("getUncategorizedPayments_returnsOnlyPaymentsWithNullCategoryId")
    fun getUncategorizedPaymentsReturnsOnlyPaymentsWithNullCategoryId() = runBlocking {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Categorized", categoryId = "cat_misc")
        val payment2 = createTestPayment(amount = 20.0, merchant = "Uncategorized 1", categoryId = null)
        val payment3 = createTestPayment(amount = 30.0, merchant = "Uncategorized 2", categoryId = null)
        repository.savePayment(payment1, "message-1", "sender-1")
        repository.savePayment(payment2, "message-2", "sender-2")
        repository.savePayment(payment3, "message-3", "sender-3")

        val uncategorized = repository.getUncategorizedPayments()
        assertEquals(2, uncategorized.size)
        assertTrue(uncategorized.all { it.categoryId == null })
    }

    @Test
    @DisplayName("getUncategorizedPayments_returnsEmptyList_whenAllAreCategorized")
    fun getUncategorizedPaymentsReturnsEmptyListWhenAllAreCategorized() = runBlocking {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Categorized", categoryId = "cat_misc")
        repository.savePayment(payment1, "message-1", "sender-1")
        assertTrue(repository.getUncategorizedPayments().isEmpty())
    }

    @Test
    @DisplayName("savePayment_returnsFalseWhenDuplicate")
    fun savePaymentReturnsFalseWhenDuplicate() = runBlocking {
        val payment = createTestPayment(amount = 10.0, merchant = "Test Merchant", categoryId = "cat1")
        val inserted = repository.savePayment(payment, "duplicate-message", "sender-1")
        val duplicateInsert = repository.savePayment(payment, "duplicate-message", "sender-1")

        assertTrue(inserted)
        assertTrue(repository.getAllPayments().size == 1)
        assertFalse(duplicateInsert)
    }

    @Test
    @DisplayName("savePayment_allowsDifferentPaymentsWithSameContentButDifferentMessage")
    fun savePaymentAllowsDifferentPaymentsWithSameContentButDifferentMessage() = runBlocking {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Test Merchant", categoryId = "cat1")
        val payment2 = createTestPayment(amount = 10.0, merchant = "Test Merchant", categoryId = "cat1")

        val inserted1 = repository.savePayment(payment1, "message-1", "sender-1")
        val inserted2 = repository.savePayment(payment2, "message-2", "sender-1")

        assertTrue(inserted1)
        assertTrue(inserted2)
        assertEquals(2, repository.getAllPayments().size)
    }

    @Test
    @DisplayName("savePayment_allowsSameMessageFromDifferentSenders")
    fun savePaymentAllowsSameMessageFromDifferentSenders() = runBlocking {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Test Merchant", categoryId = "cat1")
        val payment2 = createTestPayment(amount = 20.0, merchant = "Test Merchant 2", categoryId = "cat2")

        val inserted1 = repository.savePayment(payment1, "same-message", "sender-1")
        val inserted2 = repository.savePayment(payment2, "same-message", "sender-2")

        assertTrue(inserted1)
        assertTrue(inserted2)
        assertEquals(2, repository.getAllPayments().size)
    }

    @Test
    @DisplayName("getAllPayments_returnsPaymentsInDescendingOrder")
    fun getAllPaymentsReturnsPaymentsInDescendingOrder() = runBlocking {
        val payment1 = createTestPayment(amount = 10.0, merchant = "First")
        val payment2 = createTestPayment(amount = 20.0, merchant = "Second")
        val payment3 = createTestPayment(amount = 30.0, merchant = "Third")

        repository.savePayment(payment1, "msg-1", "sender")
        repository.savePayment(payment2, "msg-2", "sender")
        repository.savePayment(payment3, "msg-3", "sender")

        val allPayments = repository.getAllPayments()
        assertEquals(3, allPayments.size)
        // Payments should be in descending order by ID (most recent first)
        assertTrue(allPayments[0].id!! >= allPayments[1].id!!)
        assertTrue(allPayments[1].id!! >= allPayments[2].id!!)
    }

    // ==================== Sender Filtering Tests ====================

    @Test
    @DisplayName("getPaymentsBySender_returnsCorrectPayments")
    fun getPaymentsBySenderReturnsCorrectPayments() = runBlocking {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Store 1")
        val payment2 = createTestPayment(amount = 20.0, merchant = "Store 2")
        val payment3 = createTestPayment(amount = 30.0, merchant = "Store 3")
        repository.savePayment(payment1, "msg-1", "BANK-A")
        repository.savePayment(payment2, "msg-2", "BANK-B")
        repository.savePayment(payment3, "msg-3", "BANK-A")

        val bankAPayments = repository.getPaymentsBySender("BANK-A")
        assertEquals(2, bankAPayments.size)
        assertTrue(bankAPayments.all { it.senderAddress == "BANK-A" })
    }

    @Test
    @DisplayName("getPaymentsBySender_returnsEmptyListForUnknownSender")
    fun getPaymentsBySenderReturnsEmptyListForUnknownSender() = runBlocking {
        val payment = createTestPayment(amount = 10.0, merchant = "Store")
        repository.savePayment(payment, "msg-1", "BANK-A")

        val unknownPayments = repository.getPaymentsBySender("UNKNOWN")
        assertTrue(unknownPayments.isEmpty())
    }

    @Test
    @DisplayName("getDistinctSenderAddresses_returnsUniqueSortedList")
    fun getDistinctSenderAddressesReturnsUniqueSortedList() = runBlocking {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Store 1")
        val payment2 = createTestPayment(amount = 20.0, merchant = "Store 2")
        val payment3 = createTestPayment(amount = 30.0, merchant = "Store 3")
        val payment4 = createTestPayment(amount = 40.0, merchant = "Store 4")
        repository.savePayment(payment1, "msg-1", "BANK-B")
        repository.savePayment(payment2, "msg-2", "BANK-A")
        repository.savePayment(payment3, "msg-3", "BANK-B")
        repository.savePayment(payment4, "msg-4", "BANK-C")

        val senders = repository.getDistinctSenderAddresses()
        assertEquals(listOf("BANK-A", "BANK-B", "BANK-C"), senders)
    }

    @Test
    @DisplayName("getDistinctSenderAddresses_returnsEmptyListWhenNoPayments")
    fun getDistinctSenderAddressesReturnsEmptyListWhenNoPayments() = runBlocking {
        val senders = repository.getDistinctSenderAddresses()
        assertTrue(senders.isEmpty())
    }

    // ==================== Date Range Filtering Tests ====================

    @Test
    @DisplayName("getPaymentsByDateRange_returnsPaymentsInRange")
    fun getPaymentsByDateRangeReturnsPaymentsInRange() = runBlocking {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Store 1")
        val payment2 = createTestPayment(amount = 20.0, merchant = "Store 2")
        repository.savePayment(payment1, "msg-1", "BANK")
        Thread.sleep(50)
        repository.savePayment(payment2, "msg-2", "BANK")

        val allPayments = repository.getAllPayments()
        assertEquals(2, allPayments.size)

        val startTime = allPayments.minOf { it.receivedAt ?: 0 } - 1000
        val endTime = allPayments.maxOf { it.receivedAt ?: 0 } + 1000
        val rangePayments = repository.getPaymentsByDateRange(startTime, endTime)
        assertEquals(2, rangePayments.size)
    }

    @Test
    @DisplayName("getPaymentsByDateRange_excludesPaymentsOutsideRange")
    fun getPaymentsByDateRangeExcludesPaymentsOutsideRange() = runBlocking {
        val payment = createTestPayment(amount = 10.0, merchant = "Store")
        repository.savePayment(payment, "msg-1", "BANK")

        val futureStart = System.currentTimeMillis() + 100000
        val futureEnd = futureStart + 100000
        val rangePayments = repository.getPaymentsByDateRange(futureStart, futureEnd)
        assertTrue(rangePayments.isEmpty())
    }

    // ==================== Category Update Tests ====================

    @Test
    @DisplayName("updatePaymentCategory_updatesExistingPayment")
    fun updatePaymentCategoryUpdatesExistingPayment() = runBlocking {
        val payment = createTestPayment(amount = 10.0, merchant = "Store", categoryId = "old-cat")
        repository.savePayment(payment, "msg-1", "BANK")

        val saved = repository.getAllPayments().first()
        val paymentId = saved.id!!

        repository.updatePaymentCategory(paymentId, "new-cat")

        val updated = repository.getAllPayments().first()
        assertEquals("new-cat", updated.categoryId)
    }

    @Test
    @DisplayName("updatePaymentCategory_canSetCategoryToNull")
    fun updatePaymentCategoryCanSetCategoryToNull() = runBlocking {
        val payment = createTestPayment(amount = 10.0, merchant = "Store", categoryId = "cat")
        repository.savePayment(payment, "msg-1", "BANK")

        val saved = repository.getAllPayments().first()
        val paymentId = saved.id!!

        repository.updatePaymentCategory(paymentId, null)

        val updated = repository.getAllPayments().first()
        assertEquals(null, updated.categoryId)
    }

    @Test
    @DisplayName("updatePaymentCategory_doesNothingForUnknownId")
    fun updatePaymentCategoryDoesNothingForUnknownId() = runBlocking {
        val payment = createTestPayment(amount = 10.0, merchant = "Store", categoryId = "cat")
        repository.savePayment(payment, "msg-1", "BANK")

        repository.updatePaymentCategory(999999L, "new-cat")

        val unchanged = repository.getAllPayments().first()
        assertEquals("cat", unchanged.categoryId)
    }
}
