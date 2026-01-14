package com.example.banksmstracker.database

import com.example.banksmstracker.data.Rule
import com.example.banksmstracker.data.RuleType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Mappers Tests")
class MappersTest {

    @Nested
    @DisplayName("toDomainCategories()")
    inner class ToDomainCategoriesTest {

        @Test
        @DisplayName("empty list returns empty list")
        fun `empty list returns empty list`() {
            val entities = emptyList<CategoryWithMerchants>()
            val result = entities.toDomainCategories()
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("single category without merchants maps correctly")
        fun `single category without merchants maps correctly`() {
            val categoryEntity = CategoryEntity(id = 1, name = "Shopping", enabled = true)
            val categoryWithMerchants = CategoryWithMerchants(
                category = categoryEntity,
                merchants = emptyList()
            )
            val result = listOf(categoryWithMerchants).toDomainCategories()

            assertEquals(1, result.size)
            assertEquals(1L, result[0].id)
            assertEquals("Shopping", result[0].name)
            assertTrue(result[0].enabled)
            assertTrue(result[0].merchants.isEmpty())
        }

        @Test
        @DisplayName("single category with merchants maps correctly")
        fun `single category with merchants maps correctly`() {
            val categoryEntity = CategoryEntity(id = 1, name = "Shopping", enabled = true)
            val merchants = listOf(
                CategoryMerchantEntity(id = 1, categoryId = 1, name = "Amazon"),
                CategoryMerchantEntity(id = 2, categoryId = 1, name = "eBay")
            )
            val categoryWithMerchants = CategoryWithMerchants(
                category = categoryEntity,
                merchants = merchants
            )
            val result = listOf(categoryWithMerchants).toDomainCategories()

            assertEquals(1, result.size)
            assertEquals(2, result[0].merchants.size)
            assertTrue(result[0].merchants.contains("Amazon"))
            assertTrue(result[0].merchants.contains("eBay"))
        }

        @Test
        @DisplayName("multiple categories map correctly")
        fun `multiple categories map correctly`() {
            val category1 = CategoryWithMerchants(
                category = CategoryEntity(id = 1, name = "Shopping", enabled = true),
                merchants = listOf(CategoryMerchantEntity(id = 1, categoryId = 1, name = "Amazon"))
            )
            val category2 = CategoryWithMerchants(
                category = CategoryEntity(id = 2, name = "Food", enabled = false),
                merchants = listOf(
                    CategoryMerchantEntity(id = 2, categoryId = 2, name = "McDonalds"),
                    CategoryMerchantEntity(id = 3, categoryId = 2, name = "Subway")
                )
            )
            val result = listOf(category1, category2).toDomainCategories()

            assertEquals(2, result.size)
            assertEquals("Shopping", result[0].name)
            assertTrue(result[0].enabled)
            assertEquals(1, result[0].merchants.size)

            assertEquals("Food", result[1].name)
            assertEquals(false, result[1].enabled)
            assertEquals(2, result[1].merchants.size)
        }

        @Test
        @DisplayName("merchants are converted to MutableList")
        fun `merchants are converted to MutableList`() {
            val categoryEntity = CategoryEntity(id = 1, name = "Test", enabled = true)
            val merchants = listOf(
                CategoryMerchantEntity(id = 1, categoryId = 1, name = "Merchant1")
            )
            val categoryWithMerchants = CategoryWithMerchants(
                category = categoryEntity,
                merchants = merchants
            )
            val result = listOf(categoryWithMerchants).toDomainCategories()

            result[0].merchants.add("NewMerchant")
            assertEquals(2, result[0].merchants.size)
        }
    }

    @Nested
    @DisplayName("toDomainSenders()")
    inner class ToDomainSendersTest {

        @Test
        @DisplayName("empty list returns empty list")
        fun `empty list returns empty list`() {
            val entities = emptyList<SenderWithDetails>()
            val result = entities.toDomainSenders()
            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("single sender without addresses and rules maps correctly")
        fun `single sender without addresses and rules maps correctly`() {
            val senderEntity = SenderEntity(id = 1, name = "HDFC Bank", enabled = true)
            val senderWithDetails = SenderWithDetails(
                sender = senderEntity,
                addresses = emptyList(),
                rules = emptyList()
            )
            val result = listOf(senderWithDetails).toDomainSenders()

            assertEquals(1, result.size)
            assertEquals(1L, result[0].id)
            assertEquals("HDFC Bank", result[0].name)
            assertTrue(result[0].enabled)
            assertTrue(result[0].addresses.isEmpty())
            assertTrue(result[0].rules.isEmpty())
        }

        @Test
        @DisplayName("single sender with addresses and rules maps correctly")
        fun `single sender with addresses and rules maps correctly`() {
            val senderEntity = SenderEntity(id = 1, name = "HDFC Bank", enabled = true)
            val addresses = listOf(
                SenderAddressEntity(id = 1, senderId = 1, address = "HDFCBK"),
                SenderAddressEntity(id = 2, senderId = 1, address = "VM-HDFC")
            )
            val rules = listOf(
                RuleEntity(id = 1, senderId = 1, pattern = ".*debit.*", description = "Debit", enabled = true, ruleType = "payment"),
                RuleEntity(id = 2, senderId = 1, pattern = ".*credit.*", description = "Credit", enabled = false, ruleType = "income")
            )
            val senderWithDetails = SenderWithDetails(
                sender = senderEntity,
                addresses = addresses,
                rules = rules
            )
            val result = listOf(senderWithDetails).toDomainSenders()

            assertEquals(1, result.size)
            assertEquals(2, result[0].addresses.size)
            assertTrue(result[0].addresses.contains("HDFCBK"))
            assertTrue(result[0].addresses.contains("VM-HDFC"))

            assertEquals(2, result[0].rules.size)
            assertEquals(".*debit.*", result[0].rules[0].pattern)
            assertEquals(RuleType.PAYMENT, result[0].rules[0].ruleType)
            assertEquals(".*credit.*", result[0].rules[1].pattern)
            assertEquals(RuleType.INCOME, result[0].rules[1].ruleType)
        }

        @Test
        @DisplayName("multiple senders map correctly")
        fun `multiple senders map correctly`() {
            val sender1 = SenderWithDetails(
                sender = SenderEntity(id = 1, name = "Bank A", enabled = true),
                addresses = listOf(SenderAddressEntity(id = 1, senderId = 1, address = "BANKA")),
                rules = emptyList()
            )
            val sender2 = SenderWithDetails(
                sender = SenderEntity(id = 2, name = "Bank B", enabled = false),
                addresses = emptyList(),
                rules = listOf(RuleEntity(id = 1, senderId = 2, pattern = ".*", description = null, enabled = true, ruleType = "payment"))
            )
            val result = listOf(sender1, sender2).toDomainSenders()

            assertEquals(2, result.size)
            assertEquals("Bank A", result[0].name)
            assertTrue(result[0].enabled)
            assertEquals("Bank B", result[1].name)
            assertEquals(false, result[1].enabled)
        }

        @Test
        @DisplayName("addresses and rules are converted to MutableList")
        fun `addresses and rules are converted to MutableList`() {
            val senderEntity = SenderEntity(id = 1, name = "Test", enabled = true)
            val senderWithDetails = SenderWithDetails(
                sender = senderEntity,
                addresses = listOf(SenderAddressEntity(id = 1, senderId = 1, address = "ADDR1")),
                rules = listOf(RuleEntity(id = 1, senderId = 1, pattern = ".*", enabled = true, ruleType = "payment"))
            )
            val result = listOf(senderWithDetails).toDomainSenders()

            result[0].addresses.add("NewAddress")
            assertEquals(2, result[0].addresses.size)

            result[0].rules.add(Rule(pattern = "new.*"))
            assertEquals(2, result[0].rules.size)
        }
    }

    @Nested
    @DisplayName("RuleEntity.toDomainRule()")
    inner class RuleEntityToDomainRuleTest {

        @Test
        @DisplayName("maps all fields correctly")
        fun `maps all fields correctly`() {
            val entity = RuleEntity(
                id = 1,
                senderId = 2,
                pattern = ".*payment.*",
                description = "Payment rule",
                enabled = true,
                ruleType = "payment"
            )
            val result = entity.toDomainRule()

            assertEquals(1L, result.id)
            assertEquals(2L, result.senderId)
            assertEquals(".*payment.*", result.pattern)
            assertEquals("Payment rule", result.description)
            assertTrue(result.enabled)
            assertEquals(RuleType.PAYMENT, result.ruleType)
        }

        @Test
        @DisplayName("maps ignore rule type correctly")
        fun `maps ignore rule type correctly`() {
            val entity = RuleEntity(
                id = 1,
                senderId = 2,
                pattern = ".*spam.*",
                description = null,
                enabled = false,
                ruleType = "ignore"
            )
            val result = entity.toDomainRule()

            assertEquals(RuleType.IGNORE, result.ruleType)
            assertEquals(false, result.enabled)
            assertEquals(null, result.description)
        }

        @Test
        @DisplayName("maps income rule type correctly")
        fun `maps income rule type correctly`() {
            val entity = RuleEntity(
                id = 1,
                senderId = 2,
                pattern = ".*credit.*",
                ruleType = "income"
            )
            val result = entity.toDomainRule()

            assertEquals(RuleType.INCOME, result.ruleType)
        }

        @Test
        @DisplayName("unknown rule type defaults to PAYMENT")
        fun `unknown rule type defaults to PAYMENT`() {
            val entity = RuleEntity(
                id = 1,
                senderId = 2,
                pattern = ".*",
                ruleType = "unknown"
            )
            val result = entity.toDomainRule()

            assertEquals(RuleType.PAYMENT, result.ruleType)
        }
    }

    @Nested
    @DisplayName("Rule.toEntity()")
    inner class RuleToEntityTest {

        @Test
        @DisplayName("maps all fields correctly")
        fun `maps all fields correctly`() {
            val rule = Rule(
                id = 1,
                senderId = 2,
                pattern = ".*payment.*",
                description = "Payment rule",
                enabled = true,
                ruleType = RuleType.PAYMENT
            )
            val result = rule.toEntity()

            assertEquals(1L, result.id)
            assertEquals(2L, result.senderId)
            assertEquals(".*payment.*", result.pattern)
            assertEquals("Payment rule", result.description)
            assertTrue(result.enabled)
            assertEquals("payment", result.ruleType)
        }

        @Test
        @DisplayName("null id becomes 0")
        fun `null id becomes 0`() {
            val rule = Rule(
                id = null,
                senderId = 2,
                pattern = ".*"
            )
            val result = rule.toEntity()

            assertEquals(0L, result.id)
        }

        @Test
        @DisplayName("null senderId becomes 0")
        fun `null senderId becomes 0`() {
            val rule = Rule(
                id = 1,
                senderId = null,
                pattern = ".*"
            )
            val result = rule.toEntity()

            assertEquals(0L, result.senderId)
        }

        @Test
        @DisplayName("maps ignore rule type correctly")
        fun `maps ignore rule type correctly`() {
            val rule = Rule(
                pattern = ".*spam.*",
                ruleType = RuleType.IGNORE
            )
            val result = rule.toEntity()

            assertEquals("ignore", result.ruleType)
        }

        @Test
        @DisplayName("maps income rule type correctly")
        fun `maps income rule type correctly`() {
            val rule = Rule(
                pattern = ".*credit.*",
                ruleType = RuleType.INCOME
            )
            val result = rule.toEntity()

            assertEquals("income", result.ruleType)
        }

        @Test
        @DisplayName("roundtrip conversion preserves data")
        fun `roundtrip conversion preserves data`() {
            val original = Rule(
                id = 5,
                senderId = 10,
                pattern = "test pattern",
                description = "Test description",
                enabled = false,
                ruleType = RuleType.IGNORE
            )
            val entity = original.toEntity()
            val converted = entity.toDomainRule()

            assertEquals(original.id, converted.id)
            assertEquals(original.senderId, converted.senderId)
            assertEquals(original.pattern, converted.pattern)
            assertEquals(original.description, converted.description)
            assertEquals(original.enabled, converted.enabled)
            assertEquals(original.ruleType, converted.ruleType)
        }
    }
}
