package com.example.banksmstracker.parser

import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.data.PaymentRegexRule
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.text.get

class PaymentParser(private val rules: List<PaymentRegexRule>) {
    fun parse(message: String): Payment? {
        for (rule in rules) {
            val match = rule.regex.find(message) ?: continue

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
                    category = rule.category
                )
            }
        }

        throw UnparsedMessageException(message)
    }
}

class UnparsedMessageException(message: String) : Exception("Cannot parse message: $message")