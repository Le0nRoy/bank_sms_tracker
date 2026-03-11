# Testing Approaches

> This document describes all testing layers used in BankSMSTracker and how to use them effectively.
> See [testing-improvement-plan.md](testing-improvement-plan.md) for the roadmap on expanding coverage.

---

## Testing Layers Overview

| # | Layer | Framework | Location | Needs device? | Speed |
|---|-------|-----------|----------|--------------|-------|
| 1 | Static analysis | ktlint | – | No | ~5 s |
| 2 | Unit tests | JUnit 5 + Kotlin Test | `src/test/` | No | ~10 s |
| 3 | Instrumented tests | JUnit 5 + AndroidJUnit4 | `src/androidTest/` | Yes (emulator/device) | ~2 min |
| 4 | Appium feature tests | Appium + JUnit 5 | `src/test/.../appium/` | Yes (real device) | ~5–10 min/class |
| 5 | Smoke tests | Appium + `@Tag("smoke")` | same as above | Yes | ~10 min |
| 6 | Full Appium suite | Appium + JUnit 5 | same as above | Yes | ~60 min |

---

## Layer 1 — Static Analysis (ktlint)

**Purpose:** Enforce code style and catch formatting errors before they accumulate.

**Run:**
```bash
./gradlew ktlintCheck        # check all source sets
./gradlew ktlintFormat       # auto-fix what's possible (run before commit)
```

**Scope:** All Kotlin source sets (`main`, `test`, `androidTest`).

**Rules configured:** `max-line-length = 120`, import ordering, trailing commas in multiline constructs.

**When to run:** On every file change; part of CI and pre-commit discipline.

---

## Layer 2 — Unit Tests

**Purpose:** Fast, isolated tests for business logic — no Android framework, no DB, no device.

**Framework:** JUnit 5 (`junit-jupiter`) + Kotlin Test assertions. Mockito for Android stubs.

**Location:** `app/src/test/java/com/example/banksmstracker/`

**Key test classes:**

| Class | Coverage area |
|-------|---------------|
| `PaymentProcessorEdgeCaseTest` | Regex parsing edge cases (empty input, optional groups, anchors) |
| `PaymentProcessorEnabledTest` | Sender/rule/category enable flags |
| `PaymentProcessorWorkflowTest` | Payment vs Income vs Ignore rule priority |
| `DataClassesTest` | Data class equality and copy semantics |
| `MappersTest` | DB entity ↔ domain model mapping |
| `HashUtilTest` | Deduplication hash stability |
| `CategoryConcurrencyTest` | Coroutine safety for concurrent category writes |
| `ConfigRepositoryTest` | Config load/reload lifecycle |
| Performance tests | Regex throughput, batch processing timing |

**Run:**
```bash
# All unit tests
./gradlew testDebugUnitTest

# Targeted — only what changed
./gradlew testDebugUnitTest --tests "com.example.banksmstracker.processor.*"
./gradlew testDebugUnitTest --tests "com.example.banksmstracker.data.*"
./gradlew testDebugUnitTest --tests "com.example.banksmstracker.performance.*"
```

**Coverage:** `./gradlew coverage` → `app/build/reports/jacoco/`

---

## Layer 3 — Instrumented Tests (connectedAndroidTest)

**Purpose:** Test Android-specific behaviour: Room DB operations, Activity lifecycle, SharedPreferences, Broadcast receivers.

**Framework:** JUnit 5 (Mannodermaus plugin) + AndroidJUnit4 runner + Espresso.

**Location:** `app/src/androidTest/java/com/example/banksmstracker/`

**Key test classes:**

| Class | Coverage area |
|-------|---------------|
| `RoomPaymentRepositoryTest` | Room DAO: save, query, dedup |
| `ConfigRepositoryRoomTest` | Config persistence and migrations |
| `CategoryConcurrencyInstrumentedTest` | Concurrent DB writes under load |
| `SmsProcessingPipelineE2ETest` | Full pipeline: SMS → PaymentProcessor → Room |
| `PaymentFilterE2ETest` | Filter query correctness |
| `CategoryCascadeE2ETest` | Recategorize-on-rule-save cascade |
| `PaymentDeduplicationE2ETest` | Hash-based dedup across restarts |
| `DebugProdVisibilityTest` | Debug-only UI elements hidden in release |

**Run:**
```bash
# Full instrumented suite
./gradlew connectedAndroidTest

# Targeted
./gradlew connectedAndroidTest --tests "com.example.banksmstracker.repository.RoomPaymentRepositoryTest"

# Makefile shortcut
make test-android
```

