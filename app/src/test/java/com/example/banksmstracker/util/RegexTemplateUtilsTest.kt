package com.example.banksmstracker.util

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [regexToTemplate] and [templateToRegex].
 *
 * Covers BUG-010: the ⟨merchant⟩ placeholder was not converted when loading patterns that used
 * greedy quantifiers (`.+` instead of `.+?`) or fixed-string alternatives.
 */
class RegexTemplateUtilsTest {

    // ── regexToTemplate ────────────────────────────────────────────────────────

    @Test
    fun `regexToTemplate converts lazy merchant group`() {
        val regex = "(?<merchant>.+?)"
        assertEquals("⟨merchant⟩", regexToTemplate(regex))
    }

    @Test
    fun `BUG-010 regexToTemplate converts greedy merchant group`() {
        // Before the fix, (?<merchant>.+) (greedy) was NOT converted because the exact preset
        // string (?<merchant>.+?) (lazy) did not match.
        val regex = "(?<merchant>.+)"
        assertEquals("⟨merchant⟩", regexToTemplate(regex))
    }

    @Test
    fun `BUG-010 regexToTemplate converts fixed-string merchant`() {
        val regex = "(?<merchant>TBCTPMTR)"
        assertEquals("⟨merchant⟩", regexToTemplate(regex))
    }

    @Test
    fun `regexToTemplate converts amount with nested non-capturing group`() {
        val regex = "(?<amount>\\d+(?:[.]\\d{2}))"
        assertEquals("⟨amount⟩", regexToTemplate(regex))
    }

    @Test
    fun `regexToTemplate converts balance with nested non-capturing group`() {
        val regex = "(?<balance>\\d+(?:[.]\\d{2}))"
        assertEquals("⟨balance⟩", regexToTemplate(regex))
    }

    @Test
    fun `regexToTemplate converts currency group`() {
        val regex = "(?<currency>[A-Z]{3})"
        assertEquals("⟨currency⟩", regexToTemplate(regex))
    }

    @Test
    fun `regexToTemplate converts date group`() {
        val regex = "(?<date>\\d{2}/\\d{2}/\\d{4})"
        assertEquals("⟨date⟩", regexToTemplate(regex))
    }

    @Test
    fun `regexToTemplate converts time group`() {
        val regex = "(?<time>\\d{2}:\\d{2}:\\d{2})"
        assertEquals("⟨time⟩", regexToTemplate(regex))
    }

    @Test
    fun `regexToTemplate converts full card payment rule`() {
        val regex = "(?m)^(?<amount>\\d+(?:[.]\\d{2}))[ ]+(?<currency>[A-Z]{3})(?:\\r?\\n)" +
            "(?<card>.+)(?:\\r?\\n)(?<merchant>.+?)[ ]+(?<date>\\d{2}/\\d{2}/\\d{4})" +
            "[ ]+(?<time>\\d{2}:\\d{2}:\\d{2})(?:\\r?\\n)" +
            "Balance:[ ]+(?<balance>\\d+(?:[.]\\d{2}))[ ]+[A-Z]{3}"

        val template = regexToTemplate(regex)

        assertTrue(template.contains("⟨amount⟩"), "amount not converted")
        assertTrue(template.contains("⟨currency⟩"), "currency not converted")
        assertTrue(template.contains("⟨merchant⟩"), "BUG-010: merchant not converted")
        assertTrue(template.contains("⟨date⟩"), "date not converted")
        assertTrue(template.contains("⟨time⟩"), "time not converted")
        assertTrue(template.contains("⟨balance⟩"), "balance not converted")
    }

    @Test
    fun `regexToTemplate converts unknown named groups to placeholders`() {
        // Bug 2 fix: any (?<name>…) group — even with an unknown name — is converted to ⟨name⟩
        // so it is shown as a readable placeholder instead of raw regex syntax.
        val regex = "(?<unknown>foo)"
        assertEquals("⟨unknown⟩", regexToTemplate(regex))
    }

    @Test
    fun `regexToTemplate converts unknown named group with digit content`() {
        val regex = "(?<code>\\d+)"
        assertEquals("⟨code⟩", regexToTemplate(regex))
    }

