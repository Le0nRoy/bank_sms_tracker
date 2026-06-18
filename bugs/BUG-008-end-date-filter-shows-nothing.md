# BUG-008: End Date Filter Shows No Payments When End < Latest ReceivedAt

**Status:** Fixed (see fix section)
**Severity:** High
**Component:** PaymentsActivity – `applyFilter`

---

## Description

When the user sets a date range where the **end date** is before the date the SMS messages were
imported (i.e., before `receivedAt`), no payments are shown — even though there are payments with
transaction timestamps within the selected range.

---

## Reproduction Steps

Given:
- The latest payment has a **transaction date** of March 12, 2026.
- All payments were **imported** on March 12, 2026 (so `receivedAt = 2026-03-12` for all records).

Steps:
1. Open the **Payments** screen.
2. Set **Start Date** to March 1, 2026.
3. Set **End Date** to March 11, 2026 (one day before the latest transaction).
4. **Expected**: Payments with transaction timestamps between March 1–11 are shown.
5. **Actual**: The payment list is empty.

---

## Root Cause

`applyFilter()` in `PaymentsActivity.kt` filters by `payment.receivedAt`:

```kotlin
val receivedAt = payment.receivedAt ?: return@filter false
val afterStart = startDate?.let { receivedAt >= it } ?: true
val beforeEnd = endDate?.let { receivedAt <= it } ?: true
```

`receivedAt` is set to `System.currentTimeMillis()` at the moment the app processes a batch of SMS
messages. If the user imported all historical SMS at once on March 12, every payment has
`receivedAt = ~March 12 2026`. With `endDate = March 11`, the condition `receivedAt <= endDate`
is `false` for **every** payment, so the list is empty.

The correct field to filter on is `payment.timestamp`, which holds the transaction date extracted
from the SMS body (e.g., "01/03/2026 14:22:05").

---

## Fix

Extracted `parseTransactionTimestamp()` and `filterPayments()` into `PaymentsFilter.kt`.
The filter now uses the parsed `timestamp` field with a fallback to `receivedAt`.

See `PaymentsFilter.kt` and the fix to `PaymentsActivity.applyFilter()`.

---

## Tests

See `app/src/test/java/com/example/banksmstracker/ui/PaymentsFilterTest.kt`.
