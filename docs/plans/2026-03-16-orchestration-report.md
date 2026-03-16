# Orchestration Report — 2026-03-16

**Branch:** feature/create-pov
**Orchestrator:** Claude Sonnet 4.6
**Mode:** Agent Orchestration (one subagent per task)

---

## Tasks Implemented

### BUG-011: Replace `receivedAt` with Non-Nullable `timestamp`

**Commit:** `(first commit after 5931446)`
**Subagent:** general-purpose

**What was done:**
- `Payment.kt`: `timestamp: String` (was `String?`), `receivedAt: Long?` field removed entirely
- `Entities.kt`: Same changes to `PaymentEntity`
- `BankSmsDatabase.kt`: DB version bumped **8 → 9**. `MIGRATION_8_9` fills existing NULL timestamps using `strftime` on `receivedAt`, then recreates `payments` table without `receivedAt` column
- `PaymentDao.kt`: `getPaymentsByDateRange()` removed (Option A from plan — UI does in-memory filtering)
- `PaymentRepository.kt` / `RoomPaymentRepository.kt`: interface and `InMemoryPaymentRepository` cleaned; `receivedAt` assignments and `savePayment()` no longer set it
- `PaymentProcessor.kt`: `smsReceivedAt: Long` parameter added to `processMessage()` and `processMessageFull()`; `approximateDate()` rewritten to use insertion-id order proximity + device-clock fallback
- `SmsReceiver.kt`: PDU `timestampMillis` extracted and forwarded to `processMessageFull()`
- `ApplyRulesActivity.kt`: `getSmsMessages()` now returns `SmsWithDate`, passes the SMS `date` column to processor; `maxOrNull()` call uses `parseTransactionTimestamp(it.timestamp)` instead of `it.receivedAt`
- `PaymentsActivity.kt`: CSV column `ReceivedAt` removed; export date range uses `parseTransactionTimestamp`
- `PaymentsFilter.kt`: `parseTransactionTimestamp(String)` now non-nullable; `receivedAt` fallback removed
- String resources: `csv_header_payments` updated in both EN and RU
- **18 test files updated**: all `receivedAt` references removed, `timestamp = null` → `timestamp = ""`, stale date-range tests removed

**Issues:** None reported. All unit tests passed.

---

### Features 1.2 + 1.3: Merchant Data Class + Regex Matching

**Commit:** `706d6e6`
**Subagent:** general-purpose

**What was done:**

**Feature 1.2 — Optional display name:**
- New `Merchant` data class in `data/Category.kt`:
  `data class Merchant(val pattern: String, val displayName: String? = null, val isRegex: Boolean = false)`
- `Category.merchants` changed from `MutableList<String>` to `MutableList<Merchant>`
- `MerchantSerializer`: custom `@Serializable` adapter that accepts both legacy JSON string format (`"FooStore"`) and new object format (`{"pattern":"FooStore","displayName":null,"isRegex":false}`) for backward compatibility
- All UI in `CategoriesActivity` updated to display `merchant.displayName ?: merchant.pattern`

**Feature 1.3 — Regex support:**
- `PaymentProcessor.assignCategory()` and `ConfigRepository.recategorizeAllPayments()` now check `m.isRegex`: if true, uses `Regex(m.pattern, IGNORE_CASE).containsMatchIn(merchant)` for matching; otherwise case-insensitive string equality
- `ConfigLoader.validate()` duplicate-merchant check skips regex merchants

**DB Migration: v9 → v10**
`category_merchants` table recreated: `name` column renamed to `pattern`, added `displayName TEXT` and `isRegex INTEGER NOT NULL DEFAULT 0`.

**Files changed:** 9 main source files, 8 unit test files, 11 instrumented test files, 1 new test file (`MerchantTest.kt`), `TODO.md` — 29 files total.

**Issues:** None. All checks passed: `compileDebugKotlin`, `compileDebugAndroidTestKotlin`, `testDebugUnitTest`, `ktlintCheck`.

---

### Feature 1.1: Move Merchant Between Categories

**Commit:** `(after 706d6e6)`
**Subagent:** general-purpose

**What was done:**

**UI:**
- `view_dynamic_edit_text_with_delete.xml`: New `btnMove` `ImageButton` (using `ic_menu_send` icon) inserted between the `EditText` and the existing `btnDelete` in each merchant row

