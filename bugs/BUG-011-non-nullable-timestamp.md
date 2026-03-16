# BUG-011: Replace `receivedAt` with non-nullable `timestamp`

## Status
- [x] Reported
- [x] In Progress
- [x] Fixed
- [x] Verified

## Description

`Payment` had two time-related fields:
- `timestamp: String?` — transaction date parsed from the SMS body (nullable when SMS lacked date groups)
- `receivedAt: Long?` — epoch ms of when the **app** ran `processMessage()`, i.e. the batch-import moment

The date filter in `PaymentsActivity` fell back to `receivedAt` when `timestamp` was null. For a batch import of 295 payments on 2026-03-12, all payments had `receivedAt = 2026-03-12` regardless of their actual transaction date. Setting an end-date of March 11 excluded every payment (root cause of BUG-008/009).

## Root Cause

`receivedAt` records when the **app** processed the SMS, not when the transaction occurred. Using it as a date-filter fallback creates a systematic bias — all historically imported payments appear to be from the import day.

## Fix

### Step 1 — Non-nullable timestamp guarantee in `PaymentProcessor`

`approximateDate()` rewritten to fill a missing timestamp using:
1. Nearest neighbour by insertion id (most recently inserted payment with a non-null timestamp)
2. Device SMS receive time (`smsReceivedAt: Long`) formatted as `dd/MM/yyyy` — last resort

`processMessage()` and `processMessageFull()` gained a `smsReceivedAt: Long` parameter:
- `SmsReceiver.kt`: passes `messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()`
- `ApplyRulesActivity.kt`: passes `SmsWithDate.date` (the SMS provider `date` column)

### Step 2 — Remove `receivedAt` from domain and DB

- `Payment.kt`: `receivedAt` field deleted; `timestamp` made non-nullable (`String`)
- `PaymentEntity.kt`: `receivedAt` column removed; `timestamp` made non-nullable
- `RoomPaymentRepository.savePayment()`: removed `receivedAt = System.currentTimeMillis()` from entity constructor
- `PaymentsFilter.kt`: removed `?: payment.receivedAt` fallback

### Step 3 — DB migration v8 → v9

```sql
-- Back-fill NULL timestamps from receivedAt before dropping the column
UPDATE payments
SET timestamp = strftime('%d/%m/%Y', datetime(receivedAt / 1000, 'unixepoch'))
WHERE timestamp IS NULL AND receivedAt IS NOT NULL;

-- Recreate table without receivedAt, with timestamp NOT NULL
CREATE TABLE payments_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    amount REAL NOT NULL,
    currency TEXT NOT NULL,
    card TEXT,
    merchant TEXT,
    timestamp TEXT NOT NULL,
    balance REAL,
    categoryName TEXT,
    messageHash TEXT UNIQUE,
    senderAddress TEXT,
    ruleId INTEGER
);
INSERT INTO payments_new SELECT id, amount, currency, card, merchant,
    COALESCE(timestamp, '01/01/1970'), balance, categoryName, messageHash, senderAddress, ruleId
FROM payments;
DROP TABLE payments;
ALTER TABLE payments_new RENAME TO payments;
```

### Step 4 — Downstream callers updated

| Location | Change |
|----------|--------|
| `ApplyRulesActivity.kt` | `mapNotNull { it.receivedAt }` → `mapNotNull { parseTransactionTimestamp(it.timestamp) }` |
| `PaymentsActivity.kt` | Export date range from `timestamp` instead of `receivedAt`; removed `receivedAt` CSV column |
| `PaymentsFilter.kt` | Removed `?: payment.receivedAt` fallback |

## Verification

- [x] Unit tests pass — 405 tests, 0 failures (includes `PaymentsFilterTest` regression tests for BUG-008/009)
- [x] Smoke tests pass — 21/21 Appium smoke tests green
- [x] DB migration tested (v8→v9 path)

## Related Files

- `app/src/main/java/com/example/banksmstracker/data/Payment.kt`
- `app/src/main/java/com/example/banksmstracker/database/Entities.kt`
- `app/src/main/java/com/example/banksmstracker/database/BankSmsDatabase.kt` (`MIGRATION_8_9`)
- `app/src/main/java/com/example/banksmstracker/processor/PaymentProcessor.kt`
- `app/src/main/java/com/example/banksmstracker/repository/RoomPaymentRepository.kt`
- `app/src/main/java/com/example/banksmstracker/parser/SmsReceiver.kt`
- `app/src/main/java/com/example/banksmstracker/ui/ApplyRulesActivity.kt`
- `app/src/main/java/com/example/banksmstracker/ui/PaymentsActivity.kt`
- `app/src/main/java/com/example/banksmstracker/ui/PaymentsFilter.kt`

## Commit

`e5ce8c0`

## Related Bugs

- BUG-008: End date filter shows no payments (root cause: `receivedAt` batch-import bias)
- BUG-009: Start date filter shows earlier payments (same root cause)
