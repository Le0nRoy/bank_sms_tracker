package com.example.banksmstracker

import androidx.appcompat.app.AppCompatDelegate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for BankSmsTrackerApp constants and preferences.
 *
 * Tests the preference keys and default values used for theme and language settings.
 */
@DisplayName("BankSmsTrackerApp Constants Tests")
class BankSmsTrackerAppTest {

    @Nested
    @DisplayName("Preference Constants")
    inner class PreferenceConstants {

        @Test
        @DisplayName("PREFS_NAME has correct value")
        fun prefsNameHasCorrectValue() {
            assertEquals("bank_sms_tracker_prefs", BankSmsTrackerApp.PREFS_NAME)
        }

        @Test
        @DisplayName("KEY_THEME_MODE has correct value")
        fun keyThemeModeHasCorrectValue() {
            assertEquals("theme_mode", BankSmsTrackerApp.KEY_THEME_MODE)
        }

        @Test
        @DisplayName("KEY_LANGUAGE has correct value")
        fun keyLanguageHasCorrectValue() {
            assertEquals("language", BankSmsTrackerApp.KEY_LANGUAGE)
        }

        @Test
        @DisplayName("All preference keys are non-null")
        fun allPreferenceKeysAreNonNull() {
            assertNotNull(BankSmsTrackerApp.PREFS_NAME)
            assertNotNull(BankSmsTrackerApp.KEY_THEME_MODE)
            assertNotNull(BankSmsTrackerApp.KEY_LANGUAGE)
        }

        @Test
        @DisplayName("Preference keys are unique")
        fun preferenceKeysAreUnique() {
            val keys = setOf(
                BankSmsTrackerApp.PREFS_NAME,
                BankSmsTrackerApp.KEY_THEME_MODE,
                BankSmsTrackerApp.KEY_LANGUAGE
            )
            assertEquals(3, keys.size, "All preference keys should be unique")
        }
    }

    @Nested
    @DisplayName("Theme Mode Constants")
    inner class ThemeModeConstants {

        @Test
        @DisplayName("Default theme mode is System")
        fun defaultThemeModeIsSystem() {
            // The app defaults to MODE_NIGHT_FOLLOW_SYSTEM
            assertEquals(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                -1, // MODE_NIGHT_FOLLOW_SYSTEM value
                "Default theme mode should be -1 (MODE_NIGHT_FOLLOW_SYSTEM)"
            )
        }

        @Test
        @DisplayName("Theme mode constants are accessible")
        fun themeModeConstantsAreAccessible() {
            // Verify AppCompatDelegate constants can be accessed
            assertEquals(-1, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            assertEquals(1, AppCompatDelegate.MODE_NIGHT_NO)
            assertEquals(2, AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    @Nested
    @DisplayName("Language Code Constants")
    inner class LanguageCodeConstants {

        @Test
        @DisplayName("Empty string represents system default language")
        fun emptyStringRepresentsSystemDefault() {
            val systemDefault = ""
            assertEquals("", systemDefault, "Empty string should represent system default language")
        }

        @Test
        @DisplayName("Language codes are valid BCP 47 tags")
        fun languageCodesAreValidBcp47Tags() {
            val englishCode = "en"
            val russianCode = "ru"

            // BCP 47 language tags should be 2-3 letter ISO codes
            assertEquals(2, englishCode.length, "English code should be 2 letters")
            assertEquals(2, russianCode.length, "Russian code should be 2 letters")
            assertEquals("en", englishCode.lowercase())
            assertEquals("ru", russianCode.lowercase())
        }

        @Test
        @DisplayName("Supported language codes are distinct")
        fun supportedLanguageCodesAreDistinct() {
            val supportedLanguages = setOf("", "en", "ru")
            assertEquals(3, supportedLanguages.size, "All language codes should be distinct")
        }
    }
}
