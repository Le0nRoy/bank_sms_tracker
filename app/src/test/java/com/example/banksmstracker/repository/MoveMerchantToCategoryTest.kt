package com.example.banksmstracker.repository

import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Merchant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for Feature 1.1: moving a merchant between categories.
 *
 * Validates the core data manipulation that [CategoriesActivity.moveMerchantToCategory] relies on:
 * removing the merchant from the source category, adding it to the target, and updating existing
 * payment categoryIds via [PaymentRepository.updateCategoryForMerchant].
 */
class MoveMerchantToCategoryTest {

    private lateinit var paymentRepository: InMemoryPaymentRepository
    private lateinit var categoryA: Category
    private lateinit var categoryB: Category
    private val wolt = Merchant(pattern = "Wolt")

    @BeforeEach
    fun setUp() {
        paymentRepository = InMemoryPaymentRepository()
        categoryA = Category(id = 1, name = "Food", merchants = mutableListOf(wolt))
        categoryB = Category(id = 2, name = "Delivery", merchants = mutableListOf())
    }

    private suspend fun moveMerchant(merchant: Merchant, from: Category, to: Category) {
        from.merchants.removeAll { it.pattern == merchant.pattern }
        to.merchants.add(merchant)
        paymentRepository.updateCategoryForMerchant(merchant.pattern, to.name)
    }

    @Test
    fun `moveMerchant_fromCategoryA_toCategoryB removes merchant from source`() = runBlocking {
        moveMerchant(wolt, categoryA, categoryB)

        assertFalse(categoryA.merchants.any { it.pattern == wolt.pattern })
    }

    @Test
    fun `moveMerchant_fromCategoryA_toCategoryB adds merchant to target`() = runBlocking {
        moveMerchant(wolt, categoryA, categoryB)

        assertTrue(categoryB.merchants.any { it.pattern == wolt.pattern })
    }

    @Test
    fun `moveMerchant updates payment categoryId in repository`() = runBlocking {
        val payment = com.example.banksmstracker.data.Payment(
            amount = 10.0,
            currency = "GEL",
            card = null,
            merchant = "Wolt",
            timestamp = "01/03/2026 10:00:00",
            balance = null,
            categoryId = "Food"
        )
        paymentRepository.savePayment(payment, "msg-wolt", "TBC")

        moveMerchant(wolt, categoryA, categoryB)

        val updated = paymentRepository.getAllPayments().first { it.merchant == "Wolt" }
        assertEquals("Delivery", updated.categoryId)
    }

    @Test
    fun `moveMerchant with displayName preserves displayName in target category`() = runBlocking {
        val woltWithName = Merchant(pattern = "Wolt", displayName = "Wolt Food")
        categoryA.merchants.clear()
        categoryA.merchants.add(woltWithName)

        moveMerchant(woltWithName, categoryA, categoryB)

        val movedMerchant = categoryB.merchants.first { it.pattern == woltWithName.pattern }
        assertEquals("Wolt Food", movedMerchant.displayName)
    }

    @Test
    fun `moveMerchant does not affect other merchants in source category`() = runBlocking {
        val bolt = Merchant(pattern = "Bolt")
        categoryA.merchants.add(bolt)

        moveMerchant(wolt, categoryA, categoryB)

        assertTrue(categoryA.merchants.any { it.pattern == bolt.pattern })
        assertEquals(1, categoryA.merchants.size)
    }

    @Test
    fun `moveMerchant updates payments case-insensitively`() = runBlocking {
        val payment = com.example.banksmstracker.data.Payment(
            amount = 5.0,
            currency = "GEL",
            card = null,
            merchant = "WOLT",
            timestamp = "01/03/2026 10:00:00",
            balance = null,
            categoryId = "Food"
        )
        paymentRepository.savePayment(payment, "msg-wolt-upper", "TBC")

        moveMerchant(wolt, categoryA, categoryB)

        val updated = paymentRepository.getAllPayments().first { it.merchant == "WOLT" }
        assertEquals("Delivery", updated.categoryId)
    }
}
