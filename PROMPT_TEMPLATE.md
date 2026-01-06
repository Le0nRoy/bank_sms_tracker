# Session Continuation Prompt Template

Use this template when starting a new session with an AI assistant to continue work on BankSMSTracker.

---

## Quick Start Template

```
I'm continuing work on the BankSMSTracker Android project.

**Project:** Bank SMS Tracker - Android app that parses bank SMS messages and categorizes spending.

**Tech Stack:** Kotlin 2.0, Room Database, MVVM, JUnit 5, Appium E2E tests

**Current State:**
- Database version: 7
- Test coverage: 87.50%
- Appium E2E tests: 104 tests (9 suites)
- Unit tests: All passing

**Key Files:**
- `TODO.md` - Current tasks and progress
- `CHANGELOG.md` - Recent changes
- `AGENTS.md` - Development guidelines
- `bugs/` - Bug documentation

**Session Task:**
[Describe what you want to accomplish]

Please read TODO.md first to understand the current state and pending tasks.
```

---

## Detailed Template (for complex sessions)

```
## Project Context

I'm working on BankSMSTracker, an Android app that automatically tracks bank transactions from SMS messages.

### Technical Overview
- **Language:** Kotlin 2.0
- **Database:** Room (SQLite) with migrations v1→v7
- **Architecture:** MVVM with Repository pattern
- **Testing:** JUnit 5, AndroidJUnit5, Appium E2E
- **CI/CD:** GitHub Actions with ktlint

### Current Database Schema
- Categories (id, name, merchants, enabled)
- Senders (id, name, addresses, rules, enabled)
- Payments (id, amount, currency, merchant, category, senderAddress, receivedAt, ruleId)
- IgnoreRules (id, senderId, pattern, description, enabled)
- Incomes (id, amount, currency, source, timestamp, balance, messageHash, senderAddress, receivedAt, ruleId)

### Recent Completed Features
- Phase 5.9: SMS History Export (JSON/CSV)
- Phase 5.10: Income Tracking (database infrastructure)
- Phase 5.11: Light/Dark Mode Toggle
- Regex Builder layout reorganization

### Test Status
- Unit tests: Passing (87.50% coverage)
- Appium E2E tests: 104 tests passing
- Test suites: Bug Report, Category Cascade, Category Management, Ignore Rules, Main Navigation, Payments Filter, Regex Builder, Sender Management, SMS to Payment Flow

### Session Task
[Your task description here]

### Files to Read First
1. `TODO.md` - For current state and pending tasks
2. `AGENTS.md` - For coding guidelines
3. Relevant source files for your task

### Important Guidelines
- Read files before modifying
- Follow existing code patterns
- Run tests before committing: `./gradlew testDebugUnitTest`
- For Appium tests: `make appium-docker-start && make test-appium`
```

---

## Feature Request Template

```
## Feature Request: [Feature Name]

### Description
[What the feature should do]

### User Story
As a [user type], I want to [action] so that [benefit].

### Acceptance Criteria
- [ ] Criterion 1
- [ ] Criterion 2
- [ ] Criterion 3

### Technical Notes
- Database changes needed: [Yes/No - describe if yes]
- New activities/screens: [List if any]
- Affected existing code: [List files]

### Testing Requirements
- [ ] Unit tests for new logic
- [ ] Appium E2E test for UI flows
- [ ] Edge cases to consider: [List]

### References
- Related bugs: [Link to bugs/ files if any]
- Similar features: [Reference existing implementations]
```

---

## Bug Fix Template

```
## Bug Fix Session

### Bug Reference
[Link to bugs/BUG-XXX-description.md]

### Problem Summary
[Brief description of the issue]

### Root Cause Analysis
[What's causing the bug - if known]

### Proposed Fix
[High-level approach to fix]

### Testing Plan
1. Create/update unit test to reproduce bug
2. Implement fix
3. Verify fix with tests
4. Run full test suite
5. Update bug documentation

### Files to Examine
- [List relevant files]
```

---

## Test Writing Template

```
## Test Writing Session

### Target Coverage
- Component: [class/package name]
- Current coverage: [X%]
- Target coverage: [Y%]

### Test Types Needed
- [ ] Unit tests (testDebugUnitTest)
- [ ] Integration tests (connectedAndroidTest)
- [ ] Appium E2E tests (test-appium)

### Test Scenarios
1. Happy path: [description]
2. Edge case: [description]
3. Error case: [description]

### Running Tests
```bash
# Unit tests
./gradlew testDebugUnitTest

# Specific test class
./gradlew testDebugUnitTest --tests "com.example.banksmstracker.ClassName"

# Appium tests
make appium-docker-start
make test-appium
make appium-docker-stop

# Coverage report
./gradlew jacocoTestReport
```
```

---

## Quick Commands Reference

```bash
# Build
./gradlew assembleDebug

# Install on device
./gradlew installDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run Appium tests
make appium-docker-start && make test-appium && make appium-docker-stop

# Code coverage
./gradlew jacocoTestReport
./gradlew jacocoCoverageVerification

# Lint
./gradlew ktlintCheck
./gradlew ktlintFormat

# Full CI pipeline
make ci
```

---

## Notes

- Always check `TODO.md` "Current Session" section for latest context
- Bug files in `bugs/` directory have detailed reproduction steps
- Database migrations are in `BankSmsDatabase.kt`
- All UI activities are in `ui/` package
- Tests are in `src/test/` (unit) and `src/androidTest/` (instrumented)
