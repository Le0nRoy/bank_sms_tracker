package com.example.banksmstracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.data.Merchant
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.ImportResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
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
        // First reset to close any existing database connection
        ConfigRepository.reset()
        // Load fresh instance
        ConfigRepository.load(context.applicationContext as android.app.Application)
        // Clear all data to ensure test isolation
        runBlocking {
            ConfigRepository.clearAllData()
        }
        // Reload after clearing to have a fresh empty config (without seeding)
        ConfigRepository.reset()
        ConfigRepository.load(context.applicationContext as android.app.Application, seedIfEmpty = false)
    }

    @AfterEach
    fun tearDown() {
        ConfigRepository.reset()
    }

    @Test
    @DisplayName("importConfig_addsNewSendersAndCategories")
    fun importConfigAddsNewSendersAndCategories() = runBlocking {
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
    @DisplayName("importConfig_mergesExistingSendersByName")
    fun importConfigMergesExistingSendersByName() = runBlocking {
        // First, create an existing sender
        val existingSender = ConfigRepository.addSender()
        existingSender.name = "Test Bank"
        existingSender.addresses = mutableListOf("TESTBANK")
        existingSender.rules = mutableListOf(
            com.example.banksmstracker.data.Rule(pattern = "OldRule")
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
        assertTrue(mergedSender.rules.any { it.pattern == "OldRule" })
        assertTrue(mergedSender.rules.any { it.pattern == "NewRule" })
    }

    @Test
    @DisplayName("importConfig_mergesExistingCategoriesByName")
    fun importConfigMergesExistingCategoriesByName() = runBlocking {
        // Create existing category
        val existingCategory = ConfigRepository.addCategory()
        existingCategory.name = "Shopping"
        existingCategory.merchants = mutableListOf(Merchant("OldMerchant"))
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
        assertTrue(mergedCategory.merchants.any { it.pattern == "OldMerchant" })
        assertTrue(mergedCategory.merchants.any { it.pattern == "NewMerchant" })
    }

    @Test
    @DisplayName("importConfig_withInvalidJson_returnsError")
    fun importConfigWithInvalidJsonReturnsError() = runBlocking {
        val invalidJson = "{ invalid json }"

        val result = ConfigRepository.importConfig(invalidJson)

        assertTrue(result is ImportResult.Error)
    }

    @Test
    @DisplayName("importConfig_preservesEnabledState")
    fun importConfigPreservesEnabledState() = runBlocking {
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
    @DisplayName("importConfig_handlesEmptyConfig")
    fun importConfigHandlesEmptyConfig() = runBlocking {
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
    @DisplayName("importConfig_caseInsensitiveMerge")
    fun importConfigCaseInsensitiveMerge() = runBlocking {
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
    @DisplayName("importConfig_deduplicatesAddresses")
    fun importConfigDeduplicatesAddresses() = runBlocking {
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
    @DisplayName("exportAndReimport_producesEquivalentConfig")
    fun exportAndReimportProducesEquivalentConfig() = runBlocking {
        // Create config
        val category = ConfigRepository.addCategory()
        category.name = "Test Category"
        category.merchants = mutableListOf(Merchant("Merchant1"), Merchant("Merchant2"))
        ConfigRepository.updateCategory(category)

        val sender = ConfigRepository.addSender()
        sender.name = "Test Bank"
        sender.addresses = mutableListOf("12345")
        sender.rules = mutableListOf(
            com.example.banksmstracker.data.Rule(pattern = "Rule1")
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
