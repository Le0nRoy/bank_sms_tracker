package com.example.banksmstracker.data

import kotlinx.serialization.Serializable

@Serializable
data class Income(
    val id: Long? = null,
    val amount: Double,
    val currency: String,
    val source: String?,
    val timestamp: String?,
    val balance: Double?,
    val senderAddress: String? = null,
    val receivedAt: Long? = null,
    val ruleId: Long? = null
)
