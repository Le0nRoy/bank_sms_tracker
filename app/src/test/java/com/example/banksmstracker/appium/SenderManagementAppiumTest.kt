package com.example.banksmstracker.appium

import io.appium.java_client.AppiumBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
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
 * Covers:
 * - Navigation to Senders screen
 * - Adding new senders
 * - Editing sender names and addresses
 * - Adding regex rules to senders
 * - Toggling sender enabled/disabled state
 * - Multiple senders management
 *
 * NOTE: These tests require:
 * 1. Appium server running (`make appium-start` or `make appium-docker-start`)
 * 2. Android emulator running with the app installed (`make install`)
 *
 * Run with: make test-appium
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
// @Disabled("Requires Appium server and Android emulator. Run with: make test-appium")
@DisplayName("Sender Management E2E Tests")
class SenderManagementAppiumTest : AppiumBaseTest() {

    @Test
    @Order(1)
    @DisplayName("Navigate to Senders screen from main menu")
    fun navigateToSendersScreen() {
        // Click on Senders button from main activity
        val sendersButton = findByText("Senders")
        sendersButton.click()
        mediumWait()

        // Verify we're on the Senders screen by checking for the RecyclerView
        assertTrue(elementExists("recyclerViewSenders"))

        // Navigate back to main
        navigateToMain()
    }

    @Test
    @Order(2)
    @DisplayName("Add new sender with name")
    fun addNewSenderWithName() {
        // Navigate to senders
        findByText("Senders").click()
        mediumWait()

        // Click FAB to add new sender
        val addButton = findById("fabAddSender")
        addButton.click()
        shortWait()

        // A new sender item should appear in the list
        // Find the name edit text and enter a name
        val senderNameFields = findAllById("senderNameEditText")
        assertTrue(senderNameFields.isNotEmpty(), "Should have at least one sender name field")

        val lastNameField = senderNameFields.last()
        lastNameField.clear()
        lastNameField.sendKeys("Test Bank SMS")
        shortWait()

        // Verify the text was entered
        assertEquals("Test Bank SMS", lastNameField.text)

        navigateToMain()
    }

