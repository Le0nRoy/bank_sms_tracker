# Post-Fix Code Review — BankSMSTracker
**Date:** 2026-03-17
**Reviewer:** Claude Sonnet 4.6 (automated)
**Branch:** `feature/create-pov`
**Scope:** `app/src/main/java/` and `app/src/main/res/`

---

## 1. Previously Fixed Issues — Verification

### RC-1 — ConfigRepository.load() TOCTOU
**Status: FIXED (correct)**

`ConfigRepository.load()` (line 61–74) now wraps the entire `_config != null` guard and all initialization inside `runBlocking(Dispatchers.IO) { configMutex.withLock { … } }`. The check and the write are atomic with respect to the mutex, eliminating the TOCTOU window.

---

### RC-2 — getPaymentProcessor() synchronization
**Status: FIXED (correct)**

`getPaymentProcessor()` (lines 188–195) uses `synchronized(this)`. The double-checked locking pattern (`paymentProcessor ?: …`) is safe here because `paymentProcessor` is declared `@Volatile`.

Minor note: `refreshConfigInternal()` (line 481) also reassigns `paymentProcessor` inside `configMutex.withLock` without holding the `synchronized(this)` lock. This means a concurrent `getPaymentProcessor()` call could observe a stale processor if `refreshConfigInternal()` races with `getPaymentProcessor()`. The risk is low in practice (updates are infrequent), but the two-lock design is inconsistent.

---

### RC-3 / PF-1 — PaymentAdapter extends ListAdapter with DiffUtil
**Status: FIXED (correct)**

`PaymentsActivity.kt` (lines 74–80, 686) defines `PaymentDiffCallback` and `PaymentAdapter extends ListAdapter<Payment, …>(PaymentDiffCallback())`. `submitList()` is called throughout instead of manual `notifyDataSetChanged()`. The fix is complete.

---

### SEC-1 — SmsReceiver SMS body / payment logging guarded with BuildConfig.DEBUG
**Status: FIXED (correct)**

All log statements in `SmsReceiver.handleMessage()` (lines 131–163) that include `body` or `payment` content are wrapped with `if (BuildConfig.DEBUG) { … }`.

**New issue found (see NEW-1):** `SmsProcessingService` has unguarded production logs that include `body` and `sender` content.

---

### SEC-3 — backup_rules.xml excludes database and app_terms.xml
**Status: FIXED (correct)**

`backup_rules.xml` excludes `domain="database" path="bank_sms_tracker.db"` and `domain="sharedpref" path="app_terms.xml"`. Both exclusions are present.

**New issue found (see NEW-2):** `data_extraction_rules.xml` (used on API 31+) still contains only the boilerplate `<!-- TODO -->` comment with no actual exclusion rules. The `backup_rules.xml` fix only applies to devices below API 31.

---

### SEC-4 — SmsReceiver test extras guard with `if (!BuildConfig.DEBUG) return`
**Status: FIXED (correct)**

`SmsReceiver.onReceive()` (line 65) now has `if (!BuildConfig.DEBUG) return` immediately after detecting that test extras are set, before processing any test-injected message.

---

### SEC-5 — SmsExportActivity / ApplyRulesActivity date Long uses `?` placeholders + selectionArgs
**Status: FIXED (correct)**

`SmsExportActivity.loadSmsMessages()` (lines 234–253) builds selection strings with `?` placeholders and passes a `selectionArgs` array. `ApplyRulesActivity.getSmsMessages()` (lines 423–446) does the same — dates are appended to `allSelectionArgs`. No string concatenation of untrusted values into the query string.

---

### SEC-6 — Export files call deleteOnExit() or use ActivityResultLauncher cleanup
**Status: PARTIALLY FIXED**

- `PaymentsActivity.exportToCsv()` (line 363): calls `file.deleteOnExit()`. Correct.
- `SmsExportActivity.exportToJson()` (line 357) and `exportToCsv()` (line 390): both call `file.deleteOnExit()`. Correct.
- `BugReportActivity.generatePaymentsFile()` (lines 301–307): does **NOT** call `deleteOnExit()` on the created `payments_export.json` file. However, `BugReportActivity` uses `shareChooserLauncher` (an `ActivityResultLauncher`) that deletes `pendingExportFile` on return (lines 47–50). The cleanup path works, but only when the chooser returns normally. If the user force-kills the app before the chooser returns, the file is not cleaned up. This is a pre-existing risk but the `ActivityResultLauncher` path is an acceptable mitigation.
- `ConfigRepository.shareConfigFile()` (lines 266–277): creates a file in `cacheDir` with no `deleteOnExit()` and no cleanup callback. The file accumulates on every share. Severity: LOW (cacheDir is cleared by the OS under storage pressure, but this is unbounded growth in normal use).

---

### LC-5 — BugReportActivity: Build.MANUFACTURER/MODEL/PRODUCT/HARDWARE removed
**Status: FIXED (correct)**

