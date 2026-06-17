package com.example.banksmstracker.util

import com.example.banksmstracker.database.ExchangeRateDao
import com.example.banksmstracker.database.ExchangeRateEntity
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ExchangeRateCache].
 *
 * Uses a fake in-memory [ExchangeRateDao] so no Android framework or network is required.
 *
 * Regression for the null-fallback bug: when the DB is empty and the network is unreachable the
 * cache must return null, not 1.0. Returning 1.0 produced the wrong display amount while silently
 * swapping the currency label (e.g. "100 GEL → 100 USD").
 */
class ExchangeRateCacheTest {

    // ── Fake DAO ──────────────────────────────────────────────────────────────

    /**
     * Simple in-memory implementation of [ExchangeRateDao] backed by a HashMap.
     * Allows tests to pre-seed rates and inspect inserts without a real Room DB.
     */
    private class FakeExchangeRateDao : ExchangeRateDao {
        val store = HashMap<String, ExchangeRateEntity>()

        override suspend fun getRate(date: String, currency: String): ExchangeRateEntity? = store["$date:$currency"]

        override suspend fun insertRate(rate: ExchangeRateEntity) {
            store["${rate.date}:${rate.currency}"] = rate
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

    private lateinit var fakeDao: FakeExchangeRateDao

    @BeforeEach
    fun setUp() {
        ExchangeRateCache.clearMemoryCache()
        fakeDao = FakeExchangeRateDao()
    }

    @AfterEach
    fun tearDown() {
        ExchangeRateCache.clearMemoryCache()
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Test 1: Rate returned from memory cache (no DAO call on second request)")
    fun `rate is served from memory cache on second call`() = runBlocking {
        // Seed DB so the first call succeeds and populates memory cache.
        fakeDao.store["2026-01-15:USD"] = ExchangeRateEntity("2026-01-15", "USD", 2.7500)

        val dateMs = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .parse("2026-01-15")!!.time

        // First call — loads from DB and stores in memory cache.
        val first = ExchangeRateCache.getRateToGel(dateMs, "USD", fakeDao)
        assertEquals(2.75, first)

        // Now remove the DB entry so a second call CANNOT fall back to DB.
        fakeDao.store.clear()

        // Second call — must come from memory cache, not DB.
        val second = ExchangeRateCache.getRateToGel(dateMs, "USD", fakeDao)
        assertEquals(2.75, second, "Second call should be served from memory cache")
    }

    @Test
    @DisplayName("Test 2: Rate returned from fake DAO when memory cache is empty")
    fun `rate is loaded from DAO when memory cache is empty`() = runBlocking {
        fakeDao.store["2026-02-10:EUR"] = ExchangeRateEntity("2026-02-10", "EUR", 2.9800)

        val dateMs = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .parse("2026-02-10")!!.time

        val rate = ExchangeRateCache.getRateToGel(dateMs, "EUR", fakeDao)

        assertEquals(2.98, rate, "Should return rate from DAO when memory cache is empty")
    }

    @Test
    @DisplayName("Test 3: GEL always returns 1.0 immediately without DB or network")
    fun `GEL currency always returns 1_0`() = runBlocking {
        // DAO is empty — no DB hit should occur for GEL.
        val dateMs = System.currentTimeMillis()
        val rate = ExchangeRateCache.getRateToGel(dateMs, "GEL", fakeDao)
        assertEquals(1.0, rate, "GEL must always return 1.0 without hitting DB or network")
        // Confirm no side-effects on the DAO.
        assertEquals(0, fakeDao.store.size, "GEL lookup must not write to DAO")
    }

    @Test
    @DisplayName("Test 5: prefetchRates returns empty list when all rates are in memory cache")
    fun `prefetchRates returns empty list when rates already cached`() = runBlocking {
        // Seed memory cache directly via DB then first call.
        fakeDao.store["2026-03-01:USD"] = ExchangeRateEntity("2026-03-01", "USD", 2.72)
        ExchangeRateCache.getRateToGelForDate("2026-03-01", "USD", fakeDao) // populates memory

        val pairs = listOf("2026-03-01" to "USD")
        val failed = ExchangeRateCache.prefetchRates(pairs, fakeDao)

        assertEquals(emptyList<Pair<String, String>>(), failed, "No failures expected when rate is cached")
    }

    @Test
    @DisplayName("Test 6: prefetchRates skips GEL pairs (always 1.0)")
    fun `prefetchRates skips GEL pairs`() = runBlocking {
        val pairs = listOf("2026-03-01" to "GEL", "2026-03-01" to "USD")
        // USD will fail (no network in unit test, empty DAO) but GEL must be skipped
        val failed = ExchangeRateCache.prefetchRates(pairs, fakeDao)

        // GEL should not appear in failed list (it was skipped)
        assert(failed.none { it.second == "GEL" }) { "GEL should never appear in failed list" }
    }

    @Test
    @DisplayName("Test 7: prefetchRates returns all pairs when network is down and DB is empty")
    fun `prefetchRates returns all failed pairs on network failure`() = runBlocking {
        val pairs = listOf("2026-03-01" to "USD", "2026-03-02" to "EUR")
        val failed = ExchangeRateCache.prefetchRates(pairs, fakeDao)

        assertEquals(2, failed.size, "Both pairs should fail when DB is empty and network is down")
        assert(pairs.all { it in failed }) { "All input pairs should be in failed list" }
    }

    @Test
    @DisplayName("Test 8: prefetchRates returns only failed pairs when some succeed via DB")
    fun `prefetchRates returns only failed pairs when some in DB`() = runBlocking {
        fakeDao.store["2026-03-01:USD"] = ExchangeRateEntity("2026-03-01", "USD", 2.72)

        val pairs = listOf("2026-03-01" to "USD", "2026-03-02" to "EUR")
        val failed = ExchangeRateCache.prefetchRates(pairs, fakeDao)

        assertEquals(1, failed.size, "Only EUR pair should fail")
        assertEquals("2026-03-02" to "EUR", failed.first())
    }

    @Test
    @DisplayName("Test 4 (regression): Returns null when DAO is empty and network is unavailable")
    fun `returns null when DAO empty and network unreachable`() = runBlocking {
        // DAO is intentionally empty.  The cache will attempt a network fetch which will fail in
        // the unit-test environment (no real network, and even if there is one the 3-second
        // withTimeoutOrNull wrapper in the production code plus the cache's own 5-second
        // HttpURLConnection timeout means the call may take a while).  We call the internal
        // getRateToGelForDate directly so the test doesn't depend on a wall-clock timeout.
        //
        // In the unit-test sandbox the network call throws an exception immediately
        // (UnknownHostException / ConnectException) so the method returns null quickly.
        val result = ExchangeRateCache.getRateToGelForDate("2025-06-01", "USD", fakeDao)

        // Regression: before the fix the caller used `?: 1.0` and never saw null; after the fix
        // the cache correctly propagates null so the caller can decide not to update the UI.
        assertNull(
            result,
            "Should return null (not 1.0) when DAO is empty and network is unavailable — " +
                "returning 1.0 would silently display the wrong currency label"
        )
    }
}
