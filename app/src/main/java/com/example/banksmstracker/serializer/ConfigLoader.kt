package com.example.banksmstracker.serializer

import com.example.banksmstracker.data.SmsConfig
import com.example.banksmstracker.processor.PaymentProcessor
import com.example.banksmstracker.repository.InMemoryPaymentRepository
import kotlinx.serialization.json.Json

fun SmsConfig.validate(): List<String> {
    val errors = mutableListOf<String>()

    // duplicate category names
    val dupCats = categories.groupBy { it.name }.filterValues { it.size > 1 }
    if (dupCats.isNotEmpty()) {
        errors.add("Duplicate category names: ${dupCats.keys}")
    }

    // duplicate patterns inside sender
    senders.forEach { sender ->
        val dupPatterns = sender.rules.groupBy { it.pattern }.filterValues { it.size > 1 }
        if (dupPatterns.isNotEmpty()) {
            errors.add("Sender ${sender.name} has duplicate patterns: ${dupPatterns.keys}")
        }
    }

    // merchant in multiple different categories (duplicates within same category are allowed)
    val merchantMap = categories.flatMap { c ->
        c.merchants.map { it to c.name }
    }.groupBy({ it.first }, { it.second })
    merchantMap.filter { it.value.distinct().size > 1 }.forEach {
        errors.add("Merchant ${it.key} in multiple categories: ${it.value.distinct()}")
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
        val repository = InMemoryPaymentRepository()
        return PaymentProcessor(config.senders, config.categories, repository)
    }
}
