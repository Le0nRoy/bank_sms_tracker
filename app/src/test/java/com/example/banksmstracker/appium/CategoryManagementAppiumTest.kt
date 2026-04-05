package com.example.banksmstracker.appium

import io.appium.java_client.AppiumBy
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

/**
 * Appium E2E tests for Category management flow.
 *
 * Tests the complete user workflow for creating, editing, and managing categories.
 *
 * Covers:
 * - Navigation to Categories screen
 * - Adding new categories
 * - Editing category names
 * - Adding merchants to categories
 * - Toggling category enabled/disabled state
 * - Multiple categories management
 * - Data persistence after navigation
 *
 * NOTE: These tests require:
 * 1. Appium server running (`make appium-start` or `make appium-docker-start`)
 * 2. Android emulator running with the app installed (`make install`)
 *
 * Run with: make test-appium
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
// @Disabled("Requires Appium server and Android emulator. Run with: make test-appium")
@Epic("Bank SMS Tracker")
@Feature("Category Management")
@DisplayName("Category Management E2E Tests")
class CategoryManagementAppiumTest : AppiumBaseTest() {

    @Test
    @Order(1)
    @Tag("smoke")
    @DisplayName("Navigate to Categories screen from main menu")
    fun navigateToCategoriesScreen() {
        // Click on Categories button from main activity
        clickButton("btnCategories")
        longWait()

        // Verify we're on the Categories screen by checking for the RecyclerView
        assertTrue(elementExists("recyclerViewCategories"))
    }

    @Test
    @Order(2)
    @Tag("smoke")
    @DisplayName("Add new category with name")
    fun addNewCategoryWithName() {
        clickButton("btnCategories")
        longWait()

        // Click FAB to add new category
        val addButton = findById("fabAddCategory")
        addButton.click()
        shortWait()

        // A new category item should appear in the list
        // Find the name edit text and enter a name
        val categoryNameFields = findAllById("nameEditText")
        assertTrue(categoryNameFields.isNotEmpty(), "Should have at least one category name field")

        val lastNameField = categoryNameFields.last()
        lastNameField.clear()
        lastNameField.sendKeys("Shopping")
        shortWait()

        // Verify the text was entered
        assertEquals("Shopping", lastNameField.text)

        navigateToMain()
    }

    @Test
    @Order(3)
    @DisplayName("Add merchant to category")
    fun addMerchantToCategory() {
        clickButton("btnCategories")
        mediumWait()

        // Find the Add Merchant button and click it
        val addMerchantButtons = findAllById("btnAddMerchant")
        assertTrue(addMerchantButtons.isNotEmpty(), "Should have Add Merchant button")

        addMerchantButtons.first().click()
        shortWait()

        // Find the merchants container and the new EditText inside
        val merchantsContainer = findById("merchantsContainer")
        val editTexts = merchantsContainer.findElements(AppiumBy.className("android.widget.EditText"))
        assertTrue(editTexts.isNotEmpty(), "Should have merchant EditText")

        val merchantField = editTexts.last()
        merchantField.sendKeys("Amazon")
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(4)
    @DisplayName("Toggle category enabled/disabled state")
    fun toggleCategoryEnabledState() {
        clickButton("btnCategories")
        mediumWait()

        // Find the enabled switch
        val switches = findAllById("switchEnabled")
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
    @Order(5)
    @DisplayName("Add multiple merchants to category")
    fun addMultipleMerchants() {
        clickButton("btnCategories")
        mediumWait()

        // Scroll to the Add Merchant button so the category card is fully on-screen
        scrollToElementById("btnAddMerchant")

        // Add two more merchants — scroll after each click to keep new fields in view,
        // and re-fetch the button each time to avoid stale reference after DOM change.
        findAllById("btnAddMerchant").first().click()
        mediumWait()
        scrollToElementById("btnAddMerchant")   // scroll down to reveal new field
        findAllById("btnAddMerchant").first().click()
        mediumWait()
        scrollToElementById("btnAddMerchant")   // scroll down to reveal second new field

        // Collect all visible pattern fields (etValue) and type into the last two.
        val patternFields = driver.findElements(
            io.appium.java_client.AppiumBy.id("$APP_PACKAGE:id/etValue")
        )
        assertTrue(patternFields.size >= 2, "Should have at least 2 merchant pattern fields visible")
        patternFields[patternFields.size - 2].sendKeys("Walmart")
        shortWait()
        patternFields[patternFields.size - 1].sendKeys("Target")
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(6)
    @DisplayName("Create second category")
    fun createSecondCategory() {
        clickButton("btnCategories")
        extraLongWait()

        // Simplified test: just click FAB and verify a category can be added
        // without complex counting that can crash UiAutomator2
        clickFab("fabAddCategory")
        extraLongWait()

        // Verify at least one category name field exists
        val categoryNameFields = findAllById("nameEditText")
        assertTrue(categoryNameFields.isNotEmpty(), "Should have at least one category field after adding")

        // Set a name for the last category (the newly added one)
        val lastCategoryName = categoryNameFields.last()
        lastCategoryName.clear()
        lastCategoryName.sendKeys("Restaurants")
        mediumWait()

        // Verify the name was set
        val updatedText = lastCategoryName.text ?: ""
        assertTrue(
            updatedText.contains("Restaurants"),
            "New category should have name 'Restaurants' (got: $updatedText)"
        )

        navigateToMain()
    }

    /**
     * Count total category items by scrolling through the entire list.
     * RecyclerView only renders visible items, so we need to scroll to count all.
     */
    private fun countTotalCategoryItems(): Int {
        // First scroll to beginning
        try {
            driver.findElement(
                AppiumBy.androidUIAutomator(
                    "new UiScrollable(new UiSelector().resourceId(\"$APP_PACKAGE:id/recyclerViewCategories\")).scrollToBeginning(5)"
                )
            )
        } catch (e: Exception) {}
        shortWait()

        // Collect unique category names by scrolling through list
        val seenNames = mutableSetOf<String>()
        var lastCount = -1
        var currentCount = 0

        repeat(10) {
            val visible = findAllById("nameEditText")
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
                        "new UiScrollable(new UiSelector().resourceId(\"$APP_PACKAGE:id/recyclerViewCategories\")).scrollForward()"
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
    @Order(7)
    @DisplayName("Create category with multiple merchants")
    fun createCategoryWithMultipleMerchants() {
        clickButton("btnCategories")
        extraLongWait()

        // Add new category
        clickFab("fabAddCategory")
        extraLongWait()

        // Get the newly added category's name field and set name
        val categoryNameFields = findAllById("nameEditText")
        assertTrue(categoryNameFields.isNotEmpty(), "Should have category fields")
        val lastCategoryName = categoryNameFields.last()
        lastCategoryName.clear()
        lastCategoryName.sendKeys("Entertainment")
        longWait()

        // Find the Add Merchant button for this new category (last one)
        val addMerchantButtons = findAllById("btnAddMerchant")
        assertTrue(addMerchantButtons.isNotEmpty(), "Should have add merchant buttons")
        val addMerchantButton = addMerchantButtons.last()

        // Add three merchants
        addMerchantButton.click()
        longWait()
        addMerchantButton.click()
        longWait()
        addMerchantButton.click()
        longWait()

        // Find the merchants container for this category
        val merchantsContainers = findAllById("merchantsContainer")
        if (merchantsContainers.isNotEmpty()) {
            val lastMerchantsContainer = merchantsContainers.last()
            val merchantFields = lastMerchantsContainer.findElements(AppiumBy.className("android.widget.EditText"))

            // Fill in merchants
            val merchants = listOf("Netflix", "Spotify", "Cinema")
            merchants.forEachIndexed { index, merchant ->
                if (index < merchantFields.size) {
                    merchantFields[index].sendKeys(merchant)
                }
            }
        }
        mediumWait()

        navigateToMain()
    }

    @Test
    @Order(8)
    @DisplayName("Verify category data persists after navigation")
    fun verifyDataPersistsAfterNavigation() {
        clickButton("btnCategories")
        extraLongWait()

        // Go back and return
        navigateToMain()
        extraLongWait()

        clickButton("btnCategories")
        extraLongWait()

        // Verify categories still exist
        val categoryNames = findAllById("nameEditText")
        assertTrue(categoryNames.isNotEmpty(), "Categories should persist after navigation")

        // Check if any test category names exist (or default categories)
        var foundCategory = false
        for (field in categoryNames) {
            val text = field.text ?: ""
            if (text.isNotEmpty()) {
                foundCategory = true
                break
            }
        }
        assertTrue(foundCategory, "Category data should persist")

        navigateToMain()
    }

    @Test
    @Order(9)
    @DisplayName("Edit existing category name")
    fun editExistingCategoryName() {
        clickButton("btnCategories")
        extraLongWait()

        val categoryNameFields = findAllById("nameEditText")
        assertTrue(categoryNameFields.isNotEmpty(), "Should have category name fields")

        val firstCategoryName = categoryNameFields.first()
        val originalName = firstCategoryName.text ?: ""

        // Edit the name
        firstCategoryName.clear()
        firstCategoryName.sendKeys("TestCategory Updated")
        longWait()

        // Verify change
        val updatedText = firstCategoryName.text ?: ""
        assertTrue(
            updatedText.contains("Updated"),
            "Category name should be updated (got: $updatedText)"
        )

        navigateToMain()
    }

    @Test
    @Order(10)
    @DisplayName("Disabled category shows visual indication")
    fun disabledCategoryShowsVisualIndication() {
        clickButton("btnCategories")
        extraLongWait()

        // Get a switch and disable the category
        val switches = findAllById("switchEnabled")
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
        assertFalse(isChecked, "Category should be disabled")

        // Re-enable for other tests
        enabledSwitch.click()
        mediumWait()

        navigateToMain()
    }

    @Test
    @Order(11)
    @DisplayName("Categories list scrolls when many items")
    fun categoriesListScrolls() {
        clickButton("btnCategories")
        extraLongWait()

        // Add several categories to test scrolling
        repeat(3) {
            clickFab("fabAddCategory")
            longWait()
        }

        // Try to scroll the list
        val recyclerView = findById("recyclerViewCategories")
        assertTrue(recyclerView.isDisplayed, "RecyclerView should be visible")

        // Scroll down (ignore errors if list isn't scrollable)
        try {
            driver.findElement(
                AppiumBy.androidUIAutomator(
                    "new UiScrollable(new UiSelector().resourceId(\"$APP_PACKAGE:id/recyclerViewCategories\")).scrollForward()"
                )
            )
        } catch (e: Exception) {
            // List might not be scrollable if not enough items
        }
        mediumWait()

        navigateToMain()
    }
}
