package com.example.banksmstracker.data

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DataClassesTest {

    @Test
    fun `Category default enabled is true`() {
        val category = Category(name = "Test", merchants = mutableListOf())
        assertTrue(category.enabled)
    }

    @Test
    fun `Category can be created with enabled false`() {
        val category = Category(name = "Test", merchants = mutableListOf(), enabled = false)
        assertFalse(category.enabled)
    }

    @Test
    fun `Sender default enabled is true`() {
        val sender = Sender(name = "Test", addresses = mutableListOf(), rules = mutableListOf())
        assertTrue(sender.enabled)
    }

    @Test
    fun `Sender can be created with enabled false`() {
        val sender = Sender(
            name = "Test",
            addresses = mutableListOf(),
            rules = mutableListOf(),
            enabled = false
        )
        assertFalse(sender.enabled)
    }

    @Test
    fun `PaymentRegexRule default enabled is true`() {
        val rule = PaymentRegexRule(regex = ".*")
        assertTrue(rule.enabled)
    }

    @Test
    fun `PaymentRegexRule can be created with enabled false`() {
        val rule = PaymentRegexRule(regex = ".*", enabled = false)
        assertFalse(rule.enabled)
    }

    @Test
    fun `PaymentRegexRule regexPattern returns valid Regex`() {
        val rule = PaymentRegexRule(regex = "\\d+")
        val pattern = rule.regexPattern
        assertTrue(pattern.matches("123"))
        assertFalse(pattern.matches("abc"))
    }

    @Test
    fun `PaymentRegexRule with invalid regex throws exception on regexPattern access`() {
        val rule = PaymentRegexRule(regex = "[invalid")
        assertThrows<Exception> {
            rule.regexPattern
        }
    }

    @Test
    fun `Payment can be created with all fields`() {
        val payment = Payment(
            id = 1,
            amount = 100.0,
            currency = "USD",
            card = "1234",
            merchant = "Amazon",
            timestamp = "2024-01-15",
            balance = 500.0,
            categoryId = "Shopping"
        )
        assertEquals(1, payment.id)
        assertEquals(100.0, payment.amount)
        assertEquals("USD", payment.currency)
        assertEquals("1234", payment.card)
        assertEquals("Amazon", payment.merchant)
        assertEquals("2024-01-15", payment.timestamp)
        assertEquals(500.0, payment.balance)
        assertEquals("Shopping", payment.categoryId)
    }

    @Test
    fun `Payment can be created with null optional fields`() {
        val payment = Payment(
            amount = 50.0,
            currency = "EUR",
            card = null,
            merchant = null,
            timestamp = null,
            balance = null
        )
        assertEquals(50.0, payment.amount)
        assertEquals("EUR", payment.currency)
        assertEquals(null, payment.id)
        assertEquals(null, payment.card)
        assertEquals(null, payment.merchant)
        assertEquals(null, payment.timestamp)
        assertEquals(null, payment.balance)
        assertEquals(null, payment.categoryId)
    }

    @Test
    fun `Payment copy creates independent copy`() {
        val original = Payment(
            amount = 100.0,
            currency = "USD",
            card = null,
            merchant = "Test",
            timestamp = null,
            balance = null
        )
        val copy = original.copy(categoryId = "Shopping")

        assertEquals(null, original.categoryId)
        assertEquals("Shopping", copy.categoryId)
        assertEquals(original.amount, copy.amount)
    }

    @Test
    fun `SmsConfig holds senders and categories`() {
        val sender = Sender(name = "Bank", addresses = mutableListOf("123"), rules = mutableListOf())
        val category = Category(name = "Shopping", merchants = mutableListOf("Amazon"))

        val config = SmsConfig(
            senders = mutableListOf(sender),
            categories = mutableListOf(category)
        )

        assertEquals(1, config.senders.size)
        assertEquals(1, config.categories.size)
        assertEquals("Bank", config.senders[0].name)
        assertEquals("Shopping", config.categories[0].name)
    }
}
