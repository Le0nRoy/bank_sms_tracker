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

    @Query("SELECT * FROM payments WHERE senderAddress = :senderAddress ORDER BY id DESC")
    suspend fun getPaymentsBySender(senderAddress: String): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE receivedAt >= :startTime AND receivedAt <= :endTime ORDER BY id DESC")
    suspend fun getPaymentsByDateRange(startTime: Long, endTime: Long): List<PaymentEntity>

    @Query("SELECT DISTINCT senderAddress FROM payments WHERE senderAddress IS NOT NULL ORDER BY senderAddress")
    suspend fun getDistinctSenderAddresses(): List<String>

    @Query("UPDATE payments SET categoryName = :categoryName WHERE id = :paymentId")
    suspend fun updatePaymentCategory(paymentId: Long, categoryName: String?)

    @Query("SELECT * FROM payments WHERE ruleId = :ruleId ORDER BY id DESC")
    suspend fun getPaymentsByRule(ruleId: Long): List<PaymentEntity>

    @Query("UPDATE payments SET categoryName = :categoryName WHERE ruleId = :ruleId")
    suspend fun updateCategoryForRule(ruleId: Long, categoryName: String?)

    @Query("UPDATE payments SET categoryName = :categoryName WHERE merchant = :merchant COLLATE NOCASE")
    suspend fun updateCategoryForMerchant(merchant: String, categoryName: String?)
}
