package com.example.banksmstracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.banksmstracker.repository.ConfigRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * E2E tests for configuration export functionality.
 * Verifies that configuration can be exported to JSON and shared.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigExportE2ETest {

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

    @Test
    @DisplayName("exportConfigJson_returnsValidJson")
    fun exportConfigJsonReturnsValidJson() = runBlocking {
        // Create some test data
        val category = ConfigRepository.addCategory()
        category.name = "Test Category"
        category.merchants = mutableListOf("Merchant1", "Merchant2")
        ConfigRepository.updateCategory(category)

        val sender = ConfigRepository.addSender()
        sender.name = "Test Bank"
        sender.addresses = mutableListOf("12345")
        sender.rules = mutableListOf(
            com.example.banksmstracker.data.Rule(pattern = "Test Rule")
        )
        ConfigRepository.updateSender(sender)

        // Export config
        val json = ConfigRepository.exportConfigJson()

        assertNotNull(json)
        assertTrue(json.isNotEmpty())
        assertTrue(json.contains("Test Category"))
        assertTrue(json.contains("Test Bank"))
        assertTrue(json.contains("Merchant1"))
        assertTrue(json.contains("12345"))
        assertTrue(json.contains("Test Rule"))
    }

    @Test
    @DisplayName("exportConfigJson_isValidJsonFormat")
    fun exportConfigJsonIsValidJsonFormat() = runBlocking {
        val category = ConfigRepository.addCategory()
        category.name = "Category"
        ConfigRepository.updateCategory(category)

        val json = ConfigRepository.exportConfigJson()

        // Should parse as valid JSON
        val parsed = Json.parseToJsonElement(json)
        assertNotNull(parsed)

        // Should have categories array
        val jsonObject = parsed.jsonObject
        assertTrue(jsonObject.containsKey("categories"))
        assertTrue(jsonObject.containsKey("senders"))
    }

    @Test
    @DisplayName("exportConfigJson_prettyPrint_formatIsReadable")
    fun exportConfigJsonPrettyPrintFormatIsReadable() = runBlocking {
        val category = ConfigRepository.addCategory()
        category.name = "Pretty Print Test"
        ConfigRepository.updateCategory(category)

        val prettyJson = ConfigRepository.exportConfigJson(pretty = true)
        val compactJson = ConfigRepository.exportConfigJson(pretty = false)

        // Pretty print should have newlines
        assertTrue(prettyJson.contains("\n"))
        // Compact should be shorter
        assertTrue(compactJson.length < prettyJson.length)
    }

    @Test
    @DisplayName("shareConfigFile_createsFileWithValidJson")
    fun shareConfigFileCreatesFileWithValidJson() = runBlocking {
        // Create test data
        val category = ConfigRepository.addCategory()
        category.name = "Export Category"
        category.merchants = mutableListOf("Exported Merchant")
        ConfigRepository.updateCategory(category)

        val sender = ConfigRepository.addSender()
        sender.name = "Export Bank"
        sender.addresses = mutableListOf("EXPORT123")
        ConfigRepository.updateSender(sender)

        // Share config file
        val (file, uri) = ConfigRepository.shareConfigFile(context, "test_config.json")

        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.canRead())
        assertNotNull(uri)

        // Verify file content
        val fileContent = file.readText()
        assertTrue(fileContent.isNotEmpty())
        assertTrue(fileContent.contains("Export Category"))
        assertTrue(fileContent.contains("Export Bank"))

        // Clean up
        file.delete()
    }

    @Test
    @DisplayName("shareConfigFile_filePathIsInCacheDir")
    fun shareConfigFileFilePathIsInCacheDir() = runBlocking {
        val category = ConfigRepository.addCategory()
        category.name = "Path Test"
        ConfigRepository.updateCategory(category)

        val (file, _) = ConfigRepository.shareConfigFile(context, "path_test.json")

        val cacheDir = context.cacheDir
        assertTrue(file.absolutePath.startsWith(cacheDir.absolutePath))

        // Clean up
        file.delete()
    }

    @Test
    @DisplayName("shareConfigFile_uriIsValid")
    fun shareConfigFileUriIsValid() = runBlocking {
        val category = ConfigRepository.addCategory()
        category.name = "URI Test"
        ConfigRepository.updateCategory(category)

        val (file, uri) = ConfigRepository.shareConfigFile(context, "uri_test.json")

        assertNotNull(uri)
        assertEquals("content", uri.scheme)
        assertTrue(uri.toString().contains("fileprovider"))

        // Clean up
        file.delete()
    }

    @Test
    @DisplayName("shareConfigFile_jsonMatchesExport")
    fun shareConfigFileJsonMatchesExport() = runBlocking {
        val category = ConfigRepository.addCategory()
        category.name = "Match Test"
        category.merchants = mutableListOf("Merchant")
        ConfigRepository.updateCategory(category)

        val exportJson = ConfigRepository.exportConfigJson()
        val (file, _) = ConfigRepository.shareConfigFile(context, "match_test.json")

        val fileJson = file.readText()

        // Both should contain the same data
        assertTrue(exportJson.contains("Match Test"))
        assertTrue(fileJson.contains("Match Test"))
        assertTrue(exportJson.contains("Merchant"))
        assertTrue(fileJson.contains("Merchant"))

        // Clean up
        file.delete()
    }

    @Test
    @DisplayName("exportConfigJson_includesAllCategoriesAndSenders")
    fun exportConfigJsonIncludesAllCategoriesAndSenders() = runBlocking {
        // Create multiple categories and senders
        val cat1 = ConfigRepository.addCategory()
        cat1.name = "Category 1"
        cat1.merchants = mutableListOf("Merchant 1")
        ConfigRepository.updateCategory(cat1)

        val cat2 = ConfigRepository.addCategory()
        cat2.name = "Category 2"
        cat2.merchants = mutableListOf("Merchant 2", "Merchant 3")
        ConfigRepository.updateCategory(cat2)

        val sender1 = ConfigRepository.addSender()
        sender1.name = "Sender 1"
        sender1.addresses = mutableListOf("11111")
        ConfigRepository.updateSender(sender1)

        val sender2 = ConfigRepository.addSender()
        sender2.name = "Sender 2"
        sender2.addresses = mutableListOf("22222", "33333")
        ConfigRepository.updateSender(sender2)

        val json = ConfigRepository.exportConfigJson()

        // Verify all data is present
        assertTrue(json.contains("Category 1"))
        assertTrue(json.contains("Category 2"))
        assertTrue(json.contains("Merchant 1"))
        assertTrue(json.contains("Merchant 2"))
        assertTrue(json.contains("Merchant 3"))
        assertTrue(json.contains("Sender 1"))
        assertTrue(json.contains("Sender 2"))
        assertTrue(json.contains("11111"))
        assertTrue(json.contains("22222"))
        assertTrue(json.contains("33333"))
    }

    @Test
    @DisplayName("exportConfigJson_withEmptyConfig_returnsValidEmptyJson")
    fun exportConfigJsonWithEmptyConfigReturnsValidEmptyJson() = runBlocking {
        // Reset to empty state (just load, don't add anything)
        val json = ConfigRepository.exportConfigJson()

        assertNotNull(json)
        // Should be valid JSON even if empty
        val parsed = Json.parseToJsonElement(json)
        assertNotNull(parsed)

        val jsonObject = parsed.jsonObject
        assertTrue(jsonObject.containsKey("categories"))
        assertTrue(jsonObject.containsKey("senders"))
    }
}
