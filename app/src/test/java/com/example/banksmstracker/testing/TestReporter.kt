package com.example.banksmstracker.testing

import java.io.File
import java.time.Instant

/**
 * Test reporting wrapper that provides Allure-like functionality for test documentation.
 *
 * Supports:
 * - Test steps with status tracking
 * - Test titles and descriptions
 * - Attachments (files, screenshots)
 * - Test metadata (severity, owner, etc.)
 *
 * Usage:
 * ```kotlin
 * @Test
 * fun myTest() = TestReporter.test("My Test Title") {
 *     description("Tests that feature X works correctly")
 *     severity(Severity.CRITICAL)
 *
 *     step("Given initial state") {
 *         // setup code
 *     }
 *
 *     step("When action performed") {
 *         // action code
 *     }
 *
 *     step("Then expected result") {
 *         // assertion code
 *     }
 * }
 * ```
 */
object TestReporter {

    private val reports = mutableListOf<TestReport>()
    private var currentReport: TestReport? = null

    /**
     * Start a new test with the given title.
     */
    fun test(title: String, block: TestContext.() -> Unit) {
        val report = TestReport(title = title, startTime = Instant.now())
        currentReport = report
        reports.add(report)

        val context = TestContext(report)
        try {
            context.block()
            report.status = TestStatus.PASSED
        } catch (e: Throwable) {
            report.status = TestStatus.FAILED
            report.error = e.message
            throw e
        } finally {
            report.endTime = Instant.now()
            currentReport = null
        }
    }

    /**
     * Get all test reports (for custom report generation).
     */
    fun getAllReports(): List<TestReport> = reports.toList()

    /**
     * Clear all reports (useful for test isolation).
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
        sb.appendLine("=" .repeat(60))
        sb.appendLine("TEST REPORT")
        sb.appendLine("=" .repeat(60))
        sb.appendLine()

        for (report in reports) {
            sb.appendLine("Test: ${report.title}")
            sb.appendLine("Status: ${report.status}")
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

            if (report.attachments.isNotEmpty()) {
                sb.appendLine("Attachments:")
                for (attachment in report.attachments) {
                    sb.appendLine("  - ${attachment.name} (${attachment.type})")
                }
            }

            report.error?.let { sb.appendLine("Error: $it") }
            sb.appendLine("-".repeat(60))
            sb.appendLine()
        }

        return sb.toString()
    }
}

/**
 * Context for building a test report.
 */
class TestContext(private val report: TestReport) {

    /**
     * Set test description.
     */
    fun description(text: String) {
        report.description = text
    }

    /**
     * Set test severity.
     */
    fun severity(level: Severity) {
        report.severity = level
    }

    /**
     * Set test owner/author.
     */
    fun owner(name: String) {
        report.owner = name
    }

    /**
     * Add a tag to the test.
     */
    fun tag(vararg tags: String) {
        report.tags.addAll(tags)
    }

    /**
     * Execute a test step.
     */
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
     * Add a text attachment.
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

    /**
     * Add a file attachment.
     */
    fun attachFile(name: String, file: File) {
        if (file.exists()) {
            report.attachments.add(
                Attachment(
                    name = name,
                    type = AttachmentType.FILE,
                    content = file.readBytes(),
                    path = file.absolutePath
                )
            )
        }
    }

    /**
     * Add a screenshot attachment (for UI tests).
     */
    fun attachScreenshot(name: String, screenshotBytes: ByteArray) {
        report.attachments.add(
            Attachment(
                name = name,
                type = AttachmentType.SCREENSHOT,
                content = screenshotBytes
            )
        )
    }
}

/**
 * Test report data class.
 */
data class TestReport(
    val title: String,
    val startTime: Instant,
    var endTime: Instant? = null,
    var status: TestStatus = TestStatus.RUNNING,
    var description: String? = null,
    var severity: Severity? = null,
    var owner: String? = null,
    var error: String? = null,
    val tags: MutableList<String> = mutableListOf(),
    val steps: MutableList<TestStep> = mutableListOf(),
    val attachments: MutableList<Attachment> = mutableListOf()
)

/**
 * Test step data class.
 */
data class TestStep(
    val name: String,
    val startTime: Instant,
    var endTime: Instant? = null,
    var status: StepStatus = StepStatus.PASSED,
    var error: String? = null
)

/**
 * Attachment data class.
 */
data class Attachment(
    val name: String,
    val type: AttachmentType,
    val content: ByteArray,
    val path: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Attachment
        return name == other.name && type == other.type && content.contentEquals(other.content)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + content.contentHashCode()
        return result
    }
}

enum class TestStatus {
    RUNNING, PASSED, FAILED, SKIPPED
}

enum class StepStatus {
    PASSED, FAILED, SKIPPED
}

enum class Severity {
    BLOCKER, CRITICAL, NORMAL, MINOR, TRIVIAL
}

enum class AttachmentType {
    TEXT, FILE, SCREENSHOT, JSON, XML
}
