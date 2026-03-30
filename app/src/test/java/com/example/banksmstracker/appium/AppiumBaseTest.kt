package com.example.banksmstracker.appium

import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import io.qameta.allure.Allure
import io.qameta.allure.Step
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Duration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestInstance
import org.openqa.selenium.OutputType
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

        /**
         * Path to the APK on the Appium server (e.g. the Docker volume path /apk/debug/app-debug.apk).
         * When set, Appium installs/reinstalls the APK before the session. When null, the app
         * must already be installed on the device (noReset=true is used automatically).
         *
         * Override via env variable: APPIUM_APK_PATH=/apk/debug/app-debug.apk
         */
        val APK_PATH: String? = System.getenv("APPIUM_APK_PATH")
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
            if (APK_PATH != null) {
                setApp(APK_PATH)
                // noReset=true skips `pm clear` (blocked on MIUI/HyperOS); fresh install gives
                // fresh data anyway. enforceAppInstall forces reinstall even with noReset=true.
                setNoReset(true)
                setEnforceAppInstall(true)
            } else {
                // App is pre-installed (e.g. via ./gradlew installDebug); just launch it
                setNoReset(true)
            }
            setFullReset(false)
            setNewCommandTimeout(Duration.ofSeconds(300))
            // Some devices (e.g. MIUI/HyperOS) deny WRITE_SECURE_SETTINGS to adb shell,
            // preventing UiAutomator2 from clearing the hidden_api_policy global setting.
            // This capability tells the driver to continue even if that step fails.
            setIgnoreHiddenApiPolicyError(true)
            // MIUI/HyperOS blocks ADB installs of io.appium.settings unless "Install via USB"
            // is enabled in developer options. Skip device initialization to bypass that step.
            setSkipDeviceInitialization(true)
        }

        driver = AndroidDriver(URI.create(APPIUM_SERVER_URL).toURL(), options)
        driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
        wait = WebDriverWait(driver, DEFAULT_TIMEOUT)
    }

    /**
     * Check if Appium server is available.
     */
    private fun isAppiumAvailable(): Boolean = try {
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

    @AfterEach
    open fun captureScreenshot(testInfo: TestInfo) {
        if (::driver.isInitialized && isDriverAlive()) {
            try {
                val screenshot = driver.getScreenshotAs(OutputType.BYTES)
                Allure.addAttachment(
                    "Screenshot — ${testInfo.displayName}",
                    "image/png",
                    screenshot.inputStream(),
                    ".png"
                )
            } catch (e: Exception) {
                // Screenshot capture failed — ignore
            }
        }
    }

    @AfterAll
    open fun tearDown() {
        if (::driver.isInitialized) {
            try {
                driver.quit()
            } catch (e: Exception) {
                // Driver may have already died - ignore
            }
        }
    }

    /**
     * Check if driver session is still alive.
     */
    protected fun isDriverAlive(): Boolean = try {
        // Access currentPackage property to verify connection
        val pkg = driver.currentPackage
        pkg != null
    } catch (e: Exception) {
        false
    }

    /**
     * Reconnect driver if session died.
     */
    protected fun ensureDriverConnected() {
        if (!::driver.isInitialized || !isDriverAlive()) {
            try {
                if (::driver.isInitialized) {
                    try {
                        driver.quit()
                    } catch (e: Exception) { }
                }

                val options = UiAutomator2Options().apply {
                    setPlatformName("Android")
                    setAutomationName("UiAutomator2")
                    setAppPackage(APP_PACKAGE)
                    setAppActivity(APP_ACTIVITY)
                    if (APK_PATH != null) {
                        setApp(APK_PATH)
                        setNoReset(false)
                    } else {
                        setNoReset(true)
                    }
                    setFullReset(false)
                    setNewCommandTimeout(Duration.ofSeconds(300))
                }

                driver = AndroidDriver(URI.create(APPIUM_SERVER_URL).toURL(), options)
                driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
                wait = WebDriverWait(driver, DEFAULT_TIMEOUT)
                Thread.sleep(2000) // Wait for app to start
            } catch (e: Exception) {
                throw RuntimeException("Failed to reconnect Appium driver: ${e.message}", e)
            }
        }
    }

    /**
     * Ensure we start each test from main screen.
     */
    @BeforeEach
    open fun ensureMainScreen() {
        // First ensure driver is connected
        ensureDriverConnected()
        if (::driver.isInitialized) {
            dismissTermsDialogIfPresent()
            navigateToMainRobust()
        }
    }

    /**
     * Dismiss the Personal Data Agreement dialog shown on first app launch.
     * Uses a short timeout so tests are not slowed down when no dialog is present.
     */
    protected fun dismissTermsDialogIfPresent() {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(1))
            // Look for the terms dialog title (English or Russian)
            val titleElements = driver.findElements(
                AppiumBy.androidUIAutomator(
                    "new UiSelector().textMatches(\"Personal Data Notice|Уведомление о личных данных\")"
                )
            )
            if (titleElements.isNotEmpty() && titleElements[0].isDisplayed) {
                // Click the positive button (AlertDialog button1 = positive)
                val positiveButton = driver.findElement(AppiumBy.id("android:id/button1"))
                if (positiveButton.isDisplayed) {
                    positiveButton.click()
                    Thread.sleep(500)
                }
            }
        } catch (e: Exception) {
            // No terms dialog present — normal case
        } finally {
            driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
        }
    }

    /**
     * Dismiss any Android system permission dialog that may be blocking the app.
     * Uses a short timeout to avoid slowing down tests when no dialog is present.
     */
    protected fun dismissPermissionDialogIfPresent() {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(1))
            // Android 10+ uses permissioncontroller; older uses packageinstaller
            val denyIds = listOf(
                "com.android.permissioncontroller:id/permission_deny_button",
                "com.android.packageinstaller:id/permission_deny_button"
            )
            for (id in denyIds) {
                val elements = driver.findElements(AppiumBy.id(id))
                if (elements.isNotEmpty() && elements[0].isDisplayed) {
                    elements[0].click()
                    Thread.sleep(500)
                    break
                }
            }
        } catch (e: Exception) {
            // No permission dialog present — normal case
        } finally {
            driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
        }
    }

    /**
     * Robust navigation to main screen with app restart fallback.
     */
    protected fun navigateToMainRobust() {
        // Check driver connection first
        if (!isDriverAlive()) {
            try {
                ensureDriverConnected()
            } catch (e: Exception) {
                return // Can't do anything without a driver
            }
        }

        // Dismiss any system permission dialog or terms dialog before checking screen state
        dismissTermsDialogIfPresent()
        dismissPermissionDialogIfPresent()

        // First try simple back navigation
        repeat(3) {
            if (isOnMainScreen()) return
            try {
                driver.navigate().back()
                Thread.sleep(1000)
                dismissPermissionDialogIfPresent()
            } catch (e: org.openqa.selenium.remote.UnreachableBrowserException) {
                // Driver died, try to reconnect
                try {
                    ensureDriverConnected()
                    return // Fresh driver starts at main screen
                } catch (reconnectException: Exception) {
                    return
                }
            } catch (e: Exception) {
                // Ignore other errors
            }
        }

        // If still not on main, restart app
        if (!isOnMainScreen()) {
            try {
                driver.terminateApp(APP_PACKAGE)
                Thread.sleep(1000)
                driver.activateApp(APP_PACKAGE)
                Thread.sleep(2000)
                dismissPermissionDialogIfPresent()
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
    protected fun isOnMainScreen(): Boolean = try {
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2))
        val hasCategoriesBtn = driver.findElements(AppiumBy.id("$APP_PACKAGE:id/btnCategories")).isNotEmpty()
        val hasSendersBtn = driver.findElements(AppiumBy.id("$APP_PACKAGE:id/btnSenders")).isNotEmpty()
        driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
        hasCategoriesBtn && hasSendersBtn
    } catch (e: Exception) {
        driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
        false
    }

    /**
     * Helper to find element by Android resource ID.
     * Includes retry logic with driver reconnection if connection was lost.
     */
    @Step("Find element by id: {resourceId}")
    protected fun findById(resourceId: String): WebElement {
        var lastException: Exception? = null
        repeat(2) { attempt ->
            try {
                return driver.findElement(AppiumBy.id("$APP_PACKAGE:id/$resourceId"))
            } catch (e: org.openqa.selenium.remote.UnreachableBrowserException) {
                lastException = e
                if (attempt == 0) {
                    // Try to reconnect on first failure
                    try {
                        ensureDriverConnected()
                    } catch (reconnectException: Exception) {
                        throw e
                    }
                }
            } catch (e: org.openqa.selenium.WebDriverException) {
                // Handle "instrumentation process not running" errors
                if (e.message?.contains("instrumentation process is not running") == true) {
                    lastException = e
                    if (attempt == 0) {
                        try {
                            ensureDriverConnected()
                        } catch (reconnectException: Exception) {
                            throw e
                        }
                    }
                } else {
                    throw e
                }
            } catch (e: Exception) {
                throw e
            }
        }
        throw lastException ?: RuntimeException("Failed to find element: $resourceId")
    }

    /**
     * Helper to find element by text content (case-insensitive).
     * Tries exact match first, then uppercase (for Material buttons).
     */
    protected fun findByText(text: String): WebElement = try {
        driver.findElement(AppiumBy.androidUIAutomator("new UiSelector().text(\"$text\")"))
    } catch (e: Exception) {
        // Material Design buttons often render text in uppercase
        driver.findElement(AppiumBy.androidUIAutomator("new UiSelector().text(\"${text.uppercase()}\")"))
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
    protected fun findByClassName(className: String): WebElement = driver.findElement(AppiumBy.className(className))

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
    protected fun waitForElement(resourceId: String, timeout: Duration = DEFAULT_TIMEOUT): WebElement =
        WebDriverWait(driver, timeout).until(
            ExpectedConditions.visibilityOfElementLocated(
                AppiumBy.id("$APP_PACKAGE:id/$resourceId")
            )
        )

    /**
     * Wait for text to be visible and return element (case-insensitive).
     */
    protected fun waitForText(text: String, timeout: Duration = DEFAULT_TIMEOUT): WebElement = try {
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

    /**
     * Wait for element to be clickable.
     */
    protected fun waitForClickable(resourceId: String, timeout: Duration = DEFAULT_TIMEOUT): WebElement =
        WebDriverWait(driver, timeout).until(
            ExpectedConditions.elementToBeClickable(
                AppiumBy.id("$APP_PACKAGE:id/$resourceId")
            )
        )

    /**
     * Check if element exists without throwing.
     */
    protected fun elementExists(resourceId: String): Boolean = try {
        driver.manage().timeouts().implicitlyWait(SHORT_TIMEOUT)
        val elements = driver.findElements(AppiumBy.id("$APP_PACKAGE:id/$resourceId"))
        driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
        elements.isNotEmpty()
    } catch (e: Exception) {
        driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
        false
    }

    /**
     * Check if text exists on screen (case-insensitive).
     */
    protected fun textExists(text: String): Boolean = try {
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

    /**
     * Navigate back to main screen.
     * Uses multiple strategies to ensure we get back to main.
     */
    @Step("Navigate to main screen")
    protected fun navigateToMain() {
        navigateToMainRobust()
    }

    /**
     * Click a button by ID with wait for clickable.
     */
    @Step("Click button: {buttonId}")
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
                org.openqa.selenium.interactions.PointerInput.Kind.TOUCH,
                "finger"
            )
            val tap = org.openqa.selenium.interactions.Sequence(finger, 1)
            tap.addAction(
                finger.createPointerMove(
                    Duration.ZERO,
                    org.openqa.selenium.interactions.PointerInput.Origin.viewport(),
                    centerX,
                    centerY
                )
            )
            tap.addAction(
                finger.createPointerDown(org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg())
            )
            tap.addAction(
                finger.createPointerUp(org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg())
            )
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
    @Step("Scroll to text: {text}")
    protected fun scrollToText(text: String): WebElement = try {
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

    /**
     * Scroll down to find element by resource ID.
     */
    @Step("Scroll to element: {resourceId}")
    protected fun scrollToElementById(resourceId: String): WebElement = driver.findElement(
        AppiumBy.androidUIAutomator(
            "new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().resourceId(\"$APP_PACKAGE:id/$resourceId\"))"
        )
    )

    /**
     * Scroll to a preset button that may be inside a HorizontalScrollView.
     * Step 1: scroll the outer vertical container to make the preset row visible.
     * Step 2: scroll the HorizontalScrollView horizontally to the button.
     */
    protected fun scrollToPresetButton(buttonId: String): WebElement {
        // Step 1: scroll the outer ScrollView down to reveal the preset buttons row
        try {
            driver.findElement(
                AppiumBy.androidUIAutomator(
                    "new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().resourceId(\"$APP_PACKAGE:id/layoutPresets\"))"
                )
            )
            Thread.sleep(300)
        } catch (e: Exception) { /* row already visible */ }
        // Step 2: scroll horizontally within the HorizontalScrollView to the button
        return driver.findElement(
            AppiumBy.androidUIAutomator(
                "new UiScrollable(new UiSelector().scrollable(true).className(\"android.widget.HorizontalScrollView\")).setAsHorizontalList().scrollIntoView(new UiSelector().resourceId(\"$APP_PACKAGE:id/$buttonId\"))"
            )
        )
    }

    /**
     * Check if element exists, scrolling if needed.
     * Falls back to a plain existence check if no scrollable container is found.
     */
    protected fun elementExistsWithScroll(resourceId: String): Boolean = try {
        driver.manage().timeouts().implicitlyWait(SHORT_TIMEOUT)
        scrollToElementById(resourceId)
        driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
        true
    } catch (e: Exception) {
        // scrollIntoView failed (e.g., no scrollable container when content fits screen).
        // Fall back to a plain existence check.
        try {
            val elements = driver.findElements(AppiumBy.id("$APP_PACKAGE:id/$resourceId"))
            driver.manage().timeouts().implicitlyWait(DEFAULT_TIMEOUT)
            elements.isNotEmpty()
        } catch (e2: Exception) {
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
