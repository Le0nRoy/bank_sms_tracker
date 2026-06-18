package com.example.banksmstracker.data

/**
 * Represents the result of processing an SMS message.
 * Used by PaymentProcessor to indicate what action was taken.
 */
sealed class MessageProcessResult {
    /**
     * Message was parsed as a payment transaction.
     */
    data class PaymentResult(val payment: Payment) : MessageProcessResult()

    /**
     * Message was parsed as an income transaction.
     */
    data class IncomeResult(val income: Income) : MessageProcessResult()

    /**
     * Message matched an ignore rule and should be skipped.
     */
    data class Ignored(val ruleName: String? = null) : MessageProcessResult()
}
