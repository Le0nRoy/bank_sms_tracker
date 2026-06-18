package com.example.banksmstracker.data

data class PaymentRegexRule(
    val regex: Regex,
    val category: String? = null
)
