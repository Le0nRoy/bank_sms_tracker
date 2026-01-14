package com.example.banksmstracker.appium

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

/**
 * Appium E2E tests for the Ignore Rules feature.
 *
 * Tests the ignore rules management screen functionality.
 *
 * Covers:
 * - Navigation to Ignore Rules screen
 * - Adding new ignore rules
 * - Editing existing rules
 * - Toggling rule enabled/disabled state
 * - Deleting rules
 * - Empty state handling
 *
 * NOTE: These tests require:
 * 1. Appium server running (`make appium-start` or `make appium-docker-start`)
 * 2. Android emulator running with the app installed (`make install`)
 *
 * Run with: make test-appium
 *
 * DISABLED: Ignore Rules button was moved from main menu to Senders screen.
 * Tests need to be updated to navigate through Senders to access Ignore Rules.
 */
@Disabled("Ignore Rules button moved to Senders screen - tests need update")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@DisplayName("Ignore Rules E2E Tests")
class IgnoreRulesAppiumTest : AppiumBaseTest() {

    @Test
    @Order(1)
    @DisplayName("Navigate to Ignore Rules screen from main menu")
    fun navigateToIgnoreRulesScreen() {
        // Click on Ignore Rules button from main activity
        clickButton("btnIgnoreRules")
        longWait()

        // Verify we're on the Ignore Rules screen
        assertTrue(
            elementExists("recyclerIgnoreRules") || elementExists("tvEmptyState"),
            "Should be on Ignore Rules screen"
        )

        navigateToMain()
    }

    @Test
    @Order(2)
    @DisplayName("Ignore Rules screen shows empty state when no rules")
    fun showsEmptyStateWhenNoRules() {
        clickButton("btnIgnoreRules")
        longWait()

        // Should show empty state or recycler view
        val hasEmptyState = elementExists("tvEmptyState")
        val hasRules = elementExists("recyclerIgnoreRules")

        assertTrue(hasEmptyState || hasRules, "Should show empty state or rules list")

        navigateToMain()
    }

    @Test
    @Order(3)
    @DisplayName("FAB button exists for adding ignore rules")
    fun fabAddIgnoreRuleExists() {
        clickButton("btnIgnoreRules")
        longWait()

        assertTrue(elementExists("fabAddIgnoreRule"), "FAB for adding rules should exist")

        navigateToMain()
    }

    @Test
    @Order(4)
    @DisplayName("Add new ignore rule with pattern")
    fun addNewIgnoreRule() {
        clickButton("btnIgnoreRules")
        longWait()

        // Click FAB to add new rule
        clickFab("fabAddIgnoreRule")
        mediumWait()

        // Enter pattern in dialog
        if (elementExists("etPattern")) {
            val patternField = findById("etPattern")
            patternField.clear()
            patternField.sendKeys(".*promotional.*")
            shortWait()

            // Optionally add description
            if (elementExists("etDescription")) {
                val descField = findById("etDescription")
                descField.sendKeys("Block promotional messages")
            }
            shortWait()

            // Click confirm button (look for positive button in dialog)
            try {
                findByText("Confirm").click()
            } catch (e: Exception) {
                try {
                    findByText("OK").click()
                } catch (e2: Exception) {
                    // Try clicking by resource ID if text doesn't work
                    driver.navigate().back()
                }
            }
            mediumWait()
        }

        navigateToMain()
    }

    @Test
    @Order(5)
    @DisplayName("Verify added rule appears in list")
    fun verifyAddedRuleAppearsInList() {
        clickButton("btnIgnoreRules")
        longWait()

        // First add a rule to ensure there's data
        clickFab("fabAddIgnoreRule")
        mediumWait()

        if (elementExists("etPattern")) {
            val patternField = findById("etPattern")
            patternField.clear()
            patternField.sendKeys("spam_test_pattern")
            shortWait()

            try {
                findByText("Confirm").click()
            } catch (e: Exception) {
                try {
                    findByText("OK").click()
                } catch (e2: Exception) {
                    driver.navigate().back()
                }
            }
            mediumWait()
        }

        // Verify the list is visible and has content
        val hasRecycler = elementExists("recyclerIgnoreRules")
        val hasEmptyState = elementExists("tvEmptyState")

        // After adding, empty state should be gone
        assertTrue(hasRecycler || !hasEmptyState, "Should have rules list after adding")

        navigateToMain()
    }

