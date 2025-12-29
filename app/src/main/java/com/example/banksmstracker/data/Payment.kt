package com.example.banksmstracker.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Payment(
    val id: Long? = null,
    val amount: Double,
    val currency: String,
    val card: String?,
    val merchant: String?,
    val timestamp: String?,
    val balance: Double?,
    val categoryId: String? = null
)
