package com.example.banksmstracker.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IncomeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIncome(income: IncomeEntity): Long

    @Query("SELECT * FROM incomes ORDER BY id DESC")
    suspend fun getAllIncomes(): List<IncomeEntity>
}
