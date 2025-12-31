# BankSMSTracker - Testing Guide

## Overview

This document describes the testing strategy, test structure, and usage guidelines for the BankSMSTracker application.

## Test Pyramid

```
        ┌─────────┐
        │  E2E    │  ← Appium (68 tests)
        │  Tests  │     Full user flows
       ─┼─────────┼─
       │Integration│  ← AndroidJUnit (66 tests)
       │   Tests   │     Room DB, Repositories
      ─┼───────────┼─
      │   Unit     │  ← JUnit 5 (30+ tests)
      │   Tests    │     Processors, Parsers, Logic
      ─┴───────────┴─
```

## Test Categories

### 1. Unit Tests (`app/src/test/`)

**Purpose:** Test business logic in isolation without Android dependencies.

**Framework:** JUnit 5 + Mockito

**Location:** `app/src/test/java/com/example/banksmstracker/`

| Test Class | Coverage | Status |
|------------|----------|--------|
| `ConfigRepositoryTest` | Config loading, error handling | Disabled (needs Room migration) |
| `PaymentRepositoryTest` | Payment CRUD, sender/date filtering, category cascade | Active |
| `PaymentProcessorTest` | SMS parsing, categorization | Active |
| `ConfigLoaderTest` | JSON deserialization | Active |
| `PaymentProcessorEnabledTest` | Enabled/disabled rule filtering | Active |
| `DataClassesTest` | Data class coverage | Active |
| `ImportResultTest` | Import result sealed class | Active |

**Running Unit Tests:**
```bash
./gradlew test
```

### 2. Integration Tests (`app/src/androidTest/`)

**Purpose:** Test components with real Android context and Room database.

**Framework:** AndroidJUnit5 + Espresso

**Location:** `app/src/androidTest/java/com/example/banksmstracker/`

| Test Class | Coverage | Status |
|------------|----------|--------|
| `ConfigRepositoryRoomTest` | Config persistence with Room | Active |
| `RoomPaymentRepositoryTest` | Payment persistence, filtering, category cascade | Active |
| `ConfigPersistenceE2ETest` | Config load/save cycle | Active |
| `ConfigExportE2ETest` | JSON export functionality | Active |
| `PaymentFilterE2ETest` | Payment filtering by sender/date range | Active |
| `CategoryCascadeE2ETest` | Category cascade and re-categorization | Active |

**Running Integration Tests:**
```bash
./gradlew connectedAndroidTest
```

### 3. E2E Tests (`app/src/androidTest/`)

**Purpose:** Test complete user flows from SMS reception to payment storage.

| Test Class | Coverage | Status |
|------------|----------|--------|
| `SmsReceptionE2ETest` | Basic SMS processing flow | Active |
| `SmsReceptionWithRoomE2ETest` | SMS processing with real DB | Active |
| `PaymentDeduplicationE2ETest` | Duplicate payment handling | Active |
| `ConfigPersistenceE2ETest` | Config load/save cycle | Active |
| `ConfigExportE2ETest` | JSON export functionality | Active |
| `ConfigImportE2ETest` | JSON import + merge tests | Active |
| `EnabledDisabledE2ETest` | Enabled/disabled filtering | Active |

**Running E2E Tests:**
```bash
./gradlew connectedAndroidTest --tests "*E2ETest"
```

### 4. Appium UI Tests (`app/src/test/appium/`)

**Purpose:** Full UI automation testing with real user interactions.

**Framework:** Appium + UiAutomator2 + JUnit 5

**Location:** `app/src/test/java/com/example/banksmstracker/appium/`

| Test Class | Tests | Coverage |
|------------|-------|----------|
| `MainNavigationAppiumTest` | 14 | Main screen navigation |
| `CategoryManagementAppiumTest` | 11 | Category CRUD operations |
| `SenderManagementAppiumTest` | 11 | Sender CRUD operations |
| `SmsToPaymentFlowAppiumTest` | 10 | End-to-end payment flow |
| `BugReportAppiumTest` | 12 | Bug report feature |
| `RegexBuilderAppiumTest` | 13 | Regex builder feature + save-to-sender |
| `PaymentsFilterAppiumTest` | 8 | Payment filtering by sender/date range |
| `CategoryCascadeAppiumTest` | 5 | Re-categorize all payments feature |

**Prerequisites:**
1. Appium server running: `make appium-start` or `make appium-docker-start`
2. Android emulator with app installed: `make install`

**Running Appium Tests:**
```bash
# Using Makefile (recommended)
make test-appium

# Using Gradle directly
./gradlew testDebugUnitTest --tests "*.appium.*"
```

**Docker Setup:**
```bash
# Start Appium in Docker
docker-compose -f docker-compose.appium.yml up -d

# Stop Appium
docker-compose -f docker-compose.appium.yml down
```

**Test Isolation:**
- Each test class uses `@BeforeEach` to navigate to main screen
- `navigateToMain()` helper with app restart fallback ensures clean state
- Material buttons render text in ALL CAPS - tests handle both cases

## Test Reporter