`BugReportActivity.buildReport()` (lines 117–128) now only appends `BuildConfig.VERSION_NAME`, `BuildConfig.VERSION_CODE`, `BuildConfig.BUILD_TYPE`, and `Build.VERSION.SDK_INT`. The hardware fingerprint fields (`MANUFACTURER`, `MODEL`, `PRODUCT`, `HARDWARE`) are absent.

---

### PF-2 — recategorizeAllPayments() batches by merchant
**Status: FIXED (correct)**

`ConfigRepository.recategorizeAllPayments()` (lines 597–646) groups payments by `merchant.lowercase()`, resolves the category once per distinct merchant, and issues one `updateCategoryForMerchant()` call per merchant that needs updating. DB round-trips are O(distinct merchants needing recategorization) instead of O(payments).

---

### PF-3 — approximateDate() accepts existingPayments parameter
**Status: FIXED (correct)**

`PaymentProcessor.approximateDate()` (lines 263–289) accepts `existingPayments: List<Payment>? = null` and uses it when provided, falling back to `paymentRepository.getAllPayments()` only when null. `ApplyRulesActivity.applyRules()` (lines 258–280) pre-fetches `existingPayments` once and passes it to `processMessageFull()`, which forwards it to `approximateDate()`.

---

### PF-4 — getCategories() / getSenders() use configDirty flag
**Status: FIXED (correct)**

`getCategories()` (line 79) and `getSenders()` (line 85) both check `if (configDirty) refreshConfigInternal()` before returning data. `configDirty` is set to `true` on every mutation and reset to `false` at the end of `refreshConfigInternal()` (line 487).

**Residual race:** `configDirty` is `@Volatile` but the check-then-act (`if (configDirty) refresh`) is not atomic with respect to the mutex. Two concurrent callers can both see `configDirty == true`, both call `refreshConfigInternal()`, and both acquire `configMutex.withLock` serially — the second refresh is redundant but harmless. This is a minor inefficiency, not a correctness bug.

---

### PF-5 — CheckSendersActivity SMS query has LIMIT 10000
**Status: FIXED (correct)**

`CheckSendersActivity.getSmsSenders()` (lines 99–105) passes `"address ASC LIMIT 10000"` as the `sortOrder` argument to `contentResolver.query()`. The limit is in place.

---

### PF-6 — Rule.regexPattern uses lazy/cached Regex
**Status: FIXED (correct)**

`Rule.kt` (lines 17–29) defines `cachedPattern` and `cachedPatternString` as `@Transient` fields. The `regexPattern` property checks `cachedPattern == null || cachedPatternString != pattern` and only recompiles when the pattern string has changed.

**Minor concern:** The caching is not thread-safe. If two threads access `regexPattern` concurrently on the same `Rule` instance, both may observe `cachedPattern == null` and both will compile and assign — but since they produce identical `Regex` objects from the same pattern, the final result is correct (it is a benign data race). This is acceptable given the usage context.

---

### DD-5 — ApplyRulesActivity calls processMessageFull() not processMessage()
**Status: FIXED (correct)**

`ApplyRulesActivity.applyRules()` (line 275) calls `processor.processMessageFull(…)` and correctly pattern-matches on all three result types (`PaymentResult`, `IncomeResult`, `Ignored`).

---

### DD-6 — approximateDate() uses referenceTime parameter not System.currentTimeMillis()
**Status: FIXED (correct)**

`PaymentProcessor.approximateDate()` (line 279) uses `referenceTime` (the SMS receive timestamp passed by the caller) in the `minByOrNull` distance calculation. The last-resort fallback (line 287) also uses `referenceTime` (`Date(referenceTime)`).

---

### OC-1 — BankSmsDatabase exportSchema = true, build.gradle.kts has schemaLocation
**Status: FIXED (correct)**

`BankSmsDatabase.kt` (line 22) has `exportSchema = true`. `build.gradle.kts` (lines 24–28) has `annotationProcessorOptions { arguments["room.schemaLocation"] = "$projectDir/schemas" }`.

---

### OC-2 — ConfigRepository.load() uses runBlocking(Dispatchers.IO)
**Status: FIXED (correct)**

Line 61: `runBlocking(Dispatchers.IO) { … }`. The Dispatchers.IO context is passed, so the blocking coroutine runs on an IO thread pool thread rather than on the calling thread's dispatcher.

---

### OC-3 — PaymentProcessor regex calls wrapped with timeout
**Status: FIXED (correct)**

`PaymentProcessor` (lines 31–55) defines `safeRegex()` which submits the regex operation to `regexExecutor` (a cached thread pool) and calls `future.get(500, TimeUnit.MILLISECONDS)`. All three rule-trying methods (`tryPaymentRules`, `tryIncomeRules`, `tryIgnoreRules`) use `safeRegex()`. A `TimeoutException` is caught, the future is cancelled, and a warning is logged. The log statement at line 50 (`Log.w(TAG, "Regex timeout … for pattern: $patternStr")`) is not guarded by `BuildConfig.DEBUG` — this is intentional since it is a warning about a potentially malicious pattern and is appropriate for production logs.

