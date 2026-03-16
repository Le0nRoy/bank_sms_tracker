# Implementation Plan: PROMPT.md Feature Batch (2026-03-13)

**Date:** 2026-03-13
**Branch:** feature/create-pov
**Source:** PROMPT.md (new batch of requirements)

---

## Overview

| # | Task | Priority | Type | Complexity | Status |
|---|------|----------|------|-----------|--------|
| 2.2 | Category change from payment detail | HIGHEST | Bug (TDD) | M | ✅ Done |
| 2.3 | End date filter shows no payments | HIGHEST | Bug (TDD) | S | ✅ Done |
| 2.4 | Start date filter shows earlier payments | HIGHEST | Bug (TDD) | S | ✅ Done |
| 4.3 | `⟨merchant⟩` block not highlighted | HIGHEST | Bug (TDD) | S | ✅ Done |
| 4.1 | Sender not erased after regex save | MEDIUM | Feature | XS | ✅ Done |
| 4.4 | Human-readable newlines in Regex Builder | MEDIUM | Feature | XS | ✅ Done (2026-03-14) |
| 3.0 | Formatted regex display in Senders | MEDIUM | Feature | S | ✅ Done (2026-03-14) |
| 1.1 | Simple category change for merchants (Categories screen) | HIGH | Feature | M | ✅ Done (2026-03-17) |
| 1.2 | Optional display name for merchants | HIGH | Feature | M | ✅ Done (2026-03-17) |
| 1.3 | Regex support for merchant matching | HIGH | Feature | M | ✅ Done (2026-03-17) |
| 2.1 | Merchant search in Payments | MEDIUM | Feature | M | ✅ Done (2026-03-14, tests verified) |
| 4.2 | Edit Existing Pattern separate window | MEDIUM | Feature | L | ✅ Done (2026-03-17) |
| BUG-011 | Replace `receivedAt` with non-nullable `timestamp` | HIGH | Bug/Refactor | L | ✅ Done (2026-03-17) |
| 2.5 | Spending report diagrams | FUTURE | TODO.md | — | — |
| 2.6 | Central bank API for currency | FUTURE | TODO.md | — | — |
| 4.5 | Text box free from regex special chars | FUTURE | TODO.md | — | — |

**Bug handling policy:** TDD — write failing tests first, document in `bugs/` directory, then implement fix.

---

## Part 1: Bug Fixes (Highest Priority, TDD)

### BUG-007: Category Re-assignment from Payment Detail (Task 2.2)

**File:** `bugs/BUG-007-category-reassignment.md`

#### Root Cause

`PaymentsActivity.addMerchantToCategory()` adds a merchant to the new category but:
1. Does **not** remove the merchant from any previous category
2. Does **not** update existing payments' `categoryId` field — only future payments via regex will pick up the new categorization

So after reassigning, the payment still shows the old (or null) `categoryId`, and the merchant now exists in two categories (old + new). The `assignCategory` function uses `find`, so the first matching category wins — which may not be the newly selected one.

#### TDD Test Plan

Write unit tests in `com.example.banksmstracker.ui` or a new `PaymentsActivityUnitTest`:

```
Test 1: addMerchantToCategory_removesFromOtherCategories
  - Setup: Category A with merchant "FooStore", Category B (empty)
  - Action: addMerchantToCategory("FooStore", categoryB)
  - Assert: Category A no longer contains "FooStore"
  - Assert: Category B contains "FooStore"

Test 2: addMerchantToCategory_updatesExistingPayments
  - Setup: Payment with merchant="FooStore", categoryId=null (or "CategoryA")
  - Setup: Category B
  - Action: addMerchantToCategory("FooStore", categoryB)
  - Assert: Payment with merchant="FooStore" now has categoryId="CategoryB"

Test 3: addMerchantToCategory_alreadyInCategory_doesNothing
  - Setup: Category A with "FooStore", Payment with categoryId="CategoryA"
  - Action: addMerchantToCategory("FooStore", categoryA)
  - Assert: No error, no duplicate merchant added

Test 4: reassignMerchant_fromCategoryA_toCategoryB_worksCorrectly
  - Full round-trip: assign to A, then assign to B
  - Assert: A does not have "FooStore", B has "FooStore"
  - Assert: Payment's categoryId = "CategoryB"
```

#### Fix Strategy

In `PaymentsActivity.addMerchantToCategory()`:
1. Before adding to new category, iterate all categories and remove the merchant from any other category that contains it (call `ConfigRepository.updateCategory()` for each modified category)
2. After adding to new category, update all payments that have `merchant == merchantName` to set `categoryId = category.name` (new targeted `RoomPaymentRepository.updateCategoryForMerchant(merchant, categoryId)`)

New repository method needed:
```kotlin
// PaymentRepository interface + RoomPaymentRepository
suspend fun updateCategoryForMerchant(merchant: String, newCategoryId: String)
// PaymentDao: UPDATE payments SET categoryName = :newCategoryId WHERE LOWER(merchant) = LOWER(:merchant)
```

---

### BUG-008: End Date Filter Shows No Payments (Task 2.3)

**File:** `bugs/BUG-008-end-date-filter.md`

#### Precise Reproduction (confirmed by user)

- Latest payment: `receivedAt = March 12, 2026`
- User sets `startDate = March 1, 2026` AND `endDate = March 11, 2026`
- Start date IS before end date (not swapped)
- Expected: payments from March 1–11 are shown
- Actual: activity is empty — **no payments shown at all**

#### Root Cause (to be confirmed via TDD)

Since `startDate < endDate`, the filter condition `receivedAt >= March1 && receivedAt <= March11` is logically valid. The most likely causes are:

