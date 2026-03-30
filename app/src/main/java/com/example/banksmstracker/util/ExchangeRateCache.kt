package com.example.banksmstracker.util

import com.example.banksmstracker.database.ExchangeRateDao
import com.example.banksmstracker.database.ExchangeRateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exchange-rate cache for USD→GEL conversion.
 *
 * Lookup order:
 *   1. In-memory map (fastest — avoids DB round-trip within the same session)
 *   2. Room [ExchangeRateDao] (survives app restarts)
 *   3. National Bank of Georgia REST API (network, only when both caches miss)
 *
 * API: https://nbg.gov.ge/gw/api/ct/monetarypolicy/currities/en/json/?currency=USD&date=YYYY-MM-DD
 */
object ExchangeRateCache {

    private val memoryCache = mutableMapOf<String, Double>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Get USD→GEL rate for the given epoch-milliseconds date.
     * Returns null if unavailable.
     */
    suspend fun getUsdToGelRate(dateMs: Long, dao: ExchangeRateDao): Double? =
        getUsdToGelRateForDate(dateFormat.format(Date(dateMs)), dao)

    /**
     * Get USD→GEL rate for a "yyyy-MM-dd" date string.
     * Checks memory, then DB, then network. Persists to DB on a successful network fetch.
     */
    suspend fun getUsdToGelRateForDate(dateStr: String, dao: ExchangeRateDao): Double? {
        // 1. Memory
        memoryCache[dateStr]?.let { return it }

        // 2. DB
        val dbEntity = withContext(Dispatchers.IO) { dao.getRate(dateStr, "USD") }
        if (dbEntity != null) {
            memoryCache[dateStr] = dbEntity.rateToGel
            return dbEntity.rateToGel
        }

        // 3. Network
        val rate = fetchFromNetwork(dateStr) ?: return null
        memoryCache[dateStr] = rate
        withContext(Dispatchers.IO) { dao.insertRate(ExchangeRateEntity(dateStr, "USD", rate)) }
        return rate
    }

    private suspend fun fetchFromNetwork(dateStr: String): Double? = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://nbg.gov.ge/gw/api/ct/monetarypolicy/currities/en/json/" +
                    "?currency=USD&date=$dateStr"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.requestMethod = "GET"
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                parseRate(conn.inputStream.bufferedReader().readText())
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRate(json: String): Double? {
        return try {
            val array = JSONArray(json)
            if (array.length() == 0) {
                null
            } else {
                val currencies = array.getJSONObject(0).getJSONArray("currencies")
                var result: Double? = null
                for (i in 0 until currencies.length()) {
                    val entry = currencies.getJSONObject(i)
                    if (entry.getString("code") == "USD") {
                        result = entry.getDouble("rate")
                        break
                    }
                }
                result
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clearMemoryCache() = memoryCache.clear()
}
