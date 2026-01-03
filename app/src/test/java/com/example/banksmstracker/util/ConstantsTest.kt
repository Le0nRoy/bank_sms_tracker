package com.example.banksmstracker.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Constants Tests")
class ConstantsTest {

    @Nested
    @DisplayName("RegexGroups")
    inner class RegexGroupsTest {

        @Test
        @DisplayName("getGroupName returns correct names for known indices")
        fun `getGroupName returns correct names for known indices`() {
            assertEquals("amount", Constants.RegexGroups.getGroupName(1))
            assertEquals("currency", Constants.RegexGroups.getGroupName(2))
            assertEquals("card", Constants.RegexGroups.getGroupName(3))
            assertEquals("merchant", Constants.RegexGroups.getGroupName(4))
            assertEquals("timestamp", Constants.RegexGroups.getGroupName(5))
            assertEquals("balance", Constants.RegexGroups.getGroupName(6))
        }

        @Test
        @DisplayName("getGroupName returns 'extra' for unknown indices")
        fun `getGroupName returns extra for unknown indices`() {
            assertEquals("extra", Constants.RegexGroups.getGroupName(0))
            assertEquals("extra", Constants.RegexGroups.getGroupName(7))
            assertEquals("extra", Constants.RegexGroups.getGroupName(100))
            assertEquals("extra", Constants.RegexGroups.getGroupName(-1))
        }

        @Test
        @DisplayName("GROUP_NAMES map contains all expected entries")
        fun `GROUP_NAMES map contains all expected entries`() {
            assertEquals(6, Constants.RegexGroups.GROUP_NAMES.size)
            assertEquals("amount", Constants.RegexGroups.GROUP_NAMES[1])
            assertEquals("balance", Constants.RegexGroups.GROUP_NAMES[6])
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
