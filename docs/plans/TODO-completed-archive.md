# TODO Completed Tasks Archive

> **STATUS: ARCHIVED — PENDING DELETION**
> Contains fully-completed phases and the historical completion log extracted from `TODO.md`.
> Review these against `docs/design/DESIGN.md` to confirm all items are reflected there,
> then delete this file.

---

## Phase 1: Project Setup & Documentation ✅ ALL DONE

### 1.1 Documentation
- [x] Create CLAUDE.md (references AGENTS.md)
- [x] Create AGENTS.md (AI agent guidelines)
- [x] Create docs/DESIGN.md (high-level architecture)
- [x] Create TODO.md (this file)
- [x] Create ISSUES.md (for unsolvable problems)
- [x] Create docs/TESTING.md (test documentation)

### 1.2 Development Infrastructure
- [x] Add Kotlin linter (ktlint)
- [x] Configure linter for local development (.editorconfig)
- [x] Create GitHub CI workflow (lint stage only)

### 1.3 Testing Infrastructure
- [x] Configure JUnit 5 for unit tests
- [x] Configure AndroidJUnit5 for instrumented tests
- [x] Create test wrapper class for reporting (TestReporter, AndroidTestReporter)
- [x] Document test coverage requirements (docs/TESTING.md)

---

## Phase 2: Core Features ✅ ALL DONE

### 2.1 SMS Processing
- [x] SmsReceiver - BroadcastReceiver for SMS_RECEIVED
- [x] PaymentProcessor - Parse SMS with regex rules
- [x] Category assignment by merchant name
- [x] Payment deduplication by message hash

### 2.2 Configuration Management
- [x] ConfigRepository - Load/save configuration
- [x] Room database persistence
- [x] Seed from default_rules.json
- [x] Export config to JSON file

### 2.3 UI - Basic
- [x] MainActivity - Navigation hub
- [x] CategoriesActivity - Add/edit categories
- [x] SendersActivity - Add/edit senders
- [x] CheckSendersActivity - Test regex patterns
- [x] ApplyRulesActivity - Manual rule application

---

## Phase 3: Extended Features ✅ ALL DONE

### 3.1 Config Import/Export
- [x] Export configuration to JSON
- [x] Import configuration from JSON file
- [x] Merge imported rules with existing (append, not replace)
- [x] Handle duplicate senders/categories during import

### 3.2 Enable/Disable Functionality
- [x] Add `enabled` field to Sender entity
- [x] Add `enabled` field to PaymentRegexRule entity
- [x] Add `enabled` field to Category entity
- [x] UI toggles for enable/disable
- [x] Filter disabled items during processing

### 3.3 Regex Builder UI
- [x] Create RegexBuilderActivity
- [x] Real-time regex matching preview
- [x] Highlight captured groups
- [x] Save regex to sender rules

### 3.4 Payments Management
- [x] Create PaymentsActivity (list view)
- [x] Filter payments by sender
- [x] Filter payments by category
- [x] Filter payments by date range
- [x] Payment CSV export

### 3.5 Category Cascade
- [x] Track which rule parsed each payment (ruleId field added)
- [x] Update payments when rule category changes
- [x] Batch category reassignment (Re-categorize All button)

### 3.6 Retrospective SMS Processing
- [x] Request READ_SMS permission (ApplyRulesActivity)
- [x] Date range selector UI (in PaymentsActivity)
- [x] Query SMS inbox (ApplyRulesActivity)
- [x] Process historical messages (ApplyRulesActivity)

### 3.7 Bug Report
- [x] Configure bug report email in BuildConfig
- [x] Create bug report activity/dialog
- [x] Collect device info and logs
- [x] Send via email intent

---

## Phase 4: Testing ✅ MOSTLY DONE (one item open: Codecov)

