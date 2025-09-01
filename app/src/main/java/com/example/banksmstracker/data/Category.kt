package com.example.banksmstracker.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Category(
    val name: String,
    val merchants: List<String>,
)
