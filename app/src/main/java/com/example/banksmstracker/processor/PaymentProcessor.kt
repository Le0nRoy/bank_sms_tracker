package com.example.banksmstracker.processor

import android.util.Log
import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Income
import com.example.banksmstracker.data.MessageProcessResult
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.data.RuleType
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.repository.PaymentRepository

class UnparsedMessageException(message: String) : Exception("Cannot parse message: $message")

class MessageIgnoredException(val ruleName: String?) : Exception("Message ignored by rule: $ruleName")

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

    /**
     * Process a message following the workflow:
     * 1. Try payment rules -> if match, return Payment
     * 2. Try income rules -> if match, return Income
     * 3. Try ignore rules -> if match, signal message should be ignored
     * 4. If no match, throw UnparsedMessageException
     */
    fun getMessageResult(message: String, address: String): MessageProcessResult {
        val sender = enabledSenders.find { sender ->
            sender.addresses.any { it.equals(address, ignoreCase = true) }
        } ?: throw UnparsedMessageException("No sender found for address: $address")

        // 1. Try payment rules
        val paymentResult = tryPaymentRules(sender, message)
        if (paymentResult != null) {
            return MessageProcessResult.PaymentResult(paymentResult)
        }

        // 2. Try income rules
        val incomeResult = tryIncomeRules(sender, message, address)
        if (incomeResult != null) {
            return MessageProcessResult.IncomeResult(incomeResult)
        }

        // 3. Try ignore rules (no groups expected)
        val ignoreResult = tryIgnoreRules(sender, message)
        if (ignoreResult != null) {
            return ignoreResult
        }

        // 4. No match found
        throw UnparsedMessageException(message)
    }

    private fun tryPaymentRules(sender: Sender, message: String): Payment? {
        val paymentRules = sender.rules.filter { it.enabled && it.ruleType == RuleType.PAYMENT }

        for (rule in paymentRules) {
            val pattern = rule.regexPattern
            val match = pattern.find(message) ?: continue

            // Validate that regex has at least 6 capture groups
            if (match.groupValues.size < 7) {
                Log.w(TAG, "Payment rule has insufficient groups (${match.groupValues.size - 1}), need 6")
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
                    categoryId = null,
                    ruleId = rule.id,
                )
            }
        }
        return null
    }

    private fun tryIncomeRules(sender: Sender, message: String, address: String): Income? {
        val incomeRules = sender.rules.filter { it.enabled && it.ruleType == RuleType.INCOME }

        for (rule in incomeRules) {
            val pattern = rule.regexPattern
            val match = pattern.find(message) ?: continue

            // Income rules also expect 6 groups like payment rules
            if (match.groupValues.size < 7) {
                Log.w(TAG, "Income rule has insufficient groups (${match.groupValues.size - 1}), need 6")
                continue
            }

            val amount = match.groupValues.getOrNull(1)?.toDoubleOrNull()
            val currency = match.groupValues.getOrNull(2) ?: ""
            val source = match.groupValues.getOrNull(4) // source in place of merchant
            val timestamp = match.groupValues.getOrNull(5)
            val balance = match.groupValues.getOrNull(6)?.toDoubleOrNull()

            if (amount != null) {
                return Income(
                    amount = amount,
                    currency = currency,
                    source = source,
                    timestamp = timestamp,
                    balance = balance,
                    senderAddress = address,
                    receivedAt = System.currentTimeMillis(),
                    ruleId = rule.id,
                )
            }
        }
        return null
    }

    private fun tryIgnoreRules(sender: Sender, message: String): MessageProcessResult.Ignored? {
        val ignoreRules = sender.rules.filter { it.enabled && it.ruleType == RuleType.IGNORE }

        for (rule in ignoreRules) {
            val pattern = rule.regexPattern
            // Ignore rules don't expect any groups - just check if the pattern matches
            if (pattern.containsMatchIn(message)) {
                Log.d(TAG, "Message matched ignore rule: ${rule.description ?: rule.pattern}")
                return MessageProcessResult.Ignored(rule.description ?: rule.pattern)
            }
        }
        return null
    }

    /**
     * Legacy method for backward compatibility.
     * Use getMessageResult() for the full workflow.
     */
    fun getPaymentFromMessage(message: String, address: String): Payment? {
        return when (val result = getMessageResult(message, address)) {
            is MessageProcessResult.PaymentResult -> result.payment
            is MessageProcessResult.IncomeResult -> null
            is MessageProcessResult.Ignored -> throw MessageIgnoredException(result.ruleName)
        }
    }

    suspend fun processMessage(message: String, address: String): Payment {
        val result = getMessageResult(message, address)

        return when (result) {
            is MessageProcessResult.PaymentResult -> {
                val categorizedPayment = assignCategory(result.payment)
                val inserted = paymentRepository.savePayment(categorizedPayment, message, address)
                if (!inserted) {
                    Log.d(TAG, "Duplicate payment skipped for sender $address")
                }
                categorizedPayment
            }
            is MessageProcessResult.IncomeResult -> {
                Log.d(TAG, "Income detected from $address: ${result.income}")
                // For now, return a dummy payment to maintain API compatibility
                // Income handling should be done via processMessageFull()
                throw UnparsedMessageException("Income message - use processMessageFull()")
            }
            is MessageProcessResult.Ignored -> {
                Log.d(TAG, "Message ignored by rule: ${result.ruleName}")
                throw MessageIgnoredException(result.ruleName)
            }
        }
    }

    /**
     * Full message processing that handles all result types.
     * Returns the processing result for proper handling by the caller.
     */
    suspend fun processMessageFull(message: String, address: String): MessageProcessResult {
        val result = getMessageResult(message, address)

        return when (result) {
            is MessageProcessResult.PaymentResult -> {
                val categorizedPayment = assignCategory(result.payment)
                val inserted = paymentRepository.savePayment(categorizedPayment, message, address)
                if (!inserted) {
                    Log.d(TAG, "Duplicate payment skipped for sender $address")
                }
                MessageProcessResult.PaymentResult(categorizedPayment)
            }
            is MessageProcessResult.IncomeResult -> {
                Log.d(TAG, "Income detected from $address: ${result.income}")
                // Income saving should be handled by the caller with IncomeDao
                result
            }
            is MessageProcessResult.Ignored -> {
                Log.d(TAG, "Message ignored by rule: ${result.ruleName}")
                result
            }
        }
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
