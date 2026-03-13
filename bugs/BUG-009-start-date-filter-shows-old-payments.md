# BUG-009: Start Date Filter Shows Payments Earlier Than Selected Start Date

**Status:** Fixed (see fix section)
**Severity:** High
**Component:** PaymentsActivity – `applyFilter`

---

## Description

When the user sets a **start date** to filter payments, older payments (before the start date)
are still shown in the list.

---

## Reproduction Steps

Given:
- The **first** payment has a transaction date of January 1, 2026.
- The **last** payment has a transaction date of March 12, 2026.
- All payments were **imported** on March 12, 2026 (so `receivedAt = 2026-03-12` for all records).

Steps:
1. Open the **Payments** screen.
2. Set **Start Date** to March 1, 2026.
3. Leave **End Date** at end of March (default).
4. **Expected**: Only payments from March 1–31 are shown.
5. **Actual**: Payments from January and February are also shown.

---

## Root Cause

Same root cause as BUG-008: `applyFilter()` filters by `payment.receivedAt` instead of the
transaction timestamp. Since all payments have `receivedAt = March 12, 2026`, the condition
`receivedAt >= startDate (March 1)` is `true` for **all** payments, including those with transaction
dates in January and February.

---

## Fix

Same fix as BUG-008: use `payment.timestamp` (parsed from the SMS body) for date filtering.

See `PaymentsFilter.kt` and the fix to `PaymentsActivity.applyFilter()`.

---

## Tests

See `app/src/test/java/com/example/banksmstracker/ui/PaymentsFilterTest.kt`.
