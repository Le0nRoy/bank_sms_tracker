package com.example.banksmstracker.appium

import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import java.net.URI
import java.time.Duration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait

/**
 * Base class for Appium UI tests.
 *
 * Prerequisites:
 * 1. Start Appium server: `make appium-start` or `make appium-docker-start`
 * 2. Start Android emulator: `make emulator-start`
 * 3. Install the app on emulator: `make install`
 * 4. Run tests: `make test-appium`
 *
 * To set up Appium:
 * ```
 * make appium-install
 * ```
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AppiumBaseTest {

    protected lateinit var driver: AndroidDriver
    protected lateinit var wait: WebDriverWait

    companion object {
        const val APP_PACKAGE = "com.example.banksmstracker"
        const val APP_ACTIVITY = ".ui.MainActivity"
        const val APPIUM_SERVER_URL = "http://127.0.0.1:4723"
        val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(10)
        val SHORT_TIMEOUT: Duration = Duration.ofSeconds(3)
    }

    @BeforeAll
    open fun setUp() {
        val options = UiAutomator2Options().apply {
            setPlatformName("Android")
            setAutomationName("UiAutomator2")
            setAppPackage(APP_PACKAGE)
            setAppActivity(APP_ACTIVITY)
            setNoReset(false)
            setFullReset(false)
            setNewCommandTimeout(Duration.ofSeconds(300))
        }

        driver = AndroidDriver(URI.create(APPIUM_SERVER_URL).toURL(), options)
        driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
        wait = WebDriverWait(driver, DEFAULT_TIMEOUT)
    }

    @AfterAll
    open fun tearDown() {
        if (::driver.isInitialized) {
            driver.quit()
        }
    }

    /**
     * Helper to find element by Android resource ID.
     */
    protected fun findById(resourceId: String): WebElement =
        driver.findElement(AppiumBy.id("$APP_PACKAGE:id/$resourceId"))

    /**
     * Helper to find element by text content (case-insensitive).
     * Tries exact match first, then uppercase (for Material buttons).
     */
    protected fun findByText(text: String): WebElement {
        return try {
            driver.findElement(AppiumBy.androidUIAutomator("new UiSelector().text(\"$text\")"))
        } catch (e: Exception) {
            // Material Design buttons often render text in uppercase
            driver.findElement(AppiumBy.androidUIAutomator("new UiSelector().text(\"${text.uppercase()}\")"))
        }
    }

    /**
     * Helper to find element containing text (case-insensitive).
     */
    protected fun findByTextContains(text: String): WebElement =
        driver.findElement(AppiumBy.androidUIAutomator("new UiSelector().textContains(\"$text\")"))

    /**
     * Helper to find element by text using regex (case-insensitive).
     */
    protected fun findByTextIgnoreCase(text: String): WebElement =
        driver.findElement(AppiumBy.androidUIAutomator("new UiSelector().textMatches(\"(?i)${Regex.escape(text)}\")"))

    /**
     * Helper to find element by content description (accessibility).
     */
    protected fun findByContentDesc(contentDesc: String): WebElement =
        driver.findElement(AppiumBy.accessibilityId(contentDesc))

    /**
     * Helper to find element by class name.
     */
    protected fun findByClassName(className: String): WebElement =
        driver.findElement(AppiumBy.className(className))

    /**
     * Find all elements matching a resource ID.
     */
    protected fun findAllById(resourceId: String): List<WebElement> =
        driver.findElements(AppiumBy.id("$APP_PACKAGE:id/$resourceId"))

    /**
     * Find all elements matching text (case-insensitive).
     */
    protected fun findAllByText(text: String): List<WebElement> {
        val elements = driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"$text\")"))
        if (elements.isNotEmpty()) return elements
        // Try uppercase (Material buttons)
        return driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"${text.uppercase()}\")"))
    }

    /**
     * Wait for element to be visible and return it.
     */
    protected fun waitForElement(resourceId: String, timeout: Duration = DEFAULT_TIMEOUT): WebElement {
        return WebDriverWait(driver, timeout).until(
            ExpectedConditions.visibilityOfElementLocated(
                AppiumBy.id("$APP_PACKAGE:id/$resourceId")
            )
        )
    }

    /**
     * Wait for text to be visible and return element (case-insensitive).
     */
    protected fun waitForText(text: String, timeout: Duration = DEFAULT_TIMEOUT): WebElement {
        return try {
            WebDriverWait(driver, timeout).until(
                ExpectedConditions.visibilityOfElementLocated(
                    AppiumBy.androidUIAutomator("new UiSelector().text(\"$text\")")
                )
            )
        } catch (e: Exception) {
            // Try uppercase (Material buttons)
            WebDriverWait(driver, timeout).until(
                ExpectedConditions.visibilityOfElementLocated(
                    AppiumBy.androidUIAutomator("new UiSelector().text(\"${text.uppercase()}\")")
                )
            )
        }
    }

    /**
     * Wait for element to be clickable.
     */
    protected fun waitForClickable(resourceId: String, timeout: Duration = DEFAULT_TIMEOUT): WebElement {
        return WebDriverWait(driver, timeout).until(
            ExpectedConditions.elementToBeClickable(
                AppiumBy.id("$APP_PACKAGE:id/$resourceId")
            )
        )
    }

    /**
     * Check if element exists without throwing.
     */
    protected fun elementExists(resourceId: String): Boolean {
        return try {
            driver.manage().timeouts().implicitlyWait(SHORT_TIMEOUT)
            val elements = driver.findElements(AppiumBy.id("$APP_PACKAGE:id/$resourceId"))
            driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
            elements.isNotEmpty()
        } catch (e: Exception) {
            driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
            false
        }
    }

    /**
     * Check if text exists on screen (case-insensitive).
     */
    protected fun textExists(text: String): Boolean {
        return try {
            driver.manage().timeouts().implicitlyWait(SHORT_TIMEOUT)
            // Try exact match first
            var elements = driver.findElements(
                AppiumBy.androidUIAutomator("new UiSelector().text(\"$text\")")
            )
            if (elements.isEmpty()) {
                // Try uppercase (Material buttons)
                elements = driver.findElements(
                    AppiumBy.androidUIAutomator("new UiSelector().text(\"${text.uppercase()}\")")
                )
            }
            driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
            elements.isNotEmpty()
        } catch (e: Exception) {
            driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
            false
        }
    }

    /**
     * Navigate back to main screen.
     * Uses multiple strategies to ensure we get back to main.
     */
    protected fun navigateToMain() {
        var attempts = 0
        val maxAttempts = 5

        while (attempts < maxAttempts) {
            // Check if we're already on main screen
            if (textExists("Bank SMS Tracker") && elementExists("btnCategories")) {
                return
            }

            // Try pressing back
            try {
                driver.navigate().back()
                Thread.sleep(500)
            } catch (e: Exception) {
                // Ignore navigation errors
            }
            attempts++
        }

        // Last resort: restart the app activity
        try {
            driver.terminateApp(APP_PACKAGE)
            Thread.sleep(500)
            driver.activateApp(APP_PACKAGE)
            Thread.sleep(1000)
        } catch (e: Exception) {
            // Ignore if this fails
        }
    }

    /**
     * Short sleep for UI transitions.
     */
    protected fun shortWait() {
        Thread.sleep(300)
    }

    /**
     * Medium sleep for screen transitions.
     */
    protected fun mediumWait() {
        Thread.sleep(500)
    }

    /**
     * Scroll down to find element (case-insensitive).
     */
    protected fun scrollToText(text: String): WebElement {
        return try {
            driver.findElement(
                AppiumBy.androidUIAutomator(
                    "new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().text(\"$text\"))"
                )
            )
        } catch (e: Exception) {
            // Try uppercase (Material buttons)
            driver.findElement(
                AppiumBy.androidUIAutomator(
                    "new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().text(\"${text.uppercase()}\"))"
                )
            )
        }
    }

    /**
     * Perform long press on element.
     */
    protected fun longPress(element: WebElement) {
        val actions = org.openqa.selenium.interactions.Actions(driver)
        actions.clickAndHold(element)
            .pause(Duration.ofSeconds(1))
            .release()
            .perform()
    }
}