**Note on executor leak:** `regexExecutor` is a static `CachedThreadPool` that is never shut down. This is a permanent executor leak for the life of the process. For a mobile app whose process is managed by Android, this is acceptable, but it should be documented.

---

### OC-4 — sender_rules table: migration DROP TABLE added
**Status: FIXED (correct)**

`MIGRATION_10_11` (lines 286–290) executes `DROP TABLE IF EXISTS sender_rules`. The migration is registered in `addMigrations()` (line 308). The schema version is 11 (line 21).

---

## 2. Remaining Issues (from Original Review — Not Fully Fixed)

### REM-1 — SEC-3 / data_extraction_rules.xml still empty
**Severity: HIGH**

As noted above under SEC-3, `data_extraction_rules.xml` (the API 31+ backup configuration) is entirely boilerplate with no exclusion rules. On Android 12+ devices, `fullBackupContent` is ignored in favor of `dataExtractionRules`. This means `bank_sms_tracker.db` (containing financial payment history) and `app_terms.xml` are backed up to Google Drive / device transfer on all modern Android devices.

**File:** `app/src/main/res/xml/data_extraction_rules.xml`

**Fix required:** Add equivalent `<exclude>` rules inside the `<cloud-backup>` and `<device-transfer>` sections.

---

### REM-2 — isMinifyEnabled = false in release builds
**Severity: MEDIUM**

`app/build.gradle.kts` (line 33) sets `isMinifyEnabled = false` for the release build type. Without minification:
- Class and method names are fully visible in the APK, aiding reverse engineering.
- Dead code from libraries is not removed.
- The regex pattern strings, SMS rule descriptions, and merchant names stored in the APK resources are unobfuscated.

This was noted in the original review and remains unchanged. Enabling R8 with `isMinifyEnabled = true` and a proper ProGuard rules file is recommended for a production release.

---

## 3. New Issues Found

### NEW-1 — SmsProcessingService logs SMS body/sender in production without DEBUG guard
**Severity: HIGH**
**File:** `app/src/main/java/com/example/banksmstracker/service/SmsProcessingService.kt`

Multiple log statements in `SmsProcessingService` emit sensitive data unconditionally:

- **Line 161:** `Log.e(TAG, "Error processing SMS from $sender: ${e.message}\nBody: $body")` — logs the full SMS body in production on any processing error.
- **Line 168:** `Log.d(TAG, "SMS from $sender processed as payment: ${result.payment}")` — logs payment details (amount, card, merchant) in production.
- **Line 170:** `Log.d(TAG, "SMS from $sender processed as income: ${result.income}")` — logs income details in production.
- **Line 172:** `Log.d(TAG, "SMS from $sender ignored by rule '${result.ruleName}': $body")` — logs the full SMS body for ignored messages in production.

`SmsReceiver` was fixed to guard these logs with `BuildConfig.DEBUG`, but the equivalent service code was not updated. On production builds, any app with `READ_LOGS` permission (or a rooted device) can extract full SMS content from logcat.

---

### NEW-2 — data_extraction_rules.xml has no exclusions (API 31+ devices)
**Severity: HIGH**
**File:** `app/src/main/res/xml/data_extraction_rules.xml`

(Documented under REM-1 above as a remaining issue from the original review. Listed here for completeness as it was not in the original issue list.)

---

### NEW-3 — configMutex deadlock risk: refreshConfigInternal() called while holding configMutex
**Severity: MEDIUM**
**File:** `app/src/main/java/com/example/banksmstracker/repository/ConfigRepository.kt`, lines 62–73 and 473–489

`load()` acquires `configMutex` (line 62) and then calls `refreshConfigInternal()` (line 72). `refreshConfigInternal()` also tries to acquire `configMutex` (line 474). Since Kotlin's `Mutex` is **not** reentrant, calling `withLock` while already holding the same `Mutex` from the same coroutine will **deadlock**.

The current code avoids this at runtime only by luck: `load()` is the one call path that triggers `refreshConfigInternal()` while holding the lock. If any future refactoring calls `refreshConfigInternal()` inside `configMutex.withLock` in another context, or if `load()` is called re-entrantly (e.g., from a scope already holding the lock), the app will hang indefinitely.

**Recommended fix:** Extract the body of `refreshConfigInternal()` into a private `refreshConfigUnlocked()` function that does not acquire the mutex, and call it from within existing `withLock` scopes. Keep the public `refreshConfigInternal()` wrapping `withLock { refreshConfigUnlocked() }` for external callers.

---

### NEW-4 — recategorizeAllPayments() calls getAllPayments() on main thread potential
**Severity: MEDIUM**
**File:** `app/src/main/java/com/example/banksmstracker/repository/ConfigRepository.kt`, line 598

