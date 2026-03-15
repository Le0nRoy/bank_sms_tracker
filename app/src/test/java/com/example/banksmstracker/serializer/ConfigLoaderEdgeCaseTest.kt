package com.example.banksmstracker.serializer

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ConfigLoader Edge Cases")
class ConfigLoaderEdgeCaseTest {

    @Nested
    @DisplayName("Invalid JSON Handling")
    inner class InvalidJsonHandling {

        @Test
        @DisplayName("Invalid JSON syntax throws SerializationException")
        fun `invalid json syntax throws SerializationException`() {
            val invalidJson = """{ invalid json }"""

            assertFailsWith<SerializationException> {
                ConfigLoader.load(invalidJson)
            }
        }

        @Test
        @DisplayName("Missing closing brace throws SerializationException")
        fun `missing closing brace throws SerializationException`() {
            val invalidJson = """{ "senders": [], "categories": [] """

            assertFailsWith<SerializationException> {
                ConfigLoader.load(invalidJson)
            }
        }

        @Test
        @DisplayName("Empty string throws SerializationException")
        fun `empty string throws SerializationException`() {
            assertFailsWith<SerializationException> {
                ConfigLoader.load("")
            }
        }

        @Test
        @DisplayName("Null literal is handled")
        fun `null literal throws SerializationException`() {
            assertFailsWith<SerializationException> {
                ConfigLoader.load("null")
            }
        }
    }

    @Nested
    @DisplayName("Empty Config Handling")
    inner class EmptyConfigHandling {

        @Test
        @DisplayName("Empty senders and categories is valid")
        fun `empty senders and categories is valid`() {
            val configJson = """
                {
                  "senders": [],
                  "categories": []
                }
            """.trimIndent()

            val config = ConfigLoader.load(configJson)
            assertNotNull(config)
            assertTrue(config.senders.isEmpty())
            assertTrue(config.categories.isEmpty())
        }

        @Test
        @DisplayName("Sender with empty addresses is valid")
        fun `sender with empty addresses is valid`() {
            val configJson = """
                {
                  "senders": [
                    {
                      "name": "Test Bank",
                      "addresses": [],
                      "rules": []
                    }
                  ],
                  "categories": []
                }
            """.trimIndent()

            val config = ConfigLoader.load(configJson)
            assertNotNull(config)
            assertEquals(1, config.senders.size)
            assertTrue(config.senders[0].addresses.isEmpty())
        }

        @Test
        @DisplayName("Sender with empty rules is valid")
        fun `sender with empty rules is valid`() {
            val configJson = """
                {
                  "senders": [
                    {
                      "name": "Test Bank",
                      "addresses": ["TESTBANK"],
                      "rules": []
                    }
                  ],
                  "categories": []
                }
            """.trimIndent()

            val config = ConfigLoader.load(configJson)
            assertNotNull(config)
            assertTrue(config.senders[0].rules.isEmpty())
        }

        @Test
        @DisplayName("Category with empty merchants is valid")
        fun `category with empty merchants is valid`() {
            val configJson = """
                {
                  "senders": [],
                  "categories": [
                    {
                      "name": "Empty Category",
                      "merchants": []
                    }
                  ]
                }
            """.trimIndent()

            val config = ConfigLoader.load(configJson)
            assertNotNull(config)
            assertEquals(1, config.categories.size)
            assertTrue(config.categories[0].merchants.isEmpty())
        }
    }

