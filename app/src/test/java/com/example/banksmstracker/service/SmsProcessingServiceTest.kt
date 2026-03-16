package com.example.banksmstracker.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SmsProcessingService] lifecycle flag and routing logic.
 *
 * The `isRunning()` flag is used by [com.example.banksmstracker.parser.SmsReceiver]
 * to decide whether to delegate real-time SMS handling to the service or process it
 * directly as a fallback.
 */
@DisplayName("SmsProcessingService — isRunning flag")
class SmsProcessingServiceTest {

    @BeforeEach
    fun resetFlag() {
        // Ensure flag is false before each test (uses reflection to access private setter)
        setRunningFlag(false)
    }

    @AfterEach
    fun cleanUp() {
        setRunningFlag(false)
    }

    @Test
    @DisplayName("isRunning() returns false before service starts")
    fun `isRunning returns false before service starts`() {
        assertFalse(SmsProcessingService.isRunning())
    }

    @Test
    @DisplayName("isRunning() returns true after flag is set")
    fun `isRunning returns true after flag is set`() {
        setRunningFlag(true)
        assertTrue(SmsProcessingService.isRunning())
    }

    @Test
    @DisplayName("isRunning() returns false after flag is cleared")
    fun `isRunning returns false after flag is cleared`() {
        setRunningFlag(true)
        assertTrue(SmsProcessingService.isRunning())
        setRunningFlag(false)
        assertFalse(SmsProcessingService.isRunning())
    }

    /**
     * Accesses the static `isRunning` backing field on [SmsProcessingService] via
     * reflection so that tests can control the flag without starting a real Service.
     *
     * Kotlin compiles `@Volatile private var isRunning` in a companion object to a
     * static field on the outer class (`SmsProcessingService.isRunning`).
     */
    private fun setRunningFlag(value: Boolean) {
        val field = SmsProcessingService::class.java.getDeclaredField("isRunning")
        field.isAccessible = true
        field.set(null, value)
    }
}
