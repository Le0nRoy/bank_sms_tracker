package com.example.banksmstracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.ImportResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * E2E tests for configuration import functionality.
 * Tests importing JSON config and merging with existing configuration.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigImportE2ETest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @BeforeEach
    fun setUp() {
        ConfigRepository.reset()
        ConfigRepository.load(context.applicationContext as android.app.Application)
    }

    @AfterEach
    fun tearDown() {
        ConfigRepository.reset()
    }

    @Test
    fun `importConfig_addsNewSendersAndCategories`() = runBlocking {
        val jsonConfig = """
            {
                "senders": [
                    {
                        "name": "New Bank",
                        "addresses": ["NEWBANK"],
                        "rules": [{"regex": "Payment (\\d+)"}],
                        "enabled": true
                    }
                ],
                "categories": [
                    {
                        "name": "New Category",
                        "merchants": ["NewMerchant"],
                        "enabled": true
                    }
                ]
            }
        """.trimIndent()

        val result = ConfigRepository.importConfig(jsonConfig)

        assertTrue(result is ImportResult.Success)
        val success = result as ImportResult.Success
        assertEquals(1, success.sendersAdded)
        assertEquals(1, success.categoriesAdded)

        val senders = ConfigRepository.getSenders()
        val categories = ConfigRepository.getCategories()

        assertTrue(senders.any { it.name == "New Bank" })
        assertTrue(categories.any { it.name == "New Category" })
    }

    @Test
    fun `importConfig_mergesExistingSendersByName`() = runBlocking {
        // First, create an existing sender
        val existingSender = ConfigRepository.addSender()
        existingSender.name = "Test Bank"
        existingSender.addresses = mutableListOf("TESTBANK")
        existingSender.rules = mutableListOf(
            com.example.banksmstracker.data.PaymentRegexRule(regex = "OldRule")
        )
        ConfigRepository.updateSender(existingSender)

        // Import config with same sender name but different addresses/rules
        val jsonConfig = """
            {
                "senders": [
                    {
                        "name": "Test Bank",
                        "addresses": ["NEWTESTBANK"],
                        "rules": [{"regex": "NewRule"}],
                        "enabled": true
                    }
                ],
                "categories": []
            }
        """.trimIndent()

        val result = ConfigRepository.importConfig(jsonConfig)

        assertTrue(result is ImportResult.Success)
        val success = result as ImportResult.Success
        assertEquals(0, success.sendersAdded)
        assertEquals(1, success.sendersMerged)

        val senders = ConfigRepository.getSenders()
        assertEquals(1, senders.size)

        val mergedSender = senders.first { it.name.equals("Test Bank", ignoreCase = true) }
        // Should have both old and new addresses
        assertTrue(mergedSender.addresses.any { it.contains("testbank") })
        assertTrue(mergedSender.addresses.any { it.contains("newtestbank") })
        // Should have both old and new rules
        assertTrue(mergedSender.rules.any { it.regex == "OldRule" })
        assertTrue(mergedSender.rules.any { it.regex == "NewRule" })
    }

    @Test
    fun `importConfig_mergesExistingCategoriesByName`() = runBlocking {
        // Create existing category
        val existingCategory = ConfigRepository.addCategory()
        existingCategory.name = "Shopping"
        existingCategory.merchants = mutableListOf("OldMerchant")
        ConfigRepository.updateCategory(existingCategory)

        // Import config with same category name
        val jsonConfig = """
            {
                "senders": [],
                "categories": [
                    {
                        "name": "Shopping",
                        "merchants": ["NewMerchant"],
                        "enabled": true
                    }
                ]
            }
        """.trimIndent()

        val result = ConfigRepository.importConfig(jsonConfig)

        assertTrue(result is ImportResult.Success)
        val success = result as ImportResult.Success
        assertEquals(0, success.categoriesAdded)
        assertEquals(1, success.categoriesMerged)

        val categories = ConfigRepository.getCategories()
        assertEquals(1, categories.size)

        val mergedCategory = categories.first { it.name.equals("Shopping", ignoreCase = true) }
        assertTrue(mergedCategory.merchants.contains("OldMerchant"))
        assertTrue(mergedCategory.merchants.contains("NewMerchant"))
    }

    @Test
    fun `importConfig_withInvalidJson_returnsError`() = runBlocking {
        val invalidJson = "{ invalid json }"

        val result = ConfigRepository.importConfig(invalidJson)

        assertTrue(result is ImportResult.Error)
    }

    @Test
    fun `importConfig_preservesEnabledState`() = runBlocking {
        val jsonConfig = """
            {
                "senders": [
                    {
                        "name": "Disabled Bank",
                        "addresses": ["DISABLED"],
                        "rules": [],
                        "enabled": false
                    }
                ],
                "categories": [
                    {
                        "name": "Disabled Category",
                        "merchants": [],
                        "enabled": false
                    }
                ]
            }
        """.trimIndent()

        val result = ConfigRepository.importConfig(jsonConfig)

        assertTrue(result is ImportResult.Success)

        val senders = ConfigRepository.getSenders()
        val categories = ConfigRepository.getCategories()

        val disabledSender = senders.first { it.name == "Disabled Bank" }
        val disabledCategory = categories.first { it.name == "Disabled Category" }

        assertTrue(!disabledSender.enabled)
        assertTrue(!disabledCategory.enabled)
    }

    @Test
    fun `importConfig_handlesEmptyConfig`() = runBlocking {
        val emptyConfig = """
            {
                "senders": [],
                "categories": []
            }
        """.trimIndent()

        val result = ConfigRepository.importConfig(emptyConfig)

        assertTrue(result is ImportResult.Success)
        val success = result as ImportResult.Success
        assertEquals(0, success.totalChanges)
    }

    @Test
    fun `importConfig_caseInsensitiveMerge`() = runBlocking {
        // Create existing sender with lowercase name
        val existingSender = ConfigRepository.addSender()
        existingSender.name = "my bank"
        existingSender.addresses = mutableListOf("MYBANK")
        ConfigRepository.updateSender(existingSender)

        // Import with different case
        val jsonConfig = """
            {
                "senders": [
                    {
                        "name": "MY BANK",
                        "addresses": ["MYBANK2"],
                        "rules": [],
                        "enabled": true
                    }
                ],
                "categories": []
            }
        """.trimIndent()

        val result = ConfigRepository.importConfig(jsonConfig)

        assertTrue(result is ImportResult.Success)
        val success = result as ImportResult.Success
        assertEquals(0, success.sendersAdded)
        assertEquals(1, success.sendersMerged)

        val senders = ConfigRepository.getSenders()
        assertEquals(1, senders.size)
    }

    @Test
    fun `importConfig_deduplicatesAddresses`() = runBlocking {
        // Create existing sender
        val existingSender = ConfigRepository.addSender()
        existingSender.name = "Dupe Bank"
        existingSender.addresses = mutableListOf("SAME")
        ConfigRepository.updateSender(existingSender)

        // Import with same address
        val jsonConfig = """
            {
                "senders": [
                    {
                        "name": "Dupe Bank",
                        "addresses": ["SAME", "NEW"],
                        "rules": [],
                        "enabled": true
                    }
                ],
                "categories": []
            }
        """.trimIndent()

        ConfigRepository.importConfig(jsonConfig)

        val senders = ConfigRepository.getSenders()
        val sender = senders.first { it.name.equals("Dupe Bank", ignoreCase = true) }

        // Should deduplicate addresses (case insensitive)
        val uniqueAddresses = sender.addresses.map { it.lowercase() }.distinct()
        assertEquals(sender.addresses.size, uniqueAddresses.size)
    }

    @Test
    fun `exportAndReimport_producesEquivalentConfig`() = runBlocking {
        // Create config
        val category = ConfigRepository.addCategory()
        category.name = "Test Category"
        category.merchants = mutableListOf("Merchant1", "Merchant2")
        ConfigRepository.updateCategory(category)

        val sender = ConfigRepository.addSender()
        sender.name = "Test Bank"
        sender.addresses = mutableListOf("12345")
        sender.rules = mutableListOf(
            com.example.banksmstracker.data.PaymentRegexRule(regex = "Rule1")
        )
        ConfigRepository.updateSender(sender)

        // Export
        val exportedJson = ConfigRepository.exportConfigJson()

        // Reset and reimport
        ConfigRepository.reset()
        ConfigRepository.load(context.applicationContext as android.app.Application)

        val result = ConfigRepository.importConfig(exportedJson)

        assertTrue(result is ImportResult.Success)

        val categories = ConfigRepository.getCategories()
        val senders = ConfigRepository.getSenders()

        assertTrue(categories.any { it.name == "Test Category" })
        assertTrue(senders.any { it.name == "Test Bank" })
    }
}
