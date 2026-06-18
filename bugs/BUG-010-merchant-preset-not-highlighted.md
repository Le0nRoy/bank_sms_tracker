# BUG-010: ⟨merchant⟩ Block Not Highlighted in Regex Builder

**Status:** Fixed (see fix section)
**Severity:** Medium
**Component:** RegexBuilderActivity – `regexToTemplate`

---

## Description

When loading an existing regex pattern in the **Regex Builder**, the `⟨merchant⟩` placeholder is
sometimes not highlighted as a colored chip, while other placeholders (`⟨amount⟩`, `⟨currency⟩`,
etc.) are highlighted correctly.

---

## Reproduction Steps

1. Open the **Regex Builder**.
2. Select **TBC Bank** sender.
3. Select an existing PAYMENT pattern from the spinner (e.g., the utility payment rule).
4. **Expected**: All captured-group placeholders appear as colored chips.
5. **Actual**: Some placeholders (especially `⟨merchant⟩`) may not appear as colored chips.

---

## Root Cause

`regexToTemplate()` in `RegexBuilderActivity` used **exact string replacement**:

```kotlin
placeholderToRegex.forEach { (name, pattern) ->
    result = result.replace(pattern, "⟨$name⟩")
}
```

The preset regex for `merchant` is `(?<merchant>.+?)` (lazy). However, many stored rules use
`(?<merchant>.+)` (greedy) or `(?<merchant>TBCTPMTR)` (fixed string). The exact string match for
`(?<merchant>.+?)` would not find `(?<merchant>.+)`, so the merchant group would remain as raw
regex text and would not be converted to a `⟨merchant⟩` placeholder for highlighting.

The same issue affects `card` (preset is `(?<card>.+?)`, stored rules use `(?<card>.+)` greedy).

---

## Fix

Extracted `regexToTemplate()` and `templateToRegex()` into `RegexTemplateUtils.kt`.

The new `regexToTemplate()` uses a regex-based replacement that matches any `(?<name>...)` group
regardless of its inner content, using the pattern:

```
\(\?<NAME>(?:[^)(]|\([^)]*\))*\)
```

This handles:
- `(?<merchant>.+?)` ← lazy (old preset exact match)
- `(?<merchant>.+)` ← greedy (most stored rules)
- `(?<merchant>TBCTPMTR)` ← fixed string
- `(?<amount>\d+(?:[.]\d{2}))` ← nested non-capturing group inside

---

## Tests

See `app/src/test/java/com/example/banksmstracker/util/RegexTemplateUtilsTest.kt`.
