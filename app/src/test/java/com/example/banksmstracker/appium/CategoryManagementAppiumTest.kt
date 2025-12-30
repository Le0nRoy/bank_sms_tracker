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
@DisplayName("Category Management E2E Tests")
class CategoryManagementAppiumTest : AppiumBaseTest() {

    @Test
    @Order(1)
    @DisplayName("Navigate to Categories screen from main menu")
    fun navigateToCategoriesScreen() {
        // Click on Categories button from main activity
        val categoriesButton = findByText("Categories")
        categoriesButton.click()
        mediumWait()

        // Verify we're on the Categories screen by checking for the RecyclerView
        assertTrue(elementExists("recyclerViewCategories"))

        navigateToMain()
    }

    @Test
    @Order(2)
    @DisplayName("Add new category with name")
    fun addNewCategoryWithName() {
        findByText("Categories").click()
        mediumWait()

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
        findByText("Categories").click()
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
        findByText("Categories").click()
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
        findByText("Categories").click()
        mediumWait()

        // Find the Add Merchant button
        val addMerchantButton = findAllById("btnAddMerchant").first()

        // Get initial count of merchant fields
        val merchantsContainer = findById("merchantsContainer")
        val initialCount = merchantsContainer.findElements(AppiumBy.className("android.widget.EditText")).size

        // Add two more merchants
        addMerchantButton.click()
        shortWait()
        addMerchantButton.click()
        shortWait()

        // Verify count increased
        val newCount = merchantsContainer.findElements(AppiumBy.className("android.widget.EditText")).size
        assertEquals(initialCount + 2, newCount, "Should have 2 more merchant fields")

        // Fill in the new merchant fields
        val editTexts = merchantsContainer.findElements(AppiumBy.className("android.widget.EditText"))
        if (editTexts.size >= 2) {
            editTexts[editTexts.size - 2].sendKeys("Walmart")
            editTexts[editTexts.size - 1].sendKeys("Target")
        }
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(6)
    @DisplayName("Create second category")
    fun createSecondCategory() {
        findByText("Categories").click()
        mediumWait()

        // Count initial categories
        val initialCategoryNames = findAllById("nameEditText").size

        // Add new category
        findById("fabAddCategory").click()
        shortWait()

        // Verify new category was added
        val newCategoryNames = findAllById("nameEditText").size
        assertEquals(initialCategoryNames + 1, newCategoryNames, "Should have one more category")

        // Configure the new category
        val categoryNameFields = findAllById("nameEditText")
        val lastCategoryName = categoryNameFields.last()
        lastCategoryName.clear()
        lastCategoryName.sendKeys("Restaurants")
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(7)
    @DisplayName("Create category with multiple merchants")
    fun createCategoryWithMultipleMerchants() {
        findByText("Categories").click()
        mediumWait()

        // Add new category
        findById("fabAddCategory").click()
        shortWait()

        // Get the newly added category's name field and set name
        val categoryNameFields = findAllById("nameEditText")
        val lastCategoryName = categoryNameFields.last()
        lastCategoryName.clear()
        lastCategoryName.sendKeys("Entertainment")
        shortWait()

        // Find the Add Merchant button for this new category (last one)
        val addMerchantButtons = findAllById("btnAddMerchant")
        val addMerchantButton = addMerchantButtons.last()

        // Add three merchants
        addMerchantButton.click()
        shortWait()
        addMerchantButton.click()
        shortWait()
        addMerchantButton.click()
        shortWait()

        // Find the merchants container for this category
        val merchantsContainers = findAllById("merchantsContainer")
        val lastMerchantsContainer = merchantsContainers.last()
        val merchantFields = lastMerchantsContainer.findElements(AppiumBy.className("android.widget.EditText"))

        // Fill in merchants
        val merchants = listOf("Netflix", "Spotify", "Cinema")
        merchants.forEachIndexed { index, merchant ->
            if (index < merchantFields.size) {
                merchantFields[index].sendKeys(merchant)
            }
        }
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(8)
    @DisplayName("Verify category data persists after navigation")
    fun verifyDataPersistsAfterNavigation() {
        findByText("Categories").click()
        mediumWait()

        // Go back and return
        navigateToMain()
        mediumWait()

        findByText("Categories").click()
        mediumWait()

        // Verify categories still exist
        val categoryNames = findAllById("nameEditText")
        assertTrue(categoryNames.isNotEmpty(), "Categories should persist after navigation")

        // Check if our test category names exist
        var foundCategory = false
        for (field in categoryNames) {
            val text = field.text
            if (text.contains("Shopping") ||
                text.contains("Restaurants") ||
                text.contains("Entertainment")
            ) {
                foundCategory = true
                break
            }
        }
        assertTrue(foundCategory, "Test category data should persist")

        navigateToMain()
    }

    @Test
    @Order(9)
    @DisplayName("Edit existing category name")
    fun editExistingCategoryName() {
        findByText("Categories").click()
        mediumWait()

        val categoryNameFields = findAllById("nameEditText")
        assertTrue(categoryNameFields.isNotEmpty())

        val firstCategoryName = categoryNameFields.first()
        val originalName = firstCategoryName.text

        // Edit the name
        firstCategoryName.clear()
        firstCategoryName.sendKeys("$originalName Updated")
        shortWait()

        // Verify change
        assertTrue(
            firstCategoryName.text.contains("Updated"),
            "Category name should be updated"
        )

        navigateToMain()
    }

    @Test
    @Order(10)
    @DisplayName("Disabled category shows visual indication")
    fun disabledCategoryShowsVisualIndication() {
        findByText("Categories").click()
        mediumWait()

        // Get a switch and disable the category
        val switches = findAllById("switchEnabled")
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
            "Category should be disabled"
        )

        // Re-enable for other tests
        enabledSwitch.click()
        shortWait()

        navigateToMain()
    }

    @Test
    @Order(11)
    @DisplayName("Categories list scrolls when many items")
    fun categoriesListScrolls() {
        findByText("Categories").click()
        mediumWait()

        // Add several categories to test scrolling
        repeat(3) {
            findById("fabAddCategory").click()
            shortWait()
        }

        // Try to scroll the list
        val recyclerView = findById("recyclerViewCategories")
        assertTrue(recyclerView.isDisplayed)

        // Scroll down
        driver.findElement(
            AppiumBy.androidUIAutomator(
                "new UiScrollable(new UiSelector().resourceId(\"$APP_PACKAGE:id/recyclerViewCategories\")).scrollForward()"
            )
        )
        shortWait()

        navigateToMain()
    }
}