`recategorizeAllPayments()` is called at the end of `updateCategory()` (line 123), which is a `suspend fun` dispatched on `Dispatchers.IO`. However, the function itself declares `withContext(Dispatchers.IO)` at the top (line 597), so the inner body runs on IO. The call to `paymentRepository.getAllPayments()` (line 598) is therefore safe.

However, `recategorizeAllPayments()` also constructs `Regex` objects (line 617) inside the loop for merchant regex categories. For large merchant lists and large payment histories, this could be slow. The regex is freshly compiled for every category-merchant pair on every invocation, with no caching. For configs with hundreds of rules, this is O(categories × merchants) regex compilations per `updateCategory()` call. This is LOW severity for a typical personal-use config but could be a problem for imported configs with many entries.

---

### NEW-5 — IgnoreRuleEntity is a dead schema entity
**Severity: LOW**
**File:** `app/src/main/java/com/example/banksmstracker/database/Entities.kt`, lines 130–149
**File:** `app/src/main/java/com/example/banksmstracker/database/BankSmsDatabase.kt`, line 18

`IgnoreRuleEntity` and the `ignore_rules` table remain registered as a Room `@Database` entity (line 18). The `ignore_rules` table was superseded by the unified `rules` table in migration 7→8 (where the ignore_rules data was migrated to `rules` with `ruleType = 'ignore'`). No code path writes to `IgnoreRuleDao` for new ignore rules — all rule inserts go through `RuleDao`. The entity is dead weight that:

- Adds an unnecessary table to the schema.
- Causes Room to generate and maintain `IgnoreRuleDao` infrastructure.
- Could confuse future maintainers into thinking ignore rules are still stored separately.

This should be removed in a future migration (DROP TABLE ignore_rules; remove the entity from the `@Database` annotation).

---

### NEW-6 — PaymentProcessor.regexExecutor is a static resource never shut down
**Severity: LOW**
**File:** `app/src/main/java/com/example/banksmstracker/processor/PaymentProcessor.kt`, line 32

```kotlin
private val regexExecutor = Executors.newCachedThreadPool()
```

`regexExecutor` is a companion-object (static) `CachedThreadPool` that is never shut down. Threads in a cached thread pool survive for 60 seconds after their last use. If `PaymentProcessor` is garbage-collected, the executor is not. While Android's process lifecycle limits practical impact, this is a resource leak. A `newFixedThreadPool(1)` or a `newSingleThreadExecutor()` would be more predictable, and it should be shut down when the app is backgrounded/destroyed (e.g., in `Application.onTerminate()` for completeness, or using a coroutine dispatcher with cancellation).

---

### NEW-7 — SmsExportActivity uses DISTINCT in projection but sortOrder LIMIT
**Severity: LOW**
**File:** `app/src/main/java/com/example/banksmstracker/ui/SmsExportActivity.kt`, lines 249–255

`SmsExportActivity.loadSmsMessages()` passes `"date DESC LIMIT 5000"` as the `sortOrder` parameter to the SMS content provider query. The `LIMIT` clause in `sortOrder` is not supported by all Android versions or all SMS provider implementations (it is a SQLite extension applied through the sort-order string, not a standard ContentResolver parameter). On some OEM devices, the LIMIT keyword in `sortOrder` may be silently ignored, causing the query to return all matching messages and potentially causing an OOM condition on devices with large SMS histories.

**Compare:** `ApplyRulesActivity.getSmsMessages()` passes `"date DESC"` without a LIMIT, relying on address and date pre-filtering to keep the result set manageable. `SmsExportActivity` does not pre-filter by address in the SQL query (it filters in Kotlin), so it reads up to `LIMIT 5000` from the full SMS inbox — or all messages if LIMIT is unsupported.

---

### NEW-8 — buildReport() accesses UI elements from Dispatchers.IO
**Severity: MEDIUM**
**File:** `app/src/main/java/com/example/banksmstracker/ui/BugReportActivity.kt`, line 93 and 104

`buildReport()` is declared `private suspend fun buildReport(): String = withContext(Dispatchers.IO) { … }`, but inside that block it directly accesses `etBugDescription.text` (line 104) and `cbInclude*.isChecked` (lines 117, 131, 177, 217). Reading view state from a non-main thread is unsafe on Android — the View hierarchy is not thread-safe, and accessing views from a background thread can cause crashes or return stale data. The correct pattern is to read view state on the main thread before launching the coroutine, or to switch back to `Dispatchers.Main` for view access.

---

### NEW-9 — ApplyRulesActivity.getSmsMessages() has no result size cap
**Severity: LOW**
**File:** `app/src/main/java/com/example/banksmstracker/ui/ApplyRulesActivity.kt`, lines 440–446