    @Test
    fun `regexToTemplate leaves literal text unchanged`() {
        val text = "Balance:[ ]+"
        assertEquals(text, regexToTemplate(text))
    }

    // ── templateToRegex ────────────────────────────────────────────────────────

    @Test
    fun `templateToRegex converts merchant placeholder to preset regex`() {
        val template = "⟨merchant⟩"
        assertEquals("(?<merchant>.+?)", templateToRegex(template))
    }

    @Test
    fun `templateToRegex converts amount placeholder to preset regex`() {
        val template = "⟨amount⟩"
        assertEquals("(?<amount>\\d+(?:[.]\\d{2}))", templateToRegex(template))
    }

    @Test
    fun `templateToRegex converts all placeholders in a full template`() {
        val template = "⟨amount⟩ ⟨currency⟩ ⟨merchant⟩ ⟨date⟩ ⟨time⟩ ⟨balance⟩"
        val regex = templateToRegex(template)

        assertTrue(regex.contains("(?<amount>"), "amount not converted")
        assertTrue(regex.contains("(?<currency>"), "currency not converted")
        assertTrue(regex.contains("(?<merchant>"), "merchant not converted")
        assertTrue(regex.contains("(?<date>"), "date not converted")
        assertTrue(regex.contains("(?<time>"), "time not converted")
        assertTrue(regex.contains("(?<balance>"), "balance not converted")
    }

    // ── Round-trip ─────────────────────────────────────────────────────────────

    @Test
    fun `templateToRegex is inverse of regexToTemplate for lazy merchant`() {
        val original = "(?<merchant>.+?)"
        val roundTripped = templateToRegex(regexToTemplate(original))
        assertEquals(original, roundTripped)
    }

    @Test
    fun `BUG-010 round-trip normalises greedy merchant to lazy preset`() {
        // Greedy .+ gets converted to template, then back to lazy .+? (the canonical preset).
        val stored = "(?<merchant>.+)"
        val template = regexToTemplate(stored)
        assertEquals("⟨merchant⟩", template)
        val roundTripped = templateToRegex(template)
        // Round-trip normalises to the canonical (lazy) preset form.
        assertEquals("(?<merchant>.+?)", roundTripped)
    }

    @Test
    fun `round-trip for amount with nested group`() {
        val original = "(?<amount>\\d+(?:[.]\\d{2}))"
        val roundTripped = templateToRegex(regexToTemplate(original))
        assertEquals(original, roundTripped)
    }

    @Test
    fun `round-trip templateToRegex then regexToTemplate for all six groups`() {
        for ((name, preset) in PRESET_REGEXES) {
            val template = "⟨$name⟩"
            val roundTripped = regexToTemplate(templateToRegex(template))
            assertEquals(template, roundTripped, "round-trip failed for group: $name (preset was $preset)")
        }
    }

    @Test
    fun `round-trip regexToTemplate then templateToRegex for currency group`() {
        val original = "(?<currency>[A-Z]{3})"
        assertEquals(original, templateToRegex(regexToTemplate(original)))
    }

    @Test
    fun `round-trip regexToTemplate then templateToRegex for card group`() {
        val original = "(?<card>.+?)"
        assertEquals(original, templateToRegex(regexToTemplate(original)))
    }

    @Test
    fun `round-trip regexToTemplate then templateToRegex for date group`() {
        val original = "(?<date>\\d{2}/\\d{2}/\\d{4})"
        assertEquals(original, templateToRegex(regexToTemplate(original)))
    }

    @Test
    fun `round-trip regexToTemplate then templateToRegex for time group`() {
        val original = "(?<time>\\d{2}:\\d{2}:\\d{2})"
        assertEquals(original, templateToRegex(regexToTemplate(original)))
    }

    @Test
    fun `round-trip regexToTemplate then templateToRegex for balance group`() {
        val original = "(?<balance>\\d+(?:[.]\\d{2}))"
        assertEquals(original, templateToRegex(regexToTemplate(original)))
    }

    @Test
    fun `regexToTemplate with multiple groups across decoded newlines`() {
        val regex = "(?<amount>\\d+(?:[.]\\d{2}))\\n(?<currency>[A-Z]{3})\\n(?<merchant>.+?)"
        val decoded = decodeNewlines(regexToTemplate(regex))
        assertEquals("⟨amount⟩\n⟨currency⟩\n⟨merchant⟩", decoded)
    }

