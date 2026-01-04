package com.example.banksmstracker.appium

import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Duration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
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
 *
 * NOTE: Tests are automatically skipped if Appium server is not running.
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
        // Skip tests if Appium server is not available
        Assumptions.assumeTrue(
            isAppiumAvailable(),
            "Appium server not available at $APPIUM_SERVER_URL - skipping Appium tests"
        )

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

    /**
     * Check if Appium server is available.
     */
    private fun isAppiumAvailable(): Boolean {
        return try {
            val url = URL("$APPIUM_SERVER_URL/status")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val response = conn.responseCode
            conn.disconnect()
            response == 200
        } catch (e: Exception) {
            false
        }
    }

    @AfterAll
    open fun tearDown() {
        if (::driver.isInitialized) {
            driver.quit()
        }
    }

    /**
     * Ensure we start each test from main screen.
     */
    @BeforeEach
    open fun ensureMainScreen() {
        if (::driver.isInitialized) {
            navigateToMainRobust()
        }
    }

    /**
     * Robust navigation to main screen with app restart fallback.
     */
    protected fun navigateToMainRobust() {
        // First try simple back navigation
        repeat(3) {
            if (isOnMainScreen()) return
            try {
                driver.navigate().back()
                Thread.sleep(1000)
            } catch (e: Exception) {
                // Ignore
            }
        }

        // If still not on main, restart app
        if (!isOnMainScreen()) {
            try {
                driver.terminateApp(APP_PACKAGE)
                Thread.sleep(1000)
                driver.activateApp(APP_PACKAGE)
                Thread.sleep(2000)
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Final wait for main screen
        Thread.sleep(1000)
    }

    /**
     * Check if we're on the main screen.
     */
    protected fun isOnMainScreen(): Boolean {
        return try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2))
            val hasCategoriesBtn = driver.findElements(AppiumBy.id("$APP_PACKAGE:id/btnCategories")).isNotEmpty()
            val hasSendersBtn = driver.findElements(AppiumBy.id("$APP_PACKAGE:id/btnSenders")).isNotEmpty()
            driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
            hasCategoriesBtn && hasSendersBtn
        } catch (e: Exception) {
            driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
            false
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
        navigateToMainRobust()
    }

    /**
     * Click a button by ID with wait for clickable.
     */
    protected fun clickButton(buttonId: String) {
        try {
            val button = waitForClickable(buttonId, Duration.ofSeconds(10))
            button.click()
        } catch (e: Exception) {
            // Fallback to direct click
            findById(buttonId).click()
        }
    }

    /**
     * Short sleep for UI transitions.
     */
    protected fun shortWait() {
        Thread.sleep(500)
    }

    /**
     * Medium sleep for screen transitions.
     */
    protected fun mediumWait() {
        Thread.sleep(1000)
    }

    /**
     * Long wait for complex operations.
     */
    protected fun longWait() {
        Thread.sleep(2000)
    }

    /**
     * Extra long wait for very slow operations.
     */
    protected fun extraLongWait() {
        Thread.sleep(3000)
    }

    /**
     * Click FAB button with retry mechanism using multiple strategies.
     */
    protected fun clickFab(fabId: String) {
        // First, try to hide keyboard if it's visible (might be blocking FAB)
        try {
            driver.hideKeyboard()
        } catch (e: Exception) {
            // Keyboard wasn't visible
        }

        Thread.sleep(1500) // Wait for UI to settle

        // Strategy 0: Try scrolling to make FAB visible first
        try {
            driver.findElement(
                AppiumBy.androidUIAutomator(
                    "new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().resourceId(\"$APP_PACKAGE:id/$fabId\"))"
                )
            )
            Thread.sleep(500)
        } catch (e: Exception) {
            // FAB might already be visible or no scrollable container
        }

        // Strategy 1: Try direct WebElement click
        var attempts = 0
        while (attempts < 3) {
            try {
                val fab = waitForClickable(fabId, Duration.ofSeconds(5))
                fab.click()
                Thread.sleep(1500)
                return
            } catch (e: Exception) {
                attempts++
                Thread.sleep(500)
            }
        }

        // Strategy 2: Try W3C Actions tap with fresh element location
        try {
            val fab = findById(fabId)
            val location = fab.location
            val size = fab.size
            val centerX = location.x + size.width / 2
            val centerY = location.y + size.height / 2

            val finger = org.openqa.selenium.interactions.PointerInput(
                org.openqa.selenium.interactions.PointerInput.Kind.TOUCH, "finger"
            )
            val tap = org.openqa.selenium.interactions.Sequence(finger, 1)
            tap.addAction(finger.createPointerMove(Duration.ZERO, org.openqa.selenium.interactions.PointerInput.Origin.viewport(), centerX, centerY))
            tap.addAction(finger.createPointerDown(org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()))
            tap.addAction(finger.createPointerUp(org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()))
            driver.perform(listOf(tap))
            Thread.sleep(1500)
            return
        } catch (e: Exception) {
            // Continue to next strategy
        }

        // Strategy 3: Use UiAutomator to click directly
        try {
            driver.findElement(
                AppiumBy.androidUIAutomator(
                    "new UiSelector().resourceId(\"$APP_PACKAGE:id/$fabId\").clickable(true)"
                )
            ).click()
            Thread.sleep(1500)
        } catch (e: Exception) {
            // All strategies failed
        }
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
     * Scroll down to find element by resource ID.
     */
    protected fun scrollToElementById(resourceId: String): WebElement {
        return driver.findElement(
            AppiumBy.androidUIAutomator(
                "new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().resourceId(\"$APP_PACKAGE:id/$resourceId\"))"
            )
        )
    }

    /**
     * Check if element exists, scrolling if needed.
     */
    protected fun elementExistsWithScroll(resourceId: String): Boolean {
        return try {
            driver.manage().timeouts().implicitlyWait(SHORT_TIMEOUT)
            scrollToElementById(resourceId)
            driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
            true
        } catch (e: Exception) {
            driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
            false
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
