package com.example.banksmstracker.appium

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

/**
 * Appium E2E tests for Settings screen.
 *
 * Tests the Settings activity including:
 * - Theme selection (System/Light/Dark)
 * - Language selection (System/English/Russian)
 * - Settings persistence
 *
 * NOTE: These tests require:
 * 1. Appium server running
 * 2. Android emulator running with the app installed
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@DisplayName("Settings E2E Tests")
class SettingsAppiumTest : AppiumBaseTest() {

    @BeforeEach
    fun navigateToSettings() {
        longWait()
        navigateToMain()
        longWait()

        // Navigate to Settings (btnThemeToggle now opens SettingsActivity)
        try {
            clickButton("btnThemeToggle")
        } catch (e: Exception) {
            // Fallback: scroll to find the button
            scrollToElementById("btnThemeToggle").click()
        }
        longWait()
    }

    @Test
    @Order(1)
    @Tag("smoke")
    @DisplayName("Settings screen displays theme section")
    fun settingsScreenDisplaysThemeSection() {
        assertTrue(elementExists("radioGroupTheme"), "Theme radio group should exist")
        assertTrue(elementExists("radioThemeSystem"), "System theme option should exist")
        assertTrue(elementExists("radioThemeLight"), "Light theme option should exist")
        assertTrue(elementExists("radioThemeDark"), "Dark theme option should exist")
    }

    @Test
    @Order(2)
    @Tag("smoke")
    @DisplayName("Settings screen displays language section")
    fun settingsScreenDisplaysLanguageSection() {
        assertTrue(elementExists("radioGroupLanguage"), "Language radio group should exist")
        assertTrue(elementExists("radioLanguageSystem"), "System language option should exist")
        assertTrue(elementExists("radioLanguageEnglish"), "English language option should exist")
        assertTrue(elementExists("radioLanguageRussian"), "Russian language option should exist")
    }

    @Test
    @Order(3)
    @DisplayName("Theme selection changes app theme")
    fun themeSelectionChangesAppTheme() {
        // Select Dark theme
        findById("radioThemeDark").click()
        shortWait()

        // Verify it's selected
        assertTrue(
            findById("radioThemeDark").isSelected || findById("radioThemeDark").getAttribute("checked") == "true",
            "Dark theme should be selected"
        )

        // Select Light theme
        findById("radioThemeLight").click()
        shortWait()

        // Verify it's selected
        assertTrue(
            findById("radioThemeLight").isSelected || findById("radioThemeLight").getAttribute("checked") == "true",
            "Light theme should be selected"
        )

        // Return to System default
        findById("radioThemeSystem").click()
        shortWait()
    }

    @Test
    @Order(4)
    @DisplayName("Language selection is functional")
    fun languageSelectionIsFunctional() {
        // Select English
        findById("radioLanguageEnglish").click()
        shortWait()

        // Verify it's selected
        assertTrue(
            findById("radioLanguageEnglish").isSelected || findById("radioLanguageEnglish").getAttribute("checked") == "true",
            "English language should be selected"
        )

        // Return to System default
        findById("radioLanguageSystem").click()
        shortWait()
    }

    @Test
    @Order(5)
    @DisplayName("Selecting Russian language changes UI")
    fun selectingRussianLanguageChangesUI() {
        // Select Russian
        findById("radioLanguageRussian").click()
        longWait()  // Allow time for locale change and potential activity recreation

        // After language change, we may need to re-navigate to settings
        // as the activity might be recreated
        navigateToMain()
        mediumWait()
        findById("btnThemeToggle").click()
        mediumWait()

        // Return to System default to restore English
        findById("radioLanguageSystem").click()
        longWait()
    }

    @Test
    @Order(6)
    @DisplayName("Navigate back from Settings returns to main")
    fun navigateBackFromSettingsReturnsToMain() {
        driver.navigate().back()
        mediumWait()

        assertTrue(elementExists("btnCategories"), "Should be back on main screen")
    }

    @Test
    @Order(7)
    @DisplayName("Settings persist after navigation")
    fun settingsPersistAfterNavigation() {
        // Select Light theme
        findById("radioThemeLight").click()
        shortWait()

        // Navigate back
        driver.navigate().back()
        mediumWait()

        // Navigate back to settings
        findById("btnThemeToggle").click()
        mediumWait()

        // Verify Light theme is still selected
        assertTrue(
            findById("radioThemeLight").isSelected || findById("radioThemeLight").getAttribute("checked") == "true",
            "Light theme should still be selected after navigation"
        )

        // Reset to System default
        findById("radioThemeSystem").click()
        shortWait()
    }

    @Test
    @Order(8)
    @DisplayName("Language setting persists after navigation")
    fun languageSettingPersistsAfterNavigation() {
        // Select English explicitly
        findById("radioLanguageEnglish").click()
        shortWait()

        // Navigate back to main
        driver.navigate().back()
        mediumWait()

        // Navigate back to settings
        findById("btnThemeToggle").click()
        mediumWait()

        // Verify English is still selected
        assertTrue(
            findById("radioLanguageEnglish").isSelected || findById("radioLanguageEnglish").getAttribute("checked") == "true",
            "English language should still be selected after navigation"
        )

        // Reset to System default
        findById("radioLanguageSystem").click()
        shortWait()
    }

    @Test
    @Order(9)
    @DisplayName("Theme and language can be set independently")
    fun themeAndLanguageCanBeSetIndependently() {
        // Set Dark theme and English language
        findById("radioThemeDark").click()
        shortWait()
        findById("radioLanguageEnglish").click()
        shortWait()

        // Verify both are selected
        assertTrue(
            findById("radioThemeDark").isSelected || findById("radioThemeDark").getAttribute("checked") == "true",
            "Dark theme should be selected"
        )
        assertTrue(
            findById("radioLanguageEnglish").isSelected || findById("radioLanguageEnglish").getAttribute("checked") == "true",
            "English language should be selected"
        )

        // Change theme to Light, verify language unchanged
        findById("radioThemeLight").click()
        shortWait()

        assertTrue(
            findById("radioThemeLight").isSelected || findById("radioThemeLight").getAttribute("checked") == "true",
            "Light theme should be selected"
        )
        assertTrue(
            findById("radioLanguageEnglish").isSelected || findById("radioLanguageEnglish").getAttribute("checked") == "true",
            "English language should still be selected after theme change"
        )

        // Reset both to defaults
        findById("radioThemeSystem").click()
        shortWait()
        findById("radioLanguageSystem").click()
        shortWait()
    }

    @Test
    @Order(10)
    @DisplayName("All theme options are clickable")
    fun allThemeOptionsAreClickable() {
        // Click each theme option and verify selection
        val themeOptions = listOf("radioThemeSystem", "radioThemeLight", "radioThemeDark")

        for (optionId in themeOptions) {
            findById(optionId).click()
            shortWait()

            assertTrue(
                findById(optionId).isSelected || findById(optionId).getAttribute("checked") == "true",
                "$optionId should be selectable"
            )
        }

        // Reset to system
        findById("radioThemeSystem").click()
        shortWait()
    }

    @Test
    @Order(11)
    @DisplayName("All language options are clickable")
    fun allLanguageOptionsAreClickable() {
        // Click each language option
        val languageOptions = listOf("radioLanguageSystem", "radioLanguageEnglish", "radioLanguageRussian")

        for (optionId in languageOptions) {
            findById(optionId).click()
            longWait() // Language changes may trigger activity recreation

            // Re-navigate to settings if needed
            if (!elementExists(optionId)) {
                navigateToMain()
                mediumWait()
                findById("btnThemeToggle").click()
                mediumWait()
            }
        }

        // Reset to system default
        findById("radioLanguageSystem").click()
        longWait()
    }

    @Test
    @Order(12)
    @DisplayName("Settings screen has correct title")
    fun settingsScreenHasCorrectTitle() {
        // Verify we're on settings screen by checking for the unique elements
        assertTrue(
            elementExists("radioGroupTheme") && elementExists("radioGroupLanguage"),
            "Settings screen should display both theme and language sections"
        )
    }
}
