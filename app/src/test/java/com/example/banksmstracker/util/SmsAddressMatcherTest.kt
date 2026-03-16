package com.example.banksmstracker.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("SmsAddressMatcher Tests")
class SmsAddressMatcherTest {

    @Nested
    @DisplayName("matches()")
    inner class MatchesTest {

        @Test
        @DisplayName("exact match returns true")
        fun `exact match returns true`() {
            assertTrue(SmsAddressMatcher.matches("BANK", "BANK"))
        }

        @Test
        @DisplayName("case insensitive match returns true")
        fun `case insensitive match returns true`() {
            assertTrue(SmsAddressMatcher.matches("BANK", "bank"))
            assertTrue(SmsAddressMatcher.matches("bank", "BANK"))
            assertTrue(SmsAddressMatcher.matches("BaNk", "bAnK"))
        }

        @Test
        @DisplayName("configured address as substring matches")
        fun `configured address as substring matches`() {
            assertTrue(SmsAddressMatcher.matches("VM-BANK", "BANK"))
            assertTrue(SmsAddressMatcher.matches("+91BANK", "BANK"))
            assertTrue(SmsAddressMatcher.matches("AD-BANK", "BANK"))
            assertTrue(SmsAddressMatcher.matches("BANKOFAMERICA", "BANK"))
        }

        @Test
        @DisplayName("configured address as substring with case difference matches")
        fun `configured address as substring with case difference matches`() {
            assertTrue(SmsAddressMatcher.matches("VM-HDFC", "hdfc"))
            assertTrue(SmsAddressMatcher.matches("ad-sbibnk", "SBIBNK"))
        }

        @Test
        @DisplayName("non-matching addresses return false")
        fun `non-matching addresses return false`() {
            assertFalse(SmsAddressMatcher.matches("ICICI", "HDFC"))
            assertFalse(SmsAddressMatcher.matches("BANK", "CREDIT"))
            assertFalse(SmsAddressMatcher.matches("BNK", "BANK"))
        }

        @Test
        @DisplayName("empty sms address never matches")
        fun `empty sms address never matches`() {
            assertFalse(SmsAddressMatcher.matches("", "BANK"))
        }

        @Test
        @DisplayName("empty configured address always matches non-empty sms address")
        fun `empty configured address always matches non-empty sms address`() {
            assertTrue(SmsAddressMatcher.matches("BANK", ""))
            assertTrue(SmsAddressMatcher.matches("ANY", ""))
        }

        @Test
        @DisplayName("both empty strings match")
        fun `both empty strings match`() {
            assertTrue(SmsAddressMatcher.matches("", ""))
        }

        @Test
        @DisplayName("special characters in addresses")
        fun `special characters in addresses`() {
            assertTrue(SmsAddressMatcher.matches("VM-HDFC-BNK", "HDFC"))
            assertTrue(SmsAddressMatcher.matches("+919876543210", "9876543210"))
            assertTrue(SmsAddressMatcher.matches("BANK_ALERT", "BANK"))
        }

        @Test
        @DisplayName("numeric addresses match")
        fun `numeric addresses match`() {
            assertTrue(SmsAddressMatcher.matches("123456", "123456"))
            assertTrue(SmsAddressMatcher.matches("+1234567890", "1234567890"))
            assertTrue(SmsAddressMatcher.matches("VM-123456", "123456"))
        }

        @Test
        @DisplayName("partial match at different positions")
        fun `partial match at different positions`() {
            assertTrue(SmsAddressMatcher.matches("PREFIXBANKSUFFIX", "BANK"))
            assertTrue(SmsAddressMatcher.matches("BANKSUFFIX", "BANK"))
            assertTrue(SmsAddressMatcher.matches("PREFIXBANK", "BANK"))
        }
    }

    @Nested
    @DisplayName("matchesAny() with Set")
    inner class MatchesAnySetTest {

        @Test
        @DisplayName("matches when sms address matches any configured address")
        fun `matches when sms address matches any configured address`() {
            val configured = setOf("HDFC", "ICICI", "SBI")
            assertTrue(SmsAddressMatcher.matchesAny("VM-HDFC", configured))
            assertTrue(SmsAddressMatcher.matchesAny("AD-ICICI", configured))
            assertTrue(SmsAddressMatcher.matchesAny("SBIBANK", configured))
        }

        @Test
        @DisplayName("does not match when sms address matches none")
        fun `does not match when sms address matches none`() {
            val configured = setOf("HDFC", "ICICI", "SBI")
            assertFalse(SmsAddressMatcher.matchesAny("AXIS", configured))
            assertFalse(SmsAddressMatcher.matchesAny("KOTAK", configured))
        }

        @Test
        @DisplayName("empty set never matches")
        fun `empty set never matches`() {
            assertFalse(SmsAddressMatcher.matchesAny("BANK", emptySet()))
        }

        @Test
        @DisplayName("case insensitive matching with set")
        fun `case insensitive matching with set`() {
            val configured = setOf("hdfc", "icici")
            assertTrue(SmsAddressMatcher.matchesAny("VM-HDFC", configured))
            assertTrue(SmsAddressMatcher.matchesAny("AD-ICICI", configured))
        }
    }
}
