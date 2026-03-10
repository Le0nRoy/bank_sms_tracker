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
     * Named regex group constants for payment parsing.
     * These names correspond to Java named capture groups in payment regex patterns.
     */
    object RegexGroups {
        const val AMOUNT = "amount"
        const val CURRENCY = "currency"
        const val CARD = "card"
        const val MERCHANT = "merchant"
        const val DATE = "date"
        const val TIME = "time"
        const val BALANCE = "balance"
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
