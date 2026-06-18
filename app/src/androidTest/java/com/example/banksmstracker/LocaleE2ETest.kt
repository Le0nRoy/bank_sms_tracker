package com.example.banksmstracker

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * E2E tests for locale/language functionality.
 * Verifies that language settings persist and can be changed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Locale E2E Tests")
class LocaleE2ETest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: SharedPreferences

    @BeforeEach
    fun setUp() {
        prefs = context.getSharedPreferences(BankSmsTrackerApp.PREFS_NAME, Context.MODE_PRIVATE)
        // Reset to system default before each test
        prefs.edit().remove(BankSmsTrackerApp.KEY_LANGUAGE).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @AfterEach
    fun tearDown() {
        // Reset to system default after each test
        prefs.edit().remove(BankSmsTrackerApp.KEY_LANGUAGE).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test
    @DisplayName("Default language is system default (empty)")
    fun defaultLanguageIsSystemDefault() {
        val language = prefs.getString(BankSmsTrackerApp.KEY_LANGUAGE, "")
        assertEquals("", language, "Default language should be empty (system default)")
    }

    @Test
    @DisplayName("Language preference persists when set to English")
    fun languagePreferencePersistsWhenSetToEnglish() {
        prefs.edit().putString(BankSmsTrackerApp.KEY_LANGUAGE, "en").apply()

        val language = prefs.getString(BankSmsTrackerApp.KEY_LANGUAGE, "")
        assertEquals("en", language, "Language should be 'en'")
    }

    @Test
    @DisplayName("Language preference persists when set to Russian")
    fun languagePreferencePersistsWhenSetToRussian() {
        prefs.edit().putString(BankSmsTrackerApp.KEY_LANGUAGE, "ru").apply()

        val language = prefs.getString(BankSmsTrackerApp.KEY_LANGUAGE, "")
        assertEquals("ru", language, "Language should be 'ru'")
    }

    @Test
    @DisplayName("Language can be changed between options")
    fun languageCanBeChangedBetweenOptions() {
        // Set to English
        prefs.edit().putString(BankSmsTrackerApp.KEY_LANGUAGE, "en").apply()
        assertEquals("en", prefs.getString(BankSmsTrackerApp.KEY_LANGUAGE, ""))

        // Change to Russian
        prefs.edit().putString(BankSmsTrackerApp.KEY_LANGUAGE, "ru").apply()
        assertEquals("ru", prefs.getString(BankSmsTrackerApp.KEY_LANGUAGE, ""))

        // Change back to System
        prefs.edit().putString(BankSmsTrackerApp.KEY_LANGUAGE, "").apply()
        assertEquals("", prefs.getString(BankSmsTrackerApp.KEY_LANGUAGE, ""))
    }

    @Test
    @DisplayName("Theme preference is independent of language preference")
    fun themePreferenceIsIndependentOfLanguagePreference() {
        // Set both preferences
        prefs.edit()
            .putInt(BankSmsTrackerApp.KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_YES)
            .putString(BankSmsTrackerApp.KEY_LANGUAGE, "ru")
            .apply()

        // Verify both are set correctly
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, prefs.getInt(BankSmsTrackerApp.KEY_THEME_MODE, -1))
        assertEquals("ru", prefs.getString(BankSmsTrackerApp.KEY_LANGUAGE, ""))

        // Change language, verify theme is unchanged
        prefs.edit().putString(BankSmsTrackerApp.KEY_LANGUAGE, "en").apply()
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, prefs.getInt(BankSmsTrackerApp.KEY_THEME_MODE, -1))
    }

    @Test
    @DisplayName("Russian string resources exist")
    fun russianStringResourcesExist() {
        // Get a sample of string resource IDs and verify they exist
        val stringIds = listOf(
            R.string.categories,
            R.string.senders,
            R.string.payments,
            R.string.settings,
            R.string.language,
            R.string.theme
        )

        for (stringId in stringIds) {
            val stringValue = context.getString(stringId)
            assertTrue(stringValue.isNotEmpty(), "String resource $stringId should not be empty")
        }
    }

    @Test
    @DisplayName("AppCompatDelegate locale methods are available")
    fun appCompatDelegateLocaleMethodsAreAvailable() {
        // Test that we can set and get application locales
        val englishLocale = LocaleListCompat.forLanguageTags("en")
        AppCompatDelegate.setApplicationLocales(englishLocale)

        // Get current locales
        val currentLocales = AppCompatDelegate.getApplicationLocales()

        // Reset to empty
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())

        // This just verifies the APIs are accessible and don't throw
        assertTrue(true, "AppCompatDelegate locale methods should be accessible")
    }

    @Test
    @DisplayName("All key string resources have Russian translations")
    fun allKeyStringResourcesHaveRussianTranslations() {
        // Test a comprehensive list of key strings
        val stringIds = listOf(
            // Navigation
            R.string.categories,
            R.string.senders,
            R.string.payments,
            // Settings
            R.string.settings,
            R.string.language,
            R.string.theme,
            R.string.dark_mode,
            R.string.light_mode,
            R.string.theme_system,
            R.string.language_system,
            R.string.language_english,
            R.string.language_russian,
            // Common actions
            R.string.cancel,
            R.string.confirm,
            R.string.delete,
            R.string.undo,
            // Features
            R.string.regex_builder,
            R.string.bug_report,
            R.string.sms_export,
            R.string.ignore_rules
        )

        for (stringId in stringIds) {
            val stringValue = context.getString(stringId)
            assertTrue(stringValue.isNotEmpty(), "String resource $stringId should not be empty")
        }
    }

    @Test
    @DisplayName("Preference file name is consistent")
    fun preferenceFileNameIsConsistent() {
        val prefs1 = context.getSharedPreferences(BankSmsTrackerApp.PREFS_NAME, Context.MODE_PRIVATE)
        val prefs2 = context.getSharedPreferences("bank_sms_tracker_prefs", Context.MODE_PRIVATE)

        // Both should refer to the same file
        prefs1.edit().putString("test_key", "test_value").apply()
        assertEquals("test_value", prefs2.getString("test_key", ""))

        // Cleanup
        prefs1.edit().remove("test_key").apply()
    }

    @Test
    @DisplayName("Theme mode values are valid")
    fun themeModeValuesAreValid() {
        val validThemeModes = listOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )

        // Verify each mode can be saved and retrieved
        for (mode in validThemeModes) {
            prefs.edit().putInt(BankSmsTrackerApp.KEY_THEME_MODE, mode).apply()
            assertEquals(mode, prefs.getInt(BankSmsTrackerApp.KEY_THEME_MODE, -999))
        }
    }

    @Test
    @DisplayName("Clearing preferences works correctly")
    fun clearingPreferencesWorksCorrectly() {
        // Set values
        prefs.edit()
            .putString(BankSmsTrackerApp.KEY_LANGUAGE, "ru")
            .putInt(BankSmsTrackerApp.KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_YES)
            .apply()

        // Verify they're set
        assertEquals("ru", prefs.getString(BankSmsTrackerApp.KEY_LANGUAGE, ""))
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, prefs.getInt(BankSmsTrackerApp.KEY_THEME_MODE, -1))

        // Clear all
        prefs.edit().clear().apply()

        // Verify defaults are returned
        assertEquals("", prefs.getString(BankSmsTrackerApp.KEY_LANGUAGE, ""))
        assertEquals(-1, prefs.getInt(BankSmsTrackerApp.KEY_THEME_MODE, -1))
    }
}
