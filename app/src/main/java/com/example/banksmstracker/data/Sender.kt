package com.example.banksmstracker.data

import kotlinx.serialization.Serializable

@Serializable
data class Sender(
    var id: Long? = null,
    var name: String,
    var addresses: MutableList<String>,
    var rules: MutableList<Rule>,
    var enabled: Boolean = true
)
