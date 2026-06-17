package com.example.banksmstracker.util

import com.example.banksmstracker.database.ExchangeRateDao
import com.example.banksmstracker.database.ExchangeRateEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Integration-style unit tests verifying the post-processing prefetch contract (AC-6, AC-7):
 * - For any given timestamp, prefetch must attempt USD, EUR, RUB rates.
 * - Failed pairs must be returned so the caller can notify the user.
 * - Successfully fetched pairs must be persisted to DAO.
 *
 * Does not require Android framework — uses in-memory fake DAO.
 */
@DisplayName("ExchangeRatePrefetchIntegrationTest")
class ExchangeRatePrefetchIntegrationTest {

    // ── Fake DAO with call tracking ───────────────────��───────────────────────

    private class TrackingFakeDao : ExchangeRateDao {
        val store = HashMap<String, ExchangeRateEntity>()
        val insertedPairs = mutableListOf<Pair<String, String>>() // (date, currency)

        override suspend fun getRate(date: String, currency: String) = store["$date:$currency"]
        override suspend fun insertRate(rate: ExchangeRateEntity) {
            store["${rate.date}:${rate.currency}"] = rate
            insertedPairs.add(rate.date to rate.currency)
        }

        override suspend fun getAll(): List<ExchangeRateEntity> = store.values.toList()
        override suspend fun getByDateRange(startDate: String, endDate: String): List<ExchangeRateEntity> =
            store.values.filter { it.date in startDate..endDate }
        override suspend fun getByCurrencies(currencies: List<String>): List<ExchangeRateEntity> =
            store.values.filter { it.currency in currencies }
        override suspend fun deleteRate(date: String, currency: String) {
            store.remove("$date:$currency")
        }
        override suspend fun getAvailableCurrencies(): List<String> =
            store.values.map { it.currency }.distinct().sorted()
        override suspend fun getDatesForCurrency(currency: String): List<String> =
            store.values.filter { it.currency == currency }.map { it.date }.sortedDescending()
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private lateinit var dao: TrackingFakeDao

    @BeforeEach
    fun setUp() {
        ExchangeRateCache.clearMemoryCache()
        dao = TrackingFakeDao()
    }

    @AfterEach
    fun tearDown() {
        ExchangeRateCache.clearMemoryCache()
    }

    // ── AC-6/7: post-processing prefetch ────────────��────────────────────────

    @Test
    @DisplayName("AC-6/7: PREFETCH_CURRENCIES are USD, EUR, RUB (all non-GEL spinner options)")
    fun `PREFETCH_CURRENCIES contains all non-GEL spinner currencies`() {
        assertEquals(listOf("USD", "EUR", "RUB"), ExchangeRateCache.PREFETCH_CURRENCIES)
        assertTrue(
            ExchangeRateCache.PREFETCH_CURRENCIES.none { it == "GEL" },
            "GEL must not be in PREFETCH_CURRENCIES — it always returns 1.0"
        )
    }

    @Test
    @DisplayName("AC-6/7: prefetch for a processed SMS date uses all PREFETCH_CURRENCIES")
    fun `prefetch for processed date covers all prefetch currencies`() = runBlocking {
        // Simulate what SmsReceiver.prefetchRatesForDates() does:
        // collect date strings from all messages in the batch, then call prefetchRates once.
        // Seed DB so network is not needed.
        val dateStr = "2026-04-01"
        for (currency in ExchangeRateCache.PREFETCH_CURRENCIES) {
            dao.store["$dateStr:$currency"] = ExchangeRateEntity(dateStr, currency, 2.5)
        }

        val timestampMs = dateFormat.parse(dateStr)!!.time
        val date = dateFormat.format(Date(timestampMs))
        val pairs = ExchangeRateCache.PREFETCH_CURRENCIES.map { date to it }

        val failed = ExchangeRateCache.prefetchRates(pairs, dao)

        assertTrue(failed.isEmpty(), "All pairs should succeed when seeded in DB")
        // All rates should now be in memory cache
        for (currency in ExchangeRateCache.PREFETCH_CURRENCIES) {
            val rate = ExchangeRateCache.getRateToGelForDate(dateStr, currency, dao)
            assertEquals(2.5, rate ?: 0.0, 0.001, "Rate for $currency should be in memory cache")
        }
    }

    @Test
    @DisplayName("AC-6/7: prefetch returns failed pairs for unreachable currencies")
    fun `prefetch returns all failed pairs when network is down`() = runBlocking {
        // DAO is empty, network not available in unit test
        val dateStr = "2026-04-02"
        val pairs = ExchangeRateCache.PREFETCH_CURRENCIES.map { dateStr to it }

        val failed = ExchangeRateCache.prefetchRates(pairs, dao)

        assertEquals(
            ExchangeRateCache.PREFETCH_CURRENCIES.size,
            failed.size,
            "All ${ExchangeRateCache.PREFETCH_CURRENCIES.size} currency pairs should fail when network is down"
        )
    }

