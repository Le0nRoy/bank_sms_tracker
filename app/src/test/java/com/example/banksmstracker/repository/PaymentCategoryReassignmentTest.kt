package com.example.banksmstracker.repository

import com.example.banksmstracker.data.Payment
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for [InMemoryPaymentRepository.updateCategoryForMerchant].
 *
 * Reproduces BUG-007: re-assigning a payment's category did not update the categoryId of existing
 * payments in the database.
 */
class PaymentCategoryReassignmentTest {

    private lateinit var repository: InMemoryPaymentRepository

    @BeforeEach
    fun setUp() {
        repository = InMemoryPaymentRepository()
    }

    private suspend fun save(merchant: String, category: String?, sender: String = "TBC"): Payment {
        val p = Payment(
            amount = 10.0,
            currency = "GEL",
            card = null,
            merchant = merchant,
            timestamp = "01/03/2026 10:00:00",
            balance = null,
            categoryId = category
        )
        repository.savePayment(p, "msg-$merchant-${System.nanoTime()}", sender)
        return repository.getAllPayments().first { it.merchant == merchant }
    }

    // ── updateCategoryForMerchant ─────────────────────────────────────────────

    @Test
    fun `updateCategoryForMerchant sets category on all matching payments`() = runBlocking {
        save("Wolt", category = null)
        save("Wolt", category = null)
        save("Bolt", category = null)

        repository.updateCategoryForMerchant("Wolt", "Delivery")

        val wolt = repository.getAllPayments().filter { it.merchant == "Wolt" }
        val bolt = repository.getAllPayments().filter { it.merchant == "Bolt" }

        assertEquals(2, wolt.size)
        wolt.forEach { assertEquals("Delivery", it.categoryId) }
        bolt.forEach { assertNull(it.categoryId, "Bolt must not be affected") }
    }

    @Test
    fun `updateCategoryForMerchant is case-insensitive`() = runBlocking {
        save("WOLT", category = null)
        save("wolt", category = null)

        repository.updateCategoryForMerchant("Wolt", "Delivery")

        repository.getAllPayments().forEach {
            assertEquals("Delivery", it.categoryId)
        }
    }

    @Test
    fun `updateCategoryForMerchant overwrites existing category`() = runBlocking {
        save("Wolt", category = "Transport")

        repository.updateCategoryForMerchant("Wolt", "Delivery")

        val payment = repository.getAllPayments().first()
        assertEquals("Delivery", payment.categoryId)
    }

    @Test
    fun `updateCategoryForMerchant can clear category to null`() = runBlocking {
        save("Wolt", category = "Delivery")

        repository.updateCategoryForMerchant("Wolt", null)

        val payment = repository.getAllPayments().first()
        assertNull(payment.categoryId)
    }

    @Test
    fun `BUG-007 reassigning merchant category updates payment categoryId`() = runBlocking {
        // Step 1: assign Wolt to "Delivery"
        save("Wolt", category = null)
        repository.updateCategoryForMerchant("Wolt", "Delivery")
        assertEquals("Delivery", repository.getAllPayments().first { it.merchant == "Wolt" }.categoryId)

        // Step 2: re-assign Wolt to "Transport" — this must also update the stored payment
        repository.updateCategoryForMerchant("Wolt", "Transport")

        val payment = repository.getAllPayments().first { it.merchant == "Wolt" }
        assertEquals(
            "Transport",
            payment.categoryId,
            "BUG-007: re-assigning a merchant must update the categoryId of existing payments"
        )
    }
}
