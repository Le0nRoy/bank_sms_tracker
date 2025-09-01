package com.example.banksmstracker.processor

import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.repository.PaymentRepository
import android.util.Log

class PaymentProcessor(
    private val categories: List<Category>,
    private val paymentRepository: PaymentRepository
) {
    companion object {
        private const val TAG = "PaymentProcessor"
    }
    
    fun processPayment(payment: Payment): Payment {
        val categorizedPayment = assignCategory(payment)
        paymentRepository.savePayment(categorizedPayment)
        return categorizedPayment
    }
    
    private fun assignCategory(payment: Payment): Payment {
        val merchant = payment.merchant ?: return payment
        
        val category = categories.find { category ->
            category.merchants.any { it.equals(merchant, ignoreCase = true) }
        }
        
        return if (category != null) {
            payment.copy(categoryId = category.name)
        } else {
            Log.w(TAG, "No category found for merchant: $merchant")
            payment
        }
    }
    
    fun getUncategorizedPayments(): List<Payment> {
        return paymentRepository.getUncategorizedPayments()
    }
}
