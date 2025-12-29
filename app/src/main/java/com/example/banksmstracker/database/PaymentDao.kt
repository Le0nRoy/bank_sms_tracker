package com.example.banksmstracker.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PaymentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Query("SELECT * FROM payments ORDER BY id DESC")
    suspend fun getAllPayments(): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE categoryName = :categoryName ORDER BY id DESC")
    suspend fun getPaymentsByCategory(categoryName: String): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE categoryName IS NULL ORDER BY id DESC")
    suspend fun getUncategorizedPayments(): List<PaymentEntity>
}
