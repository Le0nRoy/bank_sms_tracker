package com.example.banksmstracker.repository

import com.example.banksmstracker.data.Payment

interface PaymentRepository {
    suspend fun savePayment(payment: Payment, rawMessage: String, senderAddress: String): Boolean
    suspend fun getAllPayments(): List<Payment>
    suspend fun getPaymentsByCategory(categoryId: String): List<Payment>
    suspend fun getUncategorizedPayments(): List<Payment>
    suspend fun getPaymentsBySender(senderAddress: String): List<Payment>
    suspend fun getPaymentsByDateRange(startTime: Long, endTime: Long): List<Payment>
    suspend fun getDistinctSenderAddresses(): List<String>
    suspend fun updatePaymentCategory(paymentId: Long, categoryName: String?)
    suspend fun getPaymentsByRule(ruleId: Long): List<Payment>
    suspend fun updateCategoryForRule(ruleId: Long, categoryName: String?)
}

// For testing purposes
class InMemoryPaymentRepository : PaymentRepository {
    private val payments = mutableListOf<Payment>()
    private val hashes = mutableSetOf<String>()
    private var nextId = 1L

    override suspend fun savePayment(payment: Payment, rawMessage: String, senderAddress: String): Boolean {
        val hash = computeHash(rawMessage, senderAddress)
        if (!hashes.add(hash)) {
            return false
        }
        val paymentWithMetadata = payment.copy(
            id = nextId++,
            senderAddress = senderAddress,
            receivedAt = System.currentTimeMillis()
        )
        payments.add(paymentWithMetadata)
        return true
    }

    override suspend fun getAllPayments(): List<Payment> = payments.toList()

    override suspend fun getPaymentsByCategory(categoryId: String): List<Payment> = payments.filter {
        it.categoryId == categoryId
    }

    override suspend fun getUncategorizedPayments(): List<Payment> = payments.filter { it.categoryId == null }

    override suspend fun getPaymentsBySender(senderAddress: String): List<Payment> = payments.filter {
        it.senderAddress == senderAddress
    }

    override suspend fun getPaymentsByDateRange(startTime: Long, endTime: Long): List<Payment> = payments.filter {
        val receivedAt = it.receivedAt ?: return@filter false
        receivedAt in startTime..endTime
    }

    override suspend fun getDistinctSenderAddresses(): List<String> =
        payments.mapNotNull { it.senderAddress }.distinct().sorted()

    override suspend fun updatePaymentCategory(paymentId: Long, categoryName: String?) {
        val index = payments.indexOfFirst { it.id == paymentId }
        if (index >= 0) {
            payments[index] = payments[index].copy(categoryId = categoryName)
        }
    }

    override suspend fun getPaymentsByRule(ruleId: Long): List<Payment> =
        payments.filter { it.ruleId == ruleId }

    override suspend fun updateCategoryForRule(ruleId: Long, categoryName: String?) {
        payments.replaceAll { payment ->
            if (payment.ruleId == ruleId) {
                payment.copy(categoryId = categoryName)
            } else {
                payment
            }
        }
    }

    private fun computeHash(message: String, sender: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val combined = "$sender::$message"
        val hash = digest.digest(combined.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun clear() {
        payments.clear()
        hashes.clear()
        nextId = 1L
    }
}
