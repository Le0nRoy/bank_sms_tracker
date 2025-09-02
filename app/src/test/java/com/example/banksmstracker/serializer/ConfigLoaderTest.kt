package com.example.banksmstracker.serializer

import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ConfigLoaderTest {

    @Test
    fun testDuplicateCategoryValidation() {
        val configJson = """
            {
              "senders": [
                {
                  "name": "TBC Bank",
                  "address": "TBC SMS",
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
                  "address": "TBC SMS",
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