1. **Null `receivedAt` on March 1–11 payments**: The filter does `payment.receivedAt ?: return@filter false`. If payments from March 1–11 were processed without a `receivedAt` value (e.g., historical SMS applied before `approximateDate()` was in place), they are ALL excluded by the null check. The only payment with a non-null `receivedAt` (March 12) is excluded by `receivedAt <= March 11`. Result: empty screen.

2. **`applyFilter()` invoked before `allPayments` is populated**: A race condition where the date picker callback fires while `loadData()` is still running and `allPayments` is still empty.

3. **Timestamp unit mismatch**: `receivedAt` stored in a different unit (seconds vs milliseconds) causing all comparisons to fail unexpectedly.

#### TDD Test Plan

Extract `filterPayments()` into a pure function (no Android dependency) for unit testing:

```kotlin
fun filterPayments(
    payments: List<Payment>,
    selectedCategory: String?,
    selectedSender: String?,
    startDate: Long?,
    endDate: Long?
): List<Payment>
```

Tests:
```
Test 1: filter_startAndEndDateSet_showsPaymentsInRange
  - Payment A: receivedAt = March 5, 2026 (in range)
  - Payment B: receivedAt = March 12, 2026 (out of range)
  - startDate = March 1; endDate = March 11 23:59:59
  - Expected: [Payment A] returned; Payment B excluded

Test 2: filter_paymentWithNullReceivedAt_excludedWhenDateFilterActive
  - Payment: receivedAt = null
  - startDate = March 1; endDate = March 11
  - Expected: payment excluded (null receivedAt always excluded by date filter)

Test 3: filter_noPaymentsInRange_returnsEmpty
  - All payments have receivedAt = March 12, 2026
  - startDate = March 1; endDate = March 11 23:59:59
  - Expected: empty list (correct, not a bug)

Test 4 (regression): filter_paymentWithReceivedAt_March5_withRange_March1_March11_isIncluded
  - This is the exact user scenario — confirms the fix works
```

#### Fix Strategy

After writing tests to identify the exact cause:

- **If cause is null `receivedAt`**: Change the filter to fall back to the payment's `timestamp` field (parsed from the SMS text) when `receivedAt` is null, instead of always excluding. Alternatively, improve `approximateDate()` to populate all null `receivedAt` values when payments are displayed.
  ```kotlin
  val receivedAt = payment.receivedAt
      ?: parseTimestamp(payment.timestamp)  // new fallback
      ?: return@filter false
  ```

- **If cause is race condition**: Ensure `applyFilter()` from date picker callbacks only runs after `allPayments` is populated (add guard `if (allPayments.isEmpty()) return`).

- **If cause is timestamp units**: Fix the unit wherever `receivedAt` is written.

---

### BUG-009: Start Date Filter Shows Earlier Payments (Task 2.4)

**File:** `bugs/BUG-009-start-date-filter.md`

#### Precise Reproduction (confirmed by user)

- Payments span January 1, 2026 through March 12, 2026
- User sets `startDate = March 1, 2026`
- Expected: only March payments shown (January and February excluded)
- Actual: January **and** February payments are **still shown**

#### Root Cause (to be confirmed via TDD)

For January/February payments to pass the `afterStart` check when `startDate = March 1`, one of these must be true:

1. **`startDate` is null when `applyFilter()` runs**: `startDate?.let { ... } ?: true` — if `startDate` is null the Elvis operator returns `true`, showing all payments. This would happen if:
   - A stale `applyFilter()` invocation from `spinnerCategory.onItemSelectedListener` fires AFTER the date picker sets `startDate`, but with a stale captured `startDate = null` from before the date was set (Kotlin closure captures the variable at call time, but `startDate` is a `var`, so... actually `var` captures the field reference, meaning it reads the current value. So this path is unlikely.)
   - `loadData()` is re-triggered after the date is set, resets `startDate = null`, and `applyFilter()` inside `setupCategorySpinner().onItemSelected` fires with the null value before `setDefaultDateRange()` runs.

2. **`receivedAt` comparison uses wrong units**: If `receivedAt` for January/February payments is stored in a unit that makes the value numerically larger than `startDate` (milliseconds), `afterStart` would incorrectly return `true`. For example, if some payments have `receivedAt` in nanoseconds (~10^18) vs milliseconds (~10^12 for 2026 dates), then `nanoseconds >= startDate_ms` is always true.

3. **`receivedAt` is null for January/February payments, `startDate` is also null**: With no dates set (after "Clear Dates"), the filter passes all payments. Then setting startDate triggers `applyFilter()` — but if `allPayments` was populated during the "all time" view, those null-receivedAt payments would be EXCLUDED (null check). This doesn't match the bug. More likely January/February DO have `receivedAt` set.

4. **Spinner `onItemSelected` fires during `loadData()` after date picker**: If `loadData()` is called during the interaction (e.g., user taps "add to category" from a payment detail, which calls `loadData(preserveScroll = true)`) while the date picker is open, and spinners are re-initialized inside `loadData()`, `applyFilter()` may run with `startDate = null` before `setDefaultDateRange()` re-runs.

#### TDD Test Plan

Use the same `filterPayments()` pure function extracted for BUG-008:

