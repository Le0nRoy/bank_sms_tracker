package com.example.banksmstracker.repository

import android.database.sqlite.SQLiteConstraintException
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.database.PaymentDao
import com.example.banksmstracker.database.PaymentEntity
import com.example.banksmstracker.util.HashUtil

class RoomPaymentRepository(private val paymentDao: PaymentDao) : PaymentRepository {

    override suspend fun savePayment(payment: Payment, rawMessage: String, senderAddress: String): Boolean {
        val hash = HashUtil.computeMessageHash(rawMessage, senderAddress)
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

    override suspend fun getAllPayments(): List<Payment> = paymentDao.getAllPayments().map { it.toDomain() }

    override suspend fun getPaymentsByCategory(categoryId: String): List<Payment> =
        paymentDao.getPaymentsByCategory(categoryId).map {
            it.toDomain()
        }

    override suspend fun getUncategorizedPayments(): List<Payment> = paymentDao.getUncategorizedPayments().map {
        it.toDomain()
    }

    override suspend fun getPaymentsBySender(senderAddress: String): List<Payment> =
        paymentDao.getPaymentsBySender(senderAddress).map {
            it.toDomain()
        }

    override suspend fun getPaymentsByDateRange(startTime: Long, endTime: Long): List<Payment> =
        paymentDao.getPaymentsByDateRange(startTime, endTime).map {
            it.toDomain()
        }

    override suspend fun getDistinctSenderAddresses(): List<String> = paymentDao.getDistinctSenderAddresses()

    override suspend fun updatePaymentCategory(paymentId: Long, categoryName: String?) {
        paymentDao.updatePaymentCategory(paymentId, categoryName)
    }

    override suspend fun getPaymentsByRule(ruleId: Long): List<Payment> = paymentDao.getPaymentsByRule(ruleId).map {
        it.toDomain()
    }

    override suspend fun updateCategoryForRule(ruleId: Long, categoryName: String?) {
        paymentDao.updateCategoryForRule(ruleId, categoryName)
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
