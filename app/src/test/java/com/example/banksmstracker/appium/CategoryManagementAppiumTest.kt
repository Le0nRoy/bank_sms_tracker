package com.example.banksmstracker.appium

import org.junit.jupiter.api.Assertions.assertEquals
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
 * Tests the complete user workflow for creating, editing, and deleting categories.
 *
 * NOTE: These tests require:
 * 1. Appium server running (`appium`)
 * 2. Android emulator running with the app installed
 *
 * Run with: ./gradlew test --tests "*.appium.CategoryManagementAppiumTest"
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Disabled("Requires Appium server and Android emulator. Run manually.")
@DisplayName("Category Management E2E Tests")
class CategoryManagementAppiumTest : AppiumBaseTest() {

    @Test
    @Order(1)
    @DisplayName("Navigate to Categories screen")
    fun navigateToCategoriesScreen() {
        // Click on Categories button from main activity
        val categoriesButton = findByText("Categories")
        categoriesButton.click()

        // Verify we're on the Categories screen
        Thread.sleep(500) // Wait for navigation
        val title = driver.findElement(
            io.appium.java_client.AppiumBy.androidUIAutomator(
                "new UiSelector().className(\"android.widget.TextView\").text(\"Categories\")"
            )
        )
        assertTrue(title.isDisplayed)
    }

    @Test
    @Order(2)
    @DisplayName("Add new category")
    fun addNewCategory() {
        // Navigate to categories
        findByText("Categories").click()
        Thread.sleep(500)

        // Click add button (FAB)
        val addButton = findById("fab_add")
        addButton.click()
        Thread.sleep(300)

        // Fill in category name
        val categoryNameField = findById("edit_category_name")
        categoryNameField.clear()
        categoryNameField.sendKeys("Test Category")

        // Add a merchant
        val merchantField = findById("edit_merchant")
        merchantField.sendKeys("TestMerchant")

        // Save
        val saveButton = findByText("Save")
        saveButton.click()
        Thread.sleep(500)

        // Verify category appears in list
        val newCategory = findByText("Test Category")
        assertTrue(newCategory.isDisplayed)
    }

    @Test
    @Order(3)
    @DisplayName("Edit existing category")
    fun editCategory() {
        // Navigate to categories
        findByText("Categories").click()
        Thread.sleep(500)

        // Click on existing category to edit
        val categoryItem = findByText("Test Category")
        categoryItem.click()
        Thread.sleep(300)

        // Modify category name
        val categoryNameField = findById("edit_category_name")
        categoryNameField.clear()
        categoryNameField.sendKeys("Updated Category")

        // Save changes
        val saveButton = findByText("Save")
        saveButton.click()
        Thread.sleep(500)

        // Verify updated name appears
        val updatedCategory = findByText("Updated Category")
        assertTrue(updatedCategory.isDisplayed)
    }

    @Test
    @Order(4)
    @DisplayName("Toggle category enabled state")
    fun toggleCategoryEnabled() {
        // Navigate to categories
        findByText("Categories").click()
        Thread.sleep(500)

        // Click on category
        val categoryItem = findByText("Updated Category")
        categoryItem.click()
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
    @DisplayName("Delete category")
    fun deleteCategory() {
        // Navigate to categories
        findByText("Categories").click()
        Thread.sleep(500)

        // Long press on category to show delete option
        val categoryItem = findByText("Updated Category")

        // Perform long click using Actions
        val actions = org.openqa.selenium.interactions.Actions(driver)
        actions.clickAndHold(categoryItem)
            .pause(java.time.Duration.ofSeconds(1))
            .release()
            .perform()

        Thread.sleep(300)

        // Click delete in context menu
        val deleteButton = findByText("Delete")
        deleteButton.click()
        Thread.sleep(300)

        // Confirm deletion
        val confirmButton = findByText("OK")
        confirmButton.click()
        Thread.sleep(500)

        // Verify category is gone
        val categories = driver.findElements(
            io.appium.java_client.AppiumBy.androidUIAutomator(
                "new UiSelector().text(\"Updated Category\")"
            )
        )
        assertEquals(0, categories.size)
    }
}
