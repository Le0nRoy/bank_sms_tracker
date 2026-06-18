package com.example.banksmstracker.util

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import com.example.banksmstracker.R

/**
 * Applies colored chip-style spans to all `⟨name⟩` placeholder tokens in [editable].
 *
 * Also applies a secondary amber background to raw regex-special sequences (e.g. `\d`, `[…]`,
 * `(?:…)`, quantifiers) that are not already covered by a placeholder span, so users can
 * visually distinguish literal text from active regex syntax.
 *
 * Call this after setting text to a decoded template (see [regexToTemplate] + [decodeNewlines]),
 * and also inside a TextWatcher to re-apply spans as the user types.
 *
 * @param editable The editable text of an EditText or SpannableStringBuilder.
 * @param context  Used to resolve color resources.
 */
fun applyPlaceholderSpans(editable: Editable, context: Context) {
    for (s in editable.getSpans(0, editable.length, BackgroundColorSpan::class.java)) {
        editable.removeSpan(s)
    }
    for (s in editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)) {
        editable.removeSpan(s)
    }
    for (s in editable.getSpans(0, editable.length, StyleSpan::class.java)) {
        editable.removeSpan(s)
    }

    val text = editable.toString()

    // Collect ranges covered by ⟨name⟩ placeholders so we can skip them for regex highlighting.
    val placeholderRanges = mutableListOf<IntRange>()
    val bgColor = ContextCompat.getColor(context, R.color.purple_500)
    Regex("⟨[^⟩]+⟩").findAll(text).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        placeholderRanges.add(match.range)
        editable.setSpan(BackgroundColorSpan(bgColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        editable.setSpan(ForegroundColorSpan(Color.WHITE), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        editable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    // Secondary pass: highlight raw regex syntax sequences with an amber background so they stand
    // out from literal text. Matches: escape sequences (\d \w \s \D \W \S \n \t \b and others),
    // character classes ([…]), non-capturing groups ((?:…)), and quantifiers (+, *, ?, {n,m}).
    val regexSyntax = Regex(
        """\\[dDwWsSntrb.]|""" + // escape sequences: \d \D \w \W \s \S \n \t \r \b \.
            """\[(?:[^\]\\]|\\.)*]|""" + // character class: [...]
            """\(\?:(?:[^)(]|\([^)]*\))*\)|""" + // non-capturing group: (?:...)
            """[+*?]|\{[0-9]+(?:,[0-9]*)?\}""" // quantifiers: + * ? {n} {n,} {n,m}
    )
    val amberBg = ContextCompat.getColor(context, R.color.regex_highlight)
    regexSyntax.findAll(text).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        // Skip if this range overlaps any ⟨name⟩ placeholder span.
        if (placeholderRanges.none { pr -> start < pr.last + 1 && end > pr.first }) {
            editable.setSpan(BackgroundColorSpan(amberBg), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
