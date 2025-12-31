# BUG-003: Missing Group Index Bounds Checking in PaymentProcessor

## Status
- [x] Reported
- [x] In Progress
- [x] Fixed
- [x] Verified

## Description
PaymentProcessor assumes regex patterns have exactly 6 capture groups without validation. If a regex has fewer groups, accessing `match.groupValues[1]` through `[6]` throws IndexOutOfBoundsException.

## Steps to Reproduce
1. Create a sender with regex having fewer than 6 groups (e.g., `payment (\d+)`)
2. Receive SMS from that sender
3. App crashes with IndexOutOfBoundsException

## Expected Behavior
Validate regex has required groups before accessing them, or handle missing groups gracefully.

## Actual Behavior
Unchecked access to `match.groupValues[1..6]` causes crash when groups are missing.

## Root Cause
Lines 39-44 in PaymentProcessor.kt access group indices without checking `groupValues.size`.

## Fix
Check `match.groupValues.size >= 7` (including group 0) before accessing, or use `getOrNull()`.

## Verification
- [x] Unit test with insufficient groups
- [x] Unit test with valid groups
- [x] Integration test passes

## Related Files
- `app/src/main/java/com/example/banksmstracker/processor/PaymentProcessor.kt:39-44`
