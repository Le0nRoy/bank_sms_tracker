package com.example.banksmstracker.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Database Entities Tests")
class EntitiesTest {

    @Nested
    @DisplayName("CategoryEntity")
    inner class CategoryEntityTest {

        @Test
        @DisplayName("default values are correct")
        fun `default values are correct`() {
            val entity = CategoryEntity(name = "Test")
            assertEquals(0L, entity.id)
            assertEquals("Test", entity.name)
            assertTrue(entity.enabled)
        }

        @Test
        @DisplayName("can be created with all fields")
        fun `can be created with all fields`() {
            val entity = CategoryEntity(id = 5, name = "Shopping", enabled = false)
            assertEquals(5L, entity.id)
            assertEquals("Shopping", entity.name)
            assertFalse(entity.enabled)
        }

        @Test
        @DisplayName("copy creates independent copy")
        fun `copy creates independent copy`() {
            val original = CategoryEntity(id = 1, name = "Original", enabled = true)
            val copy = original.copy(name = "Modified", enabled = false)

            assertEquals("Original", original.name)
            assertTrue(original.enabled)
            assertEquals("Modified", copy.name)
            assertFalse(copy.enabled)
        }
    }

    @Nested
    @DisplayName("CategoryMerchantEntity")
    inner class CategoryMerchantEntityTest {

        @Test
        @DisplayName("default id is 0")
        fun `default id is 0`() {
            val entity = CategoryMerchantEntity(categoryId = 1, pattern = "Amazon")
            assertEquals(0L, entity.id)
        }

        @Test
        @DisplayName("can be created with all fields")
        fun `can be created with all fields`() {
            val entity = CategoryMerchantEntity(
                id = 10,
                categoryId = 5,
                pattern = "eBay",
                displayName = "eBay Store",
                isRegex = false
            )
            assertEquals(10L, entity.id)
            assertEquals(5L, entity.categoryId)
            assertEquals("eBay", entity.pattern)
            assertEquals("eBay Store", entity.displayName)
        }
    }

    @Nested
    @DisplayName("SenderEntity")
    inner class SenderEntityTest {

        @Test
        @DisplayName("default values are correct")
        fun `default values are correct`() {
            val entity = SenderEntity(name = "Bank")
            assertEquals(0L, entity.id)
            assertEquals("Bank", entity.name)
            assertTrue(entity.enabled)
        }

        @Test
        @DisplayName("can be created with disabled status")
        fun `can be created with disabled status`() {
            val entity = SenderEntity(id = 1, name = "Old Bank", enabled = false)
            assertEquals(1L, entity.id)
            assertFalse(entity.enabled)
        }
    }

    @Nested
    @DisplayName("SenderAddressEntity")
    inner class SenderAddressEntityTest {

        @Test
        @DisplayName("default id is 0")
        fun `default id is 0`() {
            val entity = SenderAddressEntity(senderId = 1, address = "VM-HDFC")
            assertEquals(0L, entity.id)
        }

        @Test
        @DisplayName("can be created with all fields")
        fun `can be created with all fields`() {
            val entity = SenderAddressEntity(id = 5, senderId = 2, address = "AD-ICICI")
            assertEquals(5L, entity.id)
            assertEquals(2L, entity.senderId)
            assertEquals("AD-ICICI", entity.address)
        }
    }

    @Nested
    @DisplayName("RuleEntity")
    inner class RuleEntityTest {

        @Test
        @DisplayName("default values are correct")
        fun `default values are correct`() {
            val entity = RuleEntity(senderId = 1, pattern = ".*test.*")
            assertEquals(0L, entity.id)
            assertEquals(1L, entity.senderId)
            assertEquals(".*test.*", entity.pattern)
            assertNull(entity.description)
            assertTrue(entity.enabled)
            assertEquals("payment", entity.ruleType)
        }

        @Test
        @DisplayName("can be created with all fields")
        fun `can be created with all fields`() {
            val entity = RuleEntity(
                id = 10,
                senderId = 5,
                pattern = ".*income.*",
                description = "Income rule",
                enabled = false,
                ruleType = "income"
            )
            assertEquals(10L, entity.id)
            assertEquals(5L, entity.senderId)
            assertEquals(".*income.*", entity.pattern)
            assertEquals("Income rule", entity.description)
            assertFalse(entity.enabled)
            assertEquals("income", entity.ruleType)
        }

        @Test
        @DisplayName("can have ignore rule type")
        fun `can have ignore rule type`() {
            val entity = RuleEntity(senderId = 1, pattern = ".*spam.*", ruleType = "ignore")
            assertEquals("ignore", entity.ruleType)
        }
    }

