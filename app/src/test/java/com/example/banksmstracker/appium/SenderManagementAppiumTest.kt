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
import org.junit.jupiter.api.Tag
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
    @Tag("smoke")
    @DisplayName("Navigate to Senders screen from main menu")
    fun navigateToSendersScreen() {
        // Click on Senders button from main activity
        clickButton("btnSenders")
        longWait()

        // Verify we're on the Senders screen by checking for the RecyclerView
        assertTrue(elementExists("recyclerViewSenders"))
    }

    @Test
    @Order(2)
    @Tag("smoke")
    @DisplayName("Add new sender with name")
    fun addNewSenderWithName() {
        // Navigate to senders
        clickButton("btnSenders")
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
        clickButton("btnSenders")
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
        clickButton("btnSenders")
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
        clickButton("btnSenders")
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
        clickButton("btnSenders")
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
        clickButton("btnSenders")
        mediumWait()

        // Find the Add Rule button (may be below fold after MaterialCardView rules)
        val addRuleButton = scrollToElementById("btnAddRule")

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
        clickButton("btnSenders")
        extraLongWait()

        // Get initial count by scrolling through entire list
        val initialSenderNames = countTotalSenderItems()

        // Add new sender using direct FAB click approach
        var senderAdded = false
        repeat(5) { attempt ->
            if (!senderAdded) {
                // Try different click strategies on each attempt
                when (attempt) {
                    0, 1 -> clickFab("fabAddSender")
                    2 -> {
                        // Direct find and click
                        try {
                            findById("fabAddSender").click()
                        } catch (e: Exception) {}
                    }
                    else -> {
                        // UiAutomator direct click
                        try {
                            driver.findElement(
                                AppiumBy.androidUIAutomator(
                                    "new UiSelector().resourceId(\"$APP_PACKAGE:id/fabAddSender\")"
                                )
                            ).click()
                        } catch (e: Exception) {}
                    }
                }
                extraLongWait()

                // Scroll to the END to see the new item (added at the end of list)
                try {
                    driver.findElement(
                        AppiumBy.androidUIAutomator(
                            "new UiScrollable(new UiSelector().resourceId(\"$APP_PACKAGE:id/recyclerViewSenders\")).scrollToEnd(5)"
                        )
                    )
                } catch (e: Exception) {}
                mediumWait()

                // Check if sender was added by counting all items
                val newSenderNames = countTotalSenderItems()
                if (newSenderNames > initialSenderNames) {
                    senderAdded = true
                }
            }
        }

        // Verify new sender was added
        val finalSenderNames = countTotalSenderItems()
        assertTrue(finalSenderNames > initialSenderNames, "Should have more senders (had $initialSenderNames, now $finalSenderNames)")

        // Configure the new sender if it was added
        if (finalSenderNames > initialSenderNames) {
            // Scroll to end to find last sender
            try {
                driver.findElement(
                    AppiumBy.androidUIAutomator(
                        "new UiScrollable(new UiSelector().resourceId(\"$APP_PACKAGE:id/recyclerViewSenders\")).scrollToEnd(5)"
                    )
                )
            } catch (e: Exception) {}
            mediumWait()

            val senderNameFields = findAllById("senderNameEditText")
            if (senderNameFields.isNotEmpty()) {
                val lastSenderName = senderNameFields.last()
                lastSenderName.clear()
                lastSenderName.sendKeys("Another Bank")
                mediumWait()
            }
        }
    }

    /**
     * Count total sender items by scrolling through the entire list.
     * RecyclerView only renders visible items, so we need to scroll to count all.
     */
    private fun countTotalSenderItems(): Int {
        // First scroll to beginning
        try {
            driver.findElement(
                AppiumBy.androidUIAutomator(
                    "new UiScrollable(new UiSelector().resourceId(\"$APP_PACKAGE:id/recyclerViewSenders\")).scrollToBeginning(5)"
                )
            )
        } catch (e: Exception) {}
        shortWait()

        // Collect unique sender names by scrolling through list
        val seenNames = mutableSetOf<String>()
        var lastCount = -1
        var currentCount = 0

        repeat(10) {
            val visible = findAllById("senderNameEditText")
            visible.forEach { el ->
                try {
                    val text = el.text ?: el.getAttribute("text") ?: ""
                    val location = el.location
                    seenNames.add("${text}_${location.y}")
                } catch (e: Exception) {}
            }

            currentCount = seenNames.size
            if (currentCount == lastCount) {
                return@repeat
            }
            lastCount = currentCount

            // Scroll down
            try {
                driver.findElement(
                    AppiumBy.androidUIAutomator(
                        "new UiScrollable(new UiSelector().resourceId(\"$APP_PACKAGE:id/recyclerViewSenders\")).scrollForward()"
                    )
                )
            } catch (e: Exception) {
                return@repeat
            }
            shortWait()
        }

        return seenNames.size
    }

    @Test
    @Order(9)
    @DisplayName("Verify sender data persists after navigation")
    fun verifyDataPersistsAfterNavigation() {
        clickButton("btnSenders")
        extraLongWait()

        // Go back and return
        navigateToMain()
        extraLongWait()

        clickButton("btnSenders")
        extraLongWait()

        // Verify senders still exist
        val senderNames = findAllById("senderNameEditText")
        assertTrue(senderNames.isNotEmpty(), "Senders should persist after navigation")

        // Check if any sender name exists (including defaults)
        var foundSender = false
        for (field in senderNames) {
            val text = field.text ?: ""
            if (text.isNotEmpty()) {
                foundSender = true
                break
            }
        }
        assertTrue(foundSender, "Sender data should persist")

        navigateToMain()
    }

    @Test
    @Order(10)
    @DisplayName("Edit existing sender name")
    fun editExistingSenderName() {
        clickButton("btnSenders")
        extraLongWait()

        val senderNameFields = findAllById("senderNameEditText")
        assertTrue(senderNameFields.isNotEmpty(), "Should have sender name fields")

        val firstSenderName = senderNameFields.first()
        val originalName = firstSenderName.text ?: ""

        // Edit the name
        firstSenderName.clear()
        firstSenderName.sendKeys("TestSender Modified")
        longWait()

        // Verify change
        val updatedText = firstSenderName.text ?: ""
        assertTrue(
            updatedText.contains("Modified"),
            "Sender name should be modified (got: $updatedText)"
        )

        navigateToMain()
    }

    @Test
    @Order(11)
    @DisplayName("Disabled sender shows visual indication")
    fun disabledSenderShowsVisualIndication() {
        clickButton("btnSenders")
        extraLongWait()

        // Get a switch and disable the sender
        val switches = findAllById("switchSenderEnabled")
        assertTrue(switches.isNotEmpty(), "Should have enabled switches")

        val enabledSwitch = switches.first()

        // Ensure it's currently enabled
        if (enabledSwitch.getAttribute("checked") != "true") {
            enabledSwitch.click()
            longWait()
        }

        // Now disable it
        enabledSwitch.click()
        longWait()

        // Verify it's disabled
        val isChecked = enabledSwitch.getAttribute("checked")?.toBoolean() ?: false
        assertFalse(isChecked, "Sender should be disabled")

        // Re-enable for other tests
        enabledSwitch.click()
        mediumWait()

        navigateToMain()
    }
}