The project includes a custom test reporting wrapper (`TestReporter`) that provides Allure-like functionality.

### Usage in Unit Tests

```kotlin
import com.example.banksmstracker.testing.TestReporter
import com.example.banksmstracker.testing.Severity

class MyTest {

    @Test
    fun testFeature() = TestReporter.test("Feature should work correctly") {
        description("Verifies that feature X handles input Y correctly")
        severity(Severity.CRITICAL)
        tag("regression", "feature-x")

        step("Given initial state") {
            // Setup code
        }

        step("When action is performed") {
            // Action code
        }

        step("Then expected result is achieved") {
            // Assertions
        }
    }
}
```

### Usage in Android Tests

```kotlin
import com.example.banksmstracker.testing.AndroidTestReporter
import com.example.banksmstracker.testing.Severity

class MyUiTest {

    @Test
    fun testUiFlow() = AndroidTestReporter.test("UI flow should complete") {
        description("Tests the complete user flow")
        severity(Severity.BLOCKER)

        step("Open main screen") {
            // UI action
            captureScreenshot("main_screen")
        }

        step("Tap button") {
            // UI action
        }

        step("Verify result") {
            captureScreenshot("result_screen")
            // Assertions
        }
    }
}
```

### Generating Reports

```kotlin
// At end of test suite
val report = TestReporter.generateTextReport()
println(report)

// For Android tests
val androidReport = AndroidTestReporter.generateTextReport()
```

## Test Data Management

### Configuration Files

| File | Location | Purpose |
|------|----------|---------|
| `default_rules.json` | `app/src/main/assets/` | Production default config |
| `default_rules.json` | `app/src/test/resources/` | Unit test config (mirror) |
| `sms_tests.json` | `app/src/test/resources/` | SMS parsing test cases |

### SMS Test Cases Format

```json
[
  {
    "id": "test_case_1",
    "rawMessage": "Payment 123.45 USD card 1234 Amazon at 20230905 bal 500.00",
    "address": "BANK",
    "expected": {
      "amount": 123.45,
      "currency": "USD",
      "card": "1234",
      "merchant": "Amazon",
      "timestamp": "20230905",
      "balance": 500.00,
      "categoryId": "Shopping"
    }
  }
]
```

### Adding New Test Cases

1. Add test case to `app/src/test/resources/sms_tests.json`
2. If new sender/category needed, update `default_rules.json` in both locations
3. Run tests to verify: `./gradlew test`

## Best Practices

### 1. Test Isolation
- Each test should be independent
- Use `@BeforeEach` for setup
- Use `ConfigRepository.reset()` between tests

### 2. Test Naming
```kotlin
// Pattern: methodName_stateUnderTest_expectedBehavior
@Test
fun parseMessage_validPaymentSms_returnsPayment() { }

@Test
fun parseMessage_unknownSender_throwsException() { }
```

### 3. Test Structure (AAA Pattern)
```kotlin
@Test
fun testExample() {
    // Arrange - Set up test data
    val input = "test input"

    // Act - Execute the code under test
    val result = systemUnderTest.process(input)

    // Assert - Verify the result
    assertEquals(expected, result)
}
```

### 4. Assertions
```kotlin
// Prefer specific assertions
assertEquals(expected, actual, "Custom message")
assertNotNull(result)
assertTrue(condition)

// For exceptions
assertThrows<ExpectedException> {
    codeUnderTest()
}
```

## Running Tests

### All Tests
```bash
./gradlew test connectedAndroidTest
```

### Unit Tests Only
```bash
./gradlew test
```

### Specific Test Class
```bash
./gradlew test --tests "PaymentProcessorTest"
```

### Integration Tests Only
```bash
./gradlew connectedAndroidTest
```

### With Coverage Report
```bash
./gradlew test jacocoTestReport
```

## CI Integration

Tests are run automatically via GitHub Actions on:
- Push to `main` or `feature/*` branches
- Pull requests to `main`

Current CI pipeline:
1. **Lint** - ktlint code style check
2. **Unit Tests** - (to be added)
3. **Integration Tests** - (to be added, requires emulator)

## Troubleshooting

### Common Issues

**1. "Config not initialized" error**
- Ensure `ConfigRepository.load(application)` is called in test setup
- For unit tests, mock the Application context

**2. Room database lock**
- Tests should use in-memory database where possible
- Call `database.close()` in `@AfterEach`

**3. Test flakiness**
- Avoid Thread.sleep(); use proper synchronization
- Use IdlingResource for async operations in UI tests

**4. ktlint failures**
- Run `./gradlew ktlintFormat` to auto-fix issues
- Check `.editorconfig` for style rules

## Future Improvements

1. ~~**Appium Integration**~~ - ✅ Implemented (68 tests, Docker support)
2. ~~**Code Coverage**~~ - ✅ JaCoCo configured (`./gradlew jacocoTestReport`)
3. **Mutation Testing** - Verify test effectiveness
4. **Performance Tests** - Test SMS processing throughput
5. **CI Emulator Tests** - Run Appium tests in GitHub Actions with emulator
