package com.example.banksmstracker.ui

import com.example.banksmstracker.data.Payment
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parses the transaction timestamp string extracted from the SMS body.
 *
 * Format: "dd/MM/yyyy HH:mm:ss" (as stored in [Payment.timestamp]).
 * Returns milliseconds since epoch, or null if the string is blank or unparseable.
 */
internal fun parseTransactionTimestamp(timestamp: String?): Long? {
    if (timestamp.isNullOrBlank()) return null
    return try {
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).parse(timestamp)?.time
    } catch (e: ParseException) {
        null
    }
}

/**
 * Filters a list of payments by category, sender, date range, and merchant name.
 *
 * Date filtering uses [Payment.timestamp] (the transaction date from the SMS body) as the primary
 * date source, falling back to [Payment.receivedAt] if the timestamp cannot be parsed. This
 * correctly handles the case where all payments were imported in a single batch (giving them
 * the same [Payment.receivedAt]) but have different transaction dates.
 *
 * @param payments          Full list of payments to filter.
 * @param selectedCategory  Category name to match, or null to include all categories.
 * @param selectedSender    Sender address to match, or null to include all senders.
 * @param startDate         Inclusive lower bound in milliseconds, or null for no lower bound.
 * @param endDate           Inclusive upper bound in milliseconds, or null for no upper bound.
 * @param merchantQuery     Substring to match against [Payment.merchant] (case-insensitive),
 *                          or null/blank to include all merchants.
 */
internal fun filterPayments(
    payments: List<Payment>,
    selectedCategory: String?,
    selectedSender: String?,
    startDate: Long?,
    endDate: Long?,
    merchantQuery: String? = null
): List<Payment> = payments.filter { payment ->
    val matchesCategory = selectedCategory == null || payment.categoryId == selectedCategory
    val matchesSender = selectedSender == null || payment.senderAddress == selectedSender
    val matchesDate = matchesDateRange(payment, startDate, endDate)
    val matchesMerchant = merchantQuery.isNullOrBlank() ||
        (payment.merchant?.contains(merchantQuery.trim(), ignoreCase = true) == true)
    matchesCategory && matchesSender && matchesDate && matchesMerchant
}

private fun matchesDateRange(payment: Payment, startDate: Long?, endDate: Long?): Boolean {
    if (startDate == null && endDate == null) return true
    val effectiveDate = parseTransactionTimestamp(payment.timestamp) ?: payment.receivedAt
        ?: return false
    val afterStart = startDate?.let { effectiveDate >= it } ?: true
    val beforeEnd = endDate?.let { effectiveDate <= it } ?: true
    return afterStart && beforeEnd
}