```
Test 1: filter_startDateMarch1_excludesJanuaryPayment
  - Payment A: receivedAt = January 15, 2026 (should be excluded)
  - Payment B: receivedAt = March 5, 2026 (should be included)
  - startDate = March 1, 2026 00:00:00; endDate = March 31 23:59:59
  - Expected: [Payment B] only

Test 2: filter_startDateMarch1_endDateMarch31_excludesFebruary
  - Payment: receivedAt = February 14, 2026
  - startDate = March 1; endDate = March 31
  - Expected: payment excluded

Test 3: filter_startDateSet_nullReceivedAt_excluded
  - Payment: receivedAt = null
  - startDate = March 1; endDate = March 31
  - Expected: excluded (null check)

Test 4 (regression): filter_fullScenario_jan1_to_march12_startMarch1_showsOnlyMarch
  - Payments: Jan 1, Feb 14, March 5, March 12 (all with receivedAt set)
  - startDate = March 1; endDate = March 31
  - Expected: only March 5 and March 12 returned
```

#### Fix Strategy

After tests confirm which code path is broken:

- **If null `startDate` race condition**: Ensure `applyFilter()` reads `startDate` at call time (not from a closure). Since `startDate` is a `var` property on the Activity, each lambda invocation reads the current value — this should already be safe. The fix would be to add guards or serialize access.

- **If `loadData()` re-initialization race**: Add a flag `private var isInitialized = false` — set after first `loadData()` completes. `setDefaultDateRange()` only runs when `!isInitialized` (not when both dates are null), preventing unintended resets.

- **If unit mismatch**: Find where `receivedAt` is written and standardize to milliseconds throughout.

The pure `filterPayments()` function extracted for BUG-008 also serves this bug's tests.

---

### BUG-010: `⟨merchant⟩` Block Not Highlighted in Regex Builder (Task 4.3)

**File:** `bugs/BUG-010-merchant-not-highlighted.md`

#### Root Cause (Suspected)

`applyPlaceholderSpans()` uses `Regex("⟨[^⟩]+⟩")` to find all `⟨xxx⟩` tokens. The `⟨` and `⟩` characters are Unicode Mathematical Angle Brackets (U+27E8, U+27E9).

The preset buttons insert using the same characters. However, the bug may arise from:
1. **`regexToTemplate` partial mismatch**: If the stored regex is slightly different from the exact `RegexPresets.merchant` string (e.g., `.+` instead of `.+?`), `regexToTemplate` won't replace it with `⟨merchant⟩`, so it stays as raw regex text and the span doesn't apply.
2. **Character encoding inconsistency**: If the `⟨` / `⟩` characters in the preset string differ from the ones in the regex pattern (invisible encoding issue in the source file).
3. **Span removal race condition**: The `TextWatcher.afterTextChanged` fires, removes all existing spans, re-applies new spans — but if something modifies the Editable between removal and application, merchant spans may be dropped.

#### TDD Test Plan

Write unit tests for `RegexFormatUtils` (extracted utility — see implementation):
```
Test 1: applyPlaceholderSpans_highlightsMerchantToken
  - Input: "Payment ⟨amount⟩ to ⟨merchant⟩"
  - Assert: span applied at position of ⟨merchant⟩

Test 2: regexToTemplate_convertsAllPresets
  - Input: "(?<amount>\\d+(?:[.]\\d{2})) (?<currency>[A-Z]{3}) (?<merchant>.+?)"
  - Expected: "⟨amount⟩ ⟨currency⟩ ⟨merchant⟩"
  - Assert: all three presets converted

Test 3: roundtrip_templateToRegex_and_back
  - Template: "Payment: ⟨amount⟩ ⟨currency⟩\nMerchant: ⟨merchant⟩"
  - Convert to regex, then back to template
  - Assert: result equals original template
```

#### Fix Strategy

1. Extract `RegexPresets`, `templateToRegex`, `regexToTemplate`, and `applyPlaceholderSpans` logic into a shared `RegexFormatUtils` object (currently duplicated between RegexBuilder and needs to be shared with Senders display — Task 3.0 also needs this).
2. Add unit tests for `RegexFormatUtils` methods.
3. If the issue is a specific mismatch in the merchant preset string, fix the stored representation.
4. Add `regexToTemplate` conversion when loading from DB (currently only `decodePattern` is called).

---

---

### BUG-011: Replace `receivedAt` with Non-Nullable `timestamp`

**Related:** BUG-008 (root cause was date filtering using `receivedAt` instead of `timestamp`)

#### Problem Statement

`Payment.timestamp` (transaction date extracted from SMS text) is nullable, so when the SMS has no date groups, the payment is stored with `timestamp = null`. The existing workaround used `receivedAt` (epoch ms of when the **app** processed the message) as a filter fallback — which was the direct cause of BUG-008 and BUG-009.

The correct fix is to guarantee that every saved payment has a meaningful `timestamp`:
1. Use the date/time parsed from the SMS body (current behaviour, highest priority).
2. If absent, approximate from the timestamps of neighbouring payments (by insertion order).
3. If no neighbours exist, use the **device SMS receive time** (PDU `timestampMillis` or SMS provider `date` column) — **not** `receivedAt`, which records when the app processed the message.

`receivedAt` is then removed from `Payment` and `PaymentEntity`; a DB migration makes `timestamp NOT NULL`.

#### Concepts

| Term | Meaning | Source |
|---|---|---|
| `timestamp` | Transaction date/time, as written in the SMS body | Regex named groups `(?<date>...)` / `(?<time>...)` |
| device SMS receive time | When the SMS was delivered to the handset | PDU `timestampMillis` (live SMS) or `date` column (SMS provider, historical import) |
| `receivedAt` (removed) | When the **app** ran `processMessage()` — always ≈ now | `System.currentTimeMillis()` at save time |

#### Root Cause Chain (link to BUG-008)

