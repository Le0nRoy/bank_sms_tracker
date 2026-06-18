package com.example.banksmstracker.testing

import java.time.Instant

/**
 * Common types for test reporting.
 * These are duplicated from the unit test package since androidTest cannot depend on test sources.
 */

enum class TestStatus {
    RUNNING,
    PASSED,
    FAILED,
    SKIPPED
}

enum class StepStatus {
    PASSED,
    FAILED,
    SKIPPED
}

enum class Severity {
    BLOCKER,
    CRITICAL,
    NORMAL,
    MINOR,
    TRIVIAL
}

enum class AttachmentType {
    TEXT,
    FILE,
    SCREENSHOT,
    JSON,
    XML
}

data class TestStep(
    val name: String,
    val startTime: Instant,
    var endTime: Instant? = null,
    var status: StepStatus = StepStatus.PASSED,
    var error: String? = null
)

data class Attachment(val name: String, val type: AttachmentType, val content: ByteArray, val path: String? = null) {
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
