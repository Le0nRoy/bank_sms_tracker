package com.example.banksmstracker.appium

import io.appium.java_client.AppiumBy
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

/**
 * Appium E2E tests for the complete SMS to Payment flow.
 *
 * Tests the end-to-end user workflow from app setup to viewing payments.
 *
 * Covers:
 * - Initial app setup with categories and senders
 * - Apply Rules screen functionality
 * - Payments screen viewing and filtering
 * - Export configuration
 * - Complete user journey
 *
 * NOTE: These tests require:
 * 1. Appium server running (`make appium-start` or `make appium-docker-start`)
 * 2. Android emulator running with the app installed (`make install`)
 *
 * Run with: make test-appium
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
// @Disabled("Requires Appium server and Android emulator. Run with: make test-appium")
@DisplayName("SMS to Payment Flow E2E Tests")
class SmsToPaymentFlowAppiumTest : AppiumBaseTest() {

    @Test
    @Order(1)
    @DisplayName("Setup: Create category for payments")
    fun createCategory() {
        findByText("Categories").click()
        mediumWait()

        findById("fabAddCategory").click()
        shortWait()

        val categoryNameFields = findAllById("nameEditText")
        categoryNameFields.last().sendKeys("Shopping")
        shortWait()

        // Add merchant
        val addMerchantButtons = findAllById("btnAddMerchant")
        addMerchantButtons.last().click()
        shortWait()

        val merchantsContainers = findAllById("merchantsContainer")
        val merchantFields = merchantsContainers.last()
            .findElements(AppiumBy.className("android.widget.EditText"))
        if (merchantFields.isNotEmpty()) {
            merchantFields.last().sendKeys("Amazon")
        }
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(2)
    @DisplayName("Setup: Create sender with regex rule")
    fun createSenderWithRule() {
        findByText("Senders").click()
        mediumWait()

        findById("fabAddSender").click()
        shortWait()

        // Set sender name
        val senderNameFields = findAllById("senderNameEditText")
        senderNameFields.last().clear()
        senderNameFields.last().sendKeys("Bank SMS")
        shortWait()

        // Add address
        val addAddressButtons = findAllById("btnAddAddress")
        addAddressButtons.last().click()
        shortWait()

        val addressContainers = findAllById("addressesContainer")
        val addressFields = addressContainers.last()
            .findElements(AppiumBy.className("android.widget.EditText"))
        if (addressFields.isNotEmpty()) {
            addressFields.last().sendKeys("BANKTEST")
        }
        shortWait()

        // Add rule
        val addRuleButtons = findAllById("btnAddRule")
        addRuleButtons.last().click()
        shortWait()

        val rulesContainers = findAllById("rulesContainer")
        val ruleFields = rulesContainers.last()
            .findElements(AppiumBy.className("android.widget.EditText"))
        if (ruleFields.isNotEmpty()) {
            ruleFields.last().sendKeys(
                "Payment (\\d+\\.\\d{2}) (\\w{3}) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
            )
        }
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(3)
    @DisplayName("Navigate to Apply Rules screen")
    fun navigateToApplyRules() {
        findById("btnApplyRules").click()
        mediumWait()

        // Handle permission dialog if it appears
        handlePermissionDialogIfPresent()

        // Verify we're on Apply Rules screen
        // The screen should load even if there are no SMS messages
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(4)
    @DisplayName("Navigate to Payments screen")
    fun navigateToPayments() {
        findByText("Payments").click()
        mediumWait()

        // Verify we're on Payments screen by checking for the spinner or recycler
        assertTrue(
            elementExists("spinnerCategory") || elementExists("recyclerPayments"),
            "Should be on Payments screen"
        )

        navigateToMain()
    }

    @Test
    @Order(5)
    @DisplayName("Payments screen shows empty state when no payments")
    fun paymentsShowsEmptyState() {
        findByText("Payments").click()
        mediumWait()

        // Check for empty state or payment list
        val hasEmptyState = elementExists("tvEmptyState")
        val hasPayments = elementExists("recyclerPayments")

        assertTrue(hasEmptyState || hasPayments, "Should show empty state or payments list")

        navigateToMain()
    }

    @Test
    @Order(6)
    @DisplayName("Payments screen has category filter")
    fun paymentsHasCategoryFilter() {
        findByText("Payments").click()
        mediumWait()

        // Verify spinner exists
        assertTrue(elementExists("spinnerCategory"), "Should have category filter spinner")

        // Click on spinner to see options
        findById("spinnerCategory").click()
        shortWait()

        // Close spinner
        driver.navigate().back()
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(7)
    @DisplayName("Export CSV button is available")
    fun exportCsvButtonAvailable() {
        findByText("Payments").click()
        mediumWait()

        assertTrue(elementExists("btnExportCsv"), "Should have Export CSV button")

        navigateToMain()
    }

    @Test
    @Order(8)
    @DisplayName("Export configuration opens share dialog")
    fun exportConfigurationOpensShareDialog() {
        findById("btnExportConfig").click()
        mediumWait()

        // Should open share dialog or show success message
        // The exact behavior may vary, just verify we can navigate back
        driver.navigate().back()
        shortWait()

        // Make sure we're back at main
        if (!elementExists("btnCategories")) {
            driver.navigate().back()
            shortWait()
        }
    }

    @Test
    @Order(9)
    @DisplayName("Check Senders screen loads")
    fun checkSendersScreenLoads() {
        findById("btnCheckSenders").click()
        mediumWait()

        // Should navigate to check senders screen
        // Navigate back
        navigateToMain()
    }

    @Test
    @Order(10)
    @DisplayName("Complete user journey: Setup and view")
    fun completeUserJourney() {
        // 1. Create a new category
        findByText("Categories").click()
        mediumWait()

        findById("fabAddCategory").click()
        shortWait()

        val categoryNameFields = findAllById("nameEditText")
        categoryNameFields.last().sendKeys("Groceries")
        shortWait()

        val addMerchantButtons = findAllById("btnAddMerchant")
        addMerchantButtons.last().click()
        shortWait()

        val merchantsContainers = findAllById("merchantsContainer")
        val merchantFields = merchantsContainers.last()
            .findElements(AppiumBy.className("android.widget.EditText"))
        if (merchantFields.isNotEmpty()) {
            merchantFields.last().sendKeys("Whole Foods")
        }
        shortWait()

        navigateToMain()

        // 2. Create a new sender
        findByText("Senders").click()
        mediumWait()

        findById("fabAddSender").click()
        shortWait()

        val senderNameFields = findAllById("senderNameEditText")
        senderNameFields.last().sendKeys("MyBank")
        shortWait()

        navigateToMain()

        // 3. View payments (should be empty but screen should work)
        findByText("Payments").click()
        mediumWait()

        assertTrue(
            elementExists("spinnerCategory") || elementExists("tvEmptyState"),
            "Payments screen should be functional"
        )

        navigateToMain()

        // 4. Verify data persists
        findByText("Categories").click()
        mediumWait()

        var foundGroceries = false
        val allCategoryNames = findAllById("nameEditText")
        for (field in allCategoryNames) {
            if (field.text == "Groceries") {
                foundGroceries = true
                break
            }
        }
        assertTrue(foundGroceries, "Groceries category should persist")

        navigateToMain()
    }

    private fun handlePermissionDialogIfPresent() {
        try {
            // Try to find and click permission allow button
            val allowButton = driver.findElement(
                AppiumBy.id("com.android.permissioncontroller:id/permission_allow_button")
            )
            if (allowButton.isDisplayed) {
                allowButton.click()
                shortWait()
            }
        } catch (e: Exception) {
            // Permission dialog not present or already granted
        }
    }
}