BUG-008 was partially fixed by `PaymentsFilter.kt` parsing `timestamp` in memory. However:
- `timestamp` is still nullable in the DB — batch-imported historical payments without date groups still have `timestamp = null`.
- The `PaymentsFilter` fallback to `receivedAt` can only work for payments that have `receivedAt` set (which for batch imports is the single import moment, not the transaction date).
- The DAO method `getPaymentsByDateRange()` still queries on `receivedAt`.

This task is the complete, permanent fix.

#### Implementation Steps

**Step 1 — Thread device SMS receive time through the processor**

Add a `smsReceivedAt: Long` parameter to `processMessage()` and `processMessageFull()`:

```kotlin
// PaymentProcessor.kt
suspend fun processMessage(message: String, address: String, smsReceivedAt: Long): Payment
suspend fun processMessageFull(message: String, address: String, smsReceivedAt: Long): MessageProcessResult
```

Update callers:
- `SmsReceiver.kt` — extract PDU timestamp: `messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()` and pass it.
- `ApplyRulesActivity.kt` — already reads the `date` column from the SMS provider (`SmsWithDate.date`); pass it through `processor.processMessage(message, sender, smsDate)`.

**Step 2 — Rewrite `approximateDate()`**

Replace the `receivedAt`-based proximity search with `id`-based (insertion order):

```kotlin
private suspend fun approximateDate(payment: Payment, smsReceivedAt: Long): Payment {
    if (payment.timestamp != null) return payment

    // Try nearest neighbour by insertion id (sequential = chronological)
    val allDated = paymentRepository.getAllPayments()
        .filter { it.timestamp != null && it.id != null }
    val neighbor = allDated.maxByOrNull { it.id!! }  // most recently inserted datable payment

    return if (neighbor != null) {
        val approxDate = neighbor.timestamp!!.substringBefore(" ").ifEmpty { neighbor.timestamp!! }
        payment.copy(timestamp = approxDate)
    } else {
        // Last resort: format device receive time as a date string
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        payment.copy(timestamp = fmt.format(Date(smsReceivedAt)))
    }
}
```

After this change `timestamp` is **always non-null** before `savePayment()` is called.

**Step 3 — Make `timestamp` non-nullable in domain and DB**

- `Payment.kt`: `val timestamp: String` (remove `?`)
- `PaymentEntity.kt`: `val timestamp: String` (remove `?`)
- DB migration (current DB version → next):

```kotlin
// BankSmsDatabase.kt — new migration
val MIGRATION_N_N1 = object : Migration(N, N+1) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Fill existing NULLs using receivedAt as a date string fallback
        db.execSQL("""
            UPDATE payments
            SET timestamp = strftime('%d/%m/%Y', datetime(receivedAt / 1000, 'unixepoch'))
            WHERE timestamp IS NULL AND receivedAt IS NOT NULL
        """)
        // 2. Recreate table with NOT NULL constraint and drop receivedAt column
        db.execSQL("""
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
            )
        """)
        db.execSQL("""
            INSERT INTO payments_new
                (id, amount, currency, card, merchant, timestamp, balance,
                 categoryName, messageHash, senderAddress, ruleId)
            SELECT id, amount, currency, card, merchant,
                COALESCE(timestamp, '01/01/1970'),
                balance, categoryName, messageHash, senderAddress, ruleId
            FROM payments
        """)
        db.execSQL("DROP TABLE payments")
        db.execSQL("ALTER TABLE payments_new RENAME TO payments")
    }
}
```

Note: `COALESCE(timestamp, '01/01/1970')` is a safety net only — after Step 2, all rows being inserted will already have a non-null timestamp.

**Step 4 — Remove `receivedAt` from `Payment` domain class**

- Delete `val receivedAt: Long? = null` from `Payment.kt`.
- Remove from `PaymentEntity.toDomain()` mapper in `RoomPaymentRepository.kt`.
- `RoomPaymentRepository.savePayment()`: remove `receivedAt = System.currentTimeMillis()` from `PaymentEntity(...)` constructor call.

**Step 5 — Update `getPaymentsByDateRange()` DAO**

The current query filters on `receivedAt`. Since `timestamp` is a formatted string (not epoch ms), SQL range queries on it are format-dependent. Two options:

- **Option A (recommended):** Remove `getPaymentsByDateRange()` from `PaymentDao` and `PaymentRepository` interface entirely — the UI already does in-memory date filtering via `PaymentsFilter.kt`.
- **Option B:** Store `timestamp` as epoch ms (Long) alongside the human-readable string — adds redundancy.

Use **Option A** unless another caller needs the DAO method.

**Step 6 — Fix downstream usages**

| Location | Change |
|---|---|
| `ApplyRulesActivity.kt:111` — `mapNotNull { it.receivedAt }.maxOrNull()` | Replace with `mapNotNull { parseTransactionTimestamp(it.timestamp) }.maxOrNull()` |
| `PaymentsActivity.kt:400-401` — export date range from `receivedAt` | Replace with `parseTransactionTimestamp(it.timestamp)` |
| `PaymentsActivity.kt:369,382` — CSV `receivedAt` column | Remove column or replace with `timestamp` |
| `PaymentsFilter.kt:52` — `?: payment.receivedAt` fallback | Remove fallback; `timestamp` is now always non-null |

**Step 7 — Update `Income` (if desired)**

`Income` also has `receivedAt`. Since income SMS often lack parseable dates, applying the same non-nullable timestamp guarantee there is a natural follow-up, but is **out of scope** for this task.

#### TDD Test Plan