    // ── decodeNewlines / encodeNewlines ─────────────────────────────────────────

    @Test
    fun `decodeNewlines converts backslash-n literal to actual newline`() {
        // Stored pattern has the two-character sequence \n (backslash + n)
        val stored = "Line1\\nLine2"
        assertEquals("Line1\nLine2", decodeNewlines(stored))
    }

    @Test
    fun `decodeNewlines is no-op when no backslash-n present`() {
        val stored = "No newlines here"
        assertEquals("No newlines here", decodeNewlines(stored))
    }

    @Test
    fun `encodeNewlines converts actual newline to backslash-n literal`() {
        val display = "Line1\nLine2"
        assertEquals("Line1\\nLine2", encodeNewlines(display))
    }

    @Test
    fun `encodeNewlines is no-op when no newline present`() {
        val display = "No newlines here"
        assertEquals("No newlines here", encodeNewlines(display))
    }

    @Test
    fun `newlines round-trip encode then decode preserves pattern`() {
        val original = "Line1\nLine2\nLine3"
        assertEquals(original, decodeNewlines(encodeNewlines(original)))
    }

    @Test
    fun `newlines round-trip decode then encode preserves stored pattern`() {
        val stored = "Line1\\nLine2\\nLine3"
        assertEquals(stored, encodeNewlines(decodeNewlines(stored)))
    }

    @Test
    fun `Task44 loading pattern decodes backslash-n literals as visual newlines`() {
        // A stored regex like "(?m)^Amount[ ]+\\nMerchant" should display as two lines
        val stored = "(?m)^Amount[ ]+\\nMerchant"
        val display = decodeNewlines(regexToTemplate(stored))
        assertTrue('\n' in display, "Expected actual newline in display string")
        assertTrue(
            !display.contains("\\n"),
            "Expected no backslash-n literal in display string"
        )
    }

    // ── Task 3.0: display decode / save encode chains ───────────────────────

    private fun decodePattern(stored: String): String = stored.replace("\\s", " ")
    private fun encodePattern(raw: String): String = raw.replace(" ", "\\s")

    @Test
    fun `Task30 displayDecode converts full stored pattern to human-readable template`() {
        // Stored pattern uses \s, (?<name>...) groups, and \n literals
        val stored = "(?m)^(?<amount>\\d+(?:[.]\\d{2}))[ ]+(?<currency>[A-Z]{3})\\n(?<merchant>.+?)"
        val display = decodeNewlines(regexToTemplate(decodePattern(stored)))
        assertEquals("(?m)^⟨amount⟩[ ]+⟨currency⟩\n⟨merchant⟩", display)
    }

    @Test
    fun `Task30 saveEncode converts display template back to storable regex`() {
        val display = "(?m)^⟨amount⟩[ ]+⟨currency⟩\n⟨merchant⟩"
        val stored = encodePattern(templateToRegex(encodeNewlines(display)))
        assertTrue(stored.contains("(?<amount>"), "amount group not restored")
        assertTrue(stored.contains("(?<currency>"), "currency group not restored")
        assertTrue(stored.contains("(?<merchant>"), "merchant group not restored")
        assertTrue(stored.contains("\\n"), "newline not encoded back to \\n literal")
        assertTrue(
            !stored.contains("\n") || stored.indexOf('\n') == -1,
            "actual newline should not remain in stored pattern"
        )
    }

    @Test
    fun `Task30 roundtrip decode-then-encode preserves semantic equivalence`() {
        val original = "(?m)^(?<amount>\\d+(?:[.]\\d{2}))[ ]+(?<currency>[A-Z]{3})\\n(?<merchant>.+?)"
        val display = decodeNewlines(regexToTemplate(decodePattern(original)))
        val reEncoded = encodePattern(templateToRegex(encodeNewlines(display)))
        // The re-encoded pattern should be compilable as a valid regex
        val regex = Regex(reEncoded, setOf(RegexOption.MULTILINE))
        val testSms = "100.00 USD\nShop Name"
        assertTrue(regex.containsMatchIn(testSms), "Re-encoded pattern should still match SMS")
    }
}
