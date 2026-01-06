package com.example.banksmstracker.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true
)

@Entity(
    tableName = "category_merchants",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryId"])]
)
data class CategoryMerchantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String
)

data class CategoryWithMerchants(
    @Embedded val category: CategoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "categoryId",
        entity = CategoryMerchantEntity::class
    )
    val merchants: List<CategoryMerchantEntity>
)

@Entity(tableName = "senders")
data class SenderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true
)

@Entity(
    tableName = "sender_addresses",
    foreignKeys = [
        ForeignKey(
            entity = SenderEntity::class,
            parentColumns = ["id"],
            childColumns = ["senderId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["senderId"])]
)
data class SenderAddressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderId: Long,
    val address: String
)

@Entity(
    tableName = "sender_rules",
    foreignKeys = [
        ForeignKey(
            entity = SenderEntity::class,
            parentColumns = ["id"],
            childColumns = ["senderId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["senderId"])]
)
data class SenderRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderId: Long,
    val regex: String,
    val enabled: Boolean = true
)

data class SenderWithDetails(
    @Embedded val sender: SenderEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "senderId",
        entity = SenderAddressEntity::class
    )
    val addresses: List<SenderAddressEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "senderId",
        entity = SenderRuleEntity::class
    )
    val rules: List<SenderRuleEntity>
)

@Entity(
    tableName = "payments",
    indices = [Index(value = ["messageHash"], unique = true)]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val currency: String,
    val card: String?,
    val merchant: String?,
    val timestamp: String?,
    val balance: Double?,
    val categoryName: String?,
    val messageHash: String,
    val senderAddress: String? = null,
    val receivedAt: Long? = null,
    val ruleId: Long? = null
)

@Entity(tableName = "ignore_rules")
data class IgnoreRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val description: String? = null,
    val enabled: Boolean = true
)