    @Test
    @Order(3)
    @DisplayName("Add address to sender")
    fun addAddressToSender() {
        findByText("Senders").click()
        mediumWait()

        // Find the Add Address button and click it
        val addAddressButtons = findAllById("btnAddAddress")
        assertTrue(addAddressButtons.isNotEmpty(), "Should have Add Address button")

        addAddressButtons.first().click()
        shortWait()

        // Find the address container and the new EditText inside
        val addressContainer = findById("addressesContainer")
        val editTexts = addressContainer.findElements(AppiumBy.className("android.widget.EditText"))
        assertTrue(editTexts.isNotEmpty(), "Should have address EditText")

        val addressField = editTexts.last()
        addressField.sendKeys("TESTBANK123")
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(4)
    @DisplayName("Add regex rule to sender")
    fun addRegexRuleToSender() {
        findByText("Senders").click()
        mediumWait()

        // Find the Add Rule button
        val addRuleButtons = findAllById("btnAddRule")
        assertTrue(addRuleButtons.isNotEmpty(), "Should have Add Rule button")

        addRuleButtons.first().click()
        shortWait()

        // Find rules container and the new EditText
        val rulesContainer = findById("rulesContainer")
        val editTexts = rulesContainer.findElements(AppiumBy.className("android.widget.EditText"))
        assertTrue(editTexts.isNotEmpty(), "Should have rule EditText")

        val ruleField = editTexts.last()
        ruleField.sendKeys("Payment (\\d+\\.\\d{2}) (\\w{3}) card (\\d+) (.+) at (\\d+) bal (\\d+\\.\\d{2})")
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(5)
    @DisplayName("Toggle sender enabled/disabled state")
    fun toggleSenderEnabledState() {
        findByText("Senders").click()
        mediumWait()

        // Find the enabled switch
        val switches = findAllById("switchSenderEnabled")
        assertTrue(switches.isNotEmpty(), "Should have enabled switch")

        val enabledSwitch = switches.first()
        val initialState = enabledSwitch.getAttribute("checked")

        // Toggle the switch
        enabledSwitch.click()
        shortWait()

        // Verify state changed
        val newState = enabledSwitch.getAttribute("checked")
        assertNotEquals(initialState, newState, "Switch state should have changed")

        // Toggle back
        enabledSwitch.click()
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(6)
    @DisplayName("Add multiple addresses to sender")
    fun addMultipleAddresses() {
        findByText("Senders").click()
        mediumWait()

        // Find the Add Address button
        val addAddressButton = findAllById("btnAddAddress").first()

        // Get initial count of address fields
        val addressContainer = findById("addressesContainer")
        val initialCount = addressContainer.findElements(AppiumBy.className("android.widget.EditText")).size

        // Add two more addresses
        addAddressButton.click()
        shortWait()
        addAddressButton.click()
        shortWait()

        // Verify count increased
        val newCount = addressContainer.findElements(AppiumBy.className("android.widget.EditText")).size
        assertEquals(initialCount + 2, newCount, "Should have 2 more address fields")

        navigateToMain()
    }

    @Test
    @Order(7)
    @DisplayName("Add multiple regex rules to sender")
    fun addMultipleRules() {
        findByText("Senders").click()
        mediumWait()

        // Find the Add Rule button
        val addRuleButton = findAllById("btnAddRule").first()

        // Get initial count of rule fields
        val rulesContainer = findById("rulesContainer")
        val initialCount = rulesContainer.findElements(AppiumBy.className("android.widget.EditText")).size

        // Add another rule
        addRuleButton.click()
        shortWait()

        // Verify count increased
        val newCount = rulesContainer.findElements(AppiumBy.className("android.widget.EditText")).size
        assertTrue(newCount > initialCount, "Should have more rule fields")

        // Enter a different pattern
        val editTexts = rulesContainer.findElements(AppiumBy.className("android.widget.EditText"))
        val lastRuleField = editTexts.last()
        lastRuleField.sendKeys("Transfer (\\d+) from (.+)")
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(8)
    @DisplayName("Create second sender")
    fun createSecondSender() {
        findByText("Senders").click()
        mediumWait()

        // Count initial senders
        val initialSenderNames = findAllById("senderNameEditText").size

        // Add new sender
        findById("fabAddSender").click()
        shortWait()

        // Verify new sender was added
        val newSenderNames = findAllById("senderNameEditText").size
        assertEquals(initialSenderNames + 1, newSenderNames, "Should have one more sender")

        // Configure the new sender
        val senderNameFields = findAllById("senderNameEditText")
        val lastSenderName = senderNameFields.last()
        lastSenderName.clear()
        lastSenderName.sendKeys("Another Bank")
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(9)
    @DisplayName("Verify sender data persists after navigation")
    fun verifyDataPersistsAfterNavigation() {
        findByText("Senders").click()
        mediumWait()

        // Go back and return
        navigateToMain()
        mediumWait()

        findByText("Senders").click()
        mediumWait()

        // Verify senders still exist
        val senderNames = findAllById("senderNameEditText")
        assertTrue(senderNames.isNotEmpty(), "Senders should persist after navigation")

        // Check if our test sender name exists
        var foundTestBank = false
        for (field in senderNames) {
            if (field.text.contains("Test Bank") || field.text.contains("Another Bank")) {
                foundTestBank = true
                break
            }
        }
        assertTrue(foundTestBank, "Test sender data should persist")

        navigateToMain()
    }

    @Test
    @Order(10)
    @DisplayName("Edit existing sender name")
    fun editExistingSenderName() {
        findByText("Senders").click()
        mediumWait()

        val senderNameFields = findAllById("senderNameEditText")
        assertTrue(senderNameFields.isNotEmpty())

        val firstSenderName = senderNameFields.first()
        val originalName = firstSenderName.text

        // Edit the name
        firstSenderName.clear()
        firstSenderName.sendKeys("$originalName Modified")
        shortWait()

        // Verify change
        assertTrue(
            firstSenderName.text.contains("Modified"),
            "Sender name should be modified"
        )

        navigateToMain()
    }

    @Test
    @Order(11)
    @DisplayName("Disabled sender shows visual indication")
    fun disabledSenderShowsVisualIndication() {
        findByText("Senders").click()
        mediumWait()

        // Get a switch and disable the sender
        val switches = findAllById("switchSenderEnabled")
        assertTrue(switches.isNotEmpty())

        val enabledSwitch = switches.first()

        // Ensure it's currently enabled
        if (enabledSwitch.getAttribute("checked") != "true") {
            enabledSwitch.click()
            shortWait()
        }

        // Now disable it
        enabledSwitch.click()
        shortWait()

        // Verify it's disabled
        assertFalse(
            enabledSwitch.getAttribute("checked").toBoolean(),
            "Sender should be disabled"
        )

        // Re-enable for other tests
        enabledSwitch.click()
        shortWait()

        navigateToMain()
    }
}
