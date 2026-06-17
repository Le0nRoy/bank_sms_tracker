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
 * Appium E2E tests for the Incomes screen.
 *
 * Covers the full Incomes interface introduced in PROMPT-3:
 * - Sender filter spinner
 * - Date range buttons
 * - Source search field
 * - Income report button
 * - Empty-state / list display
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Epic("Bank SMS Tracker")
@Feature("Incomes")
@DisplayName("Incomes Screen E2E Tests")
class IncomesAppiumTest : AppiumBaseTest() {

    private fun navigateToIncomes() {
        findById("btnIncomes").click()
        mediumWait()
    }

    @Test
    @Order(1)
    @Tag("smoke")
    @DisplayName("Navigate to Incomes screen")
    fun navigateToIncomesScreen() {
        navigateToIncomes()

        assertTrue(
            elementExists("recyclerIncomes") || elementExists("tvEmptyState") || elementExists("tvIncomeTotal"),
            "Should be on Incomes screen"
        )

        navigateToMain()
    }

    @Test
    @Order(2)
    @Tag("smoke")
    @DisplayName("Sender filter spinner exists on Incomes screen")
    fun senderFilterSpinnerExists() {
        navigateToIncomes()

        assertTrue(elementExists("spinnerSender"), "Should have sender filter spinner")

        navigateToMain()
    }

    @Test
    @Order(3)
    @Tag("smoke")
    @DisplayName("Start date button exists on Incomes screen")
    fun startDateButtonExists() {
        navigateToIncomes()

        assertTrue(elementExists("btnStartDate"), "Should have start date button")

        navigateToMain()
    }

    @Test
    @Order(4)
    @DisplayName("End date button exists on Incomes screen")
    fun endDateButtonExists() {
        navigateToIncomes()

        assertTrue(elementExists("btnEndDate"), "Should have end date button")

        navigateToMain()
    }

    @Test
    @Order(5)
    @DisplayName("Clear dates button exists on Incomes screen")
    fun clearDatesButtonExists() {
        navigateToIncomes()

        assertTrue(elementExists("btnClearDates"), "Should have clear dates button")

        navigateToMain()
    }

    @Test
    @Order(6)
    @Tag("smoke")
    @DisplayName("Source search field exists on Incomes screen")
    fun sourceSearchFieldExists() {
        navigateToIncomes()

        assertTrue(elementExists("etSourceSearch"), "Should have source search EditText")

        navigateToMain()
    }

    @Test
    @Order(7)
    @Tag("smoke")
    @DisplayName("Income report button exists on Incomes screen")
    fun incomeReportButtonExists() {
        navigateToIncomes()

        assertTrue(elementExists("btnIncomeReport"), "Should have income report button")

        navigateToMain()
    }

    @Test
    @Order(8)
    @DisplayName("Tap start date opens date picker on Incomes screen")
    fun tapStartDateOpensDatePicker() {
        navigateToIncomes()

        findById("btnStartDate").click()
        mediumWait()

        val hasDatePicker = textExists("OK") || textExists("Cancel") || textExists("Date")
        assertTrue(hasDatePicker, "Should show date picker dialog")

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
    @DisplayName("Income total label is visible on Incomes screen")
    fun incomeTotalLabelVisible() {
        navigateToIncomes()

        assertTrue(elementExists("tvIncomeTotal"), "Should show income total label")

        navigateToMain()
    }

    @Test
    @Order(10)
    @DisplayName("Incomes screen shows list or empty state")
    fun incomesShowsListOrEmptyState() {
        navigateToIncomes()

        assertTrue(
            elementExists("recyclerIncomes") || elementExists("tvEmptyState"),
            "Should show recycler or empty state"
        )

        navigateToMain()
    }

    @Test
    @Order(11)
    @DisplayName("Source search accepts text input")
    fun sourceSearchAcceptsInput() {
        navigateToIncomes()

        val searchField = findById("etSourceSearch")
        searchField.click()
        shortWait()
        searchField.sendKeys("salary")
        shortWait()

        val text = searchField.text
        assertTrue(text.contains("salary", ignoreCase = true), "Search field should contain input, got: '$text'")

        try {
            driver.hideKeyboard()
        } catch (_: Exception) {}
        shortWait()

        navigateToMain()
    }
}
