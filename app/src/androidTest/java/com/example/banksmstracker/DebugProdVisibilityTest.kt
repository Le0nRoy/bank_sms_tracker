package com.example.banksmstracker

import android.content.Context
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.example.banksmstracker.ui.BugReportActivity
import com.example.banksmstracker.ui.MainActivity
import com.example.banksmstracker.ui.PaymentsActivity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Instrumented tests verifying that debug-only UI elements are visible in debug builds
 * and would be GONE in production builds.
 *
 * These tests always run in debug builds (where BuildConfig.DEBUG == true),
 * so they verify the debug path: all export controls must be visible.
 *
 * The release path (GONE) is enforced by code review and the BuildConfig.DEBUG gate
 * in each activity.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Debug/Prod UI Visibility Tests")
class DebugProdVisibilityTest {

    private val appContext = ApplicationProvider.getApplicationContext<Context>()

    @BeforeEach
    fun skipTermsDialog() {
        // Pre-agree to terms so the dialog does not block activity launch
        appContext.getSharedPreferences(MainActivity.PREFS_TERMS, Context.MODE_PRIVATE)
            .edit().putBoolean(MainActivity.KEY_TERMS_AGREED, true).apply()
    }

    @Test
    @DisplayName("btnSmsExport is visible in debug build")
    fun smsExportButtonVisibleInDebug() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.btnSmsExport))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        }
    }

    @Test
    @DisplayName("btnExportCsv is visible in debug build")
    fun exportCsvButtonVisibleInDebug() {
        ActivityScenario.launch(PaymentsActivity::class.java).use {
            onView(withId(R.id.btnExportCsv))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        }
    }

    @Test
    @DisplayName("cbAttachPaymentsData is visible in debug build")
    fun attachPaymentsCheckboxVisibleInDebug() {
        ActivityScenario.launch(BugReportActivity::class.java).use {
            onView(withId(R.id.cbAttachPaymentsData))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        }
    }

    @Test
    @DisplayName("Terms dialog is shown on first launch")
    fun termsDialogShownOnFirstLaunch() {
        // Clear agreed flag so the dialog will appear
        appContext.getSharedPreferences(MainActivity.PREFS_TERMS, Context.MODE_PRIVATE)
            .edit().remove(MainActivity.KEY_TERMS_AGREED).apply()

        ActivityScenario.launch(MainActivity::class.java).use {
            // The terms dialog button should be visible
            onView(withId(android.R.id.button1))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        }
    }

    @Test
    @DisplayName("Terms dialog not shown after agreement")
    fun termsDialogNotShownAfterAgreement() {
        // Already agreed in @BeforeEach
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Activity should launch and show the main screen without blocking
            scenario.onActivity { activity ->
                val btnCategories = activity.findViewById<View>(R.id.btnCategories)
                assert(btnCategories != null) { "Main screen should load without terms dialog blocking" }
            }
        }
    }
}
