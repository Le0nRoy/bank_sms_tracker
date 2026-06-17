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
 * Appium E2E tests for the Exchange Rates screen.
 *
 * Verifies REQ-9: ExchangeRatesActivity accessible from MainActivity, lists stored rates,
 * supports multi-currency filter, date range, and "Download Missing" button.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Epic("Bank SMS Tracker")
@Feature("Exchange Rates")
@DisplayName("Exchange Rates Screen E2E Tests")
class ExchangeRatesAppiumTest : AppiumBaseTest() {

    private fun navigateToExchangeRates() {
        try {
            scrollToElementById("btnExchangeRates").click()
        } catch (_: Exception) {
            findById("btnExchangeRates").click()
        }
        mediumWait()
    }

    @Test
    @Order(1)
    @Tag("smoke")
    @DisplayName("Navigate to Exchange Rates screen from MainActivity")
    fun navigateToExchangeRatesScreen() {
        navigateToExchangeRates()

        assertTrue(
            elementExists("recyclerExchangeRates") || elementExists("tvErEmpty"),
            "Should be on Exchange Rates screen"
        )

        navigateToMain()
    }

    @Test
    @Order(2)
    @Tag("smoke")
    @DisplayName("Currency filter button exists on Exchange Rates screen")
    fun currencyFilterButtonExists() {
        navigateToExchangeRates()

        assertTrue(elementExists("btnSelectCurrencies"), "Should have currency select button")

        navigateToMain()
    }

    @Test
    @Order(3)
    @Tag("smoke")
    @DisplayName("Start date button exists on Exchange Rates screen")
    fun startDateButtonExists() {
        navigateToExchangeRates()

        assertTrue(elementExists("btnErStartDate"), "Should have start date button")

        navigateToMain()
    }

    @Test
    @Order(4)
    @Tag("smoke")
    @DisplayName("End date button exists on Exchange Rates screen")
    fun endDateButtonExists() {
        navigateToExchangeRates()

        assertTrue(elementExists("btnErEndDate"), "Should have end date button")

        navigateToMain()
    }

    @Test
    @Order(5)
    @Tag("smoke")
    @DisplayName("Download Missing button exists on Exchange Rates screen")
    fun downloadMissingButtonExists() {
        navigateToExchangeRates()

        assertTrue(elementExists("btnDownloadMissing"), "Should have Download Missing button")

        navigateToMain()
    }

    @Test
    @Order(6)
    @DisplayName("Clear filters button exists on Exchange Rates screen")
    fun clearFiltersButtonExists() {
        navigateToExchangeRates()

        assertTrue(elementExists("btnErClearFilters"), "Should have clear filters button")

        navigateToMain()
    }

    @Test
    @Order(7)
    @DisplayName("Empty state shown when no rates stored")
    fun emptyStateOrListVisible() {
        navigateToExchangeRates()

        assertTrue(
            elementExists("tvErEmpty") || elementExists("recyclerExchangeRates"),
            "Should show either empty state or rates list"
        )

        navigateToMain()
    }

    @Test
    @Order(8)
    @DisplayName("Currency filter opens multi-select dialog")
    fun currencyFilterOpensDialog() {
        navigateToExchangeRates()

        findById("btnSelectCurrencies").click()
        mediumWait()

        // Dialog detected by button text (same pattern as category-filter dialog test)
        val hasDialog = textExists("OK") || textExists("Cancel")
        assertTrue(hasDialog, "Currency selection dialog should open")

        // Dismiss dialog without changing state
        if (textExists("Cancel")) {
            findByText("Cancel").click()
        } else {
            driver.navigate().back()
        }
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(9)
    @DisplayName("Payments screen default currency spinner shows no selection")
    fun paymentsSpinnerDefaultIsNone() {
        findById("btnPayments").click()
        mediumWait()

        // Spinner should be at position 0 ("— original —")
        val spinner = findById("spinnerCurrency")
        val spinnerText = spinner.text ?: ""
        assertTrue(
            spinnerText.contains("original", ignoreCase = true) || spinnerText.isBlank(),
            "Currency spinner should default to 'original' (no conversion), got: '$spinnerText'"
        )

        navigateToMain()
    }

    @Test
    @Order(10)
    @DisplayName("Payments screen loading bar hidden by default")
    fun paymentsLoadingBarHiddenByDefault() {
        findById("btnPayments").click()
        mediumWait()

        // Loading bar should be gone (not visible) before any currency is selected
        assertTrue(
            !elementExists("pbConversionLoading") || !findById("pbConversionLoading").isDisplayed,
            "Conversion loading bar should be hidden by default"
        )

        navigateToMain()
    }
}
