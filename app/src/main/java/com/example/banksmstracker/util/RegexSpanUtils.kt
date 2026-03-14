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
 * Call this after setting text to a decoded template (see [regexToTemplate] + [decodeNewlines]),
 * and also inside a TextWatcher to re-apply spans as the user types.
 *
 * @param editable The editable text of an EditText or SpannableStringBuilder.
 * @param context  Used to resolve the [R.color.purple_500] background color.
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
    val bgColor = ContextCompat.getColor(context, R.color.purple_500)
    Regex("⟨[^⟩]+⟩").findAll(editable.toString()).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        editable.setSpan(BackgroundColorSpan(bgColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        editable.setSpan(ForegroundColorSpan(Color.WHITE), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        editable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
