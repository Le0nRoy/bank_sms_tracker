package com.example.banksmstracker.util

import com.example.banksmstracker.database.ExchangeRateDao
import com.example.banksmstracker.database.ExchangeRateEntity
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Exchange-rate cache for currency→GEL conversion.
 *
 * Lookup order:
 *   1. In-memory map (fastest — avoids DB round-trip within the same session)
 *   2. Room [ExchangeRateDao] (survives app restarts)
 *   3. National Bank of Georgia REST API (network, only when both caches miss)
 *
 * API: https://nbg.gov.ge/gw/api/ct/monetarypolicy/currities/en/json/?currency=USD&date=YYYY-MM-DD
 *
 * Memory cache key: "$dateStr:$currency" to avoid collisions between currencies.
 */
object ExchangeRateCache {

    private val memoryCache = java.util.concurrent.ConcurrentHashMap<String, Double>()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Currencies pre-fetched after SMS processing (all non-GEL display options). */
    val PREFETCH_CURRENCIES = listOf("USD", "EUR", "RUB")

    /**
     * Get USD→GEL rate for the given epoch-milliseconds date.
     * Returns null if unavailable.
     */
    suspend fun getUsdToGelRate(dateMs: Long, dao: ExchangeRateDao): Double? = getRateToGel(dateMs, "USD", dao)

    /**
     * Get the exchange rate for [currency]→GEL for the given epoch-milliseconds date.
     * Returns 1.0 if [currency] is "GEL". Returns null if unavailable.
     */
    suspend fun getRateToGel(dateMs: Long, currency: String, dao: ExchangeRateDao): Double? {
        if (currency == "GEL") return 1.0
        return getRateToGelForDate(dateFormat.format(Date(dateMs)), currency, dao)
    }

    /**
     * Get the exchange rate for [currency]→GEL for a "yyyy-MM-dd" date string.
     * Checks memory, then DB, then network. Persists to DB on a successful network fetch.
     */
    suspend fun getRateToGelForDate(dateStr: String, currency: String, dao: ExchangeRateDao): Double? {
        if (currency == "GEL") return 1.0
        val cacheKey = "$dateStr:$currency"

        // 1. Memory
        memoryCache[cacheKey]?.let { return it }

        // 2. DB
        val dbEntity = withContext(Dispatchers.IO) { dao.getRate(dateStr, currency) }
        if (dbEntity != null) {
            memoryCache[cacheKey] = dbEntity.rateToGel
            return dbEntity.rateToGel
        }

        // 3. Network
        val rate = fetchFromNetwork(dateStr, currency) ?: return null
        memoryCache[cacheKey] = rate
        withContext(Dispatchers.IO) { dao.insertRate(ExchangeRateEntity(dateStr, currency, rate)) }
        return rate
    }

    /**
     * Pre-fetches exchange rates for all given (dateStr, currency) pairs in parallel.
     * GEL pairs are silently skipped (rate is always 1.0).
     * Returns the list of (dateStr, currency) pairs for which the fetch failed.
     */
    suspend fun prefetchRates(pairs: List<Pair<String, String>>, dao: ExchangeRateDao): List<Pair<String, String>> =
        coroutineScope {
            val filtered = pairs.filter { (_, currency) -> currency != "GEL" }
            val deferreds = filtered.map { pair ->
                async(Dispatchers.IO) {
                    val (dateStr, currency) = pair
                    val rate = getRateToGelForDate(dateStr, currency, dao)
                    if (rate == null) pair else null
                }
            }
            deferreds.mapNotNull { it.await() }
        }

    /**
     * Get USD→GEL rate for a "yyyy-MM-dd" date string.
     * Kept for backward compatibility — delegates to [getRateToGelForDate].
     */
    suspend fun getUsdToGelRateForDate(dateStr: String, dao: ExchangeRateDao): Double? =
        getRateToGelForDate(dateStr, "USD", dao)

    private suspend fun fetchFromNetwork(dateStr: String, currency: String): Double? = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://nbg.gov.ge/gw/api/ct/monetarypolicy/currities/en/json/" +
                    "?currency=$currency&date=$dateStr"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.requestMethod = "GET"
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                parseRate(conn.inputStream.bufferedReader().readText(), currency)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRate(json: String, currency: String): Double? = try {
        val array = JSONArray(json)
        if (array.length() == 0) {
            null
        } else {
            val currencies = array.getJSONObject(0).getJSONArray("currencies")
            var result: Double? = null
            for (i in 0 until currencies.length()) {
                val entry = currencies.getJSONObject(i)
                if (entry.getString("code") == currency) {
                    result = entry.getDouble("rate")
                    break
                }
            }
            result
        }
    } catch (_: Exception) {
        null
    }

    fun clearMemoryCache() = memoryCache.clear()
}
