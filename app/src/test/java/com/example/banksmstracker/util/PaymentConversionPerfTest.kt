package com.example.banksmstracker.util

import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.database.ExchangeRateDao
import com.example.banksmstracker.database.ExchangeRateEntity
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Performance test for per-payment currency conversion via [ExchangeRateCache].
 *
 * Uses a stub [ExchangeRateDao] backed entirely by an in-memory map — no network,
 * no Room DB, no Android context required.
 *
 * Pass criterion: converting 25 payments completes in under 200 ms.
 */
@DisplayName("PaymentConversionPerfTest")
class PaymentConversionPerfTest {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    /** Pre-seeded rates (date:currency → rateToGel). */
    private val seedRates: Map<String, Double> = mapOf(
        "2026-03-01:USD" to 2.72,
        "2026-03-05:USD" to 2.73,
        "2026-03-10:USD" to 2.71,
        "2026-03-15:USD" to 2.74,
        "2026-03-20:USD" to 2.75,
        "2026-03-01:EUR" to 2.95,
        "2026-03-05:EUR" to 2.96,
        "2026-03-10:EUR" to 2.94,
        "2026-03-15:EUR" to 2.97,
        "2026-03-20:EUR" to 2.98,
        "2026-03-01:RUB" to 0.030,
        "2026-03-05:RUB" to 0.031,
        "2026-03-10:RUB" to 0.029,
        "2026-03-15:RUB" to 0.030,
        "2026-03-20:RUB" to 0.031
    )

    /**
     * Stub DAO that returns rates from [seedRates] without any I/O.
     * Implemented as an anonymous object so kapt does not need to process Room annotations
     * for it (the interface itself is in main sources and already processed there).
     */
    private val stubDao: ExchangeRateDao = object : ExchangeRateDao {
        override suspend fun getRate(date: String, currency: String): ExchangeRateEntity? {
            val rate = seedRates["$date:$currency"] ?: return null
            return ExchangeRateEntity(date, currency, rate)
        }
        override suspend fun insertRate(rate: ExchangeRateEntity) { /* no-op */ }
    }

    @BeforeEach
    fun clearCache() {
        ExchangeRateCache.clearMemoryCache()
    }

    /**
     * Creates 25 [Payment] objects spread across multiple currencies and dates,
     * converts each to GEL via [ExchangeRateCache.getRateToGel], and asserts the
     * total elapsed time is under 200 ms.
     */
    @Test
    @DisplayName("Converting 25 payments to GEL completes in under 200 ms")
    fun conversionOf25PaymentsCompletesUnder200ms() = runBlocking {
        val payments = buildTestPayments()
        check(payments.size == 25) { "Expected 25 test payments, got ${payments.size}" }

        val startMs = System.currentTimeMillis()

        val gelAmounts = mutableListOf<Double>()
        for (payment in payments) {
            val dateMs = parseTimestampMs(payment.timestamp)
            val rate = ExchangeRateCache.getRateToGel(dateMs, payment.currency, stubDao) ?: 1.0
            val gelAmount = payment.amount * rate
            gelAmounts.add(gelAmount)
        }

        val elapsedMs = System.currentTimeMillis() - startMs
        println("[PaymentConversionPerfTest] Converted ${payments.size} payments in $elapsedMs ms")
        for ((i, p) in payments.withIndex()) {
            println("  [${i + 1}] ${p.amount} ${p.currency} on ${p.timestamp} → ${"%.4f".format(gelAmounts[i])} GEL")
        }

        assertTrue(
            elapsedMs < 200L,
            "Expected conversion to complete in <200 ms but took $elapsedMs ms"
        )
        assertTrue(
            gelAmounts.all { it > 0.0 },
            "Expected all GEL amounts to be positive"
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun buildTestPayments(): List<Payment> {
        val currencies = listOf("GEL", "USD", "EUR", "RUB")
        val dates = listOf(
            "01/03/2026", "05/03/2026", "10/03/2026", "15/03/2026", "20/03/2026"
        )
        val payments = mutableListOf<Payment>()
        var idx = 0
        outer@ for (date in dates) {
            for (currency in currencies) {
                if (payments.size >= 25) break@outer
                payments.add(
                    Payment(
                        id = idx.toLong(),
                        amount = 10.0 + idx,
                        currency = currency,
                        card = null,
                        merchant = "Merchant$idx",
                        timestamp = date,
                        balance = null
                    )
                )
                idx++
            }
        }
        // Fill remaining to exactly 25 with GEL payments
        while (payments.size < 25) {
            payments.add(
                Payment(
                    id = idx.toLong(),
                    amount = 5.0 + idx,
                    currency = "GEL",
                    card = null,
                    merchant = "Extra$idx",
                    timestamp = "20/03/2026",
                    balance = null
                )
            )
            idx++
        }
        return payments
    }

    private fun parseTimestampMs(timestamp: String): Long {
        return try {
            dateFormat.parse(timestamp)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
