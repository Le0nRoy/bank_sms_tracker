package com.example.banksmstracker.util

/**
 * Shared constants used across the application.
 */
object Constants {

    /**
     * Permission request codes.
     */
    object RequestCodes {
        const val SMS_PERMISSION = 100
    }

    /**
     * Regex group indices for payment parsing.
     * These indices correspond to capture groups in payment regex patterns.
     */
    object RegexGroups {
        const val AMOUNT = 1
        const val CURRENCY = 2
        const val CARD = 3
        const val MERCHANT = 4
        const val TIMESTAMP = 5
        const val BALANCE = 6

        val GROUP_NAMES = mapOf(
            AMOUNT to "amount",
            CURRENCY to "currency",
            CARD to "card",
            MERCHANT to "merchant",
            TIMESTAMP to "timestamp",
            BALANCE to "balance"
        )

        fun getGroupName(index: Int): String =
            GROUP_NAMES[index] ?: "extra"
    }

    /**
     * Time constants for date filters.
     */
    object Time {
        const val START_OF_DAY_HOUR = 0
        const val START_OF_DAY_MINUTE = 0
        const val START_OF_DAY_SECOND = 0
        const val START_OF_DAY_MILLIS = 0

        const val END_OF_DAY_HOUR = 23
        const val END_OF_DAY_MINUTE = 59
        const val END_OF_DAY_SECOND = 59
        const val END_OF_DAY_MILLIS = 999
    }

    /**
     * Report formatting constants.
     */
    object Formatting {
        const val SEPARATOR_LENGTH = 50
        val SEPARATOR = "=".repeat(SEPARATOR_LENGTH)
    }
}
