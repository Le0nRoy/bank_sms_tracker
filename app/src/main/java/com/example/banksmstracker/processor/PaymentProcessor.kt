package com.example.banksmstracker.processor

import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.repository.PaymentRepository
import com.example.banksmstracker.data.PaymentRegexRule

class UnparsedMessageException(message: String) : Exception("Cannot parse message: $message")

class PaymentProcessor(
    private val rules: List<PaymentRegexRule>,
    private val categories: List<Category>,
    val paymentRepository: PaymentRepository
) {
    companion object {
        private const val TAG = "PaymentProcessor"
    }

    fun getPaymentFromMessage(message: String): Payment? {
        for (rule in rules) {
            val pattern = rule.regexPattern
            val match = pattern.find(message) ?: continue

            val amount    = match.groupValues[1].toDoubleOrNull()
            val currency  = match.groupValues[2]
            val card      = match.groupValues[3]
            val merchant  = match.groupValues[4]
            val timestamp = match.groupValues[5]
            val balance   = match.groupValues[6].toDoubleOrNull()

            if (amount != null) {
                return Payment(
                    amount = amount,
                    currency = currency,
                    card = card,
                    merchant = merchant,
                    timestamp = timestamp,
                    balance = balance,
                    categoryId = null
                )
            }
        }

        throw UnparsedMessageException(message)
    }

    fun processMessage(message: String): Payment {
        val payment = this.getPaymentFromMessage(message)
        val categorizedPayment = assignCategory(payment!!)
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
            payment
        }
    }
}