```
Test 1: processMessage_smsWithNoDate_getsApproximatedTimestamp
  - Setup: existing payment with timestamp = "05/03/2026"
  - Process new SMS with no date group, smsReceivedAt = irrelevant
  - Assert: returned Payment.timestamp = "05/03/2026" (borrowed from neighbour)

Test 2: processMessage_smsWithNoDate_noNeighbours_usesDeviceReceiveTime
  - Setup: empty repository
  - Process SMS with no date group, smsReceivedAt = epoch for 2026-03-01
  - Assert: returned Payment.timestamp = "01/03/2026"

Test 3: processMessage_smsWithDate_timestampNotChanged
  - Process SMS with date group "13/03/2026"
  - Assert: Payment.timestamp = "13/03/2026" (not overwritten)

Test 4 (regression / BUG-008): dateFilter_usesTimestamp_notReceivedAt
  - Payments with timestamp spanning March 1–11 (no receivedAt field)
  - Filter: startDate=March 1, endDate=March 11
  - Assert: all payments included (existing PaymentsFilterTest covers this)

Test 5: savedPayment_timestampIsNeverNull
  - Save a payment via RoomPaymentRepository
  - Fetch it back
  - Assert: timestamp != null
```

#### Files to Create/Modify (BUG-011 specific)

| File | Change |
|---|---|
| `data/Payment.kt` | `timestamp: String` (non-nullable) |
| `database/Entities.kt` | `PaymentEntity.timestamp: String` (non-nullable); remove `receivedAt` field |
| `database/BankSmsDatabase.kt` | DB version bump; new migration |
| `database/PaymentDao.kt` | Remove `getPaymentsByDateRange()` (or update query) |
| `processor/PaymentProcessor.kt` | `approximateDate()` rewrite; add `smsReceivedAt` param to `processMessage/Full` |
| `repository/PaymentRepository.kt` | Remove `getPaymentsByDateRange()` from interface; remove `receivedAt` from InMemory impl |
| `repository/RoomPaymentRepository.kt` | Remove `receivedAt` from `savePayment()`; remove from `toDomain()` mapper |
| `parser/SmsReceiver.kt` | Extract PDU timestamp; pass to `processMessageFull()` |
| `ui/ApplyRulesActivity.kt` | Pass `SmsWithDate.date` to `processMessage()`; fix `maxOrNull` call |
| `ui/PaymentsActivity.kt` | Fix export date range; update CSV column |
| `ui/PaymentsFilter.kt` | Remove `receivedAt` fallback |
| All tests referencing `receivedAt` | Update field accesses and constructor calls |

---

## Part 2: Feature Implementations

### Feature 1.1: Simple Way to Change Category of a Merchant

**Current state:** In `CategoriesActivity`, each merchant is displayed as an editable `EditText` within a category. To change a merchant's category, the user must manually delete it from one category and add it to another — there's no "move" action.

**Goal:** Add a context menu / button to each merchant field with a "Move to Category" option.

#### Implementation Steps

1. **UI Change** in `CategoriesActivity.CategoryViewHolder.addMerchantField()`:
   - Add a "Move" button (small icon) next to the delete button in `view_dynamic_edit_text_with_delete.xml`
   OR
   - Add long-press / three-dot menu on each merchant chip

2. **"Move to Category" Dialog:**
   ```kotlin
   fun showMoveMerchantDialog(merchant: String, currentCategory: Category) {
       val allCategories = ConfigRepository.getCategories().filter { it.id != currentCategory.id }
       // Show picker dialog
       AlertDialog.Builder(context)
           .setTitle("Move '$merchant' to:")
           .setItems(allCategories.map { it.name }.toTypedArray()) { _, idx ->
               moveMerchantToCategory(merchant, currentCategory, allCategories[idx])
           }.show()
   }
   ```

3. **`moveMerchantToCategory()` function:**
   - Remove merchant from `currentCategory.merchants`
   - Add merchant to `targetCategory.merchants`
   - Call `ConfigRepository.updateCategory()` for both
   - Call `RoomPaymentRepository.updateCategoryForMerchant(merchant, targetCategory.name)` (new method — see BUG-007)
   - Refresh adapter

4. **Tests:**
   - Unit test: `moveMerchant_fromCategoryA_toCategoryB` (same tests as BUG-007 test suite)
   - Appium test: verify "Move" button exists and functional in `CategoryManagementAppiumTest`

---

### Feature 1.2 + 1.3: Optional Merchant Display Name + Regex Matching

These two features extend the merchant model and are implemented together.

#### Data Model Change

Create a new `Merchant` data class:
```kotlin
@Serializable
data class Merchant(
    val pattern: String,             // Exact string or regex for matching payment.merchant
    val displayName: String? = null, // Human-readable name (optional); if null, show pattern
    val isRegex: Boolean = false     // If true, pattern is a regex
)
```

Update `Category`:
```kotlin
data class Category(
    var id: Long? = null,
    var name: String,
    var merchants: MutableList<Merchant>,   // was MutableList<String>
    var enabled: Boolean = true
)
```

#### Database Migration (v8 → v9)

Alter `category_merchants` table:
```sql
-- Add columns displayName (nullable) and isRegex (default 0)
ALTER TABLE category_merchants ADD COLUMN displayName TEXT;
ALTER TABLE category_merchants ADD COLUMN isRegex INTEGER NOT NULL DEFAULT 0;
-- Rename 'name' to 'pattern' (requires table recreation in SQLite)
-- Create new table, copy data, drop old, rename new
```

Full migration:
```sql
CREATE TABLE category_merchants_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    categoryId INTEGER NOT NULL,
    pattern TEXT NOT NULL,
    displayName TEXT,
    isRegex INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE CASCADE ON UPDATE CASCADE
);
INSERT INTO category_merchants_new (id, categoryId, pattern)
    SELECT id, categoryId, name FROM category_merchants;
DROP TABLE category_merchants;
ALTER TABLE category_merchants_new RENAME TO category_merchants;
```

