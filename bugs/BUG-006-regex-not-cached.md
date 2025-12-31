# BUG-006: Regex Pattern Compilation Not Cached

## Status
- [x] Reported
- [x] In Progress
- [x] Fixed
- [x] Verified

## Description
PaymentRegexRule.regexPattern calls `regex.toRegex()` every time it's accessed, recompiling the regex pattern repeatedly. This causes performance degradation especially in loops.

## Steps to Reproduce
1. Process many SMS messages
2. Each message recompiles all regex patterns
3. Performance degrades with many rules

## Expected Behavior
Regex patterns should be compiled once and cached.

## Actual Behavior
`regex.toRegex()` is called on every property access.

## Root Cause
Line 7-8 in PaymentRegexRule.kt: `val regexPattern get() = regex.toRegex()`

## Fix
Use `lazy` initialization: `val regexPattern by lazy { regex.toRegex() }`

## Verification
- [x] Unit test for regex caching
- [x] Performance test
- [x] Integration test passes

## Related Files
- `app/src/main/java/com/example/banksmstracker/data/PaymentRegexRule.kt:7-8`
