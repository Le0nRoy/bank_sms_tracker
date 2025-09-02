package com.example.banksmstracker.serializer

import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.processor.PaymentProcessor
import com.example.banksmstracker.repository.InMemoryPaymentRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SmsConfig(
    val senders: List<Sender>,
    val categories: List<Category>
)

fun SmsConfig.validate(): List<String> {
    val errors = mutableListOf<String>()

    // duplicate category names
    val dupCats = categories.groupBy { it.name }.filterValues { it.size > 1 }
    if (dupCats.isNotEmpty()) {
        errors.add("Duplicate category names: ${dupCats.keys}")
    }

    // duplicate regex inside sender
    senders.forEach { sender ->
        val dupRegex = sender.rules.groupBy { it.regex }.filterValues { it.size > 1 }
        if (dupRegex.isNotEmpty()) {
            errors.add("Sender ${sender.name} has duplicate regex: ${dupRegex.keys}")
        }
    }

    // merchant in multiple categories
    // FIXME need better explanation of what happens here
    val merchantMap = categories.flatMap { c ->
        c.merchants.map { it to c.name }
    }.groupBy({ it.first }, { it.second })
    merchantMap.filter { it.value.size > 1 }.forEach {
        errors.add("Merchant ${it.key} in multiple categories: ${it.value}")
    }

    return errors
}

object ConfigLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(jsonString: String): SmsConfig {
        val config = json.decodeFromString(SmsConfig.serializer(), jsonString)
        val errors = config.validate()
        if (errors.isNotEmpty()) {
            throw IllegalArgumentException("Configuration validation failed: ${errors.joinToString(", ")}")
        }
        return config
    }

    fun createPaymentProcessor(config: SmsConfig): PaymentProcessor {
        val allRules = config.senders.flatMap { it.rules }
        val repository = InMemoryPaymentRepository()
        return PaymentProcessor(allRules, config.categories, repository)
    }
}
