# Code Review Findings

> Generated: 2026-03-12. Covers all source files under `app/src/main/java/`.

---

## Summary

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Race Conditions | 1 | 1 | 2 | 0 |
| Performance | 0 | 1 | 4 | 1 |
| Security | 1 | 3 | 2 | 0 |
| Design Discrepancies | 0 | 1 | 3 | 2 |
| Legal / Compliance | 0 | 2 | 3 | 1 |

---

## 1. Race Conditions

### RC-1 · CRITICAL — `ConfigRepository.load()` TOCTOU

**File:** `repository/ConfigRepository.kt:51-63`

`if (_config != null) return` is checked outside the mutex. Two threads (e.g., Activity `onCreate` + `SmsReceiver`) can both see `null`, both enter the initialization block, both call `seedFromAssets()` and `refreshConfigInternal()`. Result: double seeding of default config, inconsistent in-memory state.

**Fix:** wrap the entire guard + body in `synchronized(this)` or, since this is a suspend call chain, use the existing `configMutex` from the top.

---

### RC-2 · HIGH — `getPaymentProcessor()` unsynchronized lazy init

**File:** `repository/ConfigRepository.kt:163-168`

```kotlin
paymentProcessor ?: PaymentProcessor(...).also { paymentProcessor = it }
```

Two threads can both evaluate `paymentProcessor` as null, create two instances, and one is lost. The stale reference can hold outdated `senders`/`categories` snapshots after a config refresh.

**Fix:** use `synchronized(this) { paymentProcessor ?: ... }` or move construction inside `configMutex`.

---

### RC-3 · MEDIUM — `PaymentAdapter.submitList()` / `notifyDataSetChanged()` on filter changes

**File:** `ui/PaymentsActivity.kt:614-617`

`submitList()` replaces the backing list and immediately calls `notifyDataSetChanged()`. If the RecyclerView is in the middle of a layout pass, this can cause `IndexOutOfBoundsException` or skipped frames. No `DiffUtil` is used.

**Fix:** replace `PaymentAdapter` with `ListAdapter<Payment, …>(PaymentDiffCallback())`.

---

### RC-4 · MEDIUM — `SmsReceiver.initializePaymentProcessor()` not thread-safe

**File:** `parser/SmsReceiver.kt:39-45`

`!::paymentProcessor.isInitialized` check is not atomic. Android SMS_RECEIVED broadcasts are serial for a single receiver instance, so in practice this is safe. However, the pattern is fragile — if the receiver is ever invoked from a test harness concurrently, it breaks.

---

## 2. Performance Bottlenecks

### PF-1 · HIGH — `notifyDataSetChanged()` instead of DiffUtil

**File:** `ui/PaymentsActivity.kt:616`

Every call to `applyFilter()` (on every spinner selection, date change, or data load) calls `notifyDataSetChanged()`. With 500+ payments this causes a full RecyclerView rebind: all visible ViewHolders are unbound, rebound, and re-laid-out.

**Fix:** `ListAdapter` + `DiffUtil.ItemCallback<Payment>`.

---

### PF-2 · MEDIUM — N+1 DB writes in `recategorizeAllPayments()`

**File:** `repository/ConfigRepository.kt:551-570`

Loads all payments (1 query), then calls `updatePaymentCategory()` individually for each changed payment. For 1 000 changed payments this is 1 001 DB round-trips instead of 1.

**Fix:** group payments by new category name, batch-update with a single `UPDATE payments SET categoryName = ? WHERE id IN (...)` per category using Room's `@Query`.

---

### PF-3 · MEDIUM — `approximateDate()` full table scan per untimestamped payment

**File:** `processor/PaymentProcessor.kt:226-239`

Calls `paymentRepository.getAllPayments()` for every payment that has no timestamp. When processing 100 historical SMS without timestamps this executes 100 full table scans.

**Fix:** call `getAllPayments()` once before the processing loop and pass the result as a parameter.

---

### PF-4 · MEDIUM — Redundant config refreshes

**File:** `repository/ConfigRepository.kt:66-68, 71-72`

Both `getCategories()` and `getSenders()` call `refreshConfigInternal()`, which re-queries all senders, addresses, rules, categories, and merchants from the DB on every invocation — even when nothing changed.

**Fix:** add a dirty flag or version counter; only refresh when the DB has been mutated since the last load.

---

### PF-5 · MEDIUM — `CheckSendersActivity` queries all SMS with no limit

**File:** `ui/CheckSendersActivity.kt:99-115`

`getSmsSenders()` queries `content://sms` with no `LIMIT`. On a heavily used device with tens of thousands of SMS this can be very slow and allocate large result sets.

**Fix:** add `LIMIT 10000` or query only `DISTINCT address` via a GROUP BY approach.

---

### PF-6 · LOW — Regex compiled per-call via property access

**File:** `data/Rule.kt` (via `PaymentProcessor.kt:77-78`)

`rule.regexPattern` is a `Regex` computed property. If it creates a new `Regex` instance on every access, pattern compilation occurs on every `processMessage()` call. Rule objects are recreated on every `refreshConfigInternal()`.

