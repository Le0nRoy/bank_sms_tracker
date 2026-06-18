package com.example.banksmstracker.util

import com.example.banksmstracker.database.ExchangeRateDao
import com.example.banksmstracker.database.ExchangeRateEntity
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for batch prefetch and the new DAO query methods (via in-memory fake).
 * Tests REQ-3, REQ-4, REQ-6, REQ-7 behaviour without Android framework or network.
 */
@DisplayName("ExchangeRatePrefetchTest")
class ExchangeRatePrefetchTest {

    // ── Fake DAO ──────────────────────────────────────────────────────────────

    private class FakeDao : ExchangeRateDao {
        val store = LinkedHashMap<String, ExchangeRateEntity>()

        override suspend fun getRate(date: String, currency: String) = store["$date:$currency"]
        override suspend fun insertRate(rate: ExchangeRateEntity) {
            store["${rate.date}:${rate.currency}"] = rate
        }

        override suspend fun getAll(): List<ExchangeRateEntity> =
            store.values.sortedWith(compareByDescending<ExchangeRateEntity> { it.date }.thenBy { it.currency })

        override suspend fun getByDateRange(startDate: String, endDate: String): List<ExchangeRateEntity> =
            store.values.filter { it.date in startDate..endDate }
                .sortedWith(compareByDescending<ExchangeRateEntity> { it.date }.thenBy { it.currency })

        override suspend fun getByCurrencies(currencies: List<String>): List<ExchangeRateEntity> =
            store.values.filter { it.currency in currencies }
                .sortedWith(compareByDescending<ExchangeRateEntity> { it.date }.thenBy { it.currency })

        override suspend fun deleteRate(date: String, currency: String) {
            store.remove("$date:$currency")
        }

        override suspend fun getAvailableCurrencies(): List<String> =
            store.values.map { it.currency }.distinct().sorted()

        override suspend fun getDatesForCurrency(currency: String): List<String> =
            store.values.filter { it.currency == currency }.map { it.date }.sortedDescending()
    }

    private lateinit var dao: FakeDao

    @BeforeEach
    fun setUp() {
        ExchangeRateCache.clearMemoryCache()
        dao = FakeDao()
    }

    @AfterEach
    fun tearDown() {
        ExchangeRateCache.clearMemoryCache()
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAll returns all seeded rates")
    fun `getAll returns all rates`() = runBlocking {
        dao.insertRate(ExchangeRateEntity("2026-03-01", "USD", 2.72))
        dao.insertRate(ExchangeRateEntity("2026-03-01", "EUR", 2.95))
        dao.insertRate(ExchangeRateEntity("2026-03-02", "USD", 2.73))

        val all = dao.getAll()
        assertEquals(3, all.size, "Should return all 3 seeded rates")
    }

    // ── getByDateRange ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getByDateRange filters by inclusive date range")
    fun `getByDateRange returns only rates in range`() = runBlocking {
        dao.insertRate(ExchangeRateEntity("2026-03-01", "USD", 2.71))
        dao.insertRate(ExchangeRateEntity("2026-03-05", "USD", 2.72))
        dao.insertRate(ExchangeRateEntity("2026-03-10", "USD", 2.73))

        val inRange = dao.getByDateRange("2026-03-01", "2026-03-05")
        assertEquals(2, inRange.size)
        assertTrue(inRange.all { it.date in "2026-03-01".."2026-03-05" })
    }

    // ── getByCurrencies ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getByCurrencies filters by given currencies")
    fun `getByCurrencies returns only matching currencies`() = runBlocking {
        dao.insertRate(ExchangeRateEntity("2026-03-01", "USD", 2.72))
        dao.insertRate(ExchangeRateEntity("2026-03-01", "EUR", 2.95))
        dao.insertRate(ExchangeRateEntity("2026-03-01", "RUB", 0.030))

        val result = dao.getByCurrencies(listOf("USD", "EUR"))
        assertEquals(2, result.size)
        assertTrue(result.all { it.currency in listOf("USD", "EUR") })
        assertTrue(result.none { it.currency == "RUB" })
    }

    // ── deleteRate ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteRate removes the specific (date, currency) row")
    fun `deleteRate removes correct entry`() = runBlocking {
        dao.insertRate(ExchangeRateEntity("2026-03-01", "USD", 2.72))
        dao.insertRate(ExchangeRateEntity("2026-03-01", "EUR", 2.95))

        dao.deleteRate("2026-03-01", "USD")

        assertEquals(null, dao.getRate("2026-03-01", "USD"))
        assertEquals(2.95, dao.getRate("2026-03-01", "EUR")?.rateToGel)
    }

    // ── getAvailableCurrencies ────────────────────────────────────────────────

    @Test
    @DisplayName("getAvailableCurrencies returns distinct sorted currencies")
    fun `getAvailableCurrencies returns distinct values`() = runBlocking {
        dao.insertRate(ExchangeRateEntity("2026-03-01", "USD", 2.72))
        dao.insertRate(ExchangeRateEntity("2026-03-02", "USD", 2.73))
        dao.insertRate(ExchangeRateEntity("2026-03-01", "EUR", 2.95))

        val currencies = dao.getAvailableCurrencies()
        assertEquals(listOf("EUR", "USD"), currencies)
    }

    // ── prefetchRates integration ─────────────────────────────────────────────

    @Test
    @DisplayName("prefetchRates persists fetched rates to DAO (memory-hit path)")
    fun `prefetchRates populates DAO from memory cache`() = runBlocking {
        // Pre-seed DB so network is not needed.
        dao.insertRate(ExchangeRateEntity("2026-03-15", "USD", 2.7812))

        val pairs = listOf("2026-03-15" to "USD")
        val failed = ExchangeRateCache.prefetchRates(pairs, dao)

        assertTrue(failed.isEmpty(), "Rate in DB should cause no failures")
        // Verify cache was populated (second call hits memory).
        dao.store.clear()
        val rate = ExchangeRateCache.getRateToGelForDate("2026-03-15", "USD", dao)
        assertEquals(2.7812, rate ?: 0.0, 0.0001, "Rate should still be available from memory cache")
    }

    @Test
    @DisplayName("prefetchRates handles empty input list")
    fun `prefetchRates with empty list returns empty failed list`() = runBlocking {
        val failed = ExchangeRateCache.prefetchRates(emptyList(), dao)
        assertTrue(failed.isEmpty())
    }

    @Test
    @DisplayName("PREFETCH_CURRENCIES contains expected non-GEL currencies")
    fun `PREFETCH_CURRENCIES contains USD EUR RUB`() {
        val expected = listOf("USD", "EUR", "RUB")
        assertEquals(expected, ExchangeRateCache.PREFETCH_CURRENCIES)
        assertTrue(ExchangeRateCache.PREFETCH_CURRENCIES.none { it == "GEL" })
    }
}
