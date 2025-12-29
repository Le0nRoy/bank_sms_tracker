package com.example.banksmstracker.repository

import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.database.PaymentDao
import com.example.banksmstracker.database.PaymentEntity
import android.database.sqlite.SQLiteConstraintException
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking

class RoomPaymentRepository(
    private val paymentDao: PaymentDao
) : PaymentRepository {

    override fun savePayment(payment: Payment, rawMessage: String, senderAddress: String): Boolean {
        val hash = computeHash(rawMessage, senderAddress)
        return runBlocking {
            try {
                paymentDao.insertPayment(
                    PaymentEntity(
                        amount = payment.amount,
                        currency = payment.currency,
                        card = payment.card,
                        merchant = payment.merchant,
                        timestamp = payment.timestamp,
                        balance = payment.balance,
                        categoryName = payment.categoryId,
                        messageHash = hash
                    )
                )
                true
            } catch (e: SQLiteConstraintException) {
                false
            }
        }
    }

    override fun getAllPayments(): List<Payment> = runBlocking {
        paymentDao.getAllPayments().map { it.toDomain() }
    }

    override fun getPaymentsByCategory(categoryId: String): List<Payment> = runBlocking {
        paymentDao.getPaymentsByCategory(categoryId).map { it.toDomain() }
    }

    override fun getUncategorizedPayments(): List<Payment> = runBlocking {
        paymentDao.getUncategorizedPayments().map { it.toDomain() }
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
    categoryId = categoryName
)
