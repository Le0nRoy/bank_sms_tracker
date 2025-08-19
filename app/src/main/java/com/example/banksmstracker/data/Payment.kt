package com.example.banksmstracker.data

import java.time.LocalDate

data class Payment(
    val amount: Double,
    val currency: String,
    val card: String?,
    val merchant: String?,
    val timestamp: String?,
    val balance: Double?,
    val category: String? = null
)
