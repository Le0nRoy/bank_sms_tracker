# BankSMSTracker Agent Playbook

> **Related Documentation:**
> - [docs/DESIGN.md](docs/DESIGN.md) - High-level architecture and feature specs
> - [TODO.md](TODO.md) - Project progress tracking
> - [ISSUES.md](ISSUES.md) - Known issues and blockers

## Architecture Highlights

- **Entry points**
  - `MainActivity` routes to category/sender management screens (`app/src/main/java/com/example/banksmstracker/ui/MainActivity.kt`).
  - `SettingsActivity` handles theme and language preferences (`app/src/main/java/com/example/banksmstracker/ui/SettingsActivity.kt`).
  - `SmsReceiver` processes incoming SMS, bootstrapping `PaymentProcessor` from `ConfigRepository` (`app/src/main/java/com/example/banksmstracker/parser/SmsReceiver.kt`).
- **Persistence**
  - Configuration + payments live in a Room database (`app/src/main/java/com/example/banksmstracker/database/*`).
  - `ConfigRepository` is the single source of truth; always use its suspend helpers to read/update categories or senders.
- **Domain model**
  - Configuration lives in `SmsConfig` (`data/` package) and is loaded from `assets/default_rules.json`.
  - `PaymentProcessor` parses/categorises messages using regex rules supplied by the config and persists via `PaymentRepository`.
- **Repositories**
  - `ConfigRepository` lazily loads + caches config; always call `ConfigRepository.load(app)` before accessing `config`.
  - `RoomPaymentRepository` is the production implementation; respects `PaymentRepository` interface methods.

## Coding Rules

### Language & Style
- All app code is Kotlin; match existing nullability + `data class` conventions.
- Keep Android logging via `android.util.Log`; unit tests rely on the stub at `app/src/test/java/android/util/Log.kt`.
- When modifying configuration models, update both asset (`app/src/main/assets/default_rules.json`) and mirrored test fixtures (`app/src/test/resources/default_rules.json`, `sms_tests.json`).

### Code Quality Standards

**DO NOT:**
- **Duplicate code** - Extract common logic into functions or extension methods
- **Deep nesting** - Max 3 levels of nesting for conditions and loops; use early returns, guard clauses, or extract methods
- **Long functions** - Keep functions under 30 lines; extract complex logic into smaller units
- **Magic numbers/strings** - Use named constants or enums
- **Ignore nullability** - Handle nullable types explicitly with `?.`, `?:`, or `requireNotNull()`
- **Suppress warnings** without justification - Add comment explaining why suppression is necessary

**DO:**
- **Use extension functions** for reusable utility code
- **Prefer immutable data** - Use `val` over `var`, immutable collections where possible
- **Write self-documenting code** - Clear function/variable names over comments
- **Follow Kotlin idioms** - Use `when`, `let`, `also`, `apply` appropriately
- **Handle errors explicitly** - Use sealed classes or Result types for expected failures

### Formatting Standards (enforced by ktlint)
```kotlin
// Correct indentation: 4 spaces
class Example {
    fun method() {
        if (condition) {
            doSomething()
        }
    }
}

// Max line length: 120 characters
// Trailing commas in multiline constructs
data class Payment(
    val amount: Double,
    val currency: String,
    val merchant: String?,  // trailing comma
)

// Blank line between functions
fun first() { }

fun second() { }
```

### Payment Parsing
- Extend parsing by adding regex rules to `PaymentRegexRule` instances.
- For categorisation, update `categories` in config; `PaymentProcessor.assignCategory` performs case-insensitive merchant lookup.
- `PaymentRepository.savePayment` requires the raw message + sender for deduping; duplicates are ignored by hash.

### Config Editing
- Use `ConfigRepository.addCategory/updateCategory` and `addSender/updateSender`; they handle DB writes and refresh in-memory caches/processor.
- UI helpers call these on every edit; keep payloads mutable so adapters can mirror local state before persistence.

### Broadcast Receiver
- `SmsReceiver` supports debug extras (`EXTRA_TEST_SENDER`, `EXTRA_TEST_BODY`) for instrumentation tests. Preserve this pathway when refactoring.

### Localization
- All user-visible strings are in `app/src/main/res/values/strings.xml` (English default)
- Russian translations in `app/src/main/res/values-ru/strings.xml`
- Use `AppCompatDelegate.setApplicationLocales()` for per-app language switching
- Language preference stored in SharedPreferences via `BankSmsTrackerApp.KEY_LANGUAGE`
- Supported language codes: `""` (system default), `"en"`, `"ru"`
- When adding new strings: add to both `values/strings.xml` and `values-ru/strings.xml`
- Use parameterized strings (`%1$s`, `%2$d`) for dynamic content

## Testing Expectations

- **Frameworks**  
  - Unit tests use JUnit 5 + Kotlin test assertions; Mockito handles Android dependencies (`app/src/test/java/com/example/banksmstracker/repository/ConfigRepositoryTest.kt`).  
  - Instrumented tests also rely on JUnit 5 via the Mannodermaus plugin (`app/build.gradle.kts`).
