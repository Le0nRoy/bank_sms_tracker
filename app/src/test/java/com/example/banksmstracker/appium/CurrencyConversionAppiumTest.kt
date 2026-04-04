package com.example.banksmstracker.appium

import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.openqa.selenium.WebElement

/**
 * Appium E2E tests for the per-item currency conversion UI on the Payments screen.
 *
 * Covers:
 * - [spinnerCurrency] exists on the Payments screen
 * - [tvConversionRate] is hidden when payment currency == display currency (GEL)
 * - [tvConversionRate] is visible with a valid rate label when currencies differ
 * - [tvConversionRate] is hidden for a payment whose currency matches the spinner selection
 *
 * Prerequisites: Appium server running, app installed on device.
 * Run with: make test-appium
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Epic("Bank SMS Tracker")
@Feature("Currency Conversion")
@DisplayName("Currency Conversion E2E Tests")
class CurrencyConversionAppiumTest : AppiumBaseTest() {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun navigateToPayments() {
        findById("btnPayments").click()
        mediumWait()
    }

    /**
     * Select an item from the [spinnerCurrency] Spinner by its visible text.
     * Uses UiAutomator to open the spinner dropdown and click the matching option.
     */
    private fun selectCurrency(currency: String) {
        findById("spinnerCurrency").click()
        shortWait()
        // The spinner dropdown renders as a ListView with simple TextView items.
        try {
            findByText(currency).click()
        } catch (e: Exception) {
            // Fallback: text may be decorated — try textContains.
            findByTextContains(currency).click()
        }
        shortWait()
    }

    /**
     * Return the first payment item's [tvConversionRate] child, or null if the list is empty.
     */
    private fun firstItemConversionRateView(): WebElement? {
        val items = findAllById("tvConversionRate")
        return items.firstOrNull()
    }

    /**
     * Returns true if [tvConversionRate] is displayed (visible) for the first payment row.
     * Falls back to false if no payment rows exist or no tvConversionRate is found.
     */
    private fun firstItemConversionRateVisible(): Boolean {
        return try {
            driver.manage().timeouts().implicitlyWait(SHORT_TIMEOUT)
            val views = driver.findElements(
                io.appium.java_client.AppiumBy.id("$APP_PACKAGE:id/tvConversionRate")
            )
            driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
            views.isNotEmpty() && views.first().isDisplayed
        } catch (e: Exception) {
            driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
            false
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @Tag("smoke")
    @DisplayName("Currency spinner exists on Payments screen")
    fun currencySpinnerExistsOnPaymentsScreen() {
        navigateToPayments()

        assertTrue(
            elementExists("spinnerCurrency"),
            "Payments screen should have a currency display spinner (spinnerCurrency)"
        )

        navigateToMain()
    }

    @Test
    @Order(2)
    @DisplayName("GEL payments show no conversion rate label when GEL display is selected")
    fun gelPaymentsShowNoConversionRate() {
        navigateToPayments()

        // Make sure GEL is selected in the spinner (the default).
        selectCurrency("GEL")
        mediumWait()

        // If there are any payment rows, tvConversionRate must be hidden for all of them
        // because the payment currency already equals the display currency.
        val conversionViews = findAllById("tvConversionRate")
        for (view in conversionViews) {
            assertFalse(
                view.isDisplayed,
                "tvConversionRate should not be visible when display currency matches payment currency (GEL)"
            )
        }

        navigateToMain()
    }

    @Test
    @Order(3)
    @DisplayName("Switching to USD shows a conversion rate label for GEL payments")
    fun switchingToUsdShowsConversionRateText() {
        navigateToPayments()

        // Switch display currency to USD.
        selectCurrency("USD")
        mediumWait()
        // Give the async conversion coroutines time to complete.
        extraLongWait()

        // Check whether there are any payment rows at all.
        val hasPayments = elementExists("recyclerPayments") &&
            findAllById("tvMerchant").isNotEmpty()

        if (!hasPayments) {
            // No payments in DB — test is inconclusive but should not fail.
            navigateToMain()
            return
        }

        // At least one tvConversionRate must be visible and contain currency info.
        val conversionViews = findAllById("tvConversionRate")
        val visibleView = conversionViews.firstOrNull { it.isDisplayed }

        assertTrue(
            visibleView != null,
            "At least one tvConversionRate should be visible after switching to USD"
        )

        val rateText = visibleView!!.text
        assertTrue(
            rateText.contains("USD") || rateText.contains("GEL"),
            "Rate label should mention USD and/or GEL, got: '$rateText'"
        )

        navigateToMain()
    }

    @Test
    @Order(4)
    @DisplayName("Conversion rate not shown when display currency matches payment currency")
    fun conversionRateNotShownWhenSameCurrency() {
        navigateToPayments()

        // Switch to GEL — GEL-denominated payments should show no conversion label.
        selectCurrency("GEL")
        mediumWait()

        val conversionViews = findAllById("tvConversionRate")
        for (view in conversionViews) {
            assertFalse(
                view.isDisplayed,
                "tvConversionRate must be hidden when payment currency == display currency"
            )
        }

        navigateToMain()
    }
}
