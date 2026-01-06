package com.example.banksmstracker.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface IncomeDao {
    @Query("SELECT * FROM incomes ORDER BY receivedAt DESC")
    suspend fun getAllIncomes(): List<IncomeEntity>

    @Query("SELECT * FROM incomes WHERE receivedAt BETWEEN :startDate AND :endDate ORDER BY receivedAt DESC")
    suspend fun getIncomesByDateRange(startDate: Long, endDate: Long): List<IncomeEntity>

    @Query("SELECT * FROM incomes WHERE senderAddress = :senderAddress ORDER BY receivedAt DESC")
    suspend fun getIncomesBySender(senderAddress: String): List<IncomeEntity>

    @Query("SELECT DISTINCT senderAddress FROM incomes WHERE senderAddress IS NOT NULL")
    suspend fun getDistinctSenderAddresses(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIncome(income: IncomeEntity): Long

    @Update
    suspend fun updateIncome(income: IncomeEntity)

    @Query("DELETE FROM incomes WHERE id = :id")
    suspend fun deleteIncomeById(id: Long)

    @Query("SELECT COUNT(*) FROM incomes")
    suspend fun getIncomeCount(): Int

    @Query("SELECT SUM(amount) FROM incomes WHERE receivedAt BETWEEN :startDate AND :endDate")
    suspend fun getTotalIncomeByDateRange(startDate: Long, endDate: Long): Double?
}