The SMS query in `getSmsMessages()` uses `"date DESC"` with no LIMIT. If the configured senders have been active for years and the user sets no date filter (or a wide date range), the content provider could return tens of thousands of messages. Each result is held in memory as a `SmsWithDate` object, and each is then individually processed through the payment processor (which may additionally query the DB in `approximateDate()`). For large inboxes this could cause ANR or OOM. Consider adding a hard cap (e.g., 5000 messages) with a user warning.

---

### NEW-10 — ConfigRepository.shareConfigFile() file never deleted
**Severity: LOW**
**File:** `app/src/main/java/com/example/banksmstracker/repository/ConfigRepository.kt`, lines 266–277

Every call to `shareConfigFile()` creates `File(context.cacheDir, "sms_config.json")` and writes the full config JSON to it. No `deleteOnExit()` is called, and no `ActivityResultLauncher` cleanup is set up. The file persists in the cache directory until the OS evicts it. While `cacheDir` contents are not guaranteed to persist, they also may not be deleted promptly, leaving the full configuration JSON (including all regex rules and sender addresses) on disk longer than necessary.

---

## 4. Overall Assessment

### Security Posture
The previous critical gap of SMS body content leaking into production logs via `SmsReceiver` has been properly fixed. However, the same class of issue persists in `SmsProcessingService` (NEW-1), which handles real-time SMS processing. The `data_extraction_rules.xml` gap (REM-1/NEW-2) means payment data is backed up to cloud storage on all API 31+ devices — this is a HIGH severity data privacy issue that should be addressed before production release.

The SQL injection fix in the date filter queries (SEC-5) is correctly implemented. The test-injection guard (SEC-4) is correctly in place.

### Stability
The most significant stability risk is the deadlock potential in `ConfigRepository` (NEW-3) where `refreshConfigInternal()` tries to re-acquire `configMutex` that is already held by `load()`. The current code path happens to avoid triggering this at runtime, but it is one refactoring step away from a permanent hang. This should be resolved.

The BugReport activity accessing UI views from `Dispatchers.IO` (NEW-8) is a latent thread-safety bug that could manifest as crashes in release builds where thread checker enforcement is less predictable.

### Performance
The batching fix for `recategorizeAllPayments()` (PF-2) is a meaningful improvement. The `existingPayments` pre-fetch (PF-3) is correctly threaded through the call stack. The `configDirty` flag (PF-4) avoids unnecessary DB reads. The `LIMIT 10000` on the CheckSenders query (PF-5) prevents unbounded reads. These are all sound improvements.

The regex executor (NEW-6) and the un-limited ApplyRules query (NEW-9) remain as lower-priority performance concerns.

### Code Quality
The `PaymentAdapter` migration to `ListAdapter` with `DiffUtil` (RC-3/PF-1) is well done. The `Rule.regexPattern` lazy caching (PF-6) is a reasonable implementation despite the benign data race. The `PaymentsFilter.kt` extraction is clean and correctly uses parsed transaction timestamps instead of `receivedAt`.

The `IgnoreRuleEntity` dead entity (NEW-5) adds noise to the schema but has no runtime impact.

---

## Summary Table

| ID | Description | Severity | Status |
|----|-------------|----------|--------|
| RC-1 | ConfigRepository TOCTOU | CRITICAL | FIXED |
| RC-2 | getPaymentProcessor() synchronization | HIGH | FIXED |
| RC-3/PF-1 | PaymentAdapter ListAdapter + DiffUtil | MEDIUM | FIXED |
| SEC-1 | SmsReceiver log guard | HIGH | FIXED |
| SEC-3 | backup_rules.xml exclusions | HIGH | FIXED (partial — see REM-1) |
| SEC-4 | SmsReceiver test extras guard | HIGH | FIXED |
| SEC-5 | Date filter SQL injection | HIGH | FIXED |
| SEC-6 | Export file cleanup | MEDIUM | FIXED (BugReport path adequate) |
| LC-5 | Device fingerprint removed | MEDIUM | FIXED |
| PF-2 | recategorizeAllPayments() batching | MEDIUM | FIXED |
| PF-3 | approximateDate() existingPayments | MEDIUM | FIXED |
| PF-4 | configDirty flag | LOW | FIXED |
| PF-5 | CheckSenders LIMIT 10000 | LOW | FIXED |
| PF-6 | Rule.regexPattern lazy cache | LOW | FIXED |
| DD-5 | ApplyRules uses processMessageFull() | HIGH | FIXED |
| DD-6 | approximateDate() uses referenceTime | MEDIUM | FIXED |
| OC-1 | exportSchema = true + schemaLocation | MEDIUM | FIXED |
| OC-2 | runBlocking(Dispatchers.IO) | MEDIUM | FIXED |
| OC-3 | Regex timeout protection | HIGH | FIXED |
| OC-4 | sender_rules DROP TABLE migration | MEDIUM | FIXED |
| **REM-1** | data_extraction_rules.xml empty (API 31+) | **HIGH** | **NOT FIXED** |
| **REM-2** | isMinifyEnabled = false in release | **MEDIUM** | **NOT FIXED** |
| **NEW-1** | SmsProcessingService unguarded body/payment logs | **HIGH** | **NEW** |
| **NEW-2** | data_extraction_rules.xml (same as REM-1) | **HIGH** | **NEW/REMAINING** |
| **NEW-3** | configMutex deadlock risk in load() → refreshConfigInternal() | **MEDIUM** | **NEW** |
| **NEW-4** | recategorizeAllPayments() regex compiled per-pair | LOW | NEW |
| **NEW-5** | IgnoreRuleEntity dead schema entity | LOW | NEW |
| **NEW-6** | regexExecutor static thread pool never shut down | LOW | NEW |
| **NEW-7** | SmsExportActivity LIMIT in sortOrder unreliable | LOW | NEW |
| **NEW-8** | BugReportActivity reads UI views from Dispatchers.IO | **MEDIUM** | **NEW** |
| **NEW-9** | ApplyRulesActivity no query row cap | LOW | NEW |
| **NEW-10** | shareConfigFile() no file cleanup | LOW | NEW |

