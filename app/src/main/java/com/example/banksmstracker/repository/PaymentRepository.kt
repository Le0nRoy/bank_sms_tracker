package com.example.banksmstracker.repository

import com.example.banksmstracker.data.Payment

interface PaymentRepository {
    fun savePayment(payment: Payment, rawMessage: String, senderAddress: String): Boolean
    fun getAllPayments(): List<Payment>
    fun getPaymentsByCategory(categoryId: String): List<Payment>
    fun getUncategorizedPayments(): List<Payment>
}

// FIXME: Replace with actual database implementation
class InMemoryPaymentRepository : PaymentRepository {
    private val payments = mutableListOf<Payment>()
    private val hashes = mutableSetOf<String>()

    override fun savePayment(payment: Payment, rawMessage: String, senderAddress: String): Boolean {
        val hash = computeHash(rawMessage, senderAddress)
        if (!hashes.add(hash)) {
            return false
        }
        payments.add(payment)
        return true
    }

    override fun getAllPayments(): List<Payment> {
        return payments.toList()
    }
    
    override fun getPaymentsByCategory(categoryId: String): List<Payment> {
        return payments.filter { it.categoryId == categoryId }
    }
    
    override fun getUncategorizedPayments(): List<Payment> {
        return payments.filter { it.categoryId == null }
    }

    private fun computeHash(message: String, sender: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val combined = "$sender::$message"
        val hash = digest.digest(combined.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
