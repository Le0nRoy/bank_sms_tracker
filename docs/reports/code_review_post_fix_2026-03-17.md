# Post-Fix Code Review — BankSMSTracker
**Date:** 2026-03-17
**Reviewer:** Claude Sonnet 4.6 (automated)
**Branch:** `feature/create-pov`
**Scope:** `app/src/main/java/` and `app/src/main/res/`

---

> All 20 issues from the original review were verified as correctly fixed. See commit `ee117f9`.

---

## 1. Remaining Issues (from Original Review — Not Fully Fixed)

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

## 2. New Issues Found

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

## 3. Overall Assessment

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

## 4. Summary Table

> 20 fixed items removed from this table. See commit `ee117f9` for full list.

| ID | Description | Severity | Status |
|----|-------------|----------|--------|
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

## 5. Open Issues from ISSUES.md

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

## 6. Pending Testing Improvements

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

## 7. Test Coverage Tasks for New Fixes

Analysis of existing tests versus the fixes reviewed in this report.

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
| OC-2 | `ConfigRepository.load()` uses `runBlocking(Dispatchers.IO)` to avoid blocking main-thread dispatcher | No | `ConfigRepositoryDispatcherTest.loadRunsOnIODispatcher` — wrap `load()` call with a `TestCoroutineScheduler`, assert the IO work does not occur on `Dispatchers.Main` | Unit |
| OC-3 | `PaymentProcessor.safeRegex()` enforces 500 ms timeout per regex match; cancels on `TimeoutException` | Partial (`PaymentProcessorEdgeCaseTest` tests malformed patterns but not timing) | `PaymentProcessorEdgeCaseTest.catastrophicBacktrackingPatternTimesOut` — supply a ReDoS pattern (e.g., `(a+)+`) against a long non-matching string, assert `safeRegex()` returns null within 1 s | Unit |
| SEC-6 | `BugReportActivity` cleans up `payments_export.json` via `shareChooserLauncher`; `PaymentsActivity` calls `deleteOnExit()` | No | `BugReportActivityTest.exportFileDeletedAfterShareChooserReturns` — trigger report send, simulate chooser return, assert `pendingExportFile` is null and file no longer exists on disk | Instrumented |
| NEW-1 | (Unfixed) `SmsProcessingService` logs body/sender/payment in production without DEBUG guard | No | `SmsProcessingServiceLogGuardTest.processingServiceDoesNotLogSensitiveDataInProduction` — mock `Log`, process a message in a non-debug context, assert no log calls contain body or payment details | Instrumented |
| NEW-3 | (Unfixed) `ConfigRepository.load()` → `refreshConfigInternal()` deadlock risk via non-reentrant `configMutex` | No | `ConfigRepositoryDeadlockTest.loadDoesNotDeadlock` — call `load()` with a real or in-memory DB and assert it returns within 5 s (a deadlock would hang indefinitely, caught by a test timeout annotation) | Unit |
| NEW-8 | (Unfixed) `BugReportActivity.buildReport()` reads view state from `Dispatchers.IO` | No | `BugReportActivityTest.buildReportReadsViewsOnMainThread` — enable strict-mode `ThreadPolicy` in the test, trigger `buildReport()`, assert no `StrictMode` violation is raised for main-thread UI access from background thread | Instrumented |
| Feature 2.1 | Merchant search: `merchantQuery` param in `filterPayments()`, `etMerchantSearch` UI wired | Yes — `PaymentsFilterTest` has 4 merchant-filter tests | Extend with Appium test: `PaymentsFilterAppiumTest.merchantSearchFieldFiltersResults` — type merchant name in search field, assert list updates to show only matching payments | Appium |