#### Code Changes

1. **`CategoryMerchantEntity`:**
   ```kotlin
   data class CategoryMerchantEntity(
       @PrimaryKey(autoGenerate = true) val id: Long = 0,
       val categoryId: Long,
       val pattern: String,
       val displayName: String? = null,
       val isRegex: Boolean = false
   )
   ```

2. **`Mappers.kt`:** Update `toDomainCategories()` to map to `Merchant` objects.

3. **`PaymentProcessor.assignCategory()`:**
   ```kotlin
   category.merchants.any { merchant ->
       if (merchant.isRegex) {
           Regex(merchant.pattern, setOf(RegexOption.IGNORE_CASE)).containsMatchIn(payment.merchant)
       } else {
           merchant.pattern.equals(payment.merchant, ignoreCase = true)
       }
   }
   ```

4. **`CategoriesActivity.addMerchantField()`:** Add a second `EditText` for `displayName` (shown when expanded or always visible below the pattern field) and a checkbox/toggle for `isRegex`.

5. **`ConfigRepository`:** Update `updateCategory()` to save `Merchant` objects (pattern + displayName + isRegex) to the DB via `CategoryMerchantEntity`.

6. **`default_rules.json` + test fixtures:** Update to use new merchant format: `{"pattern": "FooStore", "displayName": null, "isRegex": false}` (or keep backward-compatible string format by supporting both in `ConfigLoader`).

7. **Tests:**
   - Unit test: `PaymentProcessorTest` — regex merchant matching
   - Unit test: `PaymentProcessorTest` — displayName shown in report (payment.merchant uses displayName when set)
   - DB migration test: verify v8→v9 migration preserves all existing merchants

---

### Feature 2.1: Merchant Search in Payments

**Goal:** Add a merchant search field to `PaymentsActivity` that filters payments by merchant name (either from configured merchants or free-text).

#### Implementation Steps

1. **UI Addition** in `activity_payments.xml`:
   - Add a `SearchView` or `AutoCompleteTextView` below the existing filter row
   - Label: "Search merchant"

2. **State:** Add `var merchantSearchQuery: String? = null` to `PaymentsActivity`.

3. **Filter Logic** in `applyFilter()`:
   ```kotlin
   val matchesMerchant = merchantSearchQuery.isNullOrBlank() ||
       (payment.merchant?.contains(merchantSearchQuery!!, ignoreCase = true) == true)
   ```

4. **Autocomplete:** Populate suggestions from `ConfigRepository.getCategories()` — extract all merchant names/patterns across all categories for autocomplete hints.

5. **Save/Restore:** Include `merchantSearchQuery` in `saveFilterState()` / `savedInstanceState`.

6. **Tests:**
   - Unit test: filter with merchant query shows only matching payments
   - Appium test: verify search field is visible and functional in `PaymentsFilterAppiumTest`

---

### Feature 3.0: Formatted Regex Display in Senders

**Goal:** In `SendersActivity`, show regex patterns with:
- Actual newlines instead of literal `\n`
- Highlighted `⟨preset⟩` tokens (same as in Regex Builder)

#### Implementation Steps

1. **Extract `RegexFormatUtils`** (also needed for BUG-010 and Task 4.4):
   ```kotlin
   object RegexFormatUtils {
       val presets: Map<String, String> = mapOf(...)  // moved from RegexBuilderActivity
       fun regexToTemplate(regex: String): String
       fun templateToRegex(template: String): String
       fun decodeNewlines(pattern: String): String  // "\n" literal → actual '\n'
       fun encodeNewlines(pattern: String): String  // actual '\n' → "\n" literal
       fun applyPlaceholderSpans(editable: Editable, context: Context)
   }
   ```

2. **In `SendersActivity.addRuleField()`:**
   ```kotlin
   // Display formatted version
   val displayPattern = RegexFormatUtils.decodeNewlines(
       RegexFormatUtils.regexToTemplate(RegexFormatUtils.decodePattern(rule.pattern))
   )
   editText.setText(displayPattern)
   // Apply span highlighting after setText
   editText.text?.let { RegexFormatUtils.applyPlaceholderSpans(it, itemView.context) }
   editText.addTextChangedListener {
       it?.let { e -> RegexFormatUtils.applyPlaceholderSpans(e, itemView.context) }
   }
   ```

3. **Make EditText non-editable** in SendersActivity (patterns should be edited in RegexBuilder, not directly — or if editing is allowed, apply proper encode on save).

   Alternative: keep editable, but on save call `RegexFormatUtils.templateToRegex(RegexFormatUtils.encodeNewlines(text))`.

4. **Tests:**
   - Unit test: `decodeNewlines` and `regexToTemplate` produce expected display text
   - Appium test: verify Senders screen shows formatted patterns (not raw `\n` literals)

---

### Feature 4.1: Preserve Sender After Regex Save

**Goal:** After saving a regex in `RegexBuilderActivity`, the sender spinner should stay on the same sender (not reset to position 0).

#### Root Cause

`performSaveRule()` calls `loadSenders()`, which recreates the spinner adapter, resetting selection to 0.

#### Fix

In `loadSenders()`, before recreating the adapter, store the currently selected sender's name. After adapter creation, restore the selection by name:

```kotlin
private fun loadSenders() {
    val previouslySelectedSenderName = if (spinnerSenders.selectedItemPosition > 0) {
        senders.getOrNull(spinnerSenders.selectedItemPosition - 1)?.name
    } else null

    // ... existing loadSenders logic ...

    // After adapter is set:
    if (previouslySelectedSenderName != null) {
        val idx = senders.indexOfFirst { it.name == previouslySelectedSenderName }
        if (idx >= 0) spinnerSenders.setSelection(idx + 1)
    }
}
```

