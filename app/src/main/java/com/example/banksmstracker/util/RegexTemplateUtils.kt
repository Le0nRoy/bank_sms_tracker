package com.example.banksmstracker.util

/**
 * Converts a regex pattern string to a human-readable template by replacing known named capture
 * groups with `⟨name⟩` placeholder markers.
 *
 * Unlike a simple string replacement, this uses a regex-based approach that matches any
 * `(?<name>...)` group regardless of the specific content inside, so it handles both lazy (`.+?`)
 * and greedy (`.+`) quantifiers as well as fixed-string alternatives (e.g., `TBCTPMTR`).
 *
 * Supports one level of nested non-capturing groups (e.g., `(?<amount>\d+(?:[.]\d{2}))`).
 *
 * @param regex The stored regex pattern (as it appears in the database / JSON config).
 * @return A template string where known named groups are replaced with `⟨name⟩` markers.
 */
fun regexToTemplate(regex: String): String {
    var result = regex
    for (name in KNOWN_GROUP_NAMES) {
        // Match (?<name>CONTENT) where CONTENT may contain one level of (?:...) groups.
        result = Regex("\\(\\?<$name>(?:[^)(]|\\([^)]*\\))*\\)")
            .replace(result) { "⟨$name⟩" }
    }
    // Second pass: convert any remaining (?<name>…) groups with unknown names so they are
    // also shown as ⟨name⟩ placeholders instead of raw regex syntax.
    result = Regex("\\(\\?<([^>]+)>(?:[^)(]|\\([^)]*\\))*\\)")
        .replace(result) { mr -> "⟨${mr.groupValues[1]}⟩" }
    return result
}

/**
 * Converts a template string (containing `⟨name⟩` markers) back to a valid regex pattern by
 * replacing each marker with the corresponding canonical named capture group regex.
 *
 * @param template The template string produced by [regexToTemplate] or typed directly by the user.
 * @return A valid regex pattern ready to be compiled and stored.
 */
fun templateToRegex(template: String): String {
    var result = template
    for ((name, preset) in PRESET_REGEXES) {
        result = result.replace("⟨$name⟩", preset)
    }
    // Second pass: convert any remaining ⟨name⟩ tokens with unknown names back to
    // a named capture group with a default pattern, so the regex remains valid.
    result = Regex("⟨([^⟩]+)⟩").replace(result) { mr -> "(?<${mr.groupValues[1]}>.+?)" }
    return result
}

/** Named groups recognised as preset placeholders, in replacement order. */
private val KNOWN_GROUP_NAMES = listOf(
    "amount",
    "currency",
    "card",
    "merchant",
    "date",
    "time",
    "balance"
)

/**
 * Converts the two-character sequence `\n` (backslash + n) in a stored regex pattern into an
 * actual newline character, so the pattern can be displayed across multiple lines in the UI.
 */
fun decodeNewlines(pattern: String): String = pattern.replace("\\n", "\n")

/**
 * Converts actual newline characters in a display string back into the two-character sequence
 * `\n` (backslash + n) before the pattern is stored or compiled as a regex.
 */
fun encodeNewlines(pattern: String): String = pattern.replace("\n", "\\n")

/** Canonical regex strings for each preset placeholder (used in [templateToRegex]). */
val PRESET_REGEXES: Map<String, String> = linkedMapOf(
    "amount" to "(?<amount>\\d+(?:[.]\\d{2}))",
    "currency" to "(?<currency>[A-Z]{3})",
    "card" to "(?<card>.+?)",
    "merchant" to "(?<merchant>.+?)",
    "date" to "(?<date>\\d{2}/\\d{2}/\\d{4})",
    "time" to "(?<time>\\d{2}:\\d{2}:\\d{2})",
    "balance" to "(?<balance>\\d+(?:[.]\\d{2}))"
)
