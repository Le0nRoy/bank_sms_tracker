package com.example.banksmstracker.repository

import com.example.banksmstracker.data.Payment

interface PaymentRepository {
    fun savePayment(payment: Payment)
    fun getAllPayments(): List<Payment>
    fun getPaymentsByCategory(categoryId: String): List<Payment>
    fun getUncategorizedPayments(): List<Payment>
}

// FIXME: Replace with actual database implementation
class InMemoryPaymentRepository : PaymentRepository {
    private val payments = mutableListOf<Payment>()
    
    override fun savePayment(payment: Payment) {
        payments.add(payment)
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
}