### 4.1 Unit Tests (all done)
- [x] ConfigRepositoryTest, PaymentRepositoryTest, PaymentProcessorTest, ConfigLoaderTest
- [x] PaymentProcessorEnabledTest, ImportResultTest, DataClassesTest
- [x] PaymentProcessorEdgeCaseTest, ConfigLoaderEdgeCaseTest, PaymentRepositoryEdgeCaseTest
- [x] ConstantsTest
- [x] 96.6% code coverage achieved

### 4.2 Integration Tests (all done)
- [x] ConfigRepositoryRoomTest, RoomPaymentRepositoryTest, ConfigImportE2ETest, SmsProcessingPipelineE2ETest

### 4.3 E2E / Appium Tests (all done)
- [x] SmsReceptionE2ETest, SmsReceptionWithRoomE2ETest, ConfigPersistenceE2ETest, ConfigExportE2ETest
- [x] PaymentDeduplicationE2ETest, ConfigImportE2ETest, EnabledDisabledE2ETest
- [x] 10 Appium test classes, 116+ tests, Docker Appium setup

### 4.4 Bug Fixes (all done)
- [x] BUG-001 SQL injection, BUG-002 race condition ConfigRepository, BUG-003 regex validation
- [x] BUG-004 blocking coroutines, BUG-005 race condition PaymentsActivity, BUG-006 regex cache

---

## Completed Items Log (Historical)

| Date | Task | Commit |
|------|------|--------|
| 2025-12-29 | Initial Room integration | 9e334f1 |
| 2025-12-29 | CLAUDE.md, docs/DESIGN.md, TODO.md, ISSUES.md, docs/TESTING.md created | – |
| 2025-12-29 | ktlint + .editorconfig added | – |
| 2025-12-29 | GitHub CI lint workflow added | – |
| 2025-12-29 | TestReporter classes created | – |
| 2025-12-29 | AGENTS.md code style rules added | – |
| 2025-12-30 | Phase 3 features: enabled toggles, import, payments | 94ab266 |
| 2025-12-30 | RegexBuilderActivity, BugReportActivity created | 94ab266 |
| 2025-12-30 | PaymentProcessorEnabledTest, ImportResultTest, DataClassesTest added | 94ab266 / 67189bd |
| 2025-12-30 | JaCoCo coverage configured; CI updated | 67189bd |
| 2025-12-30 | ConfigImportE2ETest + EnabledDisabledE2ETest added | 6f083cb |
| 2025-12-30 | Appium UI E2E tests setup; Docker Appium; MainNavigation/BugReport/RegexBuilder tests | – |
| 2025-12-31 | Fixed BUG-001, BUG-003, BUG-004, BUG-006 | 420175a |
| 2025-12-31 | Phase 3.3–3.5: save regex, filter payments, category cascade; DB migrations v3/v4 | – |
| 2026-01-06 | Phase 5.3–5.7: RegexBuilder, Reports, ApplyRules, PaymentDetails, IgnoreRules | 6f62277 |
| 2026-01-06 | DB migration v5 (ignore_rules); IgnoreRulesAppiumTest; 102 Appium tests passing | 6f62277 |
| 2026-01-06 | Phase 5.9–5.11: SMS Export, Income Tracking (entity/DAO/v6→v7), Theme Toggle | – |
| 2026-01-14 | Phase 5.12: Localization + SettingsActivity + Russian translations | 4395f76 |
| 2026-03-09 | Phase 1 of PROMPT.md: named groups, clear buttons, multiline, preset split, auto-scroll, ignored color | 462976a |
| 2026-03-09 | Phase 2 of PROMPT.md: CardView rules, menu reorg, auto-fill sender, bug report, date approx | 803bbdc |
| 2026-03-09 | Phase 3 of PROMPT.md: placeholder chips, recategorize-on-save, perf tests | 6f42259 |
| 2026-03-09 | Phase 4 of PROMPT.md: personal data agreement, debug/prod split, Allure reporting | da0aeb4 |
| 2026-03-13 | TBC Bank rules expanded, real-data tests, Appium sender test fixes | 5e1c797 |