    @Nested
    @DisplayName("PaymentEntity")
    inner class PaymentEntityTest {

        @Test
        @DisplayName("default values are correct")
        fun `default values are correct`() {
            val entity = PaymentEntity(
                amount = 100.0,
                currency = "USD",
                card = null,
                merchant = null,
                timestamp = "",
                balance = null,
                categoryName = null,
                messageHash = "hash123"
            )
            assertEquals(0L, entity.id)
            assertEquals(100.0, entity.amount)
            assertEquals("USD", entity.currency)
            assertNull(entity.card)
            assertNull(entity.merchant)
            assertEquals("", entity.timestamp)
            assertNull(entity.balance)
            assertNull(entity.categoryName)
            assertEquals("hash123", entity.messageHash)
            assertNull(entity.senderAddress)
            assertNull(entity.ruleId)
        }

        @Test
        @DisplayName("can be created with all fields")
        fun `can be created with all fields`() {
            val entity = PaymentEntity(
                id = 1,
                amount = 500.50,
                currency = "EUR",
                card = "1234",
                merchant = "Amazon",
                timestamp = "2024-01-15",
                balance = 1000.0,
                categoryName = "Shopping",
                messageHash = "abc123",
                senderAddress = "VM-BANK",
                ruleId = 5
            )
            assertEquals(1L, entity.id)
            assertEquals(500.50, entity.amount)
            assertEquals("EUR", entity.currency)
            assertEquals("1234", entity.card)
            assertEquals("Amazon", entity.merchant)
            assertEquals("2024-01-15", entity.timestamp)
            assertEquals(1000.0, entity.balance)
            assertEquals("Shopping", entity.categoryName)
            assertEquals("abc123", entity.messageHash)
            assertEquals("VM-BANK", entity.senderAddress)
            assertEquals(5L, entity.ruleId)
        }
    }

    @Nested
    @DisplayName("IgnoreRuleEntity")
    inner class IgnoreRuleEntityTest {

        @Test
        @DisplayName("default values are correct")
        fun `default values are correct`() {
            val entity = IgnoreRuleEntity(senderId = 1, pattern = ".*spam.*")
            assertEquals(0L, entity.id)
            assertEquals(1L, entity.senderId)
            assertEquals(".*spam.*", entity.pattern)
            assertNull(entity.description)
            assertTrue(entity.enabled)
        }

        @Test
        @DisplayName("can be created with all fields")
        fun `can be created with all fields`() {
            val entity = IgnoreRuleEntity(
                id = 5,
                senderId = 2,
                pattern = ".*promotional.*",
                description = "Promotional filter",
                enabled = false
            )
            assertEquals(5L, entity.id)
            assertEquals(2L, entity.senderId)
            assertEquals(".*promotional.*", entity.pattern)
            assertEquals("Promotional filter", entity.description)
            assertFalse(entity.enabled)
        }
    }

