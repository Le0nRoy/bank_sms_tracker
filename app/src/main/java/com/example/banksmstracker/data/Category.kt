package com.example.banksmstracker.data

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    var id: Long? = null,
    var name: String,
    var merchants: MutableList<String>,
    var enabled: Boolean = true
)
