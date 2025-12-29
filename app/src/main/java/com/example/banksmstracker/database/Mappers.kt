package com.example.banksmstracker.database

import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.PaymentRegexRule
import com.example.banksmstracker.data.Sender

fun List<CategoryWithMerchants>.toDomainCategories(): List<Category> = map { categoryWithMerchants ->
    Category(
        id = categoryWithMerchants.category.id,
        name = categoryWithMerchants.category.name,
        merchants = categoryWithMerchants.merchants
            .map { it.name }
            .toMutableList()
    )
}

fun List<SenderWithDetails>.toDomainSenders(): List<Sender> = map { senderWithDetails ->
    Sender(
        id = senderWithDetails.sender.id,
        name = senderWithDetails.sender.name,
        addresses = senderWithDetails.addresses
            .map { it.address }
            .toMutableList(),
        rules = senderWithDetails.rules
            .map { rule -> PaymentRegexRule(id = rule.id, regex = rule.regex) }
            .toMutableList()
    )
}
