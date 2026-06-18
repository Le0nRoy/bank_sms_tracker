package com.example.banksmstracker.ui

import com.example.banksmstracker.data.Payment
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Sentinel value used inside [selectedCategories] to mean "include payments with no category".
 * Distinguished from a real category name by the leading NUL character.
 */
const val UNCATEGORIZED_FILTER = "\u0000UNCATEGORIZED"

/**
 * Parses the transaction timestamp string extracted from the SMS body.
 *
 * Supported formats: "dd/MM/yyyy HH:mm:ss" and "dd/MM/yyyy".
 * Returns milliseconds since epoch, or null if the string is blank or unparseable.
 */
internal fun parseTransactionTimestamp(timestamp: String): Long? {
    if (timestamp.isBlank()) return null
    val formats = arrayOf(
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US),
        SimpleDateFormat("dd/MM/yyyy", Locale.US)
    )
    for (fmt in formats) {
        try {
            val result = fmt.parse(timestamp)
            if (result != null) return result.time
        } catch (_: ParseException) {
            // try next format
        }
    }
    return null
}

/**
 * Filters a list of payments by a set of categories, sender, date range, and merchant name.
 *
 * **Category matching:**
 * - `selectedCategories` null or empty → all payments pass the category check.
 * - A set entry equal to [UNCATEGORIZED_FILTER] → matches payments where [Payment.categoryId] is null.
 * - Any other entry → matches payments where [Payment.categoryId] equals that string.
 *
 * Date filtering uses [Payment.timestamp] (the transaction date extracted from the SMS body).
 * If the timestamp cannot be parsed the payment is excluded from date-filtered results.
 *
 * @param payments            Full list of payments to filter.
 * @param selectedCategories  Set of category names / [UNCATEGORIZED_FILTER] to include,
 *                            or null / empty set to include all.
 * @param selectedSender      Sender address to match, or null to include all senders.
 * @param startDate           Inclusive lower bound in milliseconds, or null for no lower bound.
 * @param endDate             Inclusive upper bound in milliseconds, or null for no upper bound.
 * @param merchantQuery       Substring matched against [Payment.merchant] (case-insensitive),
 *                            or null/blank to include all merchants.
 */
internal fun filterPayments(
    payments: List<Payment>,
    selectedCategories: Set<String>?,
    selectedSender: String?,
    startDate: Long?,
    endDate: Long?,
    merchantQuery: String? = null
): List<Payment> = payments.filter { payment ->
    val matchesCategory = when {
        selectedCategories.isNullOrEmpty() -> true
        else -> {
            val named = payment.categoryId != null &&
                selectedCategories.contains(payment.categoryId)
            val uncategorized = payment.categoryId == null &&
                selectedCategories.contains(UNCATEGORIZED_FILTER)
            named || uncategorized
        }
    }
    val matchesSender = selectedSender == null || payment.senderAddress == selectedSender
    val matchesDate = matchesDateRange(payment, startDate, endDate)
    val matchesMerchant = merchantQuery.isNullOrBlank() ||
        (payment.merchant?.contains(merchantQuery.trim(), ignoreCase = true) == true)
    matchesCategory && matchesSender && matchesDate && matchesMerchant
}

private fun matchesDateRange(payment: Payment, startDate: Long?, endDate: Long?): Boolean {
    if (startDate == null && endDate == null) return true
    val effectiveDate = parseTransactionTimestamp(payment.timestamp) ?: return false
    val afterStart = startDate?.let { effectiveDate >= it } ?: true
    val beforeEnd = endDate?.let { effectiveDate <= it } ?: true
    return afterStart && beforeEnd
}
