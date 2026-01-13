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

    // ==================== IgnoreRule Tests ====================

    @Test
    fun `IgnoreRule default enabled is true`() {
        val rule = IgnoreRule(senderId = 1, pattern = "spam.*")
        assertTrue(rule.enabled)
    }

    @Test
    fun `IgnoreRule can be created with enabled false`() {
        val rule = IgnoreRule(senderId = 1, pattern = "spam.*", enabled = false)
        assertFalse(rule.enabled)
    }

    @Test
    fun `IgnoreRule can be created with all fields`() {
        val rule = IgnoreRule(
            id = 1,
            senderId = 2,
            pattern = "^SPAM.*",
            description = "Blocks spam messages",
            enabled = true
        )
        assertEquals(1, rule.id)
        assertEquals(2, rule.senderId)
        assertEquals("^SPAM.*", rule.pattern)
        assertEquals("Blocks spam messages", rule.description)
        assertTrue(rule.enabled)
    }

    @Test
    fun `IgnoreRule can be created with null optional fields`() {
        val rule = IgnoreRule(senderId = 1, pattern = "test")
        assertEquals(null, rule.id)
        assertEquals(1, rule.senderId)
        assertEquals("test", rule.pattern)
        assertEquals(null, rule.description)
        assertTrue(rule.enabled)
    }

    @Test
    fun `IgnoreRule copy creates independent copy`() {
        val original = IgnoreRule(
            id = 1,
            senderId = 2,
            pattern = "original",
            description = "Original description",
            enabled = true
        )
        val copy = original.copy(pattern = "modified", enabled = false)

        assertEquals("original", original.pattern)
        assertTrue(original.enabled)
        assertEquals("modified", copy.pattern)
        assertFalse(copy.enabled)
        assertEquals(original.id, copy.id)
        assertEquals(original.senderId, copy.senderId)
    }

    @Test
    fun `IgnoreRule pattern can be valid regex`() {
        val rule = IgnoreRule(senderId = 1, pattern = "\\d+\\.\\d{2}")
        val regex = Regex(rule.pattern)
        assertTrue(regex.containsMatchIn("123.45"))
        assertFalse(regex.containsMatchIn("abc"))
    }

    @Test
    fun `IgnoreRule pattern can match SMS content`() {
        val rule = IgnoreRule(senderId = 1, pattern = ".*promotional.*", enabled = true)
        val sms = "This is a promotional message from ABC Bank"
        val regex = Regex(rule.pattern, RegexOption.IGNORE_CASE)
        assertTrue(regex.containsMatchIn(sms))
    }

    @Test
    fun `IgnoreRule with disabled status should not filter`() {
        val rule = IgnoreRule(senderId = 1, pattern = "spam", enabled = false)
        // When disabled, rule should not be applied (business logic test)
        assertFalse(rule.enabled)
    }

    // ==================== Income Tests ====================

    @Test
    fun `Income can be created with all fields`() {
        val income = Income(
            id = 1,
            amount = 5000.0,
            currency = "USD",
            source = "Salary",
            timestamp = "2024-01-15",
            balance = 10000.0,
            senderAddress = "EMPLOYER",
            receivedAt = 1705330800000,
            ruleId = 5
        )
        assertEquals(1, income.id)
        assertEquals(5000.0, income.amount)
        assertEquals("USD", income.currency)
        assertEquals("Salary", income.source)
        assertEquals("2024-01-15", income.timestamp)
        assertEquals(10000.0, income.balance)
        assertEquals("EMPLOYER", income.senderAddress)
        assertEquals(1705330800000, income.receivedAt)
        assertEquals(5, income.ruleId)
    }

    @Test
    fun `Income can be created with minimal required fields`() {
        val income = Income(
            amount = 1000.0,
            currency = "EUR",
            source = null,
            timestamp = null,
            balance = null
        )
        assertEquals(null, income.id)
        assertEquals(1000.0, income.amount)
        assertEquals("EUR", income.currency)
        assertEquals(null, income.source)
        assertEquals(null, income.timestamp)
        assertEquals(null, income.balance)
        assertEquals(null, income.senderAddress)
        assertEquals(null, income.receivedAt)
        assertEquals(null, income.ruleId)
    }

    @Test
    fun `Income can be created with null optional fields`() {
        val income = Income(
            id = null,
            amount = 250.50,
            currency = "GBP",
            source = null,
            timestamp = null,
            balance = null,
            senderAddress = null,
            receivedAt = null,
            ruleId = null
        )
        assertEquals(null, income.id)
        assertEquals(250.50, income.amount)
        assertEquals("GBP", income.currency)
        assertEquals(null, income.source)
        assertEquals(null, income.timestamp)
        assertEquals(null, income.balance)
    }

    @Test
    fun `Income copy creates independent copy`() {
        val original = Income(
            id = 1,
            amount = 500.0,
            currency = "USD",
            source = "Bonus",
            timestamp = null,
            balance = null
        )
        val copy = original.copy(amount = 750.0, source = "Commission")

        assertEquals(500.0, original.amount)
        assertEquals("Bonus", original.source)
        assertEquals(750.0, copy.amount)
        assertEquals("Commission", copy.source)
        assertEquals(original.id, copy.id)
        assertEquals(original.currency, copy.currency)
    }

    @Test
    fun `Income equality based on content`() {
        val income1 = Income(amount = 100.0, currency = "USD", source = null, timestamp = null, balance = null)
        val income2 = Income(amount = 100.0, currency = "USD", source = null, timestamp = null, balance = null)
        val income3 = Income(amount = 200.0, currency = "USD", source = null, timestamp = null, balance = null)

        assertEquals(income1, income2)
        assertFalse(income1 == income3)
    }

    @Test
    fun `Income handles different currencies`() {
        val currencies = listOf("USD", "EUR", "GBP", "JPY", "CNY", "INR")
        currencies.forEach { currency ->
            val income = Income(amount = 100.0, currency = currency, source = null, timestamp = null, balance = null)
            assertEquals(currency, income.currency)
        }
    }

    @Test
    fun `Income handles large amounts`() {
        val income = Income(
            amount = 999999999.99,
            currency = "USD",
            source = null,
            timestamp = null,
            balance = null
        )
        assertEquals(999999999.99, income.amount)
    }

    @Test
    fun `Income handles decimal precision`() {
        val income = Income(
            amount = 123.456789,
            currency = "USD",
            source = null,
            timestamp = null,
            balance = null
        )
        assertEquals(123.456789, income.amount, 0.000001)
    }

    // ==================== MessageProcessResult Tests ====================

    @Test
    fun `MessageProcessResult PaymentResult contains payment`() {
        val payment = Payment(
            amount = 100.0,
            currency = "USD",
            card = null,
            merchant = "Test",
            timestamp = null,
            balance = null
        )
        val result = MessageProcessResult.PaymentResult(payment)

        assertTrue(result is MessageProcessResult.PaymentResult)
        assertEquals(100.0, result.payment.amount)
        assertEquals("Test", result.payment.merchant)
    }

    @Test
    fun `MessageProcessResult IncomeResult contains income`() {
        val income = Income(
            amount = 5000.0,
            currency = "USD",
            source = "Salary",
            timestamp = null,
            balance = null
        )
        val result = MessageProcessResult.IncomeResult(income)

        assertTrue(result is MessageProcessResult.IncomeResult)
        assertEquals(5000.0, result.income.amount)
        assertEquals("Salary", result.income.source)
    }

    @Test
    fun `MessageProcessResult Ignored contains rule name`() {
        val result = MessageProcessResult.Ignored("OTP filter")

        assertTrue(result is MessageProcessResult.Ignored)
        assertEquals("OTP filter", result.ruleName)
    }

    @Test
    fun `MessageProcessResult Ignored can have null rule name`() {
        val result = MessageProcessResult.Ignored()

        assertTrue(result is MessageProcessResult.Ignored)
        assertEquals(null, result.ruleName)
    }

    @Test
    fun `MessageProcessResult types are distinguishable`() {
        val payment = Payment(amount = 100.0, currency = "USD", card = null, merchant = null, timestamp = null, balance = null)
        val income = Income(amount = 500.0, currency = "USD", source = null, timestamp = null, balance = null)

        val paymentResult: MessageProcessResult = MessageProcessResult.PaymentResult(payment)
        val incomeResult: MessageProcessResult = MessageProcessResult.IncomeResult(income)
        val ignoredResult: MessageProcessResult = MessageProcessResult.Ignored("test")

        assertTrue(paymentResult is MessageProcessResult.PaymentResult)
        assertFalse(paymentResult is MessageProcessResult.IncomeResult)
        assertFalse(paymentResult is MessageProcessResult.Ignored)

        assertTrue(incomeResult is MessageProcessResult.IncomeResult)
        assertFalse(incomeResult is MessageProcessResult.PaymentResult)
        assertFalse(incomeResult is MessageProcessResult.Ignored)

        assertTrue(ignoredResult is MessageProcessResult.Ignored)
        assertFalse(ignoredResult is MessageProcessResult.PaymentResult)
        assertFalse(ignoredResult is MessageProcessResult.IncomeResult)
    }

    // ==================== RuleType Tests ====================

    @Test
    fun `RuleType has correct values`() {
        assertEquals("payment", RuleType.PAYMENT.value)
        assertEquals("ignore", RuleType.IGNORE.value)
        assertEquals("income", RuleType.INCOME.value)
    }

    @Test
    fun `RuleType fromValue returns correct enum`() {
        assertEquals(RuleType.PAYMENT, RuleType.fromValue("payment"))
        assertEquals(RuleType.IGNORE, RuleType.fromValue("ignore"))
        assertEquals(RuleType.INCOME, RuleType.fromValue("income"))
    }

    @Test
    fun `RuleType fromValue with unknown value returns PAYMENT`() {
        assertEquals(RuleType.PAYMENT, RuleType.fromValue("unknown"))
        assertEquals(RuleType.PAYMENT, RuleType.fromValue(""))
        assertEquals(RuleType.PAYMENT, RuleType.fromValue("PAYMENT"))
    }
}
