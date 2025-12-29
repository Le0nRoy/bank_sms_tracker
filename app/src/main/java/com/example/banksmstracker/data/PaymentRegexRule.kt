package com.example.banksmstracker.data

import kotlinx.serialization.Serializable

@Serializable
data class PaymentRegexRule(var id: Long? = null, var regex: String, var enabled: Boolean = true) {
    val regexPattern: Regex
        get() = regex.toRegex()
}
