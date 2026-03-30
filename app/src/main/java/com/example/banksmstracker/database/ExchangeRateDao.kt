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
}