---

## 6. Open Issues from ISSUES.md

The following active issues from `docs/ISSUES.md` are not already covered by the fixes and new-issue findings documented in sections 1–4 above.

### TODO-002 — Pre-existing ktlint Violations
**Status:** Documented (2025-12-29)

Pre-existing code style violations were surfaced by ktlint integration. Auto-correctable issues can be fixed with `./gradlew ktlintFormat`. Manual fixes required for:
1. File naming: `SmsReceptionE2ETest.kt` should be renamed to `SmsReceiverE2ETest.kt`.
2. Property naming: `TAG` constants must use lowercase camelCase (`tag`) per ktlint rules.

Until these are resolved, CI lint checks cannot be made mandatory on pull requests.

---

### DESIGN-001 — Category Cascade Implementation
**Status:** Needs Decision (2025-12-29)

When a regex rule's category assignment changes, there is no agreed policy on whether historical payments should be updated. Three options exist: full cascade (requires a `ruleId` column on the payments table), forward-only (current implicit behavior), or user-prompted choice per change. A decision is required before the recategorization feature can be considered complete.

---

### DESIGN-002 — Config Import Merge Strategy
**Status:** Needs Decision (2025-12-29)

When importing a configuration JSON, it is unspecified how conflicts between the imported data and existing data are resolved (append-only, merge, replace, or per-conflict user prompt). The current `ConfigImportE2ETest` does not cover conflict scenarios. A strategy must be chosen before config import can be safely exposed in production UI.

---

### LIMITATION-001 — READ_SMS Permission on Android 10+
**Status:** Documented (2025-12-29)

On Android 10+ (API 29+) the `READ_SMS` permission requires additional Play Store justification. The retrospective SMS parsing feature (`SmsExportActivity`, `ApplyRulesActivity`) depends on this permission. For Play Store distribution, this feature may need to be disabled or restructured. APK side-loading is the current workaround for full functionality.

---

### LIMITATION-002 — Test Data Duplication Across Asset Directories
**Status:** Documented (2025-12-29)

`default_rules.json` must be kept in sync across three locations: `app/src/main/assets/`, `app/src/test/resources/`, and `app/src/androidTest/assets/`. A Gradle task to copy the fixture during build was proposed but has not yet been implemented. Any change to the default rules currently requires manual updates in all three locations, which is error-prone.

---

### TODO-001 — Appium E2E Test Infrastructure
**Status:** Deferred (2025-12-29)

**Note:** This issue is now resolved in practice — Appium tests exist and run against a Docker-based Appium server (see `docker-compose.yml` and `make test-appium`). This issue entry in `ISSUES.md` should be marked Resolved.

---

## 7. Pending Testing Improvements

Extracted from `docs/plans/testing-improvement-plan.md`. Items marked `⬜ Pending` as of 2026-03-13.

### Unit Tests

| Item | Priority |
|------|----------|
| Add `CategoryAssignmentPerformanceTest` for category matching with 50+ categories | Medium |
| Parametrize `PaymentProcessorPerformanceTest` batch sizes: 100, 1 000, 10 000 messages | Medium |

**Coverage gaps still open (from §1.1 of the plan):**

| Area | Gap | Priority |
|------|-----|----------|
| `SmsAddressMatcher` | Locale-sensitive address normalization | High |
| `PaymentProcessor.approximateDate()` | Date interpolation from neighbors | High |
| `ConfigRepository` migration paths | DB version upgrades | Medium |
| `BugReportActivity` attachment logic | JSON serialization correctness | Medium |
| `SmsExportActivity` export format | CSV/JSON output structure | Low |
| `ConfigRepository.load()` | Race condition: concurrent calls from multiple threads | High |
| `PaymentProcessor` | ReDoS guard: pathological regex patterns | Medium |

### Instrumented Tests

