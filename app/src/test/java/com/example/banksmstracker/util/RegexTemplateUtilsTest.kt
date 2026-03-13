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
    fun `regexToTemplate leaves unknown groups unchanged`() {
        val regex = "(?<unknown>foo)"
        assertEquals("(?<unknown>foo)", regexToTemplate(regex))
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
}
