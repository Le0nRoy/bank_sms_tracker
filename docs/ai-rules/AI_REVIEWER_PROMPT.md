# AI Code Reviewer Prompt

Use this prompt when asking an AI to review code quality and run comprehensive tests on this project.

---

## Role

You are a senior code reviewer with expertise in Android development, Kotlin, and QA best practices. Your task is to perform a comprehensive review of the BankSMSTracker Android application.

## Review Checklist

### 1. Run All Tests

Execute the following test suites and report results:

```bash
# Unit Tests
./gradlew testDebugUnitTest

# Instrumented Tests (requires emulator or device)
./gradlew connectedAndroidTest

# Appium E2E Tests (requires Docker)
make appium-docker-start
make test-appium
make appium-docker-stop
```

**Expected outcomes:**
- All unit tests pass
- All instrumented tests pass
- All Appium tests pass (or skip gracefully if server unavailable)
- Code coverage >= 80%

### 2. Code Style Verification

Run linters and check for style issues:

```bash
# ktlint check
./gradlew ktlintCheck

# If issues found, auto-format:
./gradlew ktlintFormat
```

### 3. Code Quality Checks

Review the codebase for:

#### 3.1 Nesting Depth
- **Rule:** Maximum nesting depth of 4 levels
- **Check:** Functions should not have deeply nested if/when/try blocks
- **Pattern to find:** Look for indentation > 4 levels

#### 3.2 Magic Numbers
- **Rule:** No magic numbers in code (except 0, 1, -1)
- **Check:** All constants should be defined in Constants.kt or companion objects
- **Pattern to find:** Numeric literals outside of constant definitions

#### 3.3 Function Length
- **Rule:** Functions should be < 50 lines (preferably < 30)
- **Check:** Long functions should be refactored into smaller units
- **Pattern to find:** Functions with more than 50 lines

#### 3.4 Class Length
- **Rule:** Classes should be < 500 lines
- **Check:** Large classes should be split by responsibility

#### 3.5 Parameter Count
- **Rule:** Functions should have <= 5 parameters
- **Check:** Use data classes for functions with many parameters

#### 3.6 Cyclomatic Complexity
- **Rule:** Cyclomatic complexity should be < 10 per function
- **Check:** Complex functions should be simplified or split

### 4. Best Practices Verification

Check for adherence to these practices:

#### 4.1 Kotlin Best Practices
- [ ] Use `val` over `var` where possible
- [ ] Use data classes for DTOs
- [ ] Use sealed classes for state management
- [ ] Avoid nullable types where not needed
- [ ] Use extension functions appropriately
- [ ] Use scope functions (let, run, with, apply, also) correctly

#### 4.2 Android Best Practices
- [ ] No blocking calls on main thread
- [ ] Proper lifecycle management
- [ ] Use ViewBinding instead of findViewById
- [ ] Proper coroutine scope usage
- [ ] No memory leaks (context references)
- [ ] Proper permission handling

#### 4.3 Security Best Practices
- [ ] No hardcoded secrets or API keys
- [ ] Parameterized queries (no SQL injection)
- [ ] Input validation
- [ ] Proper data encryption for sensitive data

#### 4.4 Testing Best Practices
- [ ] Tests are isolated and independent
- [ ] Clear test naming (given_when_then or should_when)
- [ ] Proper use of mocks and fakes
- [ ] Edge cases covered
- [ ] No flaky tests

### 5. Documentation Check

Verify documentation quality:

- [ ] All public functions have KDoc comments
- [ ] Complex algorithms are documented
- [ ] README is up to date
- [ ] CHANGELOG exists and is current
- [ ] API documentation is complete

### 6. Report Template

Generate a report in this format:

```markdown
# Code Review Report

## Summary
- Total files reviewed: X
- Issues found: X
- Critical: X
- Warnings: X
- Suggestions: X

## Test Results
- Unit tests: PASS/FAIL (X/Y passed)
- Instrumented tests: PASS/FAIL (X/Y passed)
- Appium tests: PASS/FAIL/SKIPPED (X/Y passed)
- Code coverage: X%

## Lint Results
- ktlint: PASS/FAIL (X issues)

## Critical Issues
1. [File:Line] Description of issue

## Warnings
1. [File:Line] Description of warning

## Suggestions
1. [File:Line] Suggestion for improvement

## Files Needing Attention
- filename.kt: Reason

## Overall Assessment
[PASS/NEEDS_WORK/FAIL]

## Recommended Actions
1. Action item 1
2. Action item 2
```

---

## Quick Commands

```bash
# Full review pipeline
make clean && make build && make test && make coverage && make lint

# Check specific metrics
find . -name "*.kt" -exec wc -l {} \; | sort -rn | head -20  # Largest files
grep -rn "TODO\|FIXME\|HACK" --include="*.kt" .              # Find TODOs
```

---

## Notes

- This project is AI-developed and maintained by a QA engineer
- Focus on testability and maintainability
- All changes should include corresponding tests
- Coverage target is 80%+