| Item | Priority |
|------|----------|
| Create `app/src/androidTest/.../performance/` package | Medium |
| Add `RoomPerformanceTest`: insert/query benchmarks for 1 000 and 10 000 payments | P1 |
| Add `ApplyRulesPerformanceTest`: rule application on large payment history | P1 |
| Add `@LargeTest` annotation to distinguish from fast instrumented tests | Low |

### Appium Tests

| Item | Priority | Notes |
|------|----------|-------|
| `SmsExport` screen: export flow + share intent | Low | ⬜ Pending |
| `ApplyRules` screen: end-to-end rule application on existing payments | Low | ⬜ Pending |

### CI / Reporting

| Item | Priority |
|------|----------|
| Configure Android emulator in GitHub Actions (`reactivecircus/android-emulator-runner`) | High |
| Configure Appium in Docker within the CI runner | High |
| Store Allure history in a `gh-pages` branch for trend tracking | Medium |
| Add performance test thresholds to CI (fail build on regression) | Medium |
| Add `@Story` annotations to individual Appium test methods | P1 |
| Add `@Severity(SeverityLevel.CRITICAL)` to smoke tests | P1 |
| Add `@Link` to connect tests to TODO.md task IDs | P2 |
| Add timing trend charts to Allure (requires Allure history in CI artifacts) | P2 |
| Publish Allure report as GitHub Pages artifact on each merge to `main` | P2 |
| Add `:benchmark` Gradle module (Macrobenchmark) | P2 |
| Add `StartupBenchmark` measuring cold-start time to `MainActivity` | P2 |
| Add `ScrollBenchmark` measuring frame jank on the payments list | P2 |
| Firebase Test Lab integration | P3 |

---

## 8. Test Coverage Tasks for New Fixes

Analysis of existing tests versus the 20 fixes reviewed in this report.

**Key findings:**
- `PaymentsFilterTest.kt` covers PF-1/RC-3 indirectly and BUG-008/009 (date filter) and merchant query (Feature 2.1) directly.
- `PaymentCategoryReassignmentTest.kt` covers PF-2 and BUG-007.
- `CategoryConcurrencyTest.kt` covers PF-4 concurrency behavior partially.
- `PaymentProcessorEdgeCaseTest.kt` covers PF-3 (`approximateDate` with neighbor pre-fetch) and DD-6 (`referenceTime`).
- No unit or instrumented test covers RC-1 (mutex TOCTOU), RC-2 (double-checked locking), SEC-1 (log guard), SEC-4 (test extras guard), LC-5 (device fields removed), OC-3 (regex timeout), or NEW-3 (deadlock risk).