**Fix:** cache the compiled `Regex` on the `Rule` data class as a `by lazy` property, or use a `HashMap<Long, Regex>` cache in `PaymentProcessor`.

---

## 3. Security Issues

### SEC-1 · CRITICAL — SMS body and payment data logged in plaintext

**File:** `parser/SmsReceiver.kt:119-143`

```kotlin
Log.d(TAG, "SMS from $sender processed as payment.\nMessage: $body\nParsed: ${result.payment}")
```

Full SMS body (containing amounts, card suffixes, merchant names, balances) is written to Android Logcat. Any app with `READ_LOGS` permission (e.g., adb, some system apps, some OEM tools) can read this data.

**Fix:** in release builds, log only the result type, never the SMS body or parsed fields. Guard with `if (BuildConfig.DEBUG)`.

---

### SEC-2 · HIGH — Database is **not** encrypted (documentation error)

**File:** `database/BankSmsDatabase.kt:186-202`, `docs/DESIGN.md:10.2` (now fixed)

`DESIGN.md` previously claimed "All data stored locally in **encrypted** Room database." The code uses standard `Room.databaseBuilder` without SQLCipher or `EncryptedFile`. The database is stored unencrypted at `/data/data/<package>/databases/bank_sms_tracker.db`, readable by root and ADB on unencrypted devices.

**Fix (long-term):** integrate SQLCipher (`net.zetetic:android-database-sqlcipher`) and store the key in Android Keystore.

---

### SEC-3 · HIGH — `allowBackup=true` with no database exclusion

**File:** `AndroidManifest.xml:11`, `res/xml/backup_rules.xml`

`backup_rules.xml` has all rules commented out, meaning the Room database (containing all payment history and SMS hashes) is included in Android Auto Backup to Google servers. Users are not informed.

**Fix:**
```xml
<!-- backup_rules.xml -->
<full-backup-content>
    <exclude domain="database" path="bank_sms_tracker.db" />
    <exclude domain="sharedpref" path="app_terms.xml" />
</full-backup-content>
```

---

### SEC-4 · HIGH — Test-payload injection via exported receiver (debug concern)

**File:** `parser/SmsReceiver.kt:52-70`, `AndroidManifest.xml:89-96`

`SmsReceiver` reads `EXTRA_TEST_SENDER` and `EXTRA_TEST_BODY` from any broadcast that satisfies `android.permission.BROADCAST_SMS`. This test pathway is active in all builds. A privileged app (system or OEM) holding `BROADCAST_SMS` could inject synthetic payment records.

**Fix:**
```kotlin
if (!BuildConfig.DEBUG) return  // guard at top of test-extras block
```

---

### SEC-5 · MEDIUM — Date filters in SMS queries use string interpolation

**Files:** `ui/SmsExportActivity.kt:235-244`, `ui/ApplyRulesActivity.kt:392-399`

Date/time values (`Long` millisecond timestamps) are interpolated directly into the selection string:
```kotlin
append("date >= $it")
```
These specific values cannot cause SQL injection (they are `Long`s), but the pattern is unsafe and will fail review by a security auditor. Future changes that pass user-supplied strings this way would be vulnerable.

**Fix:** pass all values as selection arguments (`selectionArgs`).

---

### SEC-6 · MEDIUM — Export files persist in `cacheDir` after sharing

**Files:** `repository/ConfigRepository.kt:242`, `ui/PaymentsActivity.kt:347`, `ui/BugReportActivity.kt:295`, `ui/SmsExportActivity.kt:352`

Exported JSON/CSV files containing financial data are written to `cacheDir` and never deleted. `cacheDir` is not backed up, but it is readable by the app and visible to ADB. Files accumulate across exports.

**Fix:** delete the file after the share intent completes (register `ActivityResultCallback` on the share chooser, or use `deleteOnExit` + finalize hook).

---

## 4. Design Discrepancies (now fixed in docs)

### DD-1 · HIGH — Docs said "encrypted Room database" — **FIXED**

`DESIGN.md §10.2` now correctly states the database is unencrypted. Encryption is listed as a planned feature.

---

### DD-2 · MEDIUM — Test pyramid counts were stale — **FIXED**

`DESIGN.md §8.3` and `README.md` now reflect the actual counts (116+ Appium tests across 10 classes, 77 instrumented, 195+ unit).

---

### DD-3 · MEDIUM — File structure was incomplete — **FIXED**

`DESIGN.md §12` now lists all activities (`SettingsActivity`, `SmsExportActivity`, `IgnoreRulesActivity`), all DAOs, and all domain model files.

---

### DD-4 · MEDIUM — `docker-compose.appium.yml` no longer exists — **FIXED**

References in `TESTING.md` and `DESIGN.md §12` updated to `docker-compose.yml` (unified file).

---

### DD-5 · LOW — `processMessage()` vs `processMessageFull()` API confusion

**File:** `processor/PaymentProcessor.kt:171-195`

`processMessage()` throws `UnparsedMessageException("Income message - use processMessageFull()")` for income results. This is a leaky API — callers of `processMessage()` must know to use the other method for income. `ApplyRulesActivity` still calls `processMessage()`, silently losing income SMS.

