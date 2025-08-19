package com.example.banksmstracker.parser

import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.data.PaymentRegexRule
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.text.get

class PaymentParser(
    private val rules: List<PaymentRegexRule>,
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
) {

    fun parse(sender: String, message: String): Payment {
        for (rule in rules) {
            val match = rule.regex.matchEntire(message) ?: continue

            val groups = match.groups

            val amount = groups["paymentAmount"]?.value?.toDoubleOrNull()
                ?: throw IllegalArgumentException("paymentAmount group missing or invalid")
            val receiver = groups["paymentReceiver"]?.value
                ?: throw IllegalArgumentException("paymentReceiver group missing")
            val date = groups["paymentDate"]?.value?.let { LocalDate.parse(it, dateFormatter) }
                ?: LocalDate.now()

            return Payment(sender, amount, receiver, date, rule.category)
        }

        throw UnparsedMessageException(message)
    }
}

class UnparsedMessageException(message: String) : Exception("Cannot parse message: $message")