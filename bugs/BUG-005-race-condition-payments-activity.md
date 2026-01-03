# BUG-005: Race Condition in PaymentsActivity Config Loading

## Status
- [x] Reported
- [x] In Progress
- [x] Fixed
- [x] Verified

## Description
PaymentsActivity calls `ConfigRepository.load()` asynchronously but immediately accesses `getCategories()` before load completes, causing a race condition.

## Steps to Reproduce
1. Navigate to PaymentsActivity quickly after app start
2. Categories spinner may be empty or crash if config not loaded

## Expected Behavior
Ensure config is fully loaded before accessing categories.

## Actual Behavior
Line 85 `ConfigRepository.getCategories()` may execute before line 75 `ConfigRepository.load()` completes.

## Root Cause
Async `load()` doesn't block the UI setup code that depends on loaded data.

## Fix
Use `lifecycleScope.launch { ConfigRepository.load(); setupCategoriesSpinner() }` pattern.

## Verification
- [ ] Config loaded before categories accessed
- [ ] Appium test for rapid navigation
- [ ] Integration test passes

## Related Files
- `app/src/main/java/com/example/banksmstracker/ui/PaymentsActivity.kt:75-85`
