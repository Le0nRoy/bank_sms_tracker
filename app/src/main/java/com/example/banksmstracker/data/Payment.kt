package com.example.banksmstracker.data

import java.time.LocalDate

data class Payment(
    val sender: String,
    val amount: Double,
    val receiver: String,
    val date: LocalDate,
    val category: String? = null
)
