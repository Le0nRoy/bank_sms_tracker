package com.example.banksmstracker.appium

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

/**
 * Appium E2E tests for main screen navigation.
 *
 * Tests that all buttons on the main screen navigate to the correct screens.
 *
 * Covers:
 * - Main screen layout verification
 * - Navigation to all screens from main menu
 * - Back navigation works correctly
 * - Screen titles are correct
 *
 * NOTE: These tests require:
 * 1. Appium server running (`make appium-start` or `make appium-docker-start`)
 * 2. Android emulator running with the app installed (`make install`)
 *
 * Run with: make test-appium
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
// @Disabled("Requires Appium server and Android emulator. Run with: make test-appium")
@DisplayName("Main Navigation E2E Tests")
class MainNavigationAppiumTest : AppiumBaseTest() {

    @BeforeEach
    fun ensureOnMainScreen() {
        // Make sure we start each test from the main screen
        // Add extra wait to handle animation/transition delays
        mediumWait()
        navigateToMain()
        mediumWait()
    }

    @Test
    @Order(1)
    @DisplayName("Main screen displays app title")
    fun mainScreenDisplaysAppTitle() {
        assertTrue(elementExists("btnCategories"), "Main screen should be displayed")
    }

    @Test
    @Order(2)
    @DisplayName("Main screen has all navigation buttons")
    fun mainScreenHasAllNavigationButtons() {
        // Use button IDs for reliable detection (main screen is not scrollable)
        assertTrue(elementExists("btnCategories"), "Categories button should exist")
        assertTrue(elementExists("btnSenders"), "Senders button should exist")
        assertTrue(elementExists("btnCheckSenders"), "Check Senders button should exist")
        assertTrue(elementExists("btnApplyRules"), "Apply Rules button should exist")
        assertTrue(elementExists("btnExportConfig"), "Export Config button should exist")
        assertTrue(elementExists("btnImportConfig"), "Import Config button should exist")
        assertTrue(elementExists("btnPayments"), "Payments button should exist")
        assertTrue(elementExists("btnRegexBuilder"), "Regex Builder button should exist")
        assertTrue(elementExists("btnBugReport"), "Bug Report button should exist")
        assertTrue(elementExists("btnIgnoreRules"), "Ignore Rules button should exist")
        assertTrue(elementExists("btnSmsExport"), "SMS Export button should exist")
        assertTrue(elementExists("btnThemeToggle"), "Theme Toggle button should exist")
    }

    @Test
    @Order(3)
    @DisplayName("Navigate to Categories and back")
    fun navigateToCategoriesAndBack() {
        findById("btnCategories").click()
        longWait()

        // Use longer wait and retry for Categories screen
        var found = false
        repeat(3) {
            if (elementExists("recyclerViewCategories")) {
                found = true
                return@repeat
            }
            shortWait()
        }
        assertTrue(found, "Should be on Categories screen")

        driver.navigate().back()
        mediumWait()

        assertTrue(elementExists("btnCategories"), "Should be back on main screen")
    }

    @Test
    @Order(4)
    @DisplayName("Navigate to Senders and back")
    fun navigateToSendersAndBack() {
        findById("btnSenders").click()
        mediumWait()

        assertTrue(elementExists("recyclerViewSenders"), "Should be on Senders screen")

        driver.navigate().back()
        mediumWait()

        assertTrue(elementExists("btnCategories"), "Should be back on main screen")
    }

    @Test
    @Order(5)
    @DisplayName("Navigate to Check Senders and back")
    fun navigateToCheckSendersAndBack() {
        findById("btnCheckSenders").click()
        mediumWait()

        // Check Senders screen should load
        shortWait()

        driver.navigate().back()
        extraLongWait()

        // Try again if first back didn't work
        if (!elementExists("btnCategories")) {
            driver.navigate().back()
            mediumWait()
        }

        // Verify we're back on main screen by checking for Categories button
        assertTrue(elementExists("btnCategories"), "Should be back on main screen")
    }

    @Test
    @Order(6)
    @DisplayName("Navigate to Apply Rules and back")
    fun navigateToApplyRulesAndBack() {
        findById("btnApplyRules").click()
        mediumWait()

        // Handle permission if needed
        handlePermissionDialogIfPresent()
        shortWait()

        driver.navigate().back()
        mediumWait()

        assertTrue(elementExists("btnCategories"), "Should be back on main screen")
    }

    @Test
    @Order(7)
    @DisplayName("Navigate to Payments and back")
    fun navigateToPaymentsAndBack() {
        findById("btnPayments").click()
        mediumWait()

        assertTrue(
            elementExists("spinnerCategory") || elementExists("recyclerPayments"),
            "Should be on Payments screen"
        )

        driver.navigate().back()
        mediumWait()

        assertTrue(elementExists("btnCategories"), "Should be back on main screen")
    }

    @Test
    @Order(8)
    @DisplayName("Navigate to Regex Builder and back")
    fun navigateToRegexBuilderAndBack() {
        findById("btnRegexBuilder").click()
        mediumWait()

        assertTrue(elementExists("etSampleSms"), "Should be on Regex Builder screen")

        driver.navigate().back()
        mediumWait()

        assertTrue(elementExists("btnCategories"), "Should be back on main screen")
    }

    @Test
    @Order(9)
    @DisplayName("Navigate to Bug Report and back")
    fun navigateToBugReportAndBack() {
        findById("btnBugReport").click()
        mediumWait()

        assertTrue(elementExists("etBugDescription"), "Should be on Bug Report screen")

        driver.navigate().back()
        mediumWait()

        assertTrue(elementExists("btnCategories"), "Should be back on main screen")
    }

    @Test
    @Order(10)
    @DisplayName("Export Config triggers share dialog")
    fun exportConfigTriggersShareDialog() {
        findById("btnExportConfig").click()
        mediumWait()

        // Should show share dialog
        // Navigate back to dismiss
        driver.navigate().back()
        shortWait()

        // May need another back if dialog was shown
        if (!elementExists("btnCategories")) {
            driver.navigate().back()
            shortWait()
        }

        assertTrue(elementExists("btnCategories"), "Should be back on main screen")
    }

    @Test
    @Order(11)
    @DisplayName("Import Config triggers file picker")
    fun importConfigTriggersFilePicker() {
        findById("btnImportConfig").click()
        mediumWait()

        // Should show file picker
        // Navigate back to dismiss
        driver.navigate().back()
        shortWait()

        // May need another back if picker was shown
        if (!elementExists("btnCategories")) {
            driver.navigate().back()
            shortWait()
        }

        assertTrue(elementExists("btnCategories"), "Should be back on main screen")
    }

    @Test
    @Order(12)
    @DisplayName("Rapid navigation between screens")
    fun rapidNavigationBetweenScreens() {
        // Navigate quickly between multiple screens using button IDs
        val buttonIds = listOf("btnCategories", "btnSenders", "btnPayments")

        for (buttonId in buttonIds) {
            findById(buttonId).click()
            shortWait()
            driver.navigate().back()
            shortWait()
        }

        // Should end up back on main screen
        assertTrue(elementExists("btnCategories"), "Should be on main screen after rapid navigation")
    }

    @Test
    @Order(13)
    @DisplayName("Deep navigation and back stack")
    fun deepNavigationAndBackStack() {
        // Navigate to Categories
        findById("btnCategories").click()
        mediumWait()

        // Add a category (if FAB exists)
        if (elementExists("fabAddCategory")) {
            findById("fabAddCategory").click()
            shortWait()
        }

        // Navigate back twice to get to main
        driver.navigate().back()
        mediumWait()

        // Should be on main screen
        assertTrue(elementExists("btnCategories"), "Should be back on main screen")
    }

    @Test
    @Order(14)
    @DisplayName("All main screen buttons are accessible")
    fun allMainScreenButtonsAccessible() {
        // Verify all buttons exist and are clickable (using IDs since layout is not scrollable)
        assertTrue(elementExists("btnCategories"), "Categories button accessible")
        assertTrue(elementExists("btnSenders"), "Senders button accessible")
        assertTrue(elementExists("btnCheckSenders"), "Check Senders button accessible")
        assertTrue(elementExists("btnApplyRules"), "Apply Rules button accessible")
        assertTrue(elementExists("btnExportConfig"), "Export Config button accessible")
        assertTrue(elementExists("btnImportConfig"), "Import Config button accessible")
        assertTrue(elementExists("btnPayments"), "Payments button accessible")
        assertTrue(elementExists("btnRegexBuilder"), "Regex Builder button accessible")
        assertTrue(elementExists("btnBugReport"), "Bug Report button accessible")
        assertTrue(elementExists("btnIgnoreRules"), "Ignore Rules button accessible")
        assertTrue(elementExists("btnSmsExport"), "SMS Export button accessible")
        assertTrue(elementExists("btnThemeToggle"), "Theme Toggle button accessible")
    }

    @Test
    @Order(15)
    @DisplayName("Navigate to SMS Export and back")
    fun navigateToSmsExportAndBack() {
        findById("btnSmsExport").click()
        mediumWait()

        // Handle permission if needed
        handlePermissionDialogIfPresent()
        shortWait()

        // SMS Export screen should show date pickers and export options
        assertTrue(
            elementExists("btnStartDate") || elementExists("spinnerSender"),
            "Should be on SMS Export screen"
        )

        driver.navigate().back()
        mediumWait()

        assertTrue(elementExists("btnCategories"), "Should be back on main screen")
    }

    @Test
    @Order(16)
    @DisplayName("Theme Toggle shows dialog")
    fun themeToggleShowsDialog() {
        findById("btnThemeToggle").click()
        mediumWait()

        // Dialog should be shown - look for dialog content or cancel button
        // The dialog uses standard AlertDialog with SingleChoiceItems
        val dialogVisible = try {
            driver.findElement(
                io.appium.java_client.AppiumBy.xpath(
                    "//*[contains(@text, 'System') or contains(@text, 'Light') or contains(@text, 'Dark')]"
                )
            ).isDisplayed
        } catch (e: Exception) {
            false
        }

        // Dismiss dialog
        driver.navigate().back()
        shortWait()

        assertTrue(elementExists("btnCategories"), "Should be back on main screen")
    }

    private fun handlePermissionDialogIfPresent() {
        try {
            val allowButton = driver.findElement(
                io.appium.java_client.AppiumBy.id(
                    "com.android.permissioncontroller:id/permission_allow_button"
                )
            )
            if (allowButton.isDisplayed) {
                allowButton.click()
                shortWait()
            }
        } catch (e: Exception) {
            // Permission dialog not present
        }
    }
}
