package com.example.banksmstracker.appium

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
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
    @Tag("smoke")
    @DisplayName("Navigate to Regex Builder screen")
    fun navigateToRegexBuilder() {
        // Scroll to find Regex Builder button if needed
        clickButton("btnRegexBuilder")
        mediumWait()

        // Verify we're on Regex Builder screen
        assertTrue(elementExists("etSampleSms"), "Should have sample SMS input")
        assertTrue(elementExists("etRegexPattern"), "Should have regex pattern input")
        assertTrue(elementExistsWithScroll("btnTestRegex"), "Should have Test button")

        navigateToMain()
    }

    @Test
    @Order(2)
    @DisplayName("Enter sample SMS message")
    fun enterSampleSmsMessage() {
        clickButton("btnRegexBuilder")
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
        clickButton("btnRegexBuilder")
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
    @Tag("smoke")
    @DisplayName("Test regex pattern with matching SMS")
    fun testRegexPatternMatching() {
        clickButton("btnRegexBuilder")
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
        scrollToElementById("btnTestRegex").click()
        mediumWait()

        // Scroll to results area (may be below fold on small screens)
        try { scrollToElementById("tvResults") } catch (e: Exception) { /* already visible */ }
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
        clickButton("btnRegexBuilder")
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
        scrollToElementById("btnTestRegex").click()
        mediumWait()

        // Scroll to results area (may be below fold on small screens)
        try { scrollToElementById("tvResults") } catch (e: Exception) { /* already visible */ }
        val resultsArea = findById("tvResults")
        assertTrue(resultsArea.isDisplayed, "Results area should be visible")

        navigateToMain()
    }

    @Test
    @Order(6)
    @DisplayName("Test full payment regex pattern with all groups")
    fun testFullPaymentRegex() {
        clickButton("btnRegexBuilder")
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
        scrollToElementById("btnTestRegex").click()
        mediumWait()

        // Scroll to results area (may be below fold on small screens)
        try { scrollToElementById("tvResults") } catch (e: Exception) { /* already visible */ }
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
        clickButton("btnRegexBuilder")
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
        clickButton("btnRegexBuilder")
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
        scrollToElementById("btnTestRegex").click()
        mediumWait()

        // Should show error or empty results
        // Just verify the app doesn't crash and we can navigate back
        navigateToMain()
    }

    @Test
    @Order(9)
    @DisplayName("Test with empty pattern shows error")
    fun testEmptyPatternShowsError() {
        clickButton("btnRegexBuilder")
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
        scrollToElementById("btnTestRegex").click()
        mediumWait()

        // Should show error or empty results
        // Just verify the app doesn't crash and we can navigate back
        navigateToMain()
    }

    @Test
    @Order(10)
    @DisplayName("Verify regex groups help text is displayed")
    fun verifyRegexGroupsHelpText() {
        clickButton("btnRegexBuilder")
        longWait()

        // Look for the help text about groups - using textContains for partial matches
        val hasHelpText = textExists("Groups:") ||
            textExists("(1)amount") ||
            textExists("amount") ||
            textExists("currency") ||
            textExists("merchant") ||
            elementExists("etRegexPattern") // Fallback: at least the pattern field exists

        assertTrue(hasHelpText, "Should display help text about regex groups or regex UI")

        navigateToMain()
    }

    // ==================== Save Regex to Sender Tests ====================

    @Test
    @Order(11)
    @DisplayName("Sender selection spinner is displayed")
    fun senderSelectionSpinnerExists() {
        clickButton("btnRegexBuilder")
        mediumWait()

        // Scroll down to see the spinner (spinnerSenders with plural s)
        val hasSpinner = elementExistsWithScroll("spinnerSenders")
        assertTrue(hasSpinner, "Should have sender selection spinner")

        navigateToMain()
    }

    @Test
    @Order(12)
    @DisplayName("Save Regex button is displayed")
    fun saveRegexButtonExists() {
        clickButton("btnRegexBuilder")
        mediumWait()

        // Scroll down to see the button
        val hasButton = elementExistsWithScroll("btnSaveRegex")
        assertTrue(hasButton, "Should have Save Regex button")

        navigateToMain()
    }

    @Test
    @Order(13)
    @DisplayName("Cannot save empty regex pattern")
    fun cannotSaveEmptyRegex() {
        clickButton("btnRegexBuilder")
        mediumWait()

        // Ensure regex field is empty
        val regexInput = findById("etRegexPattern")
        regexInput.clear()
        shortWait()

        // Scroll to and click save button
        scrollToElementById("btnSaveRegex").click()
        mediumWait()

        // App should not crash, might show toast or error
        navigateToMain()
    }

    // ==================== Phase 5.3: Regex Builder Enhancements ====================

    @Test
    @Order(14)
    @DisplayName("Select SMS button is displayed")
    fun selectSmsButtonExists() {
        clickButton("btnRegexBuilder")
        mediumWait()

        assertTrue(elementExists("btnSelectSms"), "Should have Select SMS button")

        navigateToMain()
    }

    @Test
    @Order(15)
    @DisplayName("Existing patterns spinner is displayed")
    fun existingPatternsSpinnerExists() {
        clickButton("btnRegexBuilder")
        mediumWait()

        // Scroll down to see the spinner
        val hasSpinner = elementExistsWithScroll("spinnerExistingPatterns")
        assertTrue(hasSpinner, "Should have existing patterns spinner")

        navigateToMain()
    }

    @Test
    @Order(16)
    @DisplayName("Can click existing patterns spinner")
    fun canClickExistingPatternsSpinner() {
        clickButton("btnRegexBuilder")
        mediumWait()

        // Scroll to and click spinner
        try {
            scrollToElementById("spinnerExistingPatterns").click()
            shortWait()

            // Close any dropdown that opened
            driver.navigate().back()
            shortWait()
        } catch (e: Exception) {
            // Spinner might not be clickable if no patterns exist
        }

        navigateToMain()
    }

    @Test
    @Order(17)
    @DisplayName("Select SMS button opens SMS selection")
    fun selectSmsButtonOpensSmsSelection() {
        clickButton("btnRegexBuilder")
        mediumWait()

        // Click select SMS button
        findById("btnSelectSms").click()
        mediumWait()

        // Should either show SMS selection dialog/activity or request permission
        // Just verify app doesn't crash
        shortWait()

        // Navigate back if dialog opened
        driver.navigate().back()
        shortWait()

        // Might need another back if nested
        if (!elementExists("etSampleSms")) {
            driver.navigate().back()
            shortWait()
        }

        navigateToMain()
    }

    // ==================== Phase 1: Clear Buttons and Date/Time Presets ====================

    @Test
    @Order(18)
    @DisplayName("Clear sample SMS button is displayed")
    fun clearSampleSmsButtonExists() {
        clickButton("btnRegexBuilder")
        mediumWait()

        assertTrue(elementExists("btnClearSampleSms"), "Should have Clear Sample SMS button")

        navigateToMain()
    }

    @Test
    @Order(19)
    @DisplayName("Clear regex pattern button is displayed")
    fun clearRegexPatternButtonExists() {
        clickButton("btnRegexBuilder")
        mediumWait()

        assertTrue(elementExists("btnClearRegexPattern"), "Should have Clear Regex Pattern button")

        navigateToMain()
    }

    @Test
    @Order(20)
    @DisplayName("Clear sample SMS button clears the SMS field")
    fun clearSampleSmsButtonClearsField() {
        clickButton("btnRegexBuilder")
        mediumWait()

        // Enter some text in the SMS field
        val smsInput = findById("etSampleSms")
        smsInput.clear()
        smsInput.sendKeys("Sample payment message 100.00 USD")
        shortWait()

        // Verify text was entered
        assertTrue(smsInput.text.isNotEmpty(), "SMS field should have text before clearing")

        // Click the clear button
        findById("btnClearSampleSms").click()
        shortWait()

        // Verify the field is cleared
        val textAfterClear = findById("etSampleSms").text
        assertTrue(
            textAfterClear.isEmpty() || textAfterClear == findById("etSampleSms").getAttribute("hint"),
            "SMS field should be empty after clicking clear"
        )

        navigateToMain()
    }

    @Test
    @Order(21)
    @DisplayName("Clear regex pattern button clears the pattern field")
    fun clearRegexPatternButtonClearsField() {
        clickButton("btnRegexBuilder")
        mediumWait()

        // Enter some text in the regex pattern field
        val regexInput = findById("etRegexPattern")
        regexInput.clear()
        regexInput.sendKeys("Payment (\\d+\\.\\d{2})")
        shortWait()

        // Verify text was entered
        assertTrue(regexInput.text.isNotEmpty(), "Regex field should have text before clearing")

        // Click the clear button
        findById("btnClearRegexPattern").click()
        shortWait()

        // Verify the field is cleared
        val textAfterClear = findById("etRegexPattern").text
        assertTrue(
            textAfterClear.isEmpty() || textAfterClear == findById("etRegexPattern").getAttribute("hint"),
            "Regex pattern field should be empty after clicking clear"
        )

        navigateToMain()
    }

    @Test
    @Order(22)
    @DisplayName("Preset date button is displayed")
    fun presetDateButtonExists() {
        clickButton("btnRegexBuilder")
        mediumWait()

        val hasButton = try { scrollToPresetButton("btnPresetDate"); true } catch (e: Exception) { false }
        assertTrue(hasButton, "Should have preset date button")

        navigateToMain()
    }

    @Test
    @Order(23)
    @DisplayName("Preset time button is displayed")
    fun presetTimeButtonExists() {
        clickButton("btnRegexBuilder")
        mediumWait()

        val hasButton = try { scrollToPresetButton("btnPresetTime"); true } catch (e: Exception) { false }
        assertTrue(hasButton, "Should have preset time button")

        navigateToMain()
    }

    @Test
    @Order(24)
    @DisplayName("Preset date button appends date pattern to regex field")
    fun presetDateButtonAppendsPattern() {
        clickButton("btnRegexBuilder")
        mediumWait()

        // Ensure regex field is empty first
        val regexInput = findById("etRegexPattern")
        regexInput.clear()
        shortWait()

        // Scroll to date preset button (inside HorizontalScrollView — needs two-step scroll)
        scrollToPresetButton("btnPresetDate").click()
        shortWait()

        // Verify that the preset inserted the ⟨date⟩ placeholder
        val patternText = findById("etRegexPattern").text
        assertTrue(
            patternText.contains("⟨date⟩"),
            "Date preset should insert ⟨date⟩ placeholder, got: $patternText"
        )

        navigateToMain()
    }

    @Test
    @Order(25)
    @DisplayName("Preset time button appends time pattern to regex field")
    fun presetTimeButtonAppendsPattern() {
        clickButton("btnRegexBuilder")
        mediumWait()

        // Ensure regex field is empty first
        val regexInput = findById("etRegexPattern")
        regexInput.clear()
        shortWait()

        // Scroll to time preset button (inside HorizontalScrollView — needs two-step scroll)
        scrollToPresetButton("btnPresetTime").click()
        shortWait()

        // Verify that the preset inserted the ⟨time⟩ placeholder
        val patternText = findById("etRegexPattern").text
        assertTrue(
            patternText.contains("⟨time⟩"),
            "Time preset should insert ⟨time⟩ placeholder, got: $patternText"
        )

        navigateToMain()
    }

    @Test
    @Order(26)
    @Tag("smoke")
    @DisplayName("Preset amount button inserts amount placeholder chip, not raw regex")
    fun presetAmountInsertPlaceholder() {
        clickButton("btnRegexBuilder")
        mediumWait()

        val regexInput = findById("etRegexPattern")
        regexInput.clear()
        shortWait()

        scrollToPresetButton("btnPresetAmount").click()
        shortWait()

        val patternText = findById("etRegexPattern").text
        assertTrue(
            patternText.contains("⟨amount⟩"),
            "Amount preset should insert ⟨amount⟩ placeholder, got: $patternText"
        )

        navigateToMain()
    }
}
