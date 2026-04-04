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

        override suspend fun getRate(date: String, currency: String): ExchangeRateEntity? =
            store["$date:$currency"]

        override suspend fun insertRate(rate: ExchangeRateEntity) {
            store["${rate.date}:${rate.currency}"] = rate
        }
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
        assertNull(result, "Should return null (not 1.0) when DAO is empty and network is unavailable — " +
            "returning 1.0 would silently display the wrong currency label")
    }
}