**Logic in `CategoriesActivity.kt`:**
- `CategoryCallbacks` interface: added `onMoveMerchantRequested(merchant: Merchant, currentCategory: Category)`
- `addMerchantField()`: wired `btnMove.setOnClickListener` to call the callback
- `onMoveMerchantRequested()`: fetches all other categories async, shows toast if none, otherwise shows dialog
- `showMoveMerchantDialog()`: `AlertDialog` listing other category names; on selection calls `moveMerchantToCategory()`
- `moveMerchantToCategory()`: removes merchant from source, adds to target, calls `ConfigRepository.updateCategory()` for both, calls `paymentRepository.updateCategoryForMerchant(merchant.pattern, targetCategory.name)`, shows toast, reloads list

**Strings added:**
- EN + RU: `move_to_category`, `move_merchant_dialog_title`, `merchant_moved`, `no_other_categories`

**New test file:** `MoveMerchantToCategoryTest.kt` — 6 unit tests verifying: removes from source, adds to target, preserves `displayName`, doesn't affect other merchants, updates payment `categoryId` via `updateCategoryForMerchant()` (including case-insensitive matching).

**Issues:** None. All 6 tests pass; ktlint clean.

---

### Feature 4.2: PatternListActivity (Browse Patterns)

**Commit:** `(after feature 1.1)`
**Subagent:** general-purpose

**What was done:**

**New files created:**
- `PatternListActivity.kt`: Receives `EXTRA_SENDER_ID (Long)`, `EXTRA_SENDER_NAME (String)`, `EXTRA_RULE_TYPE (String)`. Queries `RuleDao.getRulesBySenderAndType()`, displays in `RecyclerView`. Each item shows fully-formatted pattern via `decodeNewlines(regexToTemplate(decodePattern(pattern)))` with `applyPlaceholderSpans()` highlights. "Load into Builder" returns pattern via `setResult(RESULT_OK, intent.putExtra(EXTRA_SELECTED_PATTERN, rawPattern))`. "Delete" shows confirmation then removes rule.
- `activity_pattern_list.xml`: Title `TextView`, empty-state `TextView`, `RecyclerView`
- `item_pattern.xml`: Monospace `TextView` for formatted pattern + "Load into Builder" + "Delete" buttons

**Modified files:**
- `activity_regex_builder.xml`: `spinnerExistingPatterns` removed, replaced with `btnBrowsePatterns (Button)`
- `RegexBuilderActivity.kt`: Removed `spinnerExistingPatterns`, `refreshExistingPatterns()`, `setupExistingPatternsSpinner()`, `truncatePattern()`. Added `btnBrowsePatterns`, `openPatternList()` (launches `PatternListActivity` via `startActivityForResult(RC_SELECT_PATTERN)`), `onActivityResult()` (receives raw pattern, loads with `decodeNewlines(regexToTemplate(...))`)
- `AndroidManifest.xml`: `PatternListActivity` registered
- `strings.xml` + `strings-ru.xml`: 8 new strings added
- `TODO.md`: Task 4.2 marked complete

**Intent extras (constants on `PatternListActivity`):**
- `EXTRA_SENDER_ID` — Long
- `EXTRA_SENDER_NAME` — String (displayed in title)
- `EXTRA_RULE_TYPE` — String (e.g. "payment", "ignore", "income")
- `EXTRA_SELECTED_PATTERN` — String (raw stored pattern returned to caller)

**Issues fixed during implementation:**
- ktlint rejected trailing comma in single-parameter constructor — collapsed to single-line
- `bindingAdapterPosition` unresolved (newer RecyclerView API) — replaced with `holder.adapterPosition`

---

## DB Version History (this session)

| Version | Migration | Change |
|---------|-----------|--------|
| 8 | (pre-existing) | baseline |
| 9 | MIGRATION_8_9 (BUG-011) | `payments` table: drop `receivedAt`, make `timestamp NOT NULL` |
| 10 | MIGRATION_9_10 (1.2+1.3) | `category_merchants` table: rename `name`→`pattern`, add `displayName`, `isRegex` |

---

## Summary

All 4 remaining planned tasks implemented and committed on `feature/create-pov`:

| Task | Status | Commit |
|------|--------|--------|
| BUG-011: Non-nullable timestamp | ✅ Done | yes |
| 1.2+1.3: Merchant data class + regex | ✅ Done | 706d6e6 |
| 1.1: Move merchant to category | ✅ Done | yes |
| 4.2: PatternListActivity | ✅ Done | yes |

No tasks failed to implement. All unit tests passed before each commit.
