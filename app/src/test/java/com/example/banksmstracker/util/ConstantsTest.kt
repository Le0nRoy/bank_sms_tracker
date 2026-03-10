package com.example.banksmstracker.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Constants Tests")
class ConstantsTest {

    @Nested
    @DisplayName("RegexGroups")
    inner class RegexGroupsTest {

        @Test
        @DisplayName("Named group constants have correct values")
        fun `named group constants have correct values`() {
            assertEquals("amount", Constants.RegexGroups.AMOUNT)
            assertEquals("currency", Constants.RegexGroups.CURRENCY)
            assertEquals("card", Constants.RegexGroups.CARD)
            assertEquals("merchant", Constants.RegexGroups.MERCHANT)
            assertEquals("date", Constants.RegexGroups.DATE)
            assertEquals("time", Constants.RegexGroups.TIME)
            assertEquals("balance", Constants.RegexGroups.BALANCE)
        }

        @Test
        @DisplayName("Named groups work with Java regex API")
        fun `named groups work with java regex api`() {
            val pattern = Regex("(?<amount>\\d+\\.\\d{2})\\s+(?<currency>[A-Z]{3})")
            val match = pattern.find("123.45 USD")
            assertNotNull(match)
            assertEquals("123.45", match!!.groups[Constants.RegexGroups.AMOUNT]?.value)
            assertEquals("USD", match.groups[Constants.RegexGroups.CURRENCY]?.value)
            // Accessing non-existent group throws IllegalArgumentException
            var threwException = false
            try {
                match.groups[Constants.RegexGroups.TIME]
            } catch (e: IllegalArgumentException) {
                threwException = true
            }
            assertEquals(true, threwException)
        }
    }

    @Nested
    @DisplayName("Time Constants")
    inner class TimeConstantsTest {

        @Test
        @DisplayName("Start of day constants are zero")
        fun `start of day constants are zero`() {
            assertEquals(0, Constants.Time.START_OF_DAY_HOUR)
            assertEquals(0, Constants.Time.START_OF_DAY_MINUTE)
            assertEquals(0, Constants.Time.START_OF_DAY_SECOND)
            assertEquals(0, Constants.Time.START_OF_DAY_MILLIS)
        }

        @Test
        @DisplayName("End of day constants are correct")
        fun `end of day constants are correct`() {
            assertEquals(23, Constants.Time.END_OF_DAY_HOUR)
            assertEquals(59, Constants.Time.END_OF_DAY_MINUTE)
            assertEquals(59, Constants.Time.END_OF_DAY_SECOND)
            assertEquals(999, Constants.Time.END_OF_DAY_MILLIS)
        }
    }

    @Nested
    @DisplayName("Request Codes")
    inner class RequestCodesTest {

        @Test
        @DisplayName("SMS permission request code is defined")
        fun `SMS permission request code is defined`() {
            assertEquals(100, Constants.RequestCodes.SMS_PERMISSION)
        }
    }

    @Nested
    @DisplayName("Formatting")
    inner class FormattingTest {

        @Test
        @DisplayName("Separator has correct length")
        fun `separator has correct length`() {
            assertEquals(50, Constants.Formatting.SEPARATOR_LENGTH)
            assertEquals(50, Constants.Formatting.SEPARATOR.length)
        }

        @Test
        @DisplayName("Separator contains only equals signs")
        fun `separator contains only equals signs`() {
            assert(Constants.Formatting.SEPARATOR.all { it == '=' })
        }
    }
}