    @Nested
    @DisplayName("IncomeEntity")
    inner class IncomeEntityTest {

        @Test
        @DisplayName("default values are correct")
        fun `default values are correct`() {
            val entity = IncomeEntity(
                amount = 5000.0,
                currency = "USD",
                source = null,
                timestamp = null,
                balance = null,
                messageHash = "hash456"
            )
            assertEquals(0L, entity.id)
            assertEquals(5000.0, entity.amount)
            assertEquals("USD", entity.currency)
            assertNull(entity.source)
            assertNull(entity.timestamp)
            assertNull(entity.balance)
            assertEquals("hash456", entity.messageHash)
            assertNull(entity.senderAddress)
            assertNull(entity.receivedAt)
            assertNull(entity.ruleId)
        }

        @Test
        @DisplayName("can be created with all fields")
        fun `can be created with all fields`() {
            val entity = IncomeEntity(
                id = 1,
                amount = 10000.0,
                currency = "EUR",
                source = "Salary",
                timestamp = "2024-01-31",
                balance = 15000.0,
                messageHash = "xyz789",
                senderAddress = "EMPLOYER",
                receivedAt = 1706713200000,
                ruleId = 10
            )
            assertEquals(1L, entity.id)
            assertEquals(10000.0, entity.amount)
            assertEquals("EUR", entity.currency)
            assertEquals("Salary", entity.source)
            assertEquals("2024-01-31", entity.timestamp)
            assertEquals(15000.0, entity.balance)
            assertEquals("xyz789", entity.messageHash)
            assertEquals("EMPLOYER", entity.senderAddress)
            assertEquals(1706713200000, entity.receivedAt)
            assertEquals(10L, entity.ruleId)
        }
    }

    @Nested
    @DisplayName("CategoryWithMerchants")
    inner class CategoryWithMerchantsTest {

        @Test
        @DisplayName("can hold category with empty merchants list")
        fun `can hold category with empty merchants list`() {
            val category = CategoryEntity(id = 1, name = "Test", enabled = true)
            val categoryWithMerchants = CategoryWithMerchants(
                category = category,
                merchants = emptyList()
            )
            assertEquals(category, categoryWithMerchants.category)
            assertTrue(categoryWithMerchants.merchants.isEmpty())
        }

        @Test
        @DisplayName("can hold category with multiple merchants")
        fun `can hold category with multiple merchants`() {
            val category = CategoryEntity(id = 1, name = "Shopping", enabled = true)
            val merchants = listOf(
                CategoryMerchantEntity(id = 1, categoryId = 1, pattern = "Amazon"),
                CategoryMerchantEntity(id = 2, categoryId = 1, pattern = "eBay"),
                CategoryMerchantEntity(id = 3, categoryId = 1, pattern = "Walmart")
            )
            val categoryWithMerchants = CategoryWithMerchants(
                category = category,
                merchants = merchants
            )
            assertEquals(3, categoryWithMerchants.merchants.size)
        }
    }

    @Nested
    @DisplayName("SenderWithDetails")
    inner class SenderWithDetailsTest {

        @Test
        @DisplayName("can hold sender with empty addresses and rules")
        fun `can hold sender with empty addresses and rules`() {
            val sender = SenderEntity(id = 1, name = "Bank", enabled = true)
            val senderWithDetails = SenderWithDetails(
                sender = sender,
                addresses = emptyList(),
                rules = emptyList()
            )
            assertEquals(sender, senderWithDetails.sender)
            assertTrue(senderWithDetails.addresses.isEmpty())
            assertTrue(senderWithDetails.rules.isEmpty())
        }

        @Test
        @DisplayName("can hold sender with multiple addresses and rules")
        fun `can hold sender with multiple addresses and rules`() {
            val sender = SenderEntity(id = 1, name = "HDFC", enabled = true)
            val addresses = listOf(
                SenderAddressEntity(id = 1, senderId = 1, address = "HDFCBK"),
                SenderAddressEntity(id = 2, senderId = 1, address = "VM-HDFC")
            )
            val rules = listOf(
                RuleEntity(id = 1, senderId = 1, pattern = ".*debit.*", ruleType = "payment"),
                RuleEntity(id = 2, senderId = 1, pattern = ".*credit.*", ruleType = "income")
            )
            val senderWithDetails = SenderWithDetails(
                sender = sender,
                addresses = addresses,
                rules = rules
            )
            assertEquals(2, senderWithDetails.addresses.size)
            assertEquals(2, senderWithDetails.rules.size)
        }
    }
}
