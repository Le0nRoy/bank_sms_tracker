package com.example.banksmstracker.serializer

import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class ConfigLoaderTest {

    @Test
    fun testDuplicateCategoryValidation() {
        val configJson = """
            {
              "senders": [
                {
                  "name": "TBC Bank",
                  "addresses": ["TBC SMS"],
                  "rules": [
                    {
                      "regex": "test"
                    }
                  ]
                }
              ],
              "categories": [
                {
                  "name": "Shopping",
                  "merchants": ["Temu", "Amazon"]
                },
                {
                  "name": "Shopping",
                  "merchants": ["Walmart", "Target"]
                }
              ]
            }
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            ConfigLoader.load(configJson)
        }
    }

    @Test
    fun testMerchantInMultipleCategoriesValidation() {
        val configJson = """
            {
              "senders": [
                {
                  "name": "TBC Bank",
                  "addresses": ["TBC SMS"],
                  "rules": [
                    {
                      "regex": "test"
                    }
                  ]
                }
              ],
              "categories": [
                {
                  "name": "Shopping",
                  "merchants": ["Temu", "Amazon"]
                },
                {
                  "name": "Food",
                  "merchants": ["Temu", "KFC"]
                }
              ]
            }
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            ConfigLoader.load(configJson)
        }
    }
}
