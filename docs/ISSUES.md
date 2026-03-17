# BankSMSTracker - Known Issues & Blockers

This document tracks known issues, design decisions requiring user input, and blockers that cannot be resolved without external action.

## Legend
- **BLOCKER** - Cannot proceed without resolution
- **DESIGN** - Requires design decision
- **LIMITATION** - Known limitation, workaround documented
- **TODO** - Deferred, will be addressed later

---

## Active Issues

### TODO-002: Pre-existing ktlint Violations
**Status:** Documented
**Created:** 2025-12-29

**Description:**
The ktlint integration revealed pre-existing code style violations in the codebase. These need to be fixed before CI can pass lint checks.

**Violations (cannot be auto-corrected):**
1. File naming: `SmsReceptionE2ETest.kt` should be `SmsReceiverE2ETest.kt`
2. Property naming: `TAG` constants should use lowercase camelCase (`tag`)
3. Various other violations fixed by `./gradlew ktlintFormat`

**Resolution:**
- Run `./gradlew ktlintFormat` to fix auto-correctable issues
- Manually fix file naming and property naming violations
- Or disable specific rules in `.editorconfig`

---

### DESIGN-001: Category Cascade Implementation
**Status:** Needs Decision
**Created:** 2025-12-29

**Description:**
When a regex rule's category assignment changes, should all previously parsed payments be updated to the new category?

**Options:**
1. **Full cascade** - Update all historical payments processed by that rule
2. **Forward-only** - Only apply new category to future payments
3. **User choice** - Prompt user to choose per change

**Impact:** Requires tracking which rule parsed each payment (adds `ruleId` column to payments table)

**Decision:** Pending

---

### DESIGN-002: Config Import Merge Strategy
**Status:** Needs Decision
**Created:** 2025-12-29

**Description:**
When importing configuration from JSON file, how to handle conflicts?

**Options:**
1. **Append only** - Add new items, skip existing
2. **Merge** - Combine addresses/rules/merchants for matching senders/categories
3. **Replace** - Overwrite existing with imported
4. **User choice** - Prompt for each conflict

**Decision:** Pending

---

### LIMITATION-001: SMS Read Permission on Android 10+
**Status:** Documented
**Created:** 2025-12-29

**Description:**
On Android 10+ (API 29+), the `READ_SMS` permission requires additional justification for Play Store distribution. Apps must demonstrate core functionality requires SMS access.

**Workaround:**
- Real-time SMS processing works with `RECEIVE_SMS` permission
- Retrospective SMS parsing feature may need to be disabled for Play Store builds
- Consider side-loading APK distribution for full feature set

---

### LIMITATION-002: Test Data Duplication
**Status:** Documented
**Created:** 2025-12-29

**Description:**
Test fixtures need to exist in multiple locations:
- `app/src/main/assets/default_rules.json` - Production defaults
- `app/src/test/resources/default_rules.json` - Unit tests
- `app/src/androidTest/assets/` - Instrumented tests

**Workaround:**
Create Gradle task to copy test fixtures during build. Not yet implemented.

---

## Resolved Issues

### TODO-001: Appium E2E Test Setup
**Status:** Resolved
**Created:** 2025-12-29
**Resolved:** 2026-03-17

**Description:**
Full UI automation with Appium requires:
- Appium server setup
- WebDriver dependencies
- Test device/emulator configuration

**Resolution:**
Appium infrastructure is fully implemented. Docker-based Appium server configured in `docker-compose.yml`. 116+ tests across 10 test classes in `app/src/test/java/.../appium/`. Smoke subset (~18 tests) available via `make test-smoke`. Full suite via `make test-appium`. Allure reporting integrated with `@Epic`/`@Feature`/`@Step` annotations.

---

## Issue Template

```markdown
### [TYPE]-XXX: Title
**Status:** [Needs Decision | In Progress | Documented | Resolved]
**Created:** YYYY-MM-DD
**Resolved:** YYYY-MM-DD (if applicable)

**Description:**
Brief description of the issue.

**Options:** (if DESIGN type)
1. Option A
2. Option B

**Workaround:** (if LIMITATION type)
Description of workaround.

**Resolution:** (when resolved)
What was decided/implemented.
```