- **SMS scenarios**  
  - Parser tests load cases from `app/src/test/resources/sms_tests.json`; extend that file and reuse `SmsTestLoader` when adding cases.  
  - `SmsReceptionE2ETest` constructs intents via the new extras; keep helper methods aligned if receiver signatures change.
- **Config export**  
  - Sharing/exporting uses `FileProvider` and JSON from `ConfigRepository.shareConfigFile`; respect cache-dir writes and grant URI permissions when adding new share flows.
- **Utilities**  
  - `send_sms_tests.sh` replays JSON cases against a running emulator using `adb emu sms send`; ensure new scenarios are compatible with this script (no spaces in sender addresses, or adjust script accordingly).

## Operational Notes

- Always run `./gradlew test` for unit coverage and `./gradlew connectedAndroidTest --tests com.example.banksmstracker.SmsReceiverE2ETest` after touching SMS flow.
- Config loading is idempotent; if a test needs a clean slate, call `ConfigRepository.reset()` (currently `internal`).
- Keep UI changes aligned with `BaseActivity` navigation/back behaviour to maintain consistent titles/back stack handling.

## Quick Start Testing (AI Context)

**Run all tests immediately with these commands:**

```bash
# 1. Unit tests (fast, no emulator needed)
./gradlew test

# 2. Appium E2E tests (requires emulator + Appium server)
# Start Appium (Docker - recommended):
docker-compose -f docker-compose.appium.yml up -d
# Or native: appium

# Verify emulator is running:
adb devices

# Run Appium tests:
./gradlew testDebugUnitTest --tests "*.appium.*"

# Stop Appium when done:
docker-compose -f docker-compose.appium.yml down

# 3. Connected Android tests (requires emulator):
./gradlew connectedAndroidTest
```

**Current Test Status:**
- Unit tests: 195+ tests (JUnit 5) - includes BankSmsTrackerAppTest
- Appium E2E tests: 116 tests (100% pass rate) - includes SettingsAppiumTest
- Integration tests: 77 tests (AndroidJUnit) - includes LocaleE2ETest
- Code coverage: 96.6%

**Makefile shortcuts:**
```bash
make test          # Run unit tests
make test-appium   # Run Appium tests (requires server)
make test-android  # Run connected Android tests
make coverage      # Run tests with coverage report
make appium-docker-start  # Start Appium in Docker
make appium-docker-stop   # Stop Appium Docker
```

**Test Documentation:** See `docs/TESTING.md` for comprehensive test guide.

## Task Tracking (CRITICAL)

**After completing any feature or fixing any issue:**
1. **Update TODO.md** - Mark completed tasks as `[x]` and add entry to Completed Items Log
2. **Commit changes** - Do not batch TODO.md updates; update immediately after completing work
3. **Keep TODO.md current** - This file is the source of truth for project progress

**TODO.md format:**
```markdown
- [ ] Not started
- [~] In progress
- [x] Completed

## Completed Items Log
| Date | Task | Commit |
|------|------|--------|
| YYYY-MM-DD | Task description | commit_hash |
```

**NEVER skip this step** - The TODO.md must always reflect current project state.

## Session Continuity (Context Compacting)

When conversation context is summarized/compacted (agent tool, or ~2% context remaining):

### After Compacting
**ALWAYS read these files first** to restore full project context:
1. `docs/DESIGN.md` - Architecture, features, and current state
2. `TODO.md` - Project progress and current session tasks
3. `AGENTS.md` - This file, for coding rules and guidelines

### Before Compacting (~2% context left)
1. **Update TODO.md "Current Session" section** with:
   - Task being actively worked on
   - What was completed in this session
   - Next steps to continue
2. **Save any uncommitted changes** if possible
3. Ensure `Completed Items Log` is current

This ensures seamless continuation across context boundaries.

## Bug Reporting (CRITICAL)

When a bug is discovered during development or testing:

### 1. Create Bug Report File
Create a new file in `bugs/` directory with format `BUG-XXX-short-description.md`:

```markdown
# BUG-XXX: Short Title

## Status
- [ ] Reported
- [ ] In Progress
- [ ] Fixed
- [ ] Verified

## Description
Clear description of the bug.

## Steps to Reproduce
1. Step one
2. Step two
3. ...

## Expected Behavior
What should happen.

## Actual Behavior
What actually happens.

## Root Cause
(Fill after investigation)

## Fix
(Fill after fixing)

## Verification
- [ ] Unit test added/updated
- [ ] Integration test passes
- [ ] Appium test passes (if UI-related)

## Related Files
- `path/to/file.kt`
```

### 2. Add Task to TODO.md
Add entry under appropriate phase:
```markdown
- [ ] Fix BUG-XXX: Short description
```

### 3. Fix and Verify
1. Implement fix
2. Add/update tests to prevent regression
3. Run all related tests
4. Update bug report status to Fixed/Verified

**NEVER return control to user with unfixed bugs** - All reported bugs must be resolved before completing a task.
