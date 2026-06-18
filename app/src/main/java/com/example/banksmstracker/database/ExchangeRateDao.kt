package com.example.banksmstracker.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ExchangeRateDao {
    @Query("SELECT * FROM exchange_rates WHERE date = :date AND currency = :currency LIMIT 1")
    suspend fun getRate(date: String, currency: String): ExchangeRateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRate(rate: ExchangeRateEntity)

    @Query("SELECT * FROM exchange_rates ORDER BY date DESC, currency ASC")
    suspend fun getAll(): List<ExchangeRateEntity>

    @Query(
        "SELECT * FROM exchange_rates WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC, currency ASC"
    )
    suspend fun getByDateRange(startDate: String, endDate: String): List<ExchangeRateEntity>

    @Query("SELECT * FROM exchange_rates WHERE currency IN (:currencies) ORDER BY date DESC, currency ASC")
    suspend fun getByCurrencies(currencies: List<String>): List<ExchangeRateEntity>

    @Query("DELETE FROM exchange_rates WHERE date = :date AND currency = :currency")
    suspend fun deleteRate(date: String, currency: String)

    @Query("SELECT DISTINCT currency FROM exchange_rates ORDER BY currency ASC")
    suspend fun getAvailableCurrencies(): List<String>

    @Query("SELECT DISTINCT date FROM exchange_rates WHERE currency = :currency ORDER BY date DESC")
    suspend fun getDatesForCurrency(currency: String): List<String>
}
