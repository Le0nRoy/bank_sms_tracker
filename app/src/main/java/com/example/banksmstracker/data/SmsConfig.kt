package com.example.banksmstracker.data

import kotlinx.serialization.Serializable

@Serializable
data class SmsConfig(
    val senders: MutableList<Sender>,
    val categories: MutableList<Category>
)
