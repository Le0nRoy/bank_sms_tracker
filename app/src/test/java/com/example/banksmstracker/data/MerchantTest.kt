package com.example.banksmstracker.data

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Merchant data class and serializer tests")
class MerchantTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Nested
    @DisplayName("Merchant construction")
    inner class MerchantConstruction {

        @Test
        fun `default isRegex is false`() {
            val m = Merchant("FooStore")
            assertFalse(m.isRegex)
        }

        @Test
        fun `default displayName is null`() {
            val m = Merchant("FooStore")
            assertNull(m.displayName)
        }

        @Test
        fun `display name can be set`() {
            val m = Merchant(pattern = "google_youtube", displayName = "YouTube")
            assertEquals("YouTube", m.displayName)
        }
    }

    @Nested
    @DisplayName("MerchantSerializer — backward compatibility")
    inner class MerchantSerializerBackwardCompat {

        @Test
        fun `plain string deserializes to Merchant with that pattern`() {
            val m: Merchant = json.decodeFromString(MerchantSerializer, "\"FooStore\"")
            assertEquals("FooStore", m.pattern)
            assertNull(m.displayName)
            assertFalse(m.isRegex)
        }

        @Test
        fun `object format deserializes correctly`() {
            val jsonStr = """{"pattern":"FooStore","displayName":"Foo Shop","isRegex":false}"""
            val m: Merchant = json.decodeFromString(MerchantSerializer, jsonStr)
            assertEquals("FooStore", m.pattern)
            assertEquals("Foo Shop", m.displayName)
            assertFalse(m.isRegex)
        }

        @Test
        fun `object format with null displayName deserializes correctly`() {
            val jsonStr = """{"pattern":"FooStore","displayName":null,"isRegex":false}"""
            val m: Merchant = json.decodeFromString(MerchantSerializer, jsonStr)
            assertEquals("FooStore", m.pattern)
            assertNull(m.displayName)
        }

        @Test
        fun `object format with isRegex true deserializes correctly`() {
            val jsonStr = """{"pattern":"foo.*","displayName":null,"isRegex":true}"""
            val m: Merchant = json.decodeFromString(MerchantSerializer, jsonStr)
            assertEquals("foo.*", m.pattern)
            assertTrue(m.isRegex)
        }

        @Test
        fun `serialize roundtrip preserves all fields`() {
            val original = Merchant(pattern = "foo.*", displayName = "Foo", isRegex = true)
            val serialized = json.encodeToString(original)
            val restored = json.decodeFromString<Merchant>(serialized)
            assertEquals(original, restored)
        }

        @Test
        fun `category with plain string merchants loads correctly from JSON`() {
            val configJson = """{"name":"Shopping","merchants":["Amazon","eBay"]}"""
            val category = json.decodeFromString<Category>(configJson)
            assertEquals(2, category.merchants.size)
            assertEquals("Amazon", category.merchants[0].pattern)
            assertEquals("eBay", category.merchants[1].pattern)
            assertFalse(category.merchants[0].isRegex)
        }

        @Test
        fun `category with mixed format merchants loads correctly`() {
            val configJson = """
                {
                  "name": "Shopping",
                  "merchants": [
                    "Amazon",
                    {"pattern":"google.*","displayName":"Google","isRegex":true}
                  ]
                }
            """.trimIndent()
            val category = json.decodeFromString<Category>(configJson)
            assertEquals(2, category.merchants.size)
            assertEquals("Amazon", category.merchants[0].pattern)
            assertFalse(category.merchants[0].isRegex)
            assertEquals("google.*", category.merchants[1].pattern)
            assertEquals("Google", category.merchants[1].displayName)
            assertTrue(category.merchants[1].isRegex)
        }
    }

    @Nested
    @DisplayName("Merchant matching logic (mirrors PaymentProcessor.assignCategory)")
    inner class MerchantMatching {

        private fun matches(merchant: Merchant, paymentMerchant: String): Boolean =
            if (merchant.isRegex) {
                Regex(merchant.pattern, setOf(RegexOption.IGNORE_CASE)).containsMatchIn(paymentMerchant)
            } else {
                merchant.pattern.equals(paymentMerchant, ignoreCase = true)
            }

        @Test
        fun `exact match is case insensitive`() {
            val m = Merchant("amazon")
            assertTrue(matches(m, "Amazon"))
            assertTrue(matches(m, "AMAZON"))
        }

        @Test
        fun `exact match does not match partial`() {
            val m = Merchant("Amazon")
            assertFalse(matches(m, "Amazon Marketplace"))
        }

        @Test
        fun `regex merchant matches partial string`() {
            val m = Merchant(pattern = "amazon", isRegex = true)
            assertTrue(matches(m, "Amazon Marketplace"))
            assertTrue(matches(m, "amazon.de"))
        }

        @Test
        fun `regex merchant is case insensitive`() {
            val m = Merchant(pattern = "google \\*youtube", isRegex = true)
            assertTrue(matches(m, "GOOGLE *YouTube"))
        }

        @Test
        fun `regex merchant does not match unrelated string`() {
            val m = Merchant(pattern = "^Amazon$", isRegex = true)
            assertFalse(matches(m, "Not Amazon"))
        }
    }
}
