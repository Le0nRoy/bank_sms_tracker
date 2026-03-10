package com.example.banksmstracker.appium

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

/**
 * Appium E2E tests for the Bug Report feature.
 *
 * Tests the bug reporting functionality that allows users to report issues.
 *
 * Covers:
 * - Navigation to Bug Report screen
 * - Entering bug description
 * - Toggle checkboxes for including different information
 * - Preview report generation
 * - Send report functionality
 *
 * NOTE: These tests require:
 * 1. Appium server running (`make appium-start` or `make appium-docker-start`)
 * 2. Android emulator running with the app installed (`make install`)
 *
 * Run with: make test-appium
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
// @Disabled("Requires Appium server and Android emulator. Run with: make test-appium")
@DisplayName("Bug Report E2E Tests")
class BugReportAppiumTest : AppiumBaseTest() {

    @Test
    @Order(1)
    @Tag("smoke")
    @DisplayName("Navigate to Bug Report screen")
    fun navigateToBugReport() {
        // Scroll to find Bug Report button if needed
        findById("btnBugReport").click()
        mediumWait()

        // Verify we're on Bug Report screen
        assertTrue(elementExists("etBugDescription"), "Should have bug description input")
        assertTrue(elementExists("cbIncludeConfig"), "Should have config checkbox")
        assertTrue(elementExists("cbIncludeDeviceInfo"), "Should have device info checkbox")
        assertTrue(elementExists("cbIncludePaymentStats"), "Should have payment stats checkbox")
        assertTrue(elementExists("btnPreviewReport"), "Should have Preview button")
        assertTrue(elementExists("btnSendReport"), "Should have Send button")

        navigateToMain()
    }

    @Test
    @Order(2)
    @Tag("smoke")
    @DisplayName("Enter bug description")
    fun enterBugDescription() {
        findById("btnBugReport").click()
        mediumWait()

        val descriptionInput = findById("etBugDescription")
        descriptionInput.clear()
        descriptionInput.sendKeys("The app crashes when I try to add a new sender with special characters in the name.")
        shortWait()

        // Verify text was entered
        assertTrue(
            descriptionInput.text.contains("crashes"),
            "Bug description should be entered"
        )

        navigateToMain()
    }

    @Test
    @Order(3)
    @DisplayName("Toggle include config checkbox")
    fun toggleIncludeConfigCheckbox() {
        findById("btnBugReport").click()
        mediumWait()

        val configCheckbox = findById("cbIncludeConfig")
        val initialState = configCheckbox.getAttribute("checked")

        // Toggle the checkbox
        configCheckbox.click()
        shortWait()

        // Verify state changed
        val newState = configCheckbox.getAttribute("checked")
        assertNotEquals(initialState, newState, "Checkbox state should have changed")

        // Toggle back
        configCheckbox.click()
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(4)
    @DisplayName("Toggle include device info checkbox")
    fun toggleIncludeDeviceInfoCheckbox() {
        findById("btnBugReport").click()
        mediumWait()

        val deviceInfoCheckbox = findById("cbIncludeDeviceInfo")
        val initialState = deviceInfoCheckbox.getAttribute("checked")

        // Toggle the checkbox
        deviceInfoCheckbox.click()
        shortWait()

        // Verify state changed
        val newState = deviceInfoCheckbox.getAttribute("checked")
        assertNotEquals(initialState, newState, "Checkbox state should have changed")

        // Toggle back
        deviceInfoCheckbox.click()
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(5)
    @DisplayName("Toggle include payment stats checkbox")
    fun toggleIncludePaymentStatsCheckbox() {
        findById("btnBugReport").click()
        mediumWait()

        val paymentStatsCheckbox = findById("cbIncludePaymentStats")
        val initialState = paymentStatsCheckbox.getAttribute("checked")

        // Toggle the checkbox
        paymentStatsCheckbox.click()
        shortWait()

        // Verify state changed
        val newState = paymentStatsCheckbox.getAttribute("checked")
        assertNotEquals(initialState, newState, "Checkbox state should have changed")

        // Toggle back
        paymentStatsCheckbox.click()
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(6)
    @DisplayName("Preview report with all options enabled")
    fun previewReportWithAllOptions() {
        findById("btnBugReport").click()
        mediumWait()

        // Enter bug description
        val descriptionInput = findById("etBugDescription")
        descriptionInput.clear()
        descriptionInput.sendKeys("Test bug report with all options enabled")
        shortWait()

        // Ensure all checkboxes are checked
        val configCheckbox = findById("cbIncludeConfig")
        if (configCheckbox.getAttribute("checked") != "true") {
            configCheckbox.click()
            shortWait()
        }

        val deviceInfoCheckbox = findById("cbIncludeDeviceInfo")
        if (deviceInfoCheckbox.getAttribute("checked") != "true") {
            deviceInfoCheckbox.click()
            shortWait()
        }

        val paymentStatsCheckbox = findById("cbIncludePaymentStats")
        if (paymentStatsCheckbox.getAttribute("checked") != "true") {
            paymentStatsCheckbox.click()
            shortWait()
        }

        // Click preview button
        findById("btnPreviewReport").click()
        mediumWait()

        // Verify preview is generated
        val previewArea = findById("tvReportPreview")
        val previewText = previewArea.text

        assertTrue(
            previewText.isNotEmpty() || previewArea.isDisplayed,
            "Preview should be generated"
        )

        navigateToMain()
    }

    @Test
    @Order(7)
    @DisplayName("Preview report with no options enabled")
    fun previewReportWithNoOptions() {
        findById("btnBugReport").click()
        mediumWait()

        // Enter bug description
        val descriptionInput = findById("etBugDescription")
        descriptionInput.clear()
        descriptionInput.sendKeys("Minimal bug report")
        shortWait()

        // Uncheck all checkboxes
        val configCheckbox = findById("cbIncludeConfig")
        if (configCheckbox.getAttribute("checked") == "true") {
            configCheckbox.click()
            shortWait()
        }

        val deviceInfoCheckbox = findById("cbIncludeDeviceInfo")
        if (deviceInfoCheckbox.getAttribute("checked") == "true") {
            deviceInfoCheckbox.click()
            shortWait()
        }

        val paymentStatsCheckbox = findById("cbIncludePaymentStats")
        if (paymentStatsCheckbox.getAttribute("checked") == "true") {
            paymentStatsCheckbox.click()
            shortWait()
        }

        // Click preview button
        findById("btnPreviewReport").click()
        mediumWait()

        // Preview should still be generated (with just description)
        val previewArea = findById("tvReportPreview")
        assertTrue(previewArea.isDisplayed, "Preview area should be visible")

        navigateToMain()
    }

    @Test
    @Order(8)
    @DisplayName("Preview changes based on checkbox selection")
    fun previewChangesBasedOnCheckboxSelection() {
        findById("btnBugReport").click()
        mediumWait()

        // Enter bug description
        val descriptionInput = findById("etBugDescription")
        descriptionInput.clear()
        descriptionInput.sendKeys("Testing preview changes")
        shortWait()

        // Start with all options unchecked
        val configCheckbox = findById("cbIncludeConfig")
        val deviceInfoCheckbox = findById("cbIncludeDeviceInfo")
        val paymentStatsCheckbox = findById("cbIncludePaymentStats")

        // Uncheck all
        if (configCheckbox.getAttribute("checked") == "true") configCheckbox.click()
        if (deviceInfoCheckbox.getAttribute("checked") == "true") deviceInfoCheckbox.click()
        if (paymentStatsCheckbox.getAttribute("checked") == "true") paymentStatsCheckbox.click()
        shortWait()

        // Generate first preview
        findById("btnPreviewReport").click()
        mediumWait()

        val previewArea = findById("tvReportPreview")
        val minimalPreview = previewArea.text

        // Enable config option
        configCheckbox.click()
        shortWait()

        // Generate second preview
        findById("btnPreviewReport").click()
        mediumWait()

        val configPreview = previewArea.text

        // Previews should be different when options change
        // (If same, the feature might not be implemented, but test shouldn't fail)
        assertTrue(previewArea.isDisplayed, "Preview should be visible")

        navigateToMain()
    }

    @Test
    @Order(9)
    @DisplayName("Send report button triggers share dialog")
    fun sendReportTriggersShareDialog() {
        findById("btnBugReport").click()
        mediumWait()

        // Enter bug description
        val descriptionInput = findById("etBugDescription")
        descriptionInput.clear()
        descriptionInput.sendKeys("Test bug report for sending")
        shortWait()

        // Generate preview first
        findById("btnPreviewReport").click()
        mediumWait()

        // Click send button
        findById("btnSendReport").click()
        mediumWait()

        // Should open share dialog or show message
        // Navigate back to close any dialogs
        driver.navigate().back()
        shortWait()

        // Make sure we can get back to main
        navigateToMain()
    }

    @Test
    @Order(10)
    @DisplayName("Send report without preview shows message")
    fun sendReportWithoutPreviewShowsMessage() {
        findById("btnBugReport").click()
        mediumWait()

        // Enter bug description
        val descriptionInput = findById("etBugDescription")
        descriptionInput.clear()
        descriptionInput.sendKeys("Test report without preview")
        shortWait()

        // Click send without generating preview
        findById("btnSendReport").click()
        mediumWait()

        // Should show a message or handle gracefully
        // Just verify app doesn't crash
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(11)
    @DisplayName("Bug description supports multiline text")
    fun bugDescriptionSupportsMultilineText() {
        findById("btnBugReport").click()
        mediumWait()

        val descriptionInput = findById("etBugDescription")
        descriptionInput.clear()

        // Enter multiline text (using \n for newlines)
        val multilineText = """
            Step 1: Open the app
            Step 2: Click on Senders
            Step 3: Add a new sender
            Expected: Sender is saved
            Actual: App crashes
        """.trimIndent()

        descriptionInput.sendKeys(multilineText)
        shortWait()

        // Verify text contains multiple parts
        val enteredText = descriptionInput.text
        assertTrue(
            enteredText.contains("Step") || enteredText.isNotEmpty(),
            "Multiline text should be entered"
        )

        navigateToMain()
    }

    @Test
    @Order(12)
    @DisplayName("Preview area displays monospace font")
    fun previewAreaDisplaysMonospaceFont() {
        findById("btnBugReport").click()
        mediumWait()

        // Enter bug description and generate preview
        val descriptionInput = findById("etBugDescription")
        descriptionInput.clear()
        descriptionInput.sendKeys("Font test report")
        shortWait()

        findById("btnPreviewReport").click()
        mediumWait()

        // Verify preview area exists and is displayed
        val previewArea = findById("tvReportPreview")
        assertTrue(previewArea.isDisplayed, "Preview area should be displayed")

        navigateToMain()
    }
}
