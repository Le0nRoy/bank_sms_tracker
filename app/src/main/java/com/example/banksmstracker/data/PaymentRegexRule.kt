package com.example.banksmstracker.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PaymentRegexRule(
    val regex: String,
) {
    val regexPattern: Regex
        get() = regex.toRegex()
}
