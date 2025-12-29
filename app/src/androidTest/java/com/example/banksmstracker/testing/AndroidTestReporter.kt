package com.example.banksmstracker.testing

import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.screenshot.Screenshot
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Android-specific test reporter with screenshot support.
 *
 * Extends the base TestReporter functionality with:
 * - Screenshot capture during UI tests
 * - Device info attachment
 * - Log capture
 *
 * Usage:
 * ```kotlin
 * @Test
 * fun myUiTest() = AndroidTestReporter.test("My UI Test") {
 *     description("Tests UI flow")
 *
 *     step("Open main screen") {
 *         // UI action
 *         captureScreenshot("main_screen")
 *     }
 *
 *     step("Tap button") {
 *         // UI action
 *     }
 *
 *     step("Verify result") {
 *         captureScreenshot("result_screen")
 *         // assertion
 *     }
 * }
 * ```
 */
object AndroidTestReporter {

    private const val TAG = "AndroidTestReporter"
    private val reports = mutableListOf<AndroidTestReport>()
    private var currentReport: AndroidTestReport? = null
    private val screenshotDir: File by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "test_screenshots").apply {
            mkdirs()
        }
    }

    /**
     * Start a new test with the given title.
     */
    fun test(title: String, block: AndroidTestContext.() -> Unit) {
        val report = AndroidTestReport(
            title = title,
            startTime = Instant.now(),
            deviceInfo = captureDeviceInfo()
        )
        currentReport = report
        reports.add(report)

        val context = AndroidTestContext(report, this)
        try {
            context.block()
            report.status = TestStatus.PASSED
            Log.i(TAG, "Test PASSED: $title")
        } catch (e: Throwable) {
            report.status = TestStatus.FAILED
            report.error = e.message
            Log.e(TAG, "Test FAILED: $title - ${e.message}")
            // Capture screenshot on failure
            try {
                context.captureScreenshot("failure_screenshot")
            } catch (screenshotError: Exception) {
                Log.w(TAG, "Failed to capture failure screenshot: ${screenshotError.message}")
            }
            throw e
        } finally {
            report.endTime = Instant.now()
            currentReport = null
        }
    }

    /**
     * Get all test reports.
     */
    fun getAllReports(): List<AndroidTestReport> = reports.toList()

    /**
     * Clear all reports.
     */
    fun clear() {
        reports.clear()
        currentReport = null
    }

    /**
     * Generate a simple text report.
     */
    fun generateTextReport(): String {
        val sb = StringBuilder()
        sb.appendLine("=".repeat(60))
        sb.appendLine("ANDROID TEST REPORT")
        sb.appendLine("=".repeat(60))
        sb.appendLine()

        for (report in reports) {
            sb.appendLine("Test: ${report.title}")
            sb.appendLine("Status: ${report.status}")
            sb.appendLine("Device: ${report.deviceInfo}")
            report.description?.let { sb.appendLine("Description: $it") }
            report.severity?.let { sb.appendLine("Severity: $it") }

            if (report.steps.isNotEmpty()) {
                sb.appendLine("Steps:")
                for ((index, step) in report.steps.withIndex()) {
                    val statusIcon = when (step.status) {
                        StepStatus.PASSED -> "[OK]"
                        StepStatus.FAILED -> "[FAIL]"
                        StepStatus.SKIPPED -> "[SKIP]"
                    }
                    sb.appendLine("  ${index + 1}. $statusIcon ${step.name}")
                    step.error?.let { sb.appendLine("      Error: $it") }
                }
            }

            if (report.screenshots.isNotEmpty()) {
                sb.appendLine("Screenshots:")
                for (screenshot in report.screenshots) {
                    sb.appendLine("  - ${screenshot.name}: ${screenshot.path}")
                }
            }

            report.error?.let { sb.appendLine("Error: $it") }
            sb.appendLine("-".repeat(60))
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Capture screenshot and return bytes.
     */
    internal fun captureScreenshotBytes(): ByteArray? = try {
        val capture = Screenshot.capture()
        val bitmap = capture.bitmap
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.toByteArray()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to capture screenshot: ${e.message}")
        null
    }

    /**
     * Save screenshot to file and return path.
     */
    internal fun saveScreenshot(name: String): String? {
        val bytes = captureScreenshotBytes() ?: return null
        val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-")
        val filename = "${name}_$timestamp.png"
        val file = File(screenshotDir, filename)

        return try {
            FileOutputStream(file).use { it.write(bytes) }
            Log.i(TAG, "Screenshot saved: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save screenshot: ${e.message}")
            null
        }
    }

    private fun captureDeviceInfo(): String =
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT})"
}

/**
 * Context for building an Android test report.
 */
class AndroidTestContext(private val report: AndroidTestReport, private val reporter: AndroidTestReporter) {

    fun description(text: String) {
        report.description = text
    }

    fun severity(level: Severity) {
        report.severity = level
    }

    fun owner(name: String) {
        report.owner = name
    }

    fun tag(vararg tags: String) {
        report.tags.addAll(tags)
    }

    fun <T> step(name: String, block: () -> T): T {
        val step = TestStep(name = name, startTime = Instant.now())
        report.steps.add(step)

        return try {
            val result = block()
            step.status = StepStatus.PASSED
            step.endTime = Instant.now()
            result
        } catch (e: Throwable) {
            step.status = StepStatus.FAILED
            step.error = e.message
            step.endTime = Instant.now()
            throw e
        }
    }

    /**
     * Capture and attach a screenshot.
     */
    fun captureScreenshot(name: String) {
        val bytes = reporter.captureScreenshotBytes()
        val path = reporter.saveScreenshot(name)

        if (bytes != null) {
            report.screenshots.add(
                ScreenshotInfo(
                    name = name,
                    path = path ?: "memory",
                    bytes = bytes
                )
            )
        }
    }

    /**
     * Attach text content.
     */
    fun attachText(name: String, content: String) {
        report.attachments.add(
            Attachment(
                name = name,
                type = AttachmentType.TEXT,
                content = content.toByteArray()
            )
        )
    }
}

/**
 * Android test report with additional device context.
 */
data class AndroidTestReport(
    val title: String,
    val startTime: Instant,
    val deviceInfo: String,
    var endTime: Instant? = null,
    var status: TestStatus = TestStatus.RUNNING,
    var description: String? = null,
    var severity: Severity? = null,
    var owner: String? = null,
    var error: String? = null,
    val tags: MutableList<String> = mutableListOf(),
    val steps: MutableList<TestStep> = mutableListOf(),
    val screenshots: MutableList<ScreenshotInfo> = mutableListOf(),
    val attachments: MutableList<Attachment> = mutableListOf()
)

/**
 * Screenshot information.
 */
data class ScreenshotInfo(val name: String, val path: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ScreenshotInfo
        return name == other.name && path == other.path
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + path.hashCode()
        return result
    }
}

// Types are defined in TestTypes.kt
