package com.example.banksmstracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.database.ExchangeRateDao
import com.example.banksmstracker.database.ExchangeRateEntity
import com.example.banksmstracker.util.ExchangeRateCache
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Instrumented tests for [ExchangeRateCache] using a real in-memory Room database.
 *
 * Requires a connected Android device or emulator.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExchangeRateInstrumentedTest {

    private lateinit var database: BankSmsDatabase
    private lateinit var exchangeRateDao: ExchangeRateDao

    @BeforeEach
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            BankSmsDatabase::class.java
        ).allowMainThreadQueries().build()
        exchangeRateDao = database.exchangeRateDao()
        ExchangeRateCache.clearMemoryCache()
    }

    @AfterEach
    fun tearDown() {
        ExchangeRateCache.clearMemoryCache()
        database.close()
    }

    @Test
    @DisplayName("Returns correct rate from DB for a seeded ExchangeRateEntity")
    fun returnsRateFromDb() = runBlocking {
        // Arrange: seed a known rate into the DB.
        exchangeRateDao.insertRate(ExchangeRateEntity("2026-03-15", "USD", 2.7812))

        val dateMs = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .parse("2026-03-15")!!.time

        // Act
        val rate = ExchangeRateCache.getRateToGel(dateMs, "USD", exchangeRateDao)

        // Assert
        assertEquals(2.7812, rate, 0.0001,
            "Should return the seeded rate from Room DB")
    }

    @Test
    @DisplayName("Second call is served from memory cache after DB is cleared")
    fun secondCallFromMemoryCache() = runBlocking {
        // Arrange: seed DB so first call can populate memory cache.
        exchangeRateDao.insertRate(ExchangeRateEntity("2026-03-16", "EUR", 3.0500))

        val dateMs = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .parse("2026-03-16")!!.time

        // First call — loads from DB and populates memory cache.
        val first = ExchangeRateCache.getRateToGel(dateMs, "EUR", exchangeRateDao)
        assertEquals(3.05, first, 0.0001)

        // Clear the DB so only the memory cache has the value.
        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BankSmsDatabase::class.java)
            .allowMainThreadQueries().build()
        val freshDao = database.exchangeRateDao() // empty DB

        // Second call — must be served from memory cache.
        val second = ExchangeRateCache.getRateToGel(dateMs, "EUR", freshDao)
        assertEquals(3.05, second, 0.0001,
            "Second call must be served from memory cache, not DB")
    }

    @Test
    @DisplayName("GEL currency returns exactly 1.0 without any DB access")
    fun gelCurrencyReturnsOneWithoutDbAccess() = runBlocking {
        // DB is empty — GEL must not touch it.
        val dateMs = System.currentTimeMillis()
        val rate = ExchangeRateCache.getRateToGel(dateMs, "GEL", exchangeRateDao)

        assertEquals(1.0, rate,
            "GEL must return exactly 1.0 without DB or network access")
        // Confirm no rates were inserted into the DB.
        assertNull(
            exchangeRateDao.getRate(
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(dateMs)),
                "GEL"
            ),
            "GEL lookup must not insert anything into the DB"
        )
    }
}