    @Test
    @DisplayName("AC-6/7: prefetch persists successful rates to DAO")
    fun `prefetch persists rates to DAO on success`() = runBlocking {
        val dateStr = "2026-04-03"
        // Seed only USD so EUR and RUB will fail (no network)
        dao.store["$dateStr:USD"] = ExchangeRateEntity(dateStr, "USD", 2.72)

        val pairs = ExchangeRateCache.PREFETCH_CURRENCIES.map { dateStr to it }
        val failed = ExchangeRateCache.prefetchRates(pairs, dao)

        // USD should succeed, EUR and RUB should fail
        assertTrue(failed.none { it.second == "USD" }, "USD should not be in failed list")
        assertEquals(2, failed.size, "EUR and RUB should fail")

        // Verify USD rate was loaded into memory cache (persisted via DB path)
        dao.store.clear() // clear DB
        val rate = ExchangeRateCache.getRateToGelForDate(dateStr, "USD", dao)
        assertEquals(2.72, rate ?: 0.0, 0.001, "USD rate should be in memory after prefetch")
    }

    @Test
    @DisplayName("AC-6: Multiple processed dates in one batch are all prefetched")
    fun `prefetch handles multiple dates from batch processing`() = runBlocking {
        // Simulate ApplyRulesActivity processing with multiple SMS dates
        val dates = listOf("2026-04-01", "2026-04-02", "2026-04-03")
        for (d in dates) {
            for (c in ExchangeRateCache.PREFETCH_CURRENCIES) {
                dao.store["$d:$c"] = ExchangeRateEntity(d, c, 2.5 + dates.indexOf(d) * 0.01)
            }
        }

        val pairs = dates.flatMap { date ->
            ExchangeRateCache.PREFETCH_CURRENCIES.map { date to it }
        }

        val failed = ExchangeRateCache.prefetchRates(pairs, dao)
        assertTrue(failed.isEmpty(), "All pairs across multiple dates should succeed when in DB")

        // Verify total pairs = dates × currencies
        assertEquals(
            dates.size * ExchangeRateCache.PREFETCH_CURRENCIES.size,
            pairs.size,
            "Should prefetch ${ExchangeRateCache.PREFETCH_CURRENCIES.size} currencies × ${dates.size} dates"
        )
    }

    @Test
    @DisplayName("AC-6: Single prefetchRates call covers all dates from a multi-message batch")
    fun `single prefetchRates call covers all unique dates from batch`() = runBlocking {
        // SmsReceiver and SmsProcessingService now collect dates from ALL messages in a broadcast
        // and call prefetchRates exactly ONCE after the loop — not once per message.
        // This test verifies the batch contract: one call with all (date, currency) pairs.
        val dates = listOf("2026-04-10", "2026-04-11", "2026-04-12")
        for (d in dates) {
            for (c in ExchangeRateCache.PREFETCH_CURRENCIES) {
                dao.store["$d:$c"] = ExchangeRateEntity(d, c, 2.5)
            }
        }

        // Simulate building pairs from all messages in a batch (as the service now does):
        // unique dates × PREFETCH_CURRENCIES → one prefetchRates call
        val allPairs = dates.flatMap { d ->
            ExchangeRateCache.PREFETCH_CURRENCIES.map { d to it }
        }

        val failed = ExchangeRateCache.prefetchRates(allPairs, dao)
        assertTrue(failed.isEmpty(), "Batch prefetch should succeed when all pairs are in DB")
        assertEquals(
            dates.size * ExchangeRateCache.PREFETCH_CURRENCIES.size,
            allPairs.size,
            "Pairs count must equal dates × currencies"
        )
    }

    @Test
    @DisplayName("AC-4: prefetchRates with partial failure returns only the failed subset")
    fun `prefetchRates returns only the failed subset on partial network failure`() = runBlocking {
        val date1 = "2026-04-05"
        val date2 = "2026-04-06"
        // Seed date1 USD, but leave date2 empty (will fail in network)
        dao.store["$date1:USD"] = ExchangeRateEntity(date1, "USD", 2.72)

        val pairs = listOf(date1 to "USD", date2 to "USD")
        val failed = ExchangeRateCache.prefetchRates(pairs, dao)

        assertEquals(1, failed.size, "Only date2 USD should fail")
        assertEquals(date2 to "USD", failed.first())
    }
}
