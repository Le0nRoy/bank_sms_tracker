package com.example.banksmstracker.ui

import com.example.banksmstracker.data.Payment
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [filterPayments] and [parseTransactionTimestamp].
 *
 * These tests reproduce BUG-008 and BUG-009:
 *   BUG-008 – end_date filter showed nothing because applyFilter used receivedAt instead of timestamp.
 *   BUG-009 – start_date filter showed older payments for the same reason.
 */
class PaymentsFilterTest {

    private fun makePayment(timestamp: String, categoryId: String? = null, senderAddress: String? = "TBC SMS") =
        Payment(
            id = 1,
            amount = 10.0,
            currency = "GEL",
            card = null,
            merchant = "Test Merchant",
            timestamp = timestamp,
            balance = null,
            categoryId = categoryId,
            senderAddress = senderAddress,
            ruleId = null
        )

    // ── parseTransactionTimestamp ──────────────────────────────────────────────

    @Test
    fun `parseTransactionTimestamp returns millis for valid timestamp`() {
        val result = parseTransactionTimestamp("01/03/2026 10:30:00")
        val expected = parseTransactionTimestamp("01/03/2026 10:30:00")
        assertEquals(expected, result)
    }

    @Test
    fun `parseTransactionTimestamp returns null for blank input`() {
        assertNull(parseTransactionTimestamp(""))
        assertNull(parseTransactionTimestamp("   "))
    }

    @Test
    fun `parseTransactionTimestamp returns null for unparseable format`() {
        assertNull(parseTransactionTimestamp("not-a-date"))
    }

    @Test
    fun `parseTransactionTimestamp parses January date correctly`() {
        val result = parseTransactionTimestamp("01/01/2026 00:00:00")
        val march = parseTransactionTimestamp("01/03/2026 00:00:00")
        assert(result!! < march!!) { "January must be before March" }
    }

    // ── BUG-008: end_date filter ───────────────────────────────────────────────

    @Test
    fun `BUG-008 end date filter includes payments with transaction date before end date`() {
        // Payments from March 1–11, all imported on March 12 (receivedAt = March 12).
        val payments = listOf(
            makePayment("01/03/2026 10:00:00"),
            makePayment("05/03/2026 15:00:00"),
            makePayment("11/03/2026 23:59:59")
        )

        val startDate = parseTransactionTimestamp("01/03/2026 00:00:00")
        val endDate = parseTransactionTimestamp("11/03/2026 23:59:59")

        val result = filterPayments(payments, null, null, startDate, endDate)

        assertEquals(3, result.size, "All three March 1–11 payments should be shown")
    }

    @Test
    fun `BUG-008 end date filter excludes payments with transaction date after end date`() {
        val payment = makePayment("12/03/2026 10:00:00") // March 12 – after end date

        val startDate = parseTransactionTimestamp("01/03/2026 00:00:00")
        val endDate = parseTransactionTimestamp("11/03/2026 23:59:59")

        val result = filterPayments(listOf(payment), null, null, startDate, endDate)

        assertEquals(0, result.size, "March 12 payment should be excluded when end is March 11")
    }

    @Test
    fun `BUG-008 end date filter does not use receivedAt for comparison`() {
        // A payment with transaction date = March 5 (imported later but timestamp is in the SMS body).
        val payment = makePayment(timestamp = "05/03/2026 10:00:00")

        val startDate = parseTransactionTimestamp("01/03/2026 00:00:00")
        // endDate = March 11: filter must use timestamp (March 5), not the import time.
        val endDate = parseTransactionTimestamp("11/03/2026 23:59:59")

        val result = filterPayments(listOf(payment), null, null, startDate, endDate)

        assertEquals(
            1,
            result.size,
            "Payment with transaction on March 5 must be shown when filtering March 1–11, " +
                "even though receivedAt is March 12"
        )
    }

    // ── BUG-009: start_date filter ─────────────────────────────────────────────

    @Test
    fun `BUG-009 start date filter hides payments older than start date`() {
        // Payments in January and February, all imported on March 12.
        val payments = listOf(
            makePayment("01/01/2026 10:00:00"),
            makePayment("15/02/2026 12:00:00"),
            makePayment("28/02/2026 23:00:00")
        )

        val startDate = parseTransactionTimestamp("01/03/2026 00:00:00")
        val endDate = parseTransactionTimestamp("31/03/2026 23:59:59")

        val result = filterPayments(payments, null, null, startDate, endDate)

        assertEquals(0, result.size, "January/February payments must be hidden when start is March 1")
    }

    @Test
    fun `BUG-009 start date filter shows payments within selected range`() {
        val payments = listOf(
            makePayment("01/01/2026 10:00:00"), // before range
            makePayment("05/03/2026 10:00:00"), // in range
            makePayment("20/03/2026 10:00:00") // in range
        )

        val startDate = parseTransactionTimestamp("01/03/2026 00:00:00")
        val endDate = parseTransactionTimestamp("31/03/2026 23:59:59")

        val result = filterPayments(payments, null, null, startDate, endDate)

        assertEquals(2, result.size, "Only March payments should be shown")
    }

    @Test
    fun `BUG-009 start date does not use receivedAt for comparison`() {
        // Payment from January — must be excluded by a March start date regardless of import time.
        val payment = makePayment(timestamp = "01/01/2026 10:00:00")

        val startDate = parseTransactionTimestamp("01/03/2026 00:00:00")
        val endDate = parseTransactionTimestamp("31/03/2026 23:59:59")

        val result = filterPayments(listOf(payment), null, null, startDate, endDate)

        assertEquals(
            0,
            result.size,
            "January payment must be hidden when start is March 1, " +
                "even though receivedAt is March 12"
        )
    }

