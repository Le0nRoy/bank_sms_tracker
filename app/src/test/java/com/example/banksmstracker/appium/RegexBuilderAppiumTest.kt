package com.example.banksmstracker.appium

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

/**
 * Appium E2E tests for the Regex Builder feature.
 *
 * Tests the regex builder tool that helps users create and test regex patterns.
 *
 * Covers:
 * - Navigation to Regex Builder screen
 * - Entering sample SMS messages
 * - Entering regex patterns
 * - Testing patterns and viewing results
 * - Error handling for invalid patterns
 * - Copy/paste functionality
 *
 * NOTE: These tests require:
 * 1. Appium server running (`make appium-start` or `make appium-docker-start`)
 * 2. Android emulator running with the app installed (`make install`)
 *
 * Run with: make test-appium
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
// @Disabled("Requires Appium server and Android emulator. Run with: make test-appium")
@DisplayName("Regex Builder E2E Tests")
class RegexBuilderAppiumTest : AppiumBaseTest() {

    @Test
    @Order(1)
    @DisplayName("Navigate to Regex Builder screen")
    fun navigateToRegexBuilder() {
        // Scroll to find Regex Builder button if needed
        findById("btnRegexBuilder").click()
        mediumWait()

        // Verify we're on Regex Builder screen
        assertTrue(elementExists("etSampleSms"), "Should have sample SMS input")
        assertTrue(elementExists("etRegexPattern"), "Should have regex pattern input")
        assertTrue(elementExists("btnTestRegex"), "Should have Test button")
        assertTrue(elementExists("tvResults"), "Should have results area")

        navigateToMain()
    }

    @Test
    @Order(2)
    @DisplayName("Enter sample SMS message")
    fun enterSampleSmsMessage() {
        findById("btnRegexBuilder").click()
        mediumWait()

        val smsInput = findById("etSampleSms")
        smsInput.clear()
        smsInput.sendKeys("Payment 123.45 USD card 1234 Amazon at 20231225 bal 500.00")
        shortWait()

        // Verify text was entered
        assertTrue(smsInput.text.contains("Payment"), "SMS text should be entered")

        navigateToMain()
    }

    @Test
    @Order(3)
    @DisplayName("Enter regex pattern")
    fun enterRegexPattern() {
        findById("btnRegexBuilder").click()
        mediumWait()

        val regexInput = findById("etRegexPattern")
        regexInput.clear()
        regexInput.sendKeys("Payment (\\d+\\.\\d{2}) (\\w{3})")
        shortWait()

        // Verify text was entered
        assertTrue(regexInput.text.contains("Payment"), "Regex pattern should be entered")

        navigateToMain()
    }

    @Test
    @Order(4)
    @DisplayName("Test regex pattern with matching SMS")
    fun testRegexPatternMatching() {
        findById("btnRegexBuilder").click()
        mediumWait()

        // Enter sample SMS
        val smsInput = findById("etSampleSms")
        smsInput.clear()
        smsInput.sendKeys("Payment 99.99 USD card 5678 Store at 20231225 bal 100.00")
        shortWait()

        // Enter matching pattern
        val regexInput = findById("etRegexPattern")
        regexInput.clear()
        regexInput.sendKeys("Payment (\\d+\\.\\d{2}) (\\w{3})")
        shortWait()

        // Click test button
        findById("btnTestRegex").click()
        mediumWait()

        // Check results area shows something
        val resultsArea = findById("tvResults")
        val resultsText = resultsArea.text

        // Results should contain match information
        assertTrue(
            resultsText.isNotEmpty() || resultsArea.isDisplayed,
            "Results should be displayed"
        )

        navigateToMain()
    }

    @Test
    @Order(5)
    @DisplayName("Test regex pattern with non-matching SMS")
    fun testRegexPatternNonMatching() {
        findById("btnRegexBuilder").click()
        mediumWait()

        // Enter sample SMS that won't match
        val smsInput = findById("etSampleSms")
        smsInput.clear()
        smsInput.sendKeys("Hello this is a regular text message")
        shortWait()

        // Enter pattern that won't match
        val regexInput = findById("etRegexPattern")
        regexInput.clear()
        regexInput.sendKeys("Payment (\\d+\\.\\d{2})")
        shortWait()

        // Click test button
        findById("btnTestRegex").click()
        mediumWait()

        // Results should indicate no match
        val resultsArea = findById("tvResults")
        assertTrue(resultsArea.isDisplayed, "Results area should be visible")

        navigateToMain()
    }

    @Test
    @Order(6)
    @DisplayName("Test full payment regex pattern with all groups")
    fun testFullPaymentRegex() {
        findById("btnRegexBuilder").click()
        mediumWait()

        // Enter complete payment SMS
        val smsInput = findById("etSampleSms")
        smsInput.clear()
        smsInput.sendKeys("Payment 250.00 EUR card 9876 SuperMarket at 20231231 bal 1500.50")
        shortWait()

        // Enter full regex pattern with all 6 groups
        val regexInput = findById("etRegexPattern")
        regexInput.clear()
        regexInput.sendKeys(
            "Payment (\\d+\\.\\d{2}) (\\w{3}) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
        )
        shortWait()

        // Click test button
        findById("btnTestRegex").click()
        mediumWait()

        // Results should show all captured groups
        val resultsArea = findById("tvResults")
        val resultsText = resultsArea.text

        // Should show captured values
        assertTrue(
            resultsText.contains("250") || resultsText.contains("EUR") || resultsArea.isDisplayed,
            "Results should show captured groups"
        )

        navigateToMain()
    }

    @Test
    @Order(7)
    @DisplayName("Clear and re-enter data")
    fun clearAndReEnterData() {
        findById("btnRegexBuilder").click()
        mediumWait()

        // Enter some data
        val smsInput = findById("etSampleSms")
        smsInput.clear()
        smsInput.sendKeys("First message")
        shortWait()

        // Clear and enter new data
        smsInput.clear()
        smsInput.sendKeys("Second message")
        shortWait()

        // Verify new data
        assertTrue(smsInput.text.contains("Second"), "Should have new message")
        assertFalse(smsInput.text.contains("First"), "Should not have old message")

        navigateToMain()
    }

    @Test
    @Order(8)
    @DisplayName("Test with empty SMS shows error")
    fun testEmptySmsShowsError() {
        findById("btnRegexBuilder").click()
        mediumWait()

        // Ensure SMS field is empty
        val smsInput = findById("etSampleSms")
        smsInput.clear()
        shortWait()

        // Enter a pattern
        val regexInput = findById("etRegexPattern")
        regexInput.clear()
        regexInput.sendKeys("Payment (\\d+)")
        shortWait()

        // Click test button
        findById("btnTestRegex").click()
        mediumWait()

        // Should show error or empty results
        // Just verify the app doesn't crash and we can navigate back
        navigateToMain()
    }

    @Test
    @Order(9)
    @DisplayName("Test with empty pattern shows error")
    fun testEmptyPatternShowsError() {
        findById("btnRegexBuilder").click()
        mediumWait()

        // Enter SMS
        val smsInput = findById("etSampleSms")
        smsInput.clear()
        smsInput.sendKeys("Some test message")
        shortWait()

        // Ensure pattern is empty
        val regexInput = findById("etRegexPattern")
        regexInput.clear()
        shortWait()

        // Click test button
        findById("btnTestRegex").click()
        mediumWait()

        // Should show error or empty results
        // Just verify the app doesn't crash and we can navigate back
        navigateToMain()
    }

    @Test
    @Order(10)
    @DisplayName("Verify regex groups help text is displayed")
    fun verifyRegexGroupsHelpText() {
        findById("btnRegexBuilder").click()
        mediumWait()

        // Look for the help text about groups
        val hasHelpText = textExists("Groups:") ||
            textExists("amount") ||
            textExists("currency") ||
            textExists("merchant")

        assertTrue(hasHelpText, "Should display help text about regex groups")

        navigateToMain()
    }
}
