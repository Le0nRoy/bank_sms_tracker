package com.example.banksmstracker.data

import kotlinx.serialization.Serializable

@Serializable
data class PaymentRegexRule(
    val regex: String,
) {
    val regexPattern: Regex
        get() = regex.toRegex()
}
