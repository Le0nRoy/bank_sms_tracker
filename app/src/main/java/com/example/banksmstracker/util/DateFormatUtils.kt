package com.example.banksmstracker.util

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Reformats a raw transaction timestamp string captured from SMS body into a dot-separated
 * display form.
 *
 * Supported input formats: "dd/MM/yyyy HH:mm:ss" and "dd/MM/yyyy".
 * Corresponding output formats: "dd.MM.yyyy HH:mm:ss" and "dd.MM.yyyy".
 * Falls back to the original string unchanged if parsing fails.
 */
fun formatDisplayTimestamp(raw: String): String {
    if (raw.isBlank()) return raw
    val formats = listOf(
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US) to SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US),
        SimpleDateFormat("dd/MM/yyyy", Locale.US) to SimpleDateFormat("dd.MM.yyyy", Locale.US)
    )
    for ((parser, formatter) in formats) {
        try {
            val date = parser.parse(raw)
            if (date != null) return formatter.format(date)
        } catch (_: ParseException) {
            // try next format
        }
    }
    return raw
}
