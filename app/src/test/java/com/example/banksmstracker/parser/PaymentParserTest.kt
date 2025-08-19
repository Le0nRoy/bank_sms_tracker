package com.example.banksmstracker.parser

import com.example.banksmstracker.data.PaymentRegexRule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class PaymentParserTest {

    private val tbcRule = PaymentRegexRule(
        regex = Regex(
            """(?<amount>\d+\.\d{2})\s+(?<currency>[A-Z]{3})\s+(?<card>[\w\s]+\([^)]*\))\s+(?<merchant>\w+)\s+(?<timestamp>\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2}:\d{2})\s+Balance:\s+(?<balance>\d+\.\d{2})\s+GEL"""
        ),
        category = "shopping"
    )

    private val rules = listOf(
        tbcRule
    )

    private val parser = PaymentParser(rules)

    @Test
    fun testTbcMessage() {
        val message = """
            64.24 GEL
            MC GOLD (***0123)
            Temu 13/08/2025 00:38:12
            Balance: 16.83 GEL
        """.trimIndent()

        val payment = parser.parse(message)

        assertNotNull(payment)
        assertEquals(64.24, payment.amount)
        assertEquals("GEL", payment.currency)
        assertEquals("MC GOLD (***0123)", payment.card)
        assertEquals("Temu", payment.merchant)
        assertEquals("13/08/2025 00:38:12", payment.timestamp)
        assertEquals(16.83, payment.balance)
        assertEquals("shopping", payment.category)
    }

    @Test
    fun testParseInvalidMessage() {
        val message = "Some unrelated SMS text"

        assertFailsWith<UnparsedMessageException> {
            parser.parse(message)
        }
    }
}