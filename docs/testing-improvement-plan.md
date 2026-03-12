# Testing Improvement Plan

> This document tracks planned improvements to the BankSMSTracker test suite.
> For current testing approaches, see [testing-approaches.md](testing-approaches.md).

---

## 1. Coverage Gaps to Address

### 1.1 Unit Test Gaps

| Area | Gap | Priority |
|------|-----|----------|
| `SmsAddressMatcher` | Locale-sensitive address normalization | High |
| `PaymentProcessor.approximateDate()` | Date interpolation from neighbors | High |
| `ConfigRepository` migration paths | DB version upgrades | Medium |
| `BugReportActivity` attachment logic | JSON serialization correctness | Medium |
| `SmsExportActivity` export format | CSV/JSON output structure | Low |
| `ConfigRepository.load()` | Race condition: concurrent calls from multiple threads | High |
| `PaymentProcessor` | ReDoS guard: pathological regex patterns | Medium |

### 1.2 Instrumented Test Gaps

| Area | Gap | Priority |
|------|-----|----------|
| `DebugProdVisibilityTest` | Needs full release-build variant test (currently debug only) | High |
| `LocaleE2ETest` | Language switch + restart persistence | Medium |
| `ApplyRulesActivity` | Sender auto-select from address | Medium |
| `RegexBuilderActivity` | Auto-fill sender spinner via EXTRA_SENDER_ADDRESS | Low |

### 1.3 Appium Test Gaps

| Area | Gap | Priority |
|------|-----|----------|
| Terms dialog | First-launch dialog appears; agree button works | High |
| Payments screen | Export CSV in debug build; hidden in release | Medium |
| SmsExport screen | Export flow, share intent | Low |
| ApplyRules screen | End-to-end rule application on existing payments | Low |

---

## 2. Performance Testing

### 2.1 Why Performance Testing Matters

BankSMSTracker processes SMS messages in real-time (`SmsReceiver`) and on-demand (Apply Rules). With large payment histories (1 000+ payments) or many rules (50+), slow processing could:
- Delay notifications (SMS receiver runs in broadcast window ~10 s)
- Freeze the UI during rule application
- Cause ANR on low-end devices

### 2.2 What to Measure

| Metric | Target | How to measure |
|--------|--------|---------------|
| Single-message parse time | < 5 ms (p99) | `System.nanoTime()` in unit test |
| Batch parse: 1 000 messages | < 500 ms total | `measureTimeMillis` in unit test |
| Batch parse: 10 000 messages | < 5 000 ms total | `measureTimeMillis` in unit test |
| Category assignment (50 categories, 1 000 messages) | < 200 ms | `measureTimeMillis` |
| Room insert: 1 000 payments | < 1 000 ms | instrumented test with `measureTimeMillis` |
| Room query: all payments with filters | < 100 ms | instrumented test |
| DB migration (v5 → v6 with 10 000 rows) | < 5 000 ms | Room migration instrumented test |
| Apply Rules on 5 000 existing payments | < 10 000 ms | instrumented test |
| Startup time to main screen (cold) | < 2 000 ms | Appium `driver.manage().timeouts()` |
| Memory: peak heap during batch parse | < 64 MB | Android Profiler / `Debug.getNativeHeapAllocatedSize()` |

### 2.3 Best Options for Performance Testing

#### Option A — JUnit 5 Unit Tests (Recommended for parsing/logic)

**Pros:** No device, fast iteration, easy CI integration, deterministic.
**Cons:** No Android framework; cannot measure UI or DB on-device.

```kotlin
@Test
fun `batch parse 10000 messages within 5 seconds`() {
    val processor = PaymentProcessor(senders, categories, repository)
    val messages = List(10_000) { testMessage }
    val elapsed = measureTimeMillis {
        messages.forEach { processor.getMessageResult(it, testAddress) }
    }
    assertTrue(elapsed < 5_000, "Took ${elapsed}ms, expected < 5000ms")
}
```

**Location:** `app/src/test/java/com/example/banksmstracker/performance/`

**Existing class:** `PaymentProcessorPerformanceTest` — extend it.

#### Option B — AndroidJUnit4 Instrumented Tests (Recommended for DB/Room)

**Pros:** Real Room DB on device, measures actual I/O, supports `@LargeTest`.
**Cons:** Needs device, slower than unit tests.

```kotlin
@LargeTest
@Test
fun `insert 1000 payments within 1 second`() = runBlocking {
    val elapsed = measureTimeMillis {
        repeat(1_000) { i -> repository.savePayment(makePayment(i), "msg$i", "BANK") }
    }
    assertTrue(elapsed < 1_000, "Took ${elapsed}ms")
}
```

**Location:** `app/src/androidTest/java/com/example/banksmstracker/performance/`

#### Option C — Macrobenchmark (Future / High-Fidelity)

AndroidX Macrobenchmark measures startup time and frame jank on real devices with ART profile warmup. Best for startup time and UI smoothness, but requires a separate `:benchmark` module and a rooted or unlocked device for accurate results.

**When to add:** Once the app has stable release builds and startup performance becomes a concern.

**Stack:**
```toml
# gradle/libs.versions.toml
macrobenchmark = "1.3.0"
[libraries]
benchmark-macro = { group = "androidx.benchmark", name = "benchmark-macro-junit4", version.ref = "macrobenchmark" }
```

