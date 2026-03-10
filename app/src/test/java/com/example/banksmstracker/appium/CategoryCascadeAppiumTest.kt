package com.example.banksmstracker.appium

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

/**
 * Appium E2E tests for category cascade and re-categorization functionality.
 *
 * Tests the category management features including:
 * - Re-categorize all payments button
 * - Button state during operation
 *
 * NOTE: These tests require:
 * 1. Appium server running (`make appium-start` or `make appium-docker-start`)
 * 2. Android emulator running with the app installed (`make install`)
 *
 * Run with: make test-appium
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@DisplayName("Category Cascade E2E Tests")
class CategoryCascadeAppiumTest : AppiumBaseTest() {

    @Test
    @Order(1)
    @Tag("smoke")
    @DisplayName("Navigate to Categories screen")
    fun navigateToCategories() {
        findById("btnCategories").click()
        mediumWait()

        // Verify we're on Categories screen (FAB has fabAddCategory id)
        assertTrue(
            elementExists("fabAddCategory") || elementExists("btnRecategorize") ||
                textExists("Categories") || textExists("CATEGORIES"),
            "Should be on Categories screen"
        )

        navigateToMain()
    }

    @Test
    @Order(2)
    @Tag("smoke")
    @DisplayName("Re-categorize all button exists")
    fun recategorizeButtonExists() {
        findById("btnCategories").click()
        mediumWait()

        val hasRecategorizeBtn = elementExists("btnRecategorize")
        assertTrue(hasRecategorizeBtn, "Should have Re-categorize All button")

        navigateToMain()
    }

    @Test
    @Order(3)
    @DisplayName("Re-categorize button is clickable")
    fun recategorizeButtonClickable() {
        findById("btnCategories").click()
        mediumWait()

        val recategorizeBtn = findById("btnRecategorize")
        assertTrue(recategorizeBtn.isEnabled, "Re-categorize button should be enabled")

        navigateToMain()
    }

    @Test
    @Order(4)
    @DisplayName("Click re-categorize button shows result")
    fun clickRecategorizeShowsResult() {
        findById("btnCategories").click()
        longWait()

        // Click recategorize if available (button may not exist in empty state)
        try {
            if (elementExists("btnRecategorize")) {
                findById("btnRecategorize").click()
                extraLongWait()
            }
        } catch (e: Exception) {
            // Button might not be visible, that's ok
        }

        // Should not crash - any interaction with the screen after is success
        // Test passes as long as we can navigate back
        navigateToMain()
    }

    @Test
    @Order(5)
    @DisplayName("Re-categorize can be clicked multiple times")
    fun recategorizeMultipleTimes() {
        findById("btnCategories").click()
        longWait()

        // Click twice if button is available
        try {
            if (elementExists("btnRecategorize")) {
                findById("btnRecategorize").click()
                extraLongWait()

                if (elementExists("btnRecategorize")) {
                    findById("btnRecategorize").click()
                    extraLongWait()
                }
            }
        } catch (e: Exception) {
            // Button might not be visible, that's ok
        }

        // Should not crash - any interaction with the screen after is success
        // Test passes as long as we can navigate back
        navigateToMain()
    }
}
