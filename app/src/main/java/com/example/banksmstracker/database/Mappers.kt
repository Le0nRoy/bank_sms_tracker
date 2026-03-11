package com.example.banksmstracker.database

import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Rule
import com.example.banksmstracker.data.RuleType
import com.example.banksmstracker.data.Sender

fun List<CategoryWithMerchants>.toDomainCategories(): List<Category> = map { categoryWithMerchants ->
    Category(
        id = categoryWithMerchants.category.id,
        name = categoryWithMerchants.category.name,
        merchants = categoryWithMerchants.merchants
            .map { it.name }
            .toMutableList(),
        enabled = categoryWithMerchants.category.enabled
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
            .map { it.toDomainRule() }
            .toMutableList(),
        enabled = senderWithDetails.sender.enabled
    )
}

fun RuleEntity.toDomainRule(): Rule = Rule(
    id = id,
    senderId = senderId,
    pattern = pattern,
    description = description,
    enabled = enabled,
    ruleType = RuleType.fromValue(ruleType)
)

fun Rule.toEntity(): RuleEntity = RuleEntity(
    id = id ?: 0,
    senderId = senderId ?: 0,
    pattern = pattern,
    description = description,
    enabled = enabled,
    ruleType = ruleType.value
)