#### Option D — Firebase Test Lab + Perfetto (CI / Automated)

For production CI performance regression detection. Runs on a fleet of real devices, captures Perfetto traces, measures jank, startup, and memory.

**When to add:** When the project has CI integration and a release pipeline.

### 2.4 Required Changes to Implement Performance Tests

#### Immediate (Unit test layer — no structural changes needed)

- [ ] Add `app/src/test/java/com/example/banksmstracker/performance/` package
- [ ] Extend `PaymentProcessorPerformanceTest` with batch-size and timing assertions
- [ ] Add `CategoryAssignmentPerformanceTest` for category matching with 50+ categories
- [ ] Parametrize batch sizes: 100, 1 000, 10 000 messages

#### Short-term (Instrumented layer)

- [ ] Create `app/src/androidTest/java/com/example/banksmstracker/performance/` package
- [ ] Add `RoomPerformanceTest`: insert/query benchmarks for 1 000, 10 000 payments
- [ ] Add `ApplyRulesPerformanceTest`: rule application on large payment history
- [ ] Add `@LargeTest` annotation to distinguish from fast instrumented tests

#### Medium-term (Macrobenchmark)

- [ ] Add `:benchmark` Gradle module
- [ ] Add `StartupBenchmark` measuring cold-start time to MainActivity
- [ ] Add `ScrollBenchmark` measuring frame jank on the payments list with 1 000 items
- [ ] Integrate into CI: fail if startup > 2 000 ms or frame jank > 5 dropped frames

### 2.5 Performance Test Stack

| Component | Tool | Version |
|-----------|------|---------|
| Logic benchmarks | JUnit 5 + `measureTimeMillis` | JUnit 5.9+ |
| DB benchmarks | AndroidJUnit4 + Room in-memory | Current |
| UI startup | AndroidX Macrobenchmark | 1.3.0 |
| UI frame timing | Perfetto traces via Macrobenchmark | bundled |
| CI enforcement | GitHub Actions + threshold assertions | – |
| Reporting | Allure `@Step` + timing annotations | 2.27.0 |

### 2.6 Thresholds and Regression Detection

Performance tests use hard assertions (`assertTrue(elapsed < threshold)`) rather than relative comparison, because:
- Device performance varies between test runs
- Hard thresholds are simple to read and debug
- Baselines should be set conservatively (2× observed p99)

When a performance test fails:
1. Check if the regression is real (run 3× and average)
2. Profile with Android Studio Profiler or `adb shell am profile`
3. Fix the root cause before adjusting the threshold
4. Never raise a threshold without a documented reason in the commit message

---

## 3. Test Reporting Improvements

### 3.1 Allure Integration (Implemented in Phase 4)

- All Appium tests annotated with `@Epic`, `@Feature`, `@Story`
- `@Step` annotations on `AppiumBaseTest` helpers
- Screenshot captured `@AfterEach` via `Allure.addAttachment`
- Reports generated with `make allure-report` / `make allure-serve`

### 3.2 Planned Improvements

- [ ] Add `@Story` annotations to individual test methods (not just classes)
- [ ] Add `@Severity(SeverityLevel.CRITICAL)` to smoke tests
- [ ] Add `@Link` to connect tests to TODO.md task IDs
- [ ] Add timing trend charts to Allure (requires Allure history in CI artifacts)
- [ ] Publish Allure report as GitHub Pages artifact on each merge to `main`

---

## 4. CI Integration Plan

### 4.1 Current State

Tests run locally via Makefile and Gradle. No CI pipeline exists yet.

### 4.2 Proposed Pipeline (GitHub Actions)

```
On pull_request:
  1. ktlintCheck              (Layer 1, ~30 s)
  2. testDebugUnitTest        (Layer 2, ~30 s)
  3. Upload Allure results as artifact

On merge to main:
  1–2. Same as PR
  3. connectedAndroidTest on emulator (Layer 3, ~5 min)
  4. Appium smoke tests on emulator   (Layer 5, ~20 min)
  5. Publish Allure report to GitHub Pages
```

### 4.3 Required Changes for CI

- [ ] Add `.github/workflows/ci.yml`
- [ ] Configure Android emulator in GitHub Actions (using `reactivecircus/android-emulator-runner`)
- [ ] Configure Appium in Docker within the CI runner
- [ ] Store Allure history in a `gh-pages` branch for trend tracking
- [ ] Add performance test thresholds to CI (fail build on regression)

---

## 5. Priority Roadmap

| Priority | Item | Effort |
|----------|------|--------|
| P0 | Fix remaining instrumented test coverage gaps (see §1.2) | 1 day |
| P0 | Extend `PaymentProcessorPerformanceTest` with batch/timing tests | 0.5 day |
| P1 | Add `RoomPerformanceTest` instrumented benchmarks | 1 day |
| P1 | Add `@Story` + `@Severity` Allure annotations | 0.5 day |
| P2 | Add Macrobenchmark `:benchmark` module | 2 days |
| P2 | Set up GitHub Actions CI pipeline | 1 day |
| P3 | Appium tests for Terms dialog, Export CSV, SmsExport flow | 1 day |
| P3 | Firebase Test Lab integration | 3 days |
