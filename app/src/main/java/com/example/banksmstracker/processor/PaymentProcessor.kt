package com.example.banksmstracker.processor

import android.util.Log
import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.data.RuleType
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.repository.PaymentRepository

class UnparsedMessageException(message: String) : Exception("Cannot parse message: $message")

class PaymentProcessor(
    private val senders: List<Sender>,
    private val categories: List<Category>,
    val paymentRepository: PaymentRepository,
) {
    companion object {
        private const val TAG = "PaymentProcessor"
    }

    // Filter to only enabled senders and categories
    private val enabledSenders: List<Sender>
        get() = senders.filter { it.enabled }

    private val enabledCategories: List<Category>
        get() = categories.filter { it.enabled }

    fun getPaymentFromMessage(message: String, address: String): Payment? {
        val sender = enabledSenders.find { sender ->
            sender.addresses.any { it.equals(address, ignoreCase = true) }
        } ?: throw UnparsedMessageException("No sender found for address: $address")

        // Only use enabled payment rules
        val paymentRules = sender.rules.filter { it.enabled && it.ruleType == RuleType.PAYMENT }

        for (rule in paymentRules) {
            val pattern = rule.regexPattern
            val match = pattern.find(message) ?: continue

            // Validate that regex has at least 6 capture groups
            if (match.groupValues.size < 7) {
                Log.w(TAG, "Regex rule has insufficient groups (${match.groupValues.size - 1}), need 6")
                continue
            }

            val amount = match.groupValues.getOrNull(1)?.toDoubleOrNull()
            val currency = match.groupValues.getOrNull(2) ?: ""
            val card = match.groupValues.getOrNull(3)
            val merchant = match.groupValues.getOrNull(4)
            val timestamp = match.groupValues.getOrNull(5)
            val balance = match.groupValues.getOrNull(6)?.toDoubleOrNull()

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

    suspend fun processMessage(message: String, address: String): Payment {
        val payment = this.getPaymentFromMessage(message, address)
            ?: throw UnparsedMessageException("Failed to parse message")
        val categorizedPayment = assignCategory(payment)
        val inserted = paymentRepository.savePayment(categorizedPayment, message, address)
        if (!inserted) {
            Log.d(TAG, "Duplicate payment skipped for sender $address")
        }
        return categorizedPayment
    }

    private fun assignCategory(payment: Payment): Payment {
        val merchant = payment.merchant ?: return payment

        // Only match against enabled categories
        val category = enabledCategories.find { category ->
            category.merchants.any { it.equals(merchant, ignoreCase = true) }
        }

        return if (category != null) {
            payment.copy(categoryId = category.name)
        } else {
            payment
        }
    }
}
