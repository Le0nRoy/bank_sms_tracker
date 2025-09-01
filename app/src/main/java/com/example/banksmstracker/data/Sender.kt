package com.example.banksmstracker.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Sender(
    val name: String,
    val address: String,
    val rules: List<PaymentRegexRule>,
)