    @Nested
    @DisplayName("Duplicate Validation")
    inner class DuplicateValidation {

        @Test
        @DisplayName("Duplicate category names throw IllegalArgumentException")
        fun `duplicate category names throw IllegalArgumentException`() {
            val configJson = """
                {
                  "senders": [],
                  "categories": [
                    { "name": "Shopping", "merchants": ["Store1"] },
                    { "name": "Shopping", "merchants": ["Store2"] }
                  ]
                }
            """.trimIndent()

            val exception = assertFailsWith<IllegalArgumentException> {
                ConfigLoader.load(configJson)
            }
            assertTrue(exception.message?.contains("Duplicate category names") == true)
        }

        @Test
        @DisplayName("Duplicate regex in sender throws IllegalArgumentException")
        fun `duplicate regex in sender throws IllegalArgumentException`() {
            val configJson = """
                {
                  "senders": [
                    {
                      "name": "Test Bank",
                      "addresses": ["TESTBANK"],
                      "rules": [
                        { "regex": "test" },
                        { "regex": "test" }
                      ]
                    }
                  ],
                  "categories": []
                }
            """.trimIndent()

            val exception = assertFailsWith<IllegalArgumentException> {
                ConfigLoader.load(configJson)
            }
            assertTrue(exception.message?.contains("duplicate patterns") == true)
        }

        @Test
        @DisplayName("Merchant in multiple categories throws IllegalArgumentException")
        fun `merchant in multiple categories throws IllegalArgumentException`() {
            val configJson = """
                {
                  "senders": [],
                  "categories": [
                    { "name": "Shopping", "merchants": ["Amazon"] },
                    { "name": "Electronics", "merchants": ["Amazon"] }
                  ]
                }
            """.trimIndent()

            val exception = assertFailsWith<IllegalArgumentException> {
                ConfigLoader.load(configJson)
            }
            assertTrue(exception.message?.contains("Amazon") == true)
            assertTrue(exception.message?.contains("multiple categories") == true)
        }

        @Test
        @DisplayName("Same merchant in same category (duplicate) is allowed")
        fun `same merchant in same category is allowed`() {
            val configJson = """
                {
                  "senders": [],
                  "categories": [
                    { "name": "Shopping", "merchants": ["Amazon", "Amazon"] }
                  ]
                }
            """.trimIndent()

            // This is technically allowed (duplicates within same category)
            val config = ConfigLoader.load(configJson)
            assertNotNull(config)
            // Both Amazon entries should be present
            assertEquals(2, config.categories[0].merchants.count { it.pattern == "Amazon" })
        }
    }

    @Nested
    @DisplayName("Special Characters Handling")
    inner class SpecialCharactersHandling {

        @Test
        @DisplayName("Unicode characters in names are preserved")
        fun `unicode characters in names are preserved`() {
            val configJson = """
                {
                  "senders": [
                    {
                      "name": "Банк Тест",
                      "addresses": ["БАНК"],
                      "rules": []
                    }
                  ],
                  "categories": [
                    { "name": "Покупки", "merchants": ["Магазин"] }
                  ]
                }
            """.trimIndent()

            val config = ConfigLoader.load(configJson)
            assertEquals("Банк Тест", config.senders[0].name)
            assertEquals("Покупки", config.categories[0].name)
        }

        @Test
        @DisplayName("Special characters in regex are valid")
        fun `special characters in regex are valid`() {
            val configJson = """
                {
                  "senders": [
                    {
                      "name": "Test Bank",
                      "addresses": ["TESTBANK"],
                      "rules": [
                        { "regex": "\\d+\\.\\d{2}\\s+\\w+" }
                      ]
                    }
                  ],
                  "categories": []
                }
            """.trimIndent()

            val config = ConfigLoader.load(configJson)
            assertNotNull(config)
            assertEquals("\\d+\\.\\d{2}\\s+\\w+", config.senders[0].rules[0].pattern)
        }

        @Test
        @DisplayName("JSON escape sequences are handled")
        fun `json escape sequences are handled`() {
            val configJson = """
                {
                  "senders": [
                    {
                      "name": "Test \"Bank\"",
                      "addresses": ["TEST\\BANK"],
                      "rules": []
                    }
                  ],
                  "categories": []
                }
            """.trimIndent()

            val config = ConfigLoader.load(configJson)
            assertEquals("Test \"Bank\"", config.senders[0].name)
            assertEquals("TEST\\BANK", config.senders[0].addresses[0])
        }

        @Test
        @DisplayName("Newlines in merchant names are preserved")
        fun `newlines in merchant names are preserved`() {
            val configJson = """
                {
                  "senders": [],
                  "categories": [
                    { "name": "Category", "merchants": ["Store\nName"] }
                  ]
                }
            """.trimIndent()

            val config = ConfigLoader.load(configJson)
            assertEquals("Store\nName", config.categories[0].merchants[0].pattern)
        }
    }

