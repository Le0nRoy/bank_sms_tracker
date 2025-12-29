package com.example.banksmstracker.appium

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

/**
 * Appium E2E tests for Sender management flow.
 *
 * Tests the complete user workflow for creating, editing, and configuring senders.
 *
 * NOTE: These tests require:
 * 1. Appium server running (`appium`)
 * 2. Android emulator running with the app installed
 *
 * Run with: ./gradlew test --tests "*.appium.SenderManagementAppiumTest"
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Disabled("Requires Appium server and Android emulator. Run manually.")
@DisplayName("Sender Management E2E Tests")
class SenderManagementAppiumTest : AppiumBaseTest() {

    @Test
    @Order(1)
    @DisplayName("Navigate to Senders screen")
    fun navigateToSendersScreen() {
        // Click on Senders button from main activity
        val sendersButton = findByText("Senders")
        sendersButton.click()

        // Verify we're on the Senders screen
        Thread.sleep(500)
        val title = driver.findElement(
            io.appium.java_client.AppiumBy.androidUIAutomator(
                "new UiSelector().className(\"android.widget.TextView\").text(\"Senders\")"
            )
        )
        assertTrue(title.isDisplayed)
    }

    @Test
    @Order(2)
    @DisplayName("Add new sender")
    fun addNewSender() {
        // Navigate to senders
        findByText("Senders").click()
        Thread.sleep(500)

        // Click add button (FAB)
        val addButton = findById("fab_add")
        addButton.click()
        Thread.sleep(300)

        // Fill in sender name
        val senderNameField = findById("edit_sender_name")
        senderNameField.clear()
        senderNameField.sendKeys("Test Bank")

        // Add an address
        val addressField = findById("edit_address")
        addressField.sendKeys("TESTBANK")

        // Save
        val saveButton = findByText("Save")
        saveButton.click()
        Thread.sleep(500)

        // Verify sender appears in list
        val newSender = findByText("Test Bank")
        assertTrue(newSender.isDisplayed)
    }

    @Test
    @Order(3)
    @DisplayName("Add regex rule to sender")
    fun addRegexRuleToSender() {
        // Navigate to senders
        findByText("Senders").click()
        Thread.sleep(500)

        // Click on existing sender
        val senderItem = findByText("Test Bank")
        senderItem.click()
        Thread.sleep(300)

        // Add a regex rule
        val addRuleButton = findByText("Add Rule")
        addRuleButton.click()
        Thread.sleep(200)

        // Enter regex pattern
        val regexField = findById("edit_regex")
        regexField.sendKeys("Payment (\\d+\\.\\d{2}) (\\w+)")

        // Save
        findByText("Save").click()
        Thread.sleep(500)

        // Verify we're back at senders list
        assertTrue(findByText("Test Bank").isDisplayed)
    }

    @Test
    @Order(4)
    @DisplayName("Toggle sender enabled state")
    fun toggleSenderEnabled() {
        // Navigate to senders
        findByText("Senders").click()
        Thread.sleep(500)

        // Click on sender
        val senderItem = findByText("Test Bank")
        senderItem.click()
        Thread.sleep(300)

        // Toggle enabled switch
        val enabledSwitch = findById("switch_enabled")
        val initialState = enabledSwitch.getAttribute("checked")
        enabledSwitch.click()
        Thread.sleep(200)

        // Verify state changed
        val newState = enabledSwitch.getAttribute("checked")
        assertTrue(initialState != newState)

        // Save
        findByText("Save").click()
    }

    @Test
    @Order(5)
    @DisplayName("Edit sender addresses")
    fun editSenderAddresses() {
        // Navigate to senders
        findByText("Senders").click()
        Thread.sleep(500)

        // Click on sender
        findByText("Test Bank").click()
        Thread.sleep(300)

        // Add another address
        val addAddressButton = findByText("Add Address")
        addAddressButton.click()
        Thread.sleep(200)

        val newAddressField = driver.findElements(
            io.appium.java_client.AppiumBy.id("$APP_PACKAGE:id/edit_address")
        ).last()
        newAddressField.sendKeys("TESTBANK2")

        // Save
        findByText("Save").click()
        Thread.sleep(500)

        // Verify sender is updated
        assertTrue(findByText("Test Bank").isDisplayed)
    }
}
