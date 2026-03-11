package com.example.banksmstracker.data

import kotlinx.serialization.Serializable

@Serializable
enum class RuleType(val value: String) {
    PAYMENT("payment"),
    IGNORE("ignore"),
    INCOME("income");

    companion object {
        fun fromValue(value: String): RuleType = entries.find { it.value == value } ?: PAYMENT
    }
}