    // ── Category and Sender filters ────────────────────────────────────────────

    @Test
    fun `category filter includes only matching payments`() {
        val payments = listOf(
            makePayment("01/03/2026 10:00:00", categoryId = "Food"),
            makePayment("02/03/2026 10:00:00", categoryId = "Transport"),
            makePayment("03/03/2026 10:00:00", categoryId = null)
        )

        val result = filterPayments(payments, "Food", null, null, null)

        assertEquals(1, result.size)
        assertEquals("Food", result[0].categoryId)
    }

    @Test
    fun `sender filter includes only matching payments`() {
        val payments = listOf(
            makePayment("01/03/2026 10:00:00", senderAddress = "TBC SMS"),
            makePayment("02/03/2026 10:00:00", senderAddress = "BOG SMS")
        )

        val result = filterPayments(payments, null, "TBC SMS", null, null)

        assertEquals(1, result.size)
        assertEquals("TBC SMS", result[0].senderAddress)
    }

    @Test
    fun `no filters returns all payments`() {
        val payments = listOf(
            makePayment("01/01/2026 10:00:00"),
            makePayment("15/02/2026 10:00:00"),
            makePayment("10/03/2026 10:00:00")
        )

        val result = filterPayments(payments, null, null, null, null)

        assertEquals(3, result.size)
    }

    @Test
    fun `payment with parseable timestamp is included by date filter`() {
        // Payment whose timestamp falls within the filter range.
        val payment = Payment(
            id = 1,
            amount = 10.0,
            currency = "GEL",
            card = null,
            merchant = "Test",
            timestamp = "05/03/2026 10:00:00",
            balance = null,
            categoryId = null,
            senderAddress = "TBC SMS",
            ruleId = null
        )

        val startDate = parseTransactionTimestamp("01/03/2026 00:00:00")
        val endDate = parseTransactionTimestamp("31/03/2026 23:59:59")

        val result = filterPayments(listOf(payment), null, null, startDate, endDate)

        assertEquals(1, result.size, "Should include payment with parseable timestamp in range")
    }

    // ── Task 2.1: Merchant search filter ──────────────────────────────────────

    private fun makePaymentWithMerchant(merchant: String?) = Payment(
        id = 1, amount = 10.0, currency = "GEL", card = null,
        merchant = merchant, timestamp = "01/03/2026 10:00:00",
        balance = null, categoryId = null, senderAddress = "TBC SMS",
        ruleId = null
    )

    @Test
    fun `Task21 merchant query returns only payments with matching merchant`() {
        val payments = listOf(
            makePaymentWithMerchant("Carrefour"),
            makePaymentWithMerchant("Bolt"),
            makePaymentWithMerchant("bolt food") // should match "bolt" case-insensitively
        )
        val result = filterPayments(payments, null, null, null, null, merchantQuery = "bolt")
        assertEquals(2, result.size)
        assertTrue(result.all { it.merchant?.contains("bolt", ignoreCase = true) == true })
    }

    @Test
    fun `Task21 blank merchant query includes all payments`() {
        val payments = listOf(
            makePaymentWithMerchant("Carrefour"),
            makePaymentWithMerchant("Bolt")
        )
        assertEquals(2, filterPayments(payments, null, null, null, null, merchantQuery = "").size)
        assertEquals(2, filterPayments(payments, null, null, null, null, merchantQuery = "  ").size)
        assertEquals(2, filterPayments(payments, null, null, null, null, merchantQuery = null).size)
    }

    @Test
    fun `Task21 merchant query excludes payment with null merchant`() {
        val payments = listOf(
            makePaymentWithMerchant(null),
            makePaymentWithMerchant("Bolt")
        )
        val result = filterPayments(payments, null, null, null, null, merchantQuery = "bolt")
        assertEquals(1, result.size)
        assertEquals("Bolt", result[0].merchant)
    }

    @Test
    fun `Task21 merchant query is case-insensitive`() {
        val payments = listOf(makePaymentWithMerchant("CARREFOUR CITY"))
        assertEquals(1, filterPayments(payments, null, null, null, null, merchantQuery = "carrefour").size)
        assertEquals(1, filterPayments(payments, null, null, null, null, merchantQuery = "CARREFOUR").size)
        assertEquals(1, filterPayments(payments, null, null, null, null, merchantQuery = "City").size)
    }

    @Test
    fun `Task21 merchant query combined with category filter`() {
        val payments = listOf(
            makePayment("01/03/2026 10:00:00", categoryId = "Food").copy(merchant = "Bolt Food"),
            makePayment("02/03/2026 10:00:00", categoryId = "Transport").copy(merchant = "Bolt"),
            makePayment("03/03/2026 10:00:00", categoryId = "Food").copy(merchant = "Carrefour")
        )
        val result = filterPayments(payments, "Food", null, null, null, merchantQuery = "bolt")
        assertEquals(1, result.size)
        assertEquals("Bolt Food", result[0].merchant)
    }

    @Test
    fun `payment with blank timestamp is excluded when date filter active`() {
        // timestamp = "" means no parseable date — payment must be excluded from date-filtered results.
        val payment = Payment(
            id = 1,
            amount = 10.0,
            currency = "GEL",
            card = null,
            merchant = "Test",
            timestamp = "",
            balance = null,
            categoryId = null,
            senderAddress = "TBC SMS",
            ruleId = null
        )

        val startDate = parseTransactionTimestamp("01/03/2026 00:00:00")

        val result = filterPayments(listOf(payment), null, null, startDate, null)

        assertEquals(0, result.size)
    }
}
