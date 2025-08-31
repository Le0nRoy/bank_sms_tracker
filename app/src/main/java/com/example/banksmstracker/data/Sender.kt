package com.example.banksmstracker.data

data class Sender(
    val name: String,
    val address: String,
    val rules: List<PaymentRegexRule>,
)