#### Tests

- Appium test: save regex → verify sender spinner still shows same sender

---

### Feature 4.2: Edit Existing Pattern — Separate Window

**Goal:** "Edit Existing Pattern" should open a separate window/activity where patterns are:
- Shown fully (not truncated)
- Formatted with highlights and newlines
- Visually separated from each other

#### Implementation Approach

Create a new **`PatternListActivity`** (or `DialogFragment`):
- Receives: `senderId`, `senderName`, `ruleType`
- Loads all patterns for that sender+ruleType from DB
- Shows in a `RecyclerView` where each item displays the full formatted pattern
- Each item: full-height `TextView` with spans (read-only) + "Load into Builder" and "Delete" buttons
- Tapping "Load into Builder" sends the pattern back to `RegexBuilderActivity` via `setResult()`

#### Implementation Steps

1. Create `PatternListActivity` (new Activity):
   - Layout: `RecyclerView` with items showing full-text patterns using `SpannableString`
   - Each item: `TextView` (formatted, non-editable), `btnLoad` button, `btnDelete` icon
   - Formatting: `RegexFormatUtils.decodeNewlines(RegexFormatUtils.regexToTemplate(pattern))`
   - Highlighting: `RegexFormatUtils.applyPlaceholderSpans(tv.text, context)`

2. In `RegexBuilderActivity`, change the existing patterns `Spinner` to a "Browse Patterns" `Button`:
   ```kotlin
   btnBrowsePatterns.setOnClickListener {
       val intent = Intent(this, PatternListActivity::class.java).apply {
           putExtra(EXTRA_SENDER_ID, senderId)
           putExtra(EXTRA_RULE_TYPE, selectedRuleType.value)
       }
       startActivityForResult(intent, RC_SELECT_PATTERN)
   }
   ```

3. In `onActivityResult`, set the returned pattern text in `etRegexPattern`.

4. Register `PatternListActivity` in `AndroidManifest.xml`.

5. **Tests:**
   - Appium test: navigate to "Browse Patterns" from Regex Builder → verify full pattern text is shown

---

### Feature 4.4: Human-Readable Newlines in Regex Builder Text Box

**Goal:** When a regex pattern is loaded into the Regex Builder's text box, `\n` literals in the stored pattern should be displayed as actual newlines.

#### Implementation Steps

1. Add to `RegexFormatUtils`:
   ```kotlin
   fun decodeNewlines(pattern: String): String = pattern.replace("\\n", "\n")
   fun encodeNewlines(pattern: String): String = pattern.replace("\n", "\\n")
   ```
   Note: be careful not to confuse `\\n` (literal 2-char sequence backslash-n) vs actual newline char `\n`.

2. In `setupExistingPatternsSpinner.onItemSelected`:
   ```kotlin
   etRegexPattern.setText(
       RegexFormatUtils.decodeNewlines(
           regexToTemplate(decodePattern(selectedRule.pattern))
       )
   )
   ```

3. In `saveRegexToSender` (encoding phase):
   ```kotlin
   val regexPattern = encodePattern(
       templateToRegex(
           etRegexPattern.text.toString()
               .trim()
               .let { RegexFormatUtils.encodeNewlines(it) }   // convert \n → \\n before stripping
       )
   )
   ```
   Actually the current code does `.replace("\n", "").replace("\r", "")` which strips newlines. Instead, it should encode them as `\n` regex sequences.

   Better approach: The `saveRegexToSender` stripping of newlines was correct only if the regex doesn't span lines. Now that multiline is supported with actual `\n` characters, they should be preserved: `it.replace("\n", "\\n").replace("\r", "")` before encoding.

4. **Tests:**
   - Unit test: `decodeNewlines("Line1\\nLine2") == "Line1\nLine2"`
   - Unit test: round-trip encode→decode preserves pattern

---

## Part 2.5: Dead Code Removal (Maintenance)

**Identified by static scan on 2026-03-13.**

### High-priority (clear dead code)

| Symbol | File | Reason |
|--------|------|--------|
| `SenderRuleEntity` class + its ConfigDao methods (`insertRule/updateRule/deleteRule` for `SenderRuleEntity`) | `database/Entities.kt`, `database/ConfigDao.kt` | Legacy entity replaced by `RuleEntity`; no callers |
| `PaymentProcessor.getPaymentFromMessage()` | `processor/PaymentProcessor.kt:164` | Never called; `processMessageFull()` used instead |
| `SmsAddressMatcher.matchesAny(Collection<String>)` overload | `util/SmsAddressMatcher.kt:44` | Only `Set<String>` overload called in production; this is test-only |

### DAO scaffolding (lower priority — defer until after Phase 9.3)

Large sets of unused query methods in `RuleDao`, `IncomeDao`, `IgnoreRuleDao`, and `ConfigDao`. Full list in TODO.md §9.M1. Defer until after merchant model changes (tasks 1.2/1.3) in case some methods become needed.

---

## Part 3: Future Items (Add to TODO.md)

These tasks are explicitly marked "in the future" in PROMPT.md and should NOT be implemented now.

### 2.5: Spending Report Diagrams
- Bar chart / pie chart visualization of spending by category
- Candidate libraries: MPAndroidChart, Vico
- Plan: add after basic report is stable; requires chart library integration

### 2.6: Central Bank APIs for Currency Exchange Rates
- Integrate APIs from central banks (e.g., National Bank of Georgia, ECB) to retrieve exchange rates
- Use rates to normalize multi-currency payments to a base currency
- Plan: needs abstraction layer per bank, fallback to static rates when offline

