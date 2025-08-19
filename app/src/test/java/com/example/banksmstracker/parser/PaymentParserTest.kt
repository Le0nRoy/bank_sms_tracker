package com.example.banksmstracker.parser

import com.example.banksmstracker.data.PaymentRegexRule
import java.time.LocalDate

class PaymentParserTest {

    private val rules = listOf(
        PaymentRegexRule(
            Regex("""You spent (?<paymentAmount>\d+(\.\d+)?) USD at (?<paymentReceiver>[\w\s]+) on (?<paymentDate>\d{4}-\d{2}-\d{2})"""),
            category = "Food"
        )
    )

    private val parser = PaymentParser(rules)

    @Test
    fun testParseValidMessage() {
        val message = "You spent 25.50 USD at Starbucks on 2025-08-18"
        val payment = parser.parse("BankName", message)

        assertEquals("BankName", payment.sender)
        assertEquals(25.50, payment.amount)
        assertEquals("Starbucks", payment.receiver)
        assertEquals(LocalDate.of(2025, 8, 18), payment.date)
        assertEquals("Food", payment.category)
    }

    @Test
    fun testParseInvalidMessage() {
        val message = "Some unrelated SMS text"

        assertFailsWith<UnparsedMessageException> {
            parser.parse("BankName", message)
        }
    }
}