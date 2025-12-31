package com.example.banksmstracker.repository

import android.database.sqlite.SQLiteConstraintException
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.database.PaymentDao
import com.example.banksmstracker.database.PaymentEntity
import java.security.MessageDigest

class RoomPaymentRepository(private val paymentDao: PaymentDao) : PaymentRepository {

    override suspend fun savePayment(payment: Payment, rawMessage: String, senderAddress: String): Boolean {
        val hash = computeHash(rawMessage, senderAddress)
        return try {
            paymentDao.insertPayment(
                PaymentEntity(
                    amount = payment.amount,
                    currency = payment.currency,
                    card = payment.card,
                    merchant = payment.merchant,
                    timestamp = payment.timestamp,
                    balance = payment.balance,
                    categoryName = payment.categoryId,
                    messageHash = hash,
                    senderAddress = senderAddress,
                    receivedAt = System.currentTimeMillis()
                )
            )
            true
        } catch (e: SQLiteConstraintException) {
            false
        }
    }

    override suspend fun getAllPayments(): List<Payment> {
        return paymentDao.getAllPayments().map { it.toDomain() }
    }

    override suspend fun getPaymentsByCategory(categoryId: String): List<Payment> {
        return paymentDao.getPaymentsByCategory(categoryId).map { it.toDomain() }
    }

    override suspend fun getUncategorizedPayments(): List<Payment> {
        return paymentDao.getUncategorizedPayments().map { it.toDomain() }
    }

    override suspend fun getPaymentsBySender(senderAddress: String): List<Payment> {
        return paymentDao.getPaymentsBySender(senderAddress).map { it.toDomain() }
    }

    override suspend fun getPaymentsByDateRange(startTime: Long, endTime: Long): List<Payment> {
        return paymentDao.getPaymentsByDateRange(startTime, endTime).map { it.toDomain() }
    }

    override suspend fun getDistinctSenderAddresses(): List<String> {
        return paymentDao.getDistinctSenderAddresses()
    }

    override suspend fun updatePaymentCategory(paymentId: Long, categoryName: String?) {
        paymentDao.updatePaymentCategory(paymentId, categoryName)
    }

    override suspend fun getPaymentsByRule(ruleId: Long): List<Payment> {
        return paymentDao.getPaymentsByRule(ruleId).map { it.toDomain() }
    }

    override suspend fun updateCategoryForRule(ruleId: Long, categoryName: String?) {
        paymentDao.updateCategoryForRule(ruleId, categoryName)
    }

    private fun computeHash(message: String, sender: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val combined = "$sender::$message"
        val hash = digest.digest(combined.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}

private fun PaymentEntity.toDomain(): Payment = Payment(
    id = id,
    amount = amount,
    currency = currency,
    card = card,
    merchant = merchant,
    timestamp = timestamp,
    balance = balance,
    categoryId = categoryName,
    senderAddress = senderAddress,
    receivedAt = receivedAt,
    ruleId = ruleId
)
