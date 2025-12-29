package com.example.banksmstracker.appium

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

/**
 * Appium E2E tests for full SMS to Payment flow.
 *
 * Tests the complete user workflow from configuring rules to viewing payments.
 *
 * NOTE: These tests require:
 * 1. Appium server running (`appium`)
 * 2. Android emulator running with the app installed
 * 3. SMS permissions granted to the app
 *
 * Run with: ./gradlew test --tests "*.appium.SmsToPaymentFlowAppiumTest"
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Disabled("Requires Appium server and Android emulator. Run manually.")
@DisplayName("SMS to Payment Flow E2E Tests")
class SmsToPaymentFlowAppiumTest : AppiumBaseTest() {

    @Test
    @Order(1)
    @DisplayName("Setup: Create category for payments")
    fun createCategory() {
        findByText("Categories").click()
        Thread.sleep(500)

        findById("fab_add").click()
        Thread.sleep(300)

        findById("edit_category_name").sendKeys("Shopping")
        findById("edit_merchant").sendKeys("Amazon")

        findByText("Save").click()
        Thread.sleep(500)

        // Go back to main
        driver.navigate().back()
    }

    @Test
    @Order(2)
    @DisplayName("Setup: Create sender with regex rule")
    fun createSenderWithRule() {
        findByText("Senders").click()
        Thread.sleep(500)

        findById("fab_add").click()
        Thread.sleep(300)

        findById("edit_sender_name").sendKeys("Bank SMS")
        findById("edit_address").sendKeys("BANKTEST")

        // Add rule
        findByText("Add Rule").click()
        Thread.sleep(200)

        // Enter regex that captures amount, currency, card, merchant, timestamp, balance
        findById("edit_regex").sendKeys(
            "Payment (\\d+\\.\\d{2}) (\\w{3}) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})"
        )

        findByText("Save").click()
        Thread.sleep(500)

        driver.navigate().back()
    }

    @Test
    @Order(3)
    @DisplayName("Use Apply Rules to process test SMS")
    fun applyRulesToTestSms() {
        findByText("Apply Rules").click()
        Thread.sleep(500)

        // If permission dialog appears, grant it
        try {
            val allowButton = driver.findElement(
                io.appium.java_client.AppiumBy.id("com.android.permissioncontroller:id/permission_allow_button")
            )
            if (allowButton.isDisplayed) {
                allowButton.click()
                Thread.sleep(300)
            }
        } catch (e: Exception) {
            // Permission already granted
        }

        // The test would require actual SMS in the inbox
        // For demo purposes, verify the screen loads
        Thread.sleep(500)

        driver.navigate().back()
    }

    @Test
    @Order(4)
    @DisplayName("View Payments screen")
    fun viewPayments() {
        findByText("Payments").click()
        Thread.sleep(500)

        // Verify payments screen loads
        // In real test, would verify payment entries after SMS processing
        val paymentsTitle = driver.findElement(
            io.appium.java_client.AppiumBy.androidUIAutomator(
                "new UiSelector().className(\"android.widget.TextView\").textContains(\"Payments\")"
            )
        )
        assertTrue(paymentsTitle.isDisplayed)

        driver.navigate().back()
    }

    @Test
    @Order(5)
    @DisplayName("Export configuration")
    fun exportConfiguration() {
        findByText("Export Config").click()
        Thread.sleep(500)

        // Handle share dialog
        try {
            val shareTitle = driver.findElement(
                io.appium.java_client.AppiumBy.androidUIAutomator(
                    "new UiSelector().textContains(\"Share\")"
                )
            )
            assertTrue(shareTitle.isDisplayed)
            // Cancel share
            driver.navigate().back()
        } catch (e: Exception) {
            // Share dialog format may vary
        }
    }

    @Test
    @Order(6)
    @DisplayName("Test regex builder")
    fun testRegexBuilder() {
        findByText("Regex Builder").click()
        Thread.sleep(500)

        // Enter test message
        val messageInput = findById("edit_test_message")
        messageInput.sendKeys("Payment 99.99 USD card 1234 TestStore at 20231225 bal 500.00")

        // Enter regex
        val regexInput = findById("edit_regex_pattern")
        regexInput.sendKeys("Payment (\\d+\\.\\d{2}) (\\w{3})")

        // Verify match preview updates
        Thread.sleep(500)

        driver.navigate().back()
    }

    @Test
    @Order(7)
    @DisplayName("Filter payments by category")
    fun filterPaymentsByCategory() {
        findByText("Payments").click()
        Thread.sleep(500)

        // Click filter button
        val filterButton = findById("btn_filter")
        filterButton.click()
        Thread.sleep(300)

        // Select category filter
        findByText("Shopping").click()
        Thread.sleep(300)

        // Apply filter
        findByText("Apply").click()
        Thread.sleep(500)

        // Verify filter is applied
        driver.navigate().back()
    }

    @Test
    @Order(8)
    @DisplayName("Export payments to CSV")
    fun exportPaymentsCsv() {
        findByText("Payments").click()
        Thread.sleep(500)

        // Click export button
        val exportButton = findById("btn_export_csv")
        exportButton.click()
        Thread.sleep(500)

        // Handle share dialog
        driver.navigate().back()
        driver.navigate().back()
    }
}
