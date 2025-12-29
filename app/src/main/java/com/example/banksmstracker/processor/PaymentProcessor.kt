package com.example.banksmstracker.processor

import android.util.Log
import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.repository.PaymentRepository

class UnparsedMessageException(message: String) : Exception("Cannot parse message: $message")

class PaymentProcessor(
    private val senders: List<Sender>,
    private val categories: List<Category>,
    val paymentRepository: PaymentRepository
) {
    companion object {
        private const val TAG = "PaymentProcessor"
    }

    fun getPaymentFromMessage(message: String, address: String): Payment? {
        val sender = senders.find { sender -> sender.addresses.any { it.equals(address, ignoreCase = true) } }
            ?: throw UnparsedMessageException("No sender found for address: $address")

        for (rule in sender.rules) {
            val pattern = rule.regexPattern
            val match = pattern.find(message) ?: continue

            val amount = match.groupValues[1].toDoubleOrNull()
            val currency = match.groupValues[2]
            val card = match.groupValues[3]
            val merchant = match.groupValues[4]
            val timestamp = match.groupValues[5]
            val balance = match.groupValues[6].toDoubleOrNull()

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

    fun processMessage(message: String, address: String): Payment {
        val payment = this.getPaymentFromMessage(message, address)
        val categorizedPayment = assignCategory(payment!!)
        val inserted = paymentRepository.savePayment(categorizedPayment, message, address)
        if (!inserted) {
            Log.d(TAG, "Duplicate payment skipped for sender $address")
        }
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
