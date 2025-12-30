package com.example.banksmstracker.repository

import com.example.banksmstracker.data.Payment

interface PaymentRepository {
    suspend fun savePayment(payment: Payment, rawMessage: String, senderAddress: String): Boolean
    suspend fun getAllPayments(): List<Payment>
    suspend fun getPaymentsByCategory(categoryId: String): List<Payment>
    suspend fun getUncategorizedPayments(): List<Payment>
}

// For testing purposes
class InMemoryPaymentRepository : PaymentRepository {
    private val payments = mutableListOf<Payment>()
    private val hashes = mutableSetOf<String>()

    override suspend fun savePayment(payment: Payment, rawMessage: String, senderAddress: String): Boolean {
        val hash = computeHash(rawMessage, senderAddress)
        if (!hashes.add(hash)) {
            return false
        }
        payments.add(payment)
        return true
    }

    override suspend fun getAllPayments(): List<Payment> = payments.toList()

    override suspend fun getPaymentsByCategory(categoryId: String): List<Payment> = payments.filter {
        it.categoryId == categoryId
    }

    override suspend fun getUncategorizedPayments(): List<Payment> = payments.filter { it.categoryId == null }

    private fun computeHash(message: String, sender: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val combined = "$sender::$message"
        val hash = digest.digest(combined.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun clear() {
        payments.clear()
        hashes.clear()
    }
}
