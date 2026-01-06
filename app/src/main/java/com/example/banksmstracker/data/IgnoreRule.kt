package com.example.banksmstracker.data

import kotlinx.serialization.Serializable

@Serializable
data class IgnoreRule(
    var id: Long? = null,
    var pattern: String,
    var description: String? = null,
    var enabled: Boolean = true
)
