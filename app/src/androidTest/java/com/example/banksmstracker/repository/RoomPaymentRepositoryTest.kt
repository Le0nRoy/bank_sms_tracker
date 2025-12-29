package com.example.banksmstracker.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.database.PaymentDao
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit5.android.core.J5SuiteExtension
import java.io.File

/**
 * Instrumented tests for RoomPaymentRepository.
 * Tests payment persistence and deduplication using Room database.
 */
@ExtendWith(J5SuiteExtension::class)
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
    ): Payment {
        return Payment(
            amount = amount,
            currency = currency,
            card = card,
            merchant = merchant,
            timestamp = timestamp,
            balance = balance,
            categoryId = categoryId
        )
    }

    @Test
    fun `savePayment_addsPaymentToRepository`() {
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
    fun `getAllPayments_returnsAllSavedPayments`() {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Test 1", categoryId = "cat1")
        val payment2 = createTestPayment(amount = 20.0, merchant = "Test 2", categoryId = "cat2")
        repository.savePayment(payment1, "message-1", "sender-1")
        repository.savePayment(payment2, "message-2", "sender-2")
        
        val allPayments = repository.getAllPayments()
        assertEquals(2, allPayments.size)
    }

    @Test
    fun `getAllPayments_returnsEmptyList_whenNoPaymentsSaved`() {
        assertTrue(repository.getAllPayments().isEmpty())
    }

    @Test
    fun `getPaymentsByCategory_returnsCorrectPayments`() {
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
    fun `getPaymentsByCategory_returnsEmptyList_forUnknownCategory`() {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Groceries", categoryId = "cat_grocery")
        repository.savePayment(payment1, "message-1", "sender-1")
        val utilityPayments = repository.getPaymentsByCategory("cat_utility")
        assertTrue(utilityPayments.isEmpty())
    }

    @Test
    fun `getUncategorizedPayments_returnsOnlyPaymentsWithNullCategoryId`() {
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
    fun `getUncategorizedPayments_returnsEmptyList_whenAllAreCategorized`() {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Categorized", categoryId = "cat_misc")
        repository.savePayment(payment1, "message-1", "sender-1")
        assertTrue(repository.getUncategorizedPayments().isEmpty())
    }

    @Test
    fun `savePayment_returnsFalseWhenDuplicate`() {
        val payment = createTestPayment(amount = 10.0, merchant = "Test Merchant", categoryId = "cat1")
        val inserted = repository.savePayment(payment, "duplicate-message", "sender-1")
        val duplicateInsert = repository.savePayment(payment, "duplicate-message", "sender-1")
        
        assertTrue(inserted)
        assertTrue(repository.getAllPayments().size == 1)
        assertFalse(duplicateInsert)
    }

    @Test
    fun `savePayment_allowsDifferentPaymentsWithSameContentButDifferentMessage`() {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Test Merchant", categoryId = "cat1")
        val payment2 = createTestPayment(amount = 10.0, merchant = "Test Merchant", categoryId = "cat1")
        
        val inserted1 = repository.savePayment(payment1, "message-1", "sender-1")
        val inserted2 = repository.savePayment(payment2, "message-2", "sender-1")
        
        assertTrue(inserted1)
        assertTrue(inserted2)
        assertEquals(2, repository.getAllPayments().size)
    }

    @Test
    fun `savePayment_allowsSameMessageFromDifferentSenders`() {
        val payment1 = createTestPayment(amount = 10.0, merchant = "Test Merchant", categoryId = "cat1")
        val payment2 = createTestPayment(amount = 20.0, merchant = "Test Merchant 2", categoryId = "cat2")
        
        val inserted1 = repository.savePayment(payment1, "same-message", "sender-1")
        val inserted2 = repository.savePayment(payment2, "same-message", "sender-2")
        
        assertTrue(inserted1)
        assertTrue(inserted2)
        assertEquals(2, repository.getAllPayments().size)
    }

    @Test
    fun `getAllPayments_returnsPaymentsInDescendingOrder`() {
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
}