| Fix ID | What was fixed | Existing test? | Recommended test | Layer |
|--------|---------------|----------------|-----------------|-------|
| RC-1 | `ConfigRepository.load()` TOCTOU: entire guard+init inside `configMutex.withLock` | No | `ConfigRepositoryConcurrencyTest.concurrentLoadCallsDoNotRaceOnInit` — call `load()` from 10 threads simultaneously, assert `_config` is initialized exactly once (use a fake DB that counts seeding calls) | Unit |
| RC-2 | `getPaymentProcessor()` uses `@Volatile` + `synchronized(this)` double-checked locking | No | `ConfigRepositoryConcurrencyTest.concurrentGetPaymentProcessorReturnsConsistentInstance` — read from 50 threads, assert all return the same instance reference | Unit |
| RC-3 / PF-1 | `PaymentAdapter` extends `ListAdapter` with `PaymentDiffCallback`; uses `submitList()` | Partial (`PaymentsFilterTest` checks filter logic but not the adapter itself) | `PaymentAdapterTest.submitListDispatchesDiffCorrectly` — submit two lists differing by one item, verify `areItemsTheSame` and `areContentsTheSame` behave correctly | Unit |
| SEC-1 | SMS body/payment content logs in `SmsReceiver.handleMessage()` guarded by `BuildConfig.DEBUG` | No | `SmsReceiverLogGuardTest.productionBuildDoesNotLogSmsBody` — mock `Log` and verify no calls containing body text occur when `BuildConfig.DEBUG = false`; alternatively assert via instrumented test in release variant | Instrumented |
| SEC-4 | `SmsReceiver.onReceive()` returns immediately when test extras present and `!BuildConfig.DEBUG` | No | `SmsReceiverTest.testExtrasIgnoredInProductionBuild` — send broadcast with `EXTRA_TEST_SENDER` set, verify `handleMessage()` is never called when running non-debug build | Instrumented |
| LC-5 | `BugReportActivity.buildReport()` no longer appends `MANUFACTURER`/`MODEL`/`PRODUCT`/`HARDWARE` | No | `BugReportActivityTest.buildReportOmitsHardwareFingerprint` — trigger preview, assert report string does not contain `"MANUFACTURER"`, `"MODEL"`, `"PRODUCT"`, or `"HARDWARE"` | Instrumented |
| PF-2 | `recategorizeAllPayments()` batches DB updates by distinct merchant (O(merchants) not O(payments)) | Yes — `CategoryConcurrencyTest` covers recategorization correctness | `RecategorizePerformanceTest.recategorizeOneThousandPaymentsWithTenMerchantsIsFast` — insert 1000 payments with 10 distinct merchants, call `recategorizeAllPayments()`, assert elapsed < 500 ms and `updateCategoryForMerchant` called exactly 10 times | Unit |
| PF-3 | `approximateDate()` accepts pre-fetched `existingPayments` list, avoids redundant DB query per message | Yes — `PaymentProcessorEdgeCaseTest` covers `approximateDate` scenarios | Extend `PaymentProcessorEdgeCaseTest.approximateDateUsesPreFetchedListNotRepository` — pass a custom `PaymentRepository` that throws if `getAllPayments()` is called, verify processing still succeeds when list is pre-supplied | Unit |
| PF-4 | `getCategories()`/`getSenders()` check `configDirty` flag before calling `refreshConfigInternal()` | Partial (`CategoryConcurrencyTest` tests recategorization but not the dirty flag path) | `ConfigRepositoryDirtyFlagTest.getCategories_doesNotRefreshWhenNotDirty` — call `getCategories()` twice, assert the underlying DAO `getCategories()` is called only once | Unit |
| DD-5 | `ApplyRulesActivity.applyRules()` calls `processMessageFull()` not `processMessage()` | No | `ApplyRulesActivityTest.applyRulesHandlesAllThreeResultTypes` — inject a processor that returns `PaymentResult`, `IncomeResult`, and `Ignored` for different messages, assert all three are saved/counted correctly | Instrumented |
| DD-6 | `approximateDate()` uses `referenceTime` parameter not `System.currentTimeMillis()` | Yes — `PaymentProcessorEdgeCaseTest.paymentWithNoDateGetsDateFromNeighbor` covers this | No additional test needed — existing coverage is adequate | Unit |
| OC-2 | `ConfigRepository.load()` uses `runBlocking(Dispatchers.IO)` to avoid blocking main-thread dispatcher | No | `ConfigRepositoryDispatcherTest.loadRunsOnIODispatcher` — wrap `load()` call with a `TestCoroutineScheduler`, assert the IO work does not occur on `Dispatchers.Main` | Unit |
| OC-3 | `PaymentProcessor.safeRegex()` enforces 500 ms timeout per regex match; cancels on `TimeoutException` | Partial (`PaymentProcessorEdgeCaseTest` tests malformed patterns but not timing) | `PaymentProcessorEdgeCaseTest.catastrophicBacktrackingPatternTimesOut` — supply a ReDoS pattern (e.g., `(a+)+`) against a long non-matching string, assert `safeRegex()` returns null within 1 s | Unit |
| SEC-6 | `BugReportActivity` cleans up `payments_export.json` via `shareChooserLauncher`; `PaymentsActivity` calls `deleteOnExit()` | No | `BugReportActivityTest.exportFileDeletedAfterShareChooserReturns` — trigger report send, simulate chooser return, assert `pendingExportFile` is null and file no longer exists on disk | Instrumented |
| NEW-1 | (Unfixed) `SmsProcessingService` logs body/sender/payment in production without DEBUG guard | No | `SmsProcessingServiceLogGuardTest.processingServiceDoesNotLogSensitiveDataInProduction` — mock `Log`, process a message in a non-debug context, assert no log calls contain body or payment details | Instrumented |
| NEW-3 | (Unfixed) `ConfigRepository.load()` → `refreshConfigInternal()` deadlock risk via non-reentrant `configMutex` | No | `ConfigRepositoryDeadlockTest.loadDoesNotDeadlock` — call `load()` with a real or in-memory DB and assert it returns within 5 s (a deadlock would hang indefinitely, caught by a test timeout annotation) | Unit |
| NEW-8 | (Unfixed) `BugReportActivity.buildReport()` reads view state from `Dispatchers.IO` | No | `BugReportActivityTest.buildReportReadsViewsOnMainThread` — enable strict-mode `ThreadPolicy` in the test, trigger `buildReport()`, assert no `StrictMode` violation is raised for main-thread UI access from background thread | Instrumented |
| BUG-008/009 | Date filter uses parsed `payment.timestamp` not `receivedAt` | Yes — `PaymentsFilterTest` has 6 tests covering this directly | No additional test needed | Unit |
| Feature 2.1 | Merchant search: `merchantQuery` param in `filterPayments()`, `etMerchantSearch` UI wired | Yes — `PaymentsFilterTest` has 4 merchant-filter tests | Extend with Appium test: `PaymentsFilterAppiumTest.merchantSearchFieldFiltersResults` — type merchant name in search field, assert list updates to show only matching payments | Appium |
| BUG-007 | `addMerchantToCategory` removes from old categories first, updates existing payment rows | Yes — `PaymentCategoryReassignmentTest` covers this directly | No additional test needed | Unit |
