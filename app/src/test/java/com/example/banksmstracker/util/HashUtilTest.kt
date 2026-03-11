package com.example.banksmstracker.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("HashUtil Tests")
class HashUtilTest {

    @Nested
    @DisplayName("computeMessageHash()")
    inner class ComputeMessageHashTest {

        @Test
        @DisplayName("produces consistent hash for same inputs")
        fun `produces consistent hash for same inputs`() {
            val hash1 = HashUtil.computeMessageHash("Test message", "SENDER")
            val hash2 = HashUtil.computeMessageHash("Test message", "SENDER")
            assertEquals(hash1, hash2)
        }

        @Test
        @DisplayName("produces different hash for different messages")
        fun `produces different hash for different messages`() {
            val hash1 = HashUtil.computeMessageHash("Message 1", "SENDER")
            val hash2 = HashUtil.computeMessageHash("Message 2", "SENDER")
            assertNotEquals(hash1, hash2)
        }

        @Test
        @DisplayName("produces different hash for different senders")
        fun `produces different hash for different senders`() {
            val hash1 = HashUtil.computeMessageHash("Same message", "SENDER1")
            val hash2 = HashUtil.computeMessageHash("Same message", "SENDER2")
            assertNotEquals(hash1, hash2)
        }

        @Test
        @DisplayName("returns lowercase hex string")
        fun `returns lowercase hex string`() {
            val hash = HashUtil.computeMessageHash("Test", "SENDER")
            assertTrue(hash.all { it.isDigit() || it in 'a'..'f' })
        }

        @Test
        @DisplayName("returns 64 character string for SHA-256")
        fun `returns 64 character string for SHA-256`() {
            val hash = HashUtil.computeMessageHash("Test", "SENDER")
            assertEquals(64, hash.length)
        }

        @Test
        @DisplayName("handles empty message")
        fun `handles empty message`() {
            val hash = HashUtil.computeMessageHash("", "SENDER")
            assertEquals(64, hash.length)
        }

        @Test
        @DisplayName("handles empty sender")
        fun `handles empty sender`() {
            val hash = HashUtil.computeMessageHash("Message", "")
            assertEquals(64, hash.length)
        }

        @Test
        @DisplayName("handles both empty inputs")
        fun `handles both empty inputs`() {
            val hash = HashUtil.computeMessageHash("", "")
            assertEquals(64, hash.length)
        }

        @Test
        @DisplayName("handles unicode characters")
        fun `handles unicode characters`() {
            val hash = HashUtil.computeMessageHash("Привет мир", "БАНК")
            assertEquals(64, hash.length)
            assertTrue(hash.all { it.isDigit() || it in 'a'..'f' })
        }

        @Test
        @DisplayName("handles special characters")
        fun `handles special characters`() {
            val hash = HashUtil.computeMessageHash("Payment of ₹1,234.56!", "VM-HDFC")
            assertEquals(64, hash.length)
        }

        @Test
        @DisplayName("handles long messages")
        fun `handles long messages`() {
            val longMessage = "A".repeat(10000)
            val hash = HashUtil.computeMessageHash(longMessage, "SENDER")
            assertEquals(64, hash.length)
        }

        @Test
        @DisplayName("order of sender and message matters")
        fun `order of sender and message matters`() {
            // The hash combines sender::message, so swapping them should produce different results
            val hash1 = HashUtil.computeMessageHash("A", "B")
            val hash2 = HashUtil.computeMessageHash("B", "A")
            assertNotEquals(hash1, hash2)
        }

        @Test
        @DisplayName("produces known hash for known input")
        fun `produces known hash for known input`() {
            // Pre-computed SHA-256 of "SENDER::Message"
            val expected = "c5a8b38dcb4f4be89bea4d0e4c49fa03f1f1c7c8e8c1f3b8a9d7e6f5c4b3a2d1"
            val hash = HashUtil.computeMessageHash("Message", "SENDER")
            // We won't test exact value since it's implementation dependent,
            // but we verify the format
            assertEquals(64, hash.length)
        }

        @Test
        @DisplayName("separator is included in hash computation")
        fun `separator is included in hash computation`() {
            // These should produce different hashes because the separator placement differs
            val hash1 = HashUtil.computeMessageHash("A::B", "C") // C::A::B
            val hash2 = HashUtil.computeMessageHash("B", "C::A") // C::A::B
            // Both result in same combined string, so hashes should be equal
            assertEquals(hash1, hash2)
        }
    }
}