---

### DD-6 · LOW — `approximateDate()` uses wrong reference point

**File:** `processor/PaymentProcessor.kt:226-239`

Finds the neighbor payment closest to *now* (`System.currentTimeMillis()`), not closest to when the current message was received. For historical SMS processing, all untimestamped payments get the timestamp of the most recent payment in the database rather than their contextual neighbor.

---

## 5. Legal / Compliance

### LC-1 · HIGH — GDPR: no data retention or erasure feature (EU)

The app accumulates payment records indefinitely. GDPR Article 5(1)(e) requires "storage limitation" — data should be kept no longer than necessary. There is no:
- User-facing delete-payment UI
- Automatic retention cutoff setting
- GDPR Subject Access Request (export all data) feature

**Recommended additions:** Settings → Data → "Delete payments older than X months"; Settings → Data → "Export all my data".

---

### LC-2 · HIGH — Russian FZ-152 / post-Soviet data localization (backup concern)

Russian Federal Law 152-FZ requires personal data of Russian citizens to be stored on servers within Russia. With `allowBackup=true` (SEC-3 above), payment and config data may flow to Google's backup infrastructure outside Russia. Similar laws exist in Kazakhstan (Law on Personal Data), Belarus, and Uzbekistan.

**Mitigation:** exclude the database from backup (SEC-3 fix). Document that SMS data stays on-device.

---

### LC-3 · MEDIUM — PSD2 / financial aggregator classification (EU)

The app aggregates financial transaction data from SMS. In the EU, apps aggregating bank account data may be classified as Account Information Service Providers (AISPs) under PSD2, requiring registration with a national financial authority. Using SMS parsing as the data source (rather than direct API access) is a grey area — some regulators may still classify this as aggregation.

**Note for users:** not a code issue, but should be disclosed in the privacy notice if targeting EU users.

---

### LC-4 · MEDIUM — India DPDP Act 2023 / South Korea PIPA

Both laws require explicit, informed consent before processing financial data and the right to withdraw consent. The existing terms dialog provides consent, but:
- There is no consent withdrawal mechanism
- The privacy notice does not specify a data controller contact

---

### LC-5 · MEDIUM — Bug report shares device hardware fingerprint

**File:** `ui/BugReportActivity.kt:117-124`

The bug report includes `Build.MANUFACTURER`, `Build.MODEL`, `Build.PRODUCT`, `Build.HARDWARE`, and Android version. Under GDPR, device fingerprint data is personal data if it can uniquely identify a user. The privacy notice should explicitly mention this.

---

### LC-6 · LOW — PCI-DSS: card last-4 digits stored unencrypted

Storing the last 4 digits of a card number is generally outside PCI-DSS scope (not considered a sensitive authentication element). However, combined with merchant name, amount, and timestamp, it can constitute a rich transaction fingerprint. This is worth noting in the privacy notice.

---

## 6. Other Caveats

### OC-1 — `exportSchema = false` prevents migration validation

**File:** `database/BankSmsDatabase.kt:23`

`exportSchema = false` means Room does not write schema JSON files. This disables Room's built-in migration test utilities (`MigrationTestHelper`) and makes it impossible to verify that migration scripts match the actual entity definitions at compile time. Silent schema drift can occur.

**Fix:** set `exportSchema = true` and add the schema output directory to source control. Add `MigrationTestHelper` instrumented tests.

---

### OC-2 — `runBlocking` in `ConfigRepository.load()`

**File:** `repository/ConfigRepository.kt:58-63`

`load()` calls `runBlocking { ... }` which blocks the calling thread for DB I/O. If called from the main thread (which it is — indirectly via `BaseActivity` or `BankSmsTrackerApp.onCreate()`), this produces strict mode violations and potential ANR on slow devices.

**Fix:** make `load()` a `suspend` function, or move initialization to a background coroutine launched from `Application.onCreate()`.

---

### OC-3 — ReDoS risk from user-supplied regex

**File:** `ui/RegexBuilderActivity.kt`, `repository/ConfigRepository.kt:138`

User-supplied regex patterns are compiled and run against SMS bodies inside the broadcast receiver's async window (~10 s). A pathological pattern (e.g., `(a+)+$`) against a crafted SMS body can cause catastrophic backtracking, blocking the coroutine for seconds and potentially triggering an ANR.

**Mitigation:** validate patterns against a maximum complexity heuristic, or run `pattern.find()` with a timeout using `withTimeout(500)`.

---

### OC-4 — `SenderWithDetails` joins on legacy `rules` table only (post-migration)

**File:** `database/Entities.kt:114-128`

`SenderWithDetails` uses `@Relation(entity = RuleEntity::class)` which maps to the `rules` table. After migration 7→8, `sender_rules` still exists and is populated in `ConfigDao`. New rules written via `RuleDao.insertRule()` go to `rules`; old code paths writing via `ConfigDao.insertRule()` (deprecated SenderRuleEntity) go to `sender_rules`. Both tables are kept "for backward compatibility" but there is no clear delineation of ownership.

**Fix:** remove all writes to `sender_rules` (legacy) and drop the table in migration 8→9.
