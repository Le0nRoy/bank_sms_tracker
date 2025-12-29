package com.example.banksmstracker.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Category(
    var id: Long? = null,
    var name: String,
    var merchants: MutableList<String>,
)