### 4.5: Regex-Free Text Box in Regex Builder
- Fully WYSIWYG editor: user sees only words and colored preset chips
- Regex transformations are invisible (internal state vs. display state)
- Plan: requires custom `EditText` subclass with token model; significant UI work

---

## Implementation Order

0. **Phase 0 — BUG-011: Non-nullable timestamp** (do this before other features; removes `receivedAt` used in several places)
   - Thread `smsReceivedAt` through `processMessage/Full()`
   - Rewrite `approximateDate()` to use id-order proximity + device-time fallback
   - DB migration: make `timestamp NOT NULL`, drop `receivedAt` column
   - Remove `receivedAt` from `Payment`, `PaymentEntity`, `RoomPaymentRepository`, `PaymentsFilter`
   - Remove `getPaymentsByDateRange()` DAO method from interface
   - Fix downstream callers: `ApplyRulesActivity`, `PaymentsActivity`
   - Update all tests

1. **Phase 1 — Extract `RegexFormatUtils`** (prerequisite for BUG-010, Task 3.0, Task 4.4)
   - Create `app/src/main/java/com/example/banksmstracker/util/RegexFormatUtils.kt`
   - Move presets, `templateToRegex`, `regexToTemplate`, `applyPlaceholderSpans` from `RegexBuilderActivity`
   - Write unit tests: `RegexFormatUtilsTest`

2. **Phase 2 — Bugs (BUG-007 through BUG-010)**
   - Write failing tests first for each bug
   - Fix each bug
   - Verify tests pass
   - Create `bugs/BUG-007` through `bugs/BUG-010` files

3. **Phase 3 — Quick Wins (4.1, 4.4, 3.0)**
   - Task 4.1: Preserve sender selection after save (2 lines of code)
   - Task 4.4: Decode newlines when loading pattern (uses RegexFormatUtils)
   - Task 3.0: Apply formatting to Senders screen (uses RegexFormatUtils)

4. **Phase 4 — Merchant Features (1.2 + 1.3 together, then 1.1)**
   - DB migration v8→v9
   - `Merchant` data class
   - Update `CategoryMerchantEntity`, Mappers, ConfigRepository, PaymentProcessor
   - Update `CategoriesActivity` UI (two fields per merchant: pattern + displayName, isRegex toggle)
   - Task 1.1 (Move button) reuses the `updateCategoryForMerchant` method from BUG-007 fix

5. **Phase 5 — New Features (2.1, 4.2)**
   - Task 2.1: Merchant search in Payments
   - Task 4.2: `PatternListActivity` (most complex, requires new Activity + manifest registration)

6. **Phase 6 — Testing & Cleanup**
   - Run full Appium smoke tests (`make test-smoke`)
   - Update TODO.md with all completed items
   - Add future items to TODO.md

---

## Files to Create/Modify

### New Files
- `bugs/BUG-007-category-reassignment.md`
- `bugs/BUG-008-end-date-filter.md`
- `bugs/BUG-009-start-date-filter.md`
- `bugs/BUG-010-merchant-not-highlighted.md`
- `app/src/main/java/com/example/banksmstracker/util/RegexFormatUtils.kt`
- `app/src/main/java/com/example/banksmstracker/ui/PatternListActivity.kt`
- `app/src/test/java/com/example/banksmstracker/util/RegexFormatUtilsTest.kt`

### Modified Files
- `app/src/main/java/com/example/banksmstracker/data/Category.kt` — add Merchant data class
- `app/src/main/java/com/example/banksmstracker/database/Entities.kt` — update CategoryMerchantEntity
- `app/src/main/java/com/example/banksmstracker/database/Mappers.kt` — update toDomainCategories
- `app/src/main/java/com/example/banksmstracker/database/BankSmsDatabase.kt` — version 8→9, add migration
- `app/src/main/java/com/example/banksmstracker/processor/PaymentProcessor.kt` — regex merchant matching
- `app/src/main/java/com/example/banksmstracker/repository/PaymentRepository.kt` — new method
- `app/src/main/java/com/example/banksmstracker/repository/RoomPaymentRepository.kt` — implement method
- `app/src/main/java/com/example/banksmstracker/ui/PaymentsActivity.kt` — bug fixes, merchant search
- `app/src/main/java/com/example/banksmstracker/ui/CategoriesActivity.kt` — Move button, new merchant fields
- `app/src/main/java/com/example/banksmstracker/ui/SendersActivity.kt` — formatted pattern display
- `app/src/main/java/com/example/banksmstracker/ui/RegexBuilderActivity.kt` — use RegexFormatUtils, fix 4.1/4.4
- `app/src/main/AndroidManifest.xml` — register PatternListActivity
- `app/src/main/assets/default_rules.json` — update merchant format
- `app/src/test/resources/default_rules.json` — mirror update
- `app/src/main/res/layout/activity_payments.xml` — add merchant search field
- `app/src/main/res/layout/item_category.xml` — update merchant fields (displayName, isRegex)
- `app/src/main/res/values/strings.xml` + `values-ru/strings.xml` — new strings for all UI additions
- `TODO.md` — add new tasks, add future items

---

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| DB migration breaks existing data | Test migration on device with existing data; create instrumented migration test |
| `Merchant` model change breaks existing JSON import/export | Support both old (string) and new (object) merchant format in `ConfigLoader`; add backward-compat deserialization |
| `RegexFormatUtils` extraction breaks `RegexBuilderActivity` | Move carefully, keep all tests green at each step |
| `PatternListActivity` complexity exceeds plan | Can simplify to a `BottomSheetDialogFragment` instead of full Activity |
