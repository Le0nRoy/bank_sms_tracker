package com.example.banksmstracker.appium

import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import java.net.URI
import java.time.Duration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

/**
 * Base class for Appium UI tests.
 *
 * Prerequisites:
 * 1. Start Appium server: `appium`
 * 2. Start Android emulator: `emulator -avd <avd_name>`
 * 3. Install the app on emulator: `./gradlew installDebug`
 * 4. Run tests: `./gradlew test --tests "*.appium.*"`
 *
 * To set up Appium:
 * ```
 * npm install -g appium
 * appium driver install uiautomator2
 * ```
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AppiumBaseTest {

    protected lateinit var driver: AndroidDriver

    companion object {
        const val APP_PACKAGE = "com.example.banksmstracker"
        const val APP_ACTIVITY = ".ui.MainActivity"
        const val APPIUM_SERVER_URL = "http://127.0.0.1:4723"
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
            setNewCommandTimeout(Duration.ofSeconds(60))
        }

        driver = AndroidDriver(URI.create(APPIUM_SERVER_URL).toURL(), options)
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10))
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
    protected fun findById(resourceId: String) =
        driver.findElement(io.appium.java_client.AppiumBy.id("$APP_PACKAGE:id/$resourceId"))

    /**
     * Helper to find element by text content.
     */
    protected fun findByText(text: String) =
        driver.findElement(io.appium.java_client.AppiumBy.androidUIAutomator("new UiSelector().text(\"$text\")"))

    /**
     * Helper to find element by content description (accessibility).
     */
    protected fun findByContentDesc(contentDesc: String) =
        driver.findElement(io.appium.java_client.AppiumBy.accessibilityId(contentDesc))

    /**
     * Wait for element to be visible.
     */
    protected fun waitForElement(resourceId: String, timeout: Duration = Duration.ofSeconds(10)) {
        org.openqa.selenium.support.ui.WebDriverWait(driver, timeout)
            .until { findById(resourceId).isDisplayed }
    }
}