    @Nested
    @DisplayName("Enabled Field Defaults")
    inner class EnabledFieldDefaults {

        @Test
        @DisplayName("Sender enabled defaults to true when not specified")
        fun `sender enabled defaults to true when not specified`() {
            val configJson = """
                {
                  "senders": [
                    {
                      "name": "Test Bank",
                      "addresses": ["TESTBANK"],
                      "rules": []
                    }
                  ],
                  "categories": []
                }
            """.trimIndent()

            val config = ConfigLoader.load(configJson)
            assertTrue(config.senders[0].enabled)
        }

        @Test
        @DisplayName("Category enabled defaults to true when not specified")
        fun `category enabled defaults to true when not specified`() {
            val configJson = """
                {
                  "senders": [],
                  "categories": [
                    { "name": "Shopping", "merchants": ["Amazon"] }
                  ]
                }
            """.trimIndent()

            val config = ConfigLoader.load(configJson)
            assertTrue(config.categories[0].enabled)
        }

        @Test
        @DisplayName("Rule enabled defaults to true when not specified")
        fun `rule enabled defaults to true when not specified`() {
            val configJson = """
                {
                  "senders": [
                    {
                      "name": "Test Bank",
                      "addresses": ["TESTBANK"],
                      "rules": [{ "regex": "test" }]
                    }
                  ],
                  "categories": []
                }
            """.trimIndent()

            val config = ConfigLoader.load(configJson)
            assertTrue(config.senders[0].rules[0].enabled)
        }

        @Test
        @DisplayName("Explicit enabled=false is respected")
        fun `explicit enabled false is respected`() {
            val configJson = """
                {
                  "senders": [
                    {
                      "name": "Test Bank",
                      "addresses": ["TESTBANK"],
                      "rules": [{ "regex": "test", "enabled": false }],
                      "enabled": false
                    }
                  ],
                  "categories": [
                    { "name": "Shopping", "merchants": ["Amazon"], "enabled": false }
                  ]
                }
            """.trimIndent()

            val config = ConfigLoader.load(configJson)
            assertTrue(!config.senders[0].enabled)
            assertTrue(!config.senders[0].rules[0].enabled)
            assertTrue(!config.categories[0].enabled)
        }
    }

    @Nested
    @DisplayName("Large Config Handling")
    inner class LargeConfigHandling {

        @Test
        @DisplayName("Config with many senders loads correctly")
        fun `config with many senders loads correctly`() {
            val senders = (1..100).map { i ->
                """{ "name": "Bank$i", "addresses": ["BANK$i"], "rules": [{ "regex": "test$i" }] }"""
            }.joinToString(",")

            val configJson = """
                {
                  "senders": [$senders],
                  "categories": []
                }
            """.trimIndent()

            val config = ConfigLoader.load(configJson)
            assertEquals(100, config.senders.size)
        }

        @Test
        @DisplayName("Config with many categories loads correctly")
        fun `config with many categories loads correctly`() {
            val categories = (1..100).map { i ->
                """{ "name": "Category$i", "merchants": ["Merchant$i"] }"""
            }.joinToString(",")

            val configJson = """
                {
                  "senders": [],
                  "categories": [$categories]
                }
            """.trimIndent()

            val config = ConfigLoader.load(configJson)
            assertEquals(100, config.categories.size)
        }

        @Test
        @DisplayName("Config with many rules per sender loads correctly")
        fun `config with many rules per sender loads correctly`() {
            val rules = (1..50).map { i ->
                """{ "regex": "pattern$i" }"""
            }.joinToString(",")

            val configJson = """
                {
                  "senders": [
                    {
                      "name": "Test Bank",
                      "addresses": ["TESTBANK"],
                      "rules": [$rules]
                    }
                  ],
                  "categories": []
                }
            """.trimIndent()

            val config = ConfigLoader.load(configJson)
            assertEquals(50, config.senders[0].rules.size)
        }
    }

    @Nested
    @DisplayName("PaymentProcessor Creation")
    inner class PaymentProcessorCreation {

        @Test
        @DisplayName("createPaymentProcessor returns valid processor")
        fun `createPaymentProcessor returns valid processor`() {
            val configJson = """
                {
                  "senders": [
                    {
                      "name": "Test Bank",
                      "addresses": ["TESTBANK"],
                      "rules": [{ "regex": "(\\d+)\\s*(USD)()()()()" }]
                    }
                  ],
                  "categories": [
                    { "name": "Shopping", "merchants": ["Amazon"] }
                  ]
                }
            """.trimIndent()

            val config = ConfigLoader.load(configJson)
            val processor = ConfigLoader.createPaymentProcessor(config)

            assertNotNull(processor)
            assertNotNull(processor.paymentRepository)
        }

        @Test
        @DisplayName("createPaymentProcessor with empty config returns valid processor")
        fun `createPaymentProcessor with empty config returns valid processor`() {
            val configJson = """
                {
                  "senders": [],
                  "categories": []
                }
            """.trimIndent()

            val config = ConfigLoader.load(configJson)
            val processor = ConfigLoader.createPaymentProcessor(config)

            assertNotNull(processor)
        }
    }
}