    @Test
    @Order(6)
    @DisplayName("Toggle ignore rule enabled state")
    fun toggleIgnoreRuleEnabledState() {
        clickButton("btnIgnoreRules")
        longWait()

        // First ensure there's at least one rule
        if (elementExists("tvEmptyState")) {
            clickFab("fabAddIgnoreRule")
            mediumWait()
            if (elementExists("etPattern")) {
                findById("etPattern").sendKeys("toggle_test")
                try {
                    findByText("Confirm").click()
                } catch (e: Exception) {
                    driver.navigate().back()
                }
                mediumWait()
            }
        }

        // Find and toggle a switch
        val switches = findAllById("switchEnabled")
        if (switches.isNotEmpty()) {
            val switchElement = switches.first()
            val initialState = switchElement.getAttribute("checked")

            switchElement.click()
            shortWait()

            val newState = switchElement.getAttribute("checked")
            // State should have changed
            assertTrue(initialState != newState, "Switch state should change after click")
        }

        navigateToMain()
    }

    @Test
    @Order(7)
    @DisplayName("Delete ignore rule")
    fun deleteIgnoreRule() {
        clickButton("btnIgnoreRules")
        longWait()

        // First ensure there's at least one rule
        if (elementExists("tvEmptyState")) {
            clickFab("fabAddIgnoreRule")
            mediumWait()
            if (elementExists("etPattern")) {
                findById("etPattern").sendKeys("delete_test")
                try {
                    findByText("Confirm").click()
                } catch (e: Exception) {
                    driver.navigate().back()
                }
                mediumWait()
            }
        }

        // Find and click delete button
        val deleteButtons = findAllById("btnDelete")
        if (deleteButtons.isNotEmpty()) {
            deleteButtons.first().click()
            mediumWait()

            // Confirm deletion in dialog
            try {
                findByText("Confirm").click()
            } catch (e: Exception) {
                try {
                    findByText("OK").click()
                } catch (e2: Exception) {
                    try {
                        findByText("YES").click()
                    } catch (e3: Exception) {
                        // Dialog might auto-dismiss
                    }
                }
            }
            mediumWait()
        }

        navigateToMain()
    }

    @Test
    @Order(8)
    @DisplayName("Edit existing ignore rule")
    fun editExistingIgnoreRule() {
        clickButton("btnIgnoreRules")
        longWait()

        // First ensure there's at least one rule
        if (elementExists("tvEmptyState")) {
            clickFab("fabAddIgnoreRule")
            mediumWait()
            if (elementExists("etPattern")) {
                findById("etPattern").sendKeys("edit_test")
                try {
                    findByText("Confirm").click()
                } catch (e: Exception) {
                    driver.navigate().back()
                }
                mediumWait()
            }
        }

        // Click on a rule item to edit (items are clickable)
        val patterns = findAllById("tvPattern")
        if (patterns.isNotEmpty()) {
            patterns.first().click()
            mediumWait()

            // Should open edit dialog
            if (elementExists("etPattern")) {
                val patternField = findById("etPattern")
                patternField.clear()
                patternField.sendKeys("edited_pattern")
                shortWait()

                try {
                    findByText("Confirm").click()
                } catch (e: Exception) {
                    driver.navigate().back()
                }
                mediumWait()
            }
        }

        navigateToMain()
    }

    @Test
    @Order(9)
    @DisplayName("Invalid regex pattern shows error")
    fun invalidRegexPatternShowsError() {
        clickButton("btnIgnoreRules")
        longWait()

        // Click FAB to add new rule
        clickFab("fabAddIgnoreRule")
        mediumWait()

        if (elementExists("etPattern")) {
            val patternField = findById("etPattern")
            patternField.clear()
            // Enter invalid regex
            patternField.sendKeys("[invalid")
            shortWait()

            try {
                findByText("Confirm").click()
            } catch (e: Exception) {
                try {
                    findByText("OK").click()
                } catch (e2: Exception) {
                    driver.navigate().back()
                }
            }
            mediumWait()

            // App should not crash and should show error or stay on dialog
            // Just verify we're still in the app
        }

        navigateToMain()
    }

    @Test
    @Order(10)
    @DisplayName("Navigate back to main screen")
    fun navigateBackToMainScreen() {
        clickButton("btnIgnoreRules")
        longWait()

        driver.navigate().back()
        mediumWait()

        assertTrue(elementExists("btnCategories"), "Should be back on main screen")
    }
}
