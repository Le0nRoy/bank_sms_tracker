package com.example.banksmstracker.data

import kotlinx.serialization.Serializable

@Serializable
data class SmsConfig(
    val senders: List<Sender>,
    val categories: List<Category>
)
