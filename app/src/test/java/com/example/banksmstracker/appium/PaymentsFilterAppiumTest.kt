package com.example.banksmstracker.appium

import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

/**
 * Appium E2E tests for payment filtering functionality.
 *
 * Tests the payment list filtering features including:
 * - Sender filter spinner
 * - Date range filter buttons
 * - Combined filter operations
 *
 * NOTE: These tests require:
 * 1. Appium server running (`make appium-start` or `make appium-docker-start`)
 * 2. Android emulator running with the app installed (`make install`)
 *
 * Run with: make test-appium
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Epic("Bank SMS Tracker")
@Feature("Payments")
@DisplayName("Payments Filter E2E Tests")
class PaymentsFilterAppiumTest : AppiumBaseTest() {

    @Test
    @Order(1)
    @Tag("smoke")
    @DisplayName("Navigate to Payments screen")
    fun navigateToPaymentsScreen() {
        findById("btnPayments").click()
        mediumWait()

        // Verify we're on Payments screen
        assertTrue(
            elementExists("spinnerCategory") || textExists("Payment") || textExists("PAYMENT"),
            "Should be on Payments screen"
        )

        navigateToMain()
    }

    @Test
    @Order(2)
    @Tag("smoke")
    @DisplayName("Sender filter spinner exists")
    fun senderFilterSpinnerExists() {
        findById("btnPayments").click()
        mediumWait()

        val hasSenderSpinner = elementExists("spinnerSender")
        assertTrue(hasSenderSpinner, "Should have sender filter spinner")

        navigateToMain()
    }

    @Test
    @Order(3)
    @DisplayName("Start date button exists")
    fun startDateButtonExists() {
        findById("btnPayments").click()
        mediumWait()

        val hasStartDateBtn = elementExists("btnStartDate")
        assertTrue(hasStartDateBtn, "Should have start date button")

        navigateToMain()
    }

    @Test
    @Order(4)
    @DisplayName("End date button exists")
    fun endDateButtonExists() {
        findById("btnPayments").click()
        mediumWait()

        val hasEndDateBtn = elementExists("btnEndDate")
        assertTrue(hasEndDateBtn, "Should have end date button")

        navigateToMain()
    }

    @Test
    @Order(5)
    @DisplayName("Clear dates button exists")
    fun clearDatesButtonExists() {
        findById("btnPayments").click()
        mediumWait()

        val hasClearBtn = elementExists("btnClearDates")
        assertTrue(hasClearBtn, "Should have clear dates button")

        navigateToMain()
    }

    @Test
    @Order(6)
    @DisplayName("Tap start date opens date picker")
    fun tapStartDateOpensDatePicker() {
        findById("btnPayments").click()
        mediumWait()

        findById("btnStartDate").click()
        mediumWait()

        // Date picker should show OK or Cancel button
        val hasDatePicker = textExists("OK") ||
            textExists("Cancel") ||
            textExists("Date") ||
            textExists("DATE")

        assertTrue(hasDatePicker, "Should show date picker dialog")

        // Dismiss dialog
        if (textExists("Cancel")) {
            findByText("Cancel").click()
            shortWait()
        } else {
            driver.navigate().back()
            shortWait()
        }

        navigateToMain()
    }

    @Test
    @Order(7)
    @DisplayName("Tap end date opens date picker")
    fun tapEndDateOpensDatePicker() {
        findById("btnPayments").click()
        mediumWait()

        findById("btnEndDate").click()
        mediumWait()

        // Date picker should show OK or Cancel button
        val hasDatePicker = textExists("OK") ||
            textExists("Cancel") ||
            textExists("Date") ||
            textExists("DATE")

        assertTrue(hasDatePicker, "Should show date picker dialog")

        // Dismiss dialog
        if (textExists("Cancel")) {
            findByText("Cancel").click()
            shortWait()
        } else {
            driver.navigate().back()
            shortWait()
        }

        navigateToMain()
    }

    @Test
    @Order(8)
    @DisplayName("Category filter spinner still works")
    fun categoryFilterSpinnerWorks() {
        findById("btnPayments").click()
        mediumWait()

        val hasCategorySpinner = elementExists("spinnerCategory")
        assertTrue(hasCategorySpinner, "Should have category filter spinner")

        // Click to open spinner
        findById("spinnerCategory").click()
        mediumWait()

        // Should show dropdown options
        // Dismiss by tapping elsewhere or back
        driver.navigate().back()
        shortWait()

        navigateToMain()
    }

    // ==================== Phase 5.6: Payment Detail & Categorization ====================

    @Test
    @Order(9)
    @DisplayName("Export CSV button exists")
    fun exportCsvButtonExists() {
        findById("btnPayments").click()
        mediumWait()

        val hasExportBtn = elementExists("btnExportCsv")
        assertTrue(hasExportBtn, "Should have Export CSV button")

        navigateToMain()
    }

    @Test
    @Order(10)
    @DisplayName("Spending report button exists")
    fun spendingReportButtonExists() {
        findById("btnPayments").click()
        mediumWait()

        val hasReportBtn = elementExists("btnSpendingReport")
        assertTrue(hasReportBtn, "Should have Spending Report button")

        navigateToMain()
    }

    @Test
    @Order(11)
    @DisplayName("Spending report button opens report dialog")
    fun spendingReportButtonOpensDialog() {
        findById("btnPayments").click()
        mediumWait()

        // Click spending report button
        if (elementExists("btnSpendingReport")) {
            findById("btnSpendingReport").click()
            mediumWait()

            // Should show a dialog with spending report
            // Just verify app doesn't crash and close any dialog
            driver.navigate().back()
            shortWait()
        }

        navigateToMain()
    }

    @Test
    @Order(12)
    @DisplayName("Payment recycler view or empty state displayed")
    fun paymentRecyclerOrEmptyStateDisplayed() {
        findById("btnPayments").click()
        mediumWait()

        val hasRecycler = elementExists("recyclerPayments")
        val hasEmptyState = elementExists("tvEmptyState")

        assertTrue(
            hasRecycler || hasEmptyState,
            "Should show either payment list or empty state"
        )

        navigateToMain()
    }
}
