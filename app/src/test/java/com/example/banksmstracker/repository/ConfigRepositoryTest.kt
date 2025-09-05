package com.example.banksmstracker.repository

import android.app.Application
import android.content.res.AssetManager
import com.example.banksmstracker.serializer.ConfigLoader
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.assertThrows
import java.io.FileNotFoundException

@ExtendWith(MockitoExtension::class) // For Mockito integration with JUnit 5
class ConfigRepositoryTest {

    @Mock
    private lateinit var mockApplication: Application

    @Mock
    private lateinit var mockAssetManager: AssetManager

    // This config is used by tests within the nested class
    private val testJsonConfig = """
    {
        "senders": [
            {
                "name": "Test Bank",
                "addresses": ["12345", "67890"],
                "rules": [
                    {"regex": "Payment of (\\d+\\.\\d{2}) made"},
                    {"regex": "Spent (\\d+\\.\\d{2})"}
                ]
            }
        ],
        "categories": [
            {
                "name": "Groceries",
                "merchants": ["supermarket", "grocery"]
            }
        ]
    }
    """

    // This test should run BEFORE any setup that loads a config
    @Test
    fun config_throwsIllegalStateException_whenAccessedBeforeLoad() {
        ConfigRepository.reset() // Ensure clean state
        assertThrows<IllegalStateException> {
            ConfigRepository.config
        }
    }

    @Nested
    inner class WhenConfigDependenciesAreMocked {

        @BeforeEach
        fun setUp() {
            // Reset singleton for each test in this nested class
            ConfigRepository.reset()
            `when`(mockApplication.assets).thenReturn(mockAssetManager)
        }

        @Test
        fun load_parsesConfigCorrectly() {
            val inputStream = ByteArrayInputStream(testJsonConfig.toByteArray())
            `when`(mockAssetManager.open("default_rules.json")).thenReturn(inputStream)

            ConfigRepository.load(mockApplication)
            val config = ConfigRepository.config
            assertNotNull(config)
            assertEquals(1, config.senders.size)
            assertEquals("Test Bank", config.senders[0].name)
            assertEquals(2, config.senders[0].addresses.size)
            assertEquals("12345", config.senders[0].addresses[0])
            assertEquals(2, config.senders[0].rules.size)
            assertEquals("""Payment of (\d+\.\d{2}) made""", config.senders[0].rules[0].regex)
            assertEquals("""Spent (\d+\.\d{2})""", config.senders[0].rules[1].regex)
            assertEquals(1, config.categories.size)
            assertEquals("Groceries", config.categories[0].name)
        }

        @Test
        fun load_isIdempotent() {
            val inputStream1 = ByteArrayInputStream(testJsonConfig.toByteArray())
            `when`(mockAssetManager.open("default_rules.json")).thenReturn(inputStream1)

            ConfigRepository.load(mockApplication)
            val firstConfigInstance = ConfigRepository.config

            // Second load attempt
            val inputStream2 = ByteArrayInputStream(testJsonConfig.toByteArray())
            // Mockito by default will use the last stubbing if called again for the same method.
            // If we wanted to ensure it's a new stream for a second real load (if not idempotent),
            // we might need a more complex stubbing, but for idempotency, just ensuring
            // open() is called once is key.
            // `when`(mockAssetManager.open("default_rules.json")).thenReturn(inputStream2) // Not strictly needed if open isn't called again

            ConfigRepository.load(mockApplication)
            val secondConfigInstance = ConfigRepository.config

            assertSame(firstConfigInstance, secondConfigInstance, "Config should be loaded only once and be the same instance")
            verify(mockAssetManager, times(1)).open("default_rules.json")
        }

        @Test
        fun load_handlesEmptyConfig() {
            val emptyConfigJson = """{"senders":[], "categories":[]}"""
            val inputStream = ByteArrayInputStream(emptyConfigJson.toByteArray())
            `when`(mockAssetManager.open("default_rules.json")).thenReturn(inputStream)

            ConfigRepository.load(mockApplication)
            val config = ConfigRepository.config
            assertNotNull(config)
            assertEquals(0, config.senders.size)
            assertEquals(0, config.categories.size)
        }

        @Test
        fun load_handlesAssetManagerIOException() {
            `when`(
                mockAssetManager.open("default_rules.json")
            ).thenThrow(
                IOException("Test IO Exception")
            )

            val exception = assertThrows<RuntimeException> {
                ConfigRepository.load(mockApplication)
            }
            assertEquals("Failed to open config", exception.message)
            assertNotNull(exception.cause)
            assertEquals(IOException::class.java, exception.cause!!::class.java)
            assertEquals("Test IO Exception", exception.cause!!.message)
        }

        @Test
        fun load_handlesAssetManagerSerializationException() {
            `when`(
                mockAssetManager.open("default_rules.json")
            ).thenThrow(
                SerializationException("Test SerializationException Exception")
            )

            val exception = assertThrows<RuntimeException> {
                ConfigRepository.load(mockApplication)
            }
            assertEquals("Failed to deserialize config", exception.message)
            assertNotNull(exception.cause)
            assertEquals(SerializationException::class.java, exception.cause!!::class.java)
            assertEquals("Test SerializationException Exception", exception.cause!!.message)
        }

        @Test
        fun load_handlesAssetManagerFileNotFoundException() {
            `when`(
                mockAssetManager.open("default_rules.json")
            ).thenThrow(
                FileNotFoundException("Test FileNotFoundException Exception")
            )

            val exception = assertThrows<RuntimeException> {
                ConfigRepository.load(mockApplication)
            }
            assertEquals("Failed to find config file", exception.message)
            assertNotNull(exception.cause)
            assertEquals(FileNotFoundException::class.java, exception.cause!!::class.java)
            assertEquals("Test FileNotFoundException Exception", exception.cause!!.message)
        }
    }
}