**Note:** The app is installed and uninstalled on the device automatically; it is **not** left installed after the run.

---

## Layer 4 — Appium Feature Tests

**Purpose:** Black-box UI testing from the user's perspective. Verifies full user flows end-to-end on a real device.

**Framework:** Appium 2 + JUnit 5 + io.appium.java-client.

**Location:** `app/src/test/java/com/example/banksmstracker/appium/`

**Infrastructure:**
- Appium server runs in Docker (`docker compose up -d appium`)
- App installed by Appium when `APPIUM_APK_PATH` is set (ensures clean state via `noReset=false`)
- Without `APPIUM_APK_PATH`, Appium connects to an already-installed app (`noReset=true`)

**Test classes:**

| Class | Feature | Smoke tests |
|-------|---------|-------------|
| `MainNavigationAppiumTest` | Main screen navigation | `mainScreenDisplaysAppTitle`, `mainScreenHasAllNavigationButtons` |
| `RegexBuilderAppiumTest` | Regex builder UI | `navigateToRegexBuilder`, `testRegexPatternMatching` |
| `CategoryManagementAppiumTest` | Category CRUD | `navigateToCategoriesScreen`, `addNewCategoryWithName` |
| `SenderManagementAppiumTest` | Sender CRUD | `navigateToSendersScreen`, `addNewSenderWithName` |
| `SettingsAppiumTest` | Theme / language settings | `settingsScreenDisplaysThemeSection`, `settingsScreenDisplaysLanguageSection` |
| `BugReportAppiumTest` | Bug report UI | `navigateToBugReport`, `enterBugDescription` |
| `CategoryCascadeAppiumTest` | Recategorize cascade | `navigateToCategories`, `recategorizeButtonExists` |
| `PaymentsFilterAppiumTest` | Payment filter | `navigateToPaymentsScreen`, `senderFilterSpinnerExists` |
| `SmsToPaymentFlowAppiumTest` | Full SMS → payment flow | `createCategory`, `createSenderWithRule` |

**Run (feature-targeted):**
```bash
APPIUM_APK_PATH=/apk/debug/app-debug.apk ./gradlew testDebugUnitTest \
  --tests "*.appium.RegexBuilderAppiumTest" --no-daemon
```

**Run (specific failing test):**
```bash
APPIUM_APK_PATH=/apk/debug/app-debug.apk ./gradlew testDebugUnitTest \
  --tests "*.appium.RegexBuilderAppiumTest.testRegexPatternMatching" --no-daemon
```

**Allure reporting:**
```bash
make allure-install   # one-time setup
make allure-report    # generate static HTML report
make allure-serve     # open in browser
```

---

## Layer 5 — Smoke Tests

**Purpose:** Fast regression check — 1-2 critical tests per feature to detect cross-feature breakage after any change.

**All smoke tests** are annotated `@Tag("smoke")` in their respective Appium test class.

**Run:**
```bash
make test-smoke   # ~18 tests, ~10 minutes
```

**When to add smoke tests:** Every new Appium test class must include at least 1 `@Tag("smoke")` test on the class's primary navigation and one core action.

---

## Layer 6 — Full Appium Suite

**Purpose:** Complete regression coverage across all features and edge cases.

**Run:**
```bash
make test-appium   # ~116 tests, ~60 minutes
```

**When to run:**
- Before merging a branch
- After broad refactoring touching multiple features
- On explicit user request

---

## Test Infrastructure

### Docker Compose Services

```bash
docker compose up -d          # start all services
docker compose up -d appium   # start only Appium (port 4723)
docker compose down           # stop and remove all services
```

| Service | Port | Purpose |
|---------|------|---------|
| `appium` | 4723 | Appium server, `network_mode=host` for ADB access |
| `gradle-cache` | 5071 | Remote Gradle build cache (opt-in with `-PbuildCacheRemote=true`) |

### Device Requirements

- Real Android device connected via ADB, or emulator with ADB access
- `adb devices` must show the device as `device` (not `unauthorized`)
- Appium server must be running and reachable at `http://localhost:4723`

### Makefile Quick Reference

```bash
make test               # unit tests
make test-smoke         # Appium smoke tests
make test-appium        # full Appium suite
make test-android       # instrumented (connectedAndroidTest)
make coverage           # unit tests with JaCoCo report
make cluster-start      # start all Docker services
make cluster-stop       # stop all Docker services
make allure-install     # install Allure CLI into .venv
make allure-report      # generate HTML report from last run
make allure-serve       # serve report in browser
```
