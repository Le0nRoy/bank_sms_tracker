# BankSMSTracker - Project TODO

## Legend
- [ ] Not started
- [~] In progress
- [x] Completed

---

## Current Session

> **Purpose:** Track active work for session continuity during context compacting.
> Update this section before context is summarized (~2% remaining).

### Active Task
- ALL tasks from the 2026-03-13 PROMPT.md batch are complete on branch `feature/create-pov`.

### Completed This Session
- Phase 9.0: Extracted `RegexTemplateUtils.kt` (`regexToTemplate`/`templateToRegex`) and `RegexSpanUtils.kt` (`applyPlaceholderSpans`)
- Phase 9.1: Fixed BUG-007, BUG-008, BUG-009, BUG-010 (all TDD)
- Phase 9.2: Task 4.1 (preserve sender), Task 4.4 (decode newlines in Regex Builder), Task 3.0 (formatted display in Senders)
- Task 2.1: Merchant search/filter in Payments (UI + unit + instrumented + Appium tests, all passing)
- BUG-011: Non-nullable `timestamp` — DB migration v8→v9, removed `receivedAt`, `smsReceivedAt` param added to `processMessage()`
- Task 1.2: Merchant data class with `displayName` field; DB migration v9→v10
- Task 1.3: Regex support for merchant matching (`isRegex` flag in `PaymentProcessor`)
- Task 1.1: "Move to Category" dialog for each merchant in Categories screen
- Task 4.2: `PatternListActivity` for browsing and selecting existing patterns

### Next Steps
- Branch `feature/create-pov` is ready for review/merge. All 2026-03-13 prompt features are done.
- Future items tracked below (9.F1–9.F3) are deferred to a later session.

---

## Phase 1: Project Setup & Documentation

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
- [x] Add instrumented test CI job (Espresso, API 35 emulator, issue #11)

### 1.3 Testing Infrastructure
- [x] Configure JUnit 5 for unit tests
- [x] Configure AndroidJUnit5 for instrumented tests
- [x] Create test wrapper class for reporting (TestReporter, AndroidTestReporter)
- [x] Document test coverage requirements (docs/TESTING.md)

---

## Phase 2: Core Features (Implemented)

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

## Phase 3: Extended Features

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

## Phase 4: Testing

### 4.1 Unit Tests
- [x] ConfigRepositoryTest
- [x] PaymentRepositoryTest
- [x] PaymentProcessorTest
- [x] ConfigLoaderTest
- [x] PaymentProcessorEnabledTest (enabled/disabled functionality)
- [x] ImportResultTest (import result sealed class)
- [x] DataClassesTest (data class coverage)
- [x] PaymentProcessorEdgeCaseTest (edge cases: empty inputs, boundaries, regex patterns)
- [x] ConfigLoaderEdgeCaseTest (edge cases: invalid JSON, empty configs, special chars)
- [x] PaymentRepositoryEdgeCaseTest (edge cases: null values, boundaries, ordering)
- [x] ConstantsTest (Constants utility class)
- [x] Achieve 80%+ code coverage (96.6% achieved)

### 4.1.1 Code Coverage
- [x] Add JaCoCo coverage plugin
- [x] Configure local coverage reports (./gradlew jacocoTestReport)
- [x] Add coverage to CI workflow
- [x] Add coverage badge to README
- [ ] Set up Codecov or similar service for PR coverage tracking

### 4.2 Integration Tests
- [x] ConfigRepositoryRoomTest
- [x] RoomPaymentRepositoryTest
- [x] Config import/export integration (ConfigImportE2ETest)
- [x] Full SMS processing pipeline (SmsProcessingPipelineE2ETest)

### 4.3 E2E Tests
- [x] SmsReceptionE2ETest
- [x] SmsReceptionWithRoomE2ETest
- [x] ConfigPersistenceE2ETest
- [x] ConfigExportE2ETest
- [x] PaymentDeduplicationE2ETest
- [x] ConfigImportE2ETest (JSON import + merge tests)
- [x] EnabledDisabledE2ETest (enabled/disabled filtering)
- [x] Set up Appium for UI automation (dependencies + base test class)
- [x] E2E: Category management flow (CategoryManagementAppiumTest)
- [x] E2E: Sender management flow (SenderManagementAppiumTest)
- [x] E2E: Full SMS to payment flow (SmsToPaymentFlowAppiumTest)
- [x] E2E: Main navigation flow (MainNavigationAppiumTest)
- [x] E2E: Bug report feature (BugReportAppiumTest)
- [x] E2E: Regex builder feature (RegexBuilderAppiumTest)
- [x] Docker setup for Appium server (docker-compose.appium.yml)

### 4.4 Bug Fixes
- [x] Fix BUG-001: SQL injection in ApplyRulesActivity
- [x] Fix BUG-002: Race condition in ConfigRepository (added Mutex synchronization)
- [x] Fix BUG-003: Missing regex group validation
- [x] Fix BUG-004: Blocking coroutines in RoomPaymentRepository
- [x] Fix BUG-005: Race condition in PaymentsActivity (fixed by proper coroutine sequencing)
- [x] Fix BUG-006: Regex pattern not cached

### 4.5 Bug Documentation

All bugs are documented in the `bugs/` directory with detailed reports:

| Bug ID | Title | Status | File |
|--------|-------|--------|------|
| BUG-001 | SQL Injection in ApplyRulesActivity | Fixed | [BUG-001](bugs/BUG-001-sql-injection-apply-rules.md) |
| BUG-002 | Race Condition in ConfigRepository | Fixed | [BUG-002](bugs/BUG-002-race-condition-config-repository.md) |
| BUG-003 | Missing Regex Group Validation | Fixed | [BUG-003](bugs/BUG-003-missing-regex-group-validation.md) |
| BUG-004 | Blocking Coroutines in Room Repository | Fixed | [BUG-004](bugs/BUG-004-blocking-coroutines-room-repository.md) |
| BUG-005 | Race Condition in PaymentsActivity | Fixed | [BUG-005](bugs/BUG-005-race-condition-payments-activity.md) |
| BUG-006 | Regex Pattern Not Cached | Fixed | [BUG-006](bugs/BUG-006-regex-not-cached.md) |

---

## Phase 5: UI Polish

### 5.1 Visual Improvements
- [ ] Material Design 3 components
- [ ] Consistent color scheme
- [x] Loading indicators (CategoriesActivity, SendersActivity)
- [x] Empty state views (CategoriesActivity, SendersActivity)
- [x] Error state views with retry functionality

### 5.2 UX Improvements
- [x] Input validation feedback (duplicate validation for senders/categories)
- [x] Confirmation dialogs for destructive actions (recategorize, save regex)
- [ ] Undo functionality for deletions
- [ ] Keyboard navigation
- [x] Grouped main menu layout

### 5.3 Regex Builder Enhancements
- [x] Select SMS message from inbox (instead of just pasting)
- [x] SMS message separator in selection dialog
- [x] Filter SMS selection by configured senders only
- [x] Show sender selection dialog before showing messages
- [x] Display full message content in selection dialog
- [x] Better visual separation of messages (highlight each in a box)
- [x] Allow choosing existing regex pattern to modify it (spinner)

### 5.4 Spending Reports
- [x] Spending report dialog with category breakdown
- [x] Date range filtering for reports
- [x] Percentage breakdown by category
- [x] Default date range: first to last day of current month
- [x] Show actual dates in report even for empty range (first/last payment dates)
- [x] Apply date filter to both payment list and spending report

### 5.5 Apply Rules Improvements
- [x] Choose time range for which to apply rules
- [x] Default time range: since last processed payment until now
- [x] Failed messages have button to redirect to regex builder with message pre-filled
- [x] Better error formatting: multiline with "Error parsing:\nMessage content"

### 5.6 Payment Details & Categorization
- [x] Click on payment opens detail window
- [x] Add seller from payment to existing category
- [x] Create new category and add seller from payment

### 5.7 Ignore Rules for Spam
- [x] Add ignore rules configuration (separate from payment rules)
- [x] Database entity and DAO for ignore rules
- [x] UI for managing ignore rules (IgnoreRulesActivity)
- [x] Toggle enabled/disabled state for ignore rules

### 5.8 Simplified Rules Creation ✅ Implemented (commit 6f42259)
**Goal: More user-friendly regex creation**

Implemented (placeholder chip approach):
- Preset buttons insert `⟨amount⟩`, `⟨currency⟩`, etc. (Unicode angle-bracket markers)
- `TextWatcher` renders placeholders as colored bold spans (chips) in the regex EditText
- `templateToRegex()` / `regexToTemplate()` convert between chip view and stored regex
- Named groups (`(?<amount>...)`) used throughout; positional groups removed

Full custom token-view approach (drag-and-drop, separate token model) remains a future option.

### 5.9 SMS History Export
- [x] SmsExportActivity with date range filtering
- [x] Sender filtering (all or specific sender)
- [x] Export to JSON format
- [x] Export to CSV format
- [x] Message count preview before export
- [x] Share exported file via Intent

### 5.10 Income Tracking
- [x] Income data class with serialization
- [x] IncomeEntity database entity
- [x] IncomeDao with CRUD operations
- [x] Database migration v6→v7 (incomes table)
- [x] Income processing rules (INCOME rule type in PaymentProcessor; default_rules.json has salary/deposit/transfer rules)
- [ ] Income UI screen (IncomesActivity — list view of recorded income entries)

### 5.11 Theme Toggle
- [x] Light/Dark/System theme options
- [x] SharedPreferences persistence
- [x] Theme applied on app startup
- [x] Settings dialog in MainActivity

### 5.12 Localization & Multi-Language Support
- [x] Create SettingsActivity (replaces theme popup dialog)
- [x] Add language selection (System/English/Russian)
- [x] Implement per-app language using AppCompatDelegate.setApplicationLocales()
- [x] Extract hardcoded strings to resources (90+ strings)
- [x] Create Russian translations (values-ru/strings.xml, 331 strings)
- [x] Add localization tests (BankSmsTrackerAppTest, SettingsAppiumTest, LocaleE2ETest)
- [x] Move theme settings from popup to SettingsActivity

---

## Phase 6: Production Readiness

### 6.1 Default Configurations
- [ ] Default senders for common banks
- [ ] Default categories
- [ ] Default regex patterns
- [ ] Build-time configuration (email, etc.)

### 6.2 Documentation
- [x] User manual (docs/USER_MANUAL.md)
- [ ] API documentation (for extensibility)
- [x] Release notes (CHANGELOG.md)
- [x] README with build instructions
- [x] AI reviewer prompt (AI_REVIEWER_PROMPT.md)

---

## Phase 7: Analytics & Reporting

### 7.0 Anonymous Usage Statistics (Future)
- [ ] Track most common categories used
- [ ] Track number of configured senders per user
- [ ] Track regex patterns usage frequency
- [ ] Track app usage patterns (screens visited, features used)
- [ ] Implement opt-in analytics collection
- [ ] Privacy-first design (no PII collection)
- [ ] Analytics dashboard for aggregate insights

---

## Phase 8: Background Service & Real-time Processing

### 8.1 Background Service
- [x] Implement foreground service for permanent background operation
- [x] Add service lifecycle management (start on boot, survive app close)
- [x] Create persistent notification for service status

### 8.2 Real-time SMS Monitoring
- [x] Watch for all incoming SMS messages in real time
- [x] Parse messages immediately on receipt (background service)
- [x] Handle messages from both configured and unknown senders

### 8.3 Notification System
- [x] Send notification for messages from configured senders without applicable regex
- [x] Allow quick action to open regex builder from notification
- [x] Notification preferences (enable/disable, sound, vibration)

### 8.4 Onboarding & Permissions
- [x] Create onboarding screen flow for first-time users
- [x] Request all required permissions at first start
- [x] Explain why each permission is needed (SMS, storage, notifications)
- [x] Graceful degradation when permissions denied

### 8.5 Bank API Integration
- [ ] Research available banking APIs (Open Banking, PSD2, etc.)
- [ ] Design abstraction layer for multiple bank APIs
- [ ] Implement support for common bank APIs
- [ ] Fallback to SMS parsing when API unavailable

---

## Phase 9: PROMPT.md Batch (2026-03-13)

> **Plan:** [docs/plans/2026-03-13-prompt-features.md](docs/plans/2026-03-13-prompt-features.md)

### 9.0 Prerequisites (before bug fixes)
- [x] Extract `RegexFormatUtils` shared utility → `RegexTemplateUtils.kt` + `RegexSpanUtils.kt`

### 9.1 Bug Fixes (TDD — write tests first)
- [x] Fix BUG-007: Category re-assignment from payment detail doesn't update existing payments
- [x] Fix BUG-008: End date filter shows no payments when end < start date
- [x] Fix BUG-009: Start date filter shows earlier payments (investigate race condition)
- [x] Fix BUG-010: `⟨merchant⟩` block not highlighted in Regex Builder

### 9.2 Quick Wins
- [x] Task 4.1: Preserve sender selection after regex save
- [x] Task 4.4: Show human-readable newlines when loading pattern into Regex Builder
- [x] Task 3.0: Show formatted (highlighted + newlines) regexes in Senders screen

### 9.3 Merchant Model Enhancements
- [x] DB migration v9→v10: add `displayName` and `isRegex` columns to `category_merchants` (rename `name` → `pattern`)
- [x] Task 1.2: Optional display name for merchants (`Merchant` data class with `displayName`; shown in Categories screen)
- [x] Task 1.3: Regex support for merchant matching in `PaymentProcessor` (`isRegex` flag triggers `containsMatchIn`)
- [x] Task 1.1: "Move to Category" button for each merchant in Categories screen

### 9.4 New Features
- [x] Task 2.1: Merchant search/filter in Payments screen
- [x] Task 4.2: Edit Existing Pattern — open as separate `PatternListActivity`

---

## Phase 9 Maintenance

### 9.M1 Remove Dead Code
- [x] `SenderRuleEntity` already removed from `database/Entities.kt`
- [x] Delete `PaymentProcessor.getPaymentFromMessage()` — migrate tests to `getMessageResult()`
- [x] `SmsAddressMatcher.matchesAny(Collection<String>)` overload already removed
- [x] `RuleDao` already pruned to only used methods
- [x] `IncomeDao` already pruned to only `insertIncome`
- [x] `IgnoreRuleDao` already pruned to only used methods
- [x] `ConfigDao` `updateMerchant`/`deleteMerchant`/`updateAddress`/`deleteAddress` already removed

---

## Phase 9 Future Items

### 9.F1 Spending Report Diagrams (Task 2.5)
**Plan:** Bar chart/pie chart visualization of spending by category using Vico library.
- [x] Research chart library (Vico chosen — Compose-friendly, active maintenance)
- [x] Design bar chart / pie chart for category breakdown
- [x] Integrate into spending report dialog

### 9.F2 Central Bank API for Currency Rates (Task 2.6)
**Plan:** Integrate APIs from central banks (e.g., National Bank of Georgia, ECB) to retrieve exchange rates. Use rates to normalize multi-currency payments to a base currency. Needs abstraction layer per bank, fallback to static rates when offline.
- [ ] Research available central bank APIs (NBG, ECB, etc.)
- [ ] Design abstraction layer for multiple bank APIs
- [ ] Implement exchange rate retrieval and caching
- [ ] Apply rates to normalize multi-currency payments

### 9.F3 Regex-Free Text Box in Regex Builder (Task 4.5)
**Plan:** Chip-based WYSIWYG editor where user sees only words and colored preset chips; regex is internal state only.
- [x] Design token model: words + preset chips rendered via SpannableStringBuilder
- [x] Implement chip-based display with hidden regex state
- [x] Regex transformation hidden from user (internal state only)
- [x] Full multiline support in token view
- [x] Toggle between regex-free and regex-full editor views

---

## Completed Items Log

| Date | Task | Commit |
|------|------|--------|
| 2026-03-17 | Task 4.2: PatternListActivity for browsing/selecting patterns | a9e7737 |
| 2025-12-29 | Initial Room integration | 9e334f1 |
| 2025-12-29 | CLAUDE.md created | - |
| 2025-12-29 | docs/DESIGN.md created | - |
| 2025-12-29 | TODO.md created | - |
| 2025-12-29 | ISSUES.md created | - |
| 2025-12-29 | docs/TESTING.md created | - |
| 2025-12-29 | ktlint + .editorconfig added | - |
| 2025-12-29 | GitHub CI lint workflow added | - |
| 2025-12-29 | TestReporter classes created | - |
| 2025-12-29 | AGENTS.md code style rules added | - |
| 2025-12-30 | Phase 3 features: enabled toggles, import, payments | 94ab266 |
| 2025-12-30 | RegexBuilderActivity created | 94ab266 |
| 2025-12-30 | BugReportActivity created | 94ab266 |
| 2025-12-30 | PaymentProcessorEnabledTest added | 94ab266 |
| 2025-12-30 | JaCoCo coverage configured | 67189bd |
| 2025-12-30 | CI workflow updated with tests + coverage | 67189bd |
| 2025-12-30 | AGENTS.md task tracking rule added | 67189bd |
| 2025-12-30 | ImportResultTest added | 67189bd |
| 2025-12-30 | DataClassesTest added | 67189bd |
| 2025-12-30 | ConfigImportE2ETest + EnabledDisabledE2ETest added | 6f083cb |
| 2025-12-30 | Fixed JUnit 5 Android test setup | 6f083cb |
| 2025-12-30 | Appium UI E2E tests setup | - |
| 2025-12-30 | Added MainNavigationAppiumTest (14 tests) | - |
| 2025-12-30 | Added BugReportAppiumTest (12 tests) | - |
| 2025-12-30 | Added RegexBuilderAppiumTest (10 tests) | - |
| 2025-12-30 | Docker Appium setup (docker-compose.appium.yml) | - |
| 2025-12-30 | Fixed Appium test isolation issues | - |
| 2025-12-31 | Documentation updates: DESIGN.md, TESTING.md, AGENTS.md | 5719d8e |
| 2025-12-31 | Added session continuity rules to AGENTS.md | 5719d8e |
| 2025-12-31 | Added Current Session section to TODO.md | 5719d8e |
| 2025-12-31 | Fixed BUG-001, BUG-003, BUG-004, BUG-006 | 420175a |
| 2025-12-31 | Phase 3.3: Save regex to sender rules | - |
| 2025-12-31 | Phase 3.4: Filter payments by sender | - |
| 2025-12-31 | Phase 3.4/3.6: Date range filter UI | - |
| 2025-12-31 | Phase 3.5: Category Cascade (ruleId, re-categorize all) | - |
| 2025-12-31 | Database migrations v3 (senderAddress, receivedAt) v4 (ruleId) | - |
| 2026-01-06 | Phase 5.3: Regex Builder enhancements | 6f62277 |
| 2026-01-06 | Phase 5.4: Spending Reports improvements | 6f62277 |
| 2026-01-06 | Phase 5.5: Apply Rules improvements | 6f62277 |
| 2026-01-06 | Phase 5.6: Payment Details & Categorization | 6f62277 |
| 2026-01-06 | Phase 5.7: Ignore Rules for Spam | 6f62277 |
| 2026-01-06 | Database migration v5 (ignore_rules table) | 6f62277 |
| 2026-01-06 | IgnoreRulesAppiumTest (10 tests) | 6f62277 |
| 2026-01-06 | Enhanced Appium tests (102 total, 100% pass) | 6f62277 |
| 2026-01-06 | Phase 5.9: SMS History Export (JSON/CSV) | - |
| 2026-01-06 | Phase 5.10: Income Tracking (entity, DAO, migration v6→v7) | - |
| 2026-01-06 | Phase 5.11: Light/Dark Mode Toggle | - |
| 2026-01-06 | Regex Builder layout reorganization | - |
| 2026-01-06 | Income data class tests (+9 tests) | - |
| 2026-01-06 | MainNavigationAppiumTest updates (+2 tests) | - |
| 2026-01-06 | Bug documentation linked to TODO.md | - |
| 2026-01-06 | All 104 Appium tests passing | - |
| 2026-01-14 | Phase 5.12: Localization & Multi-Language Support | - |
| 2026-01-14 | SettingsActivity created (theme + language) | - |
| 2026-01-14 | Russian translations (values-ru/strings.xml) | - |
| 2026-01-14 | Extracted 90+ hardcoded strings to resources | - |
| 2026-01-14 | BankSmsTrackerAppTest added (preference constants) | - |
| 2026-01-14 | SettingsAppiumTest added (12 tests) | - |
| 2026-01-14 | LocaleE2ETest enhanced (11 tests) | - |
| 2026-01-14 | Documentation updates (AGENTS.md, TESTING.md) | - |
| 2026-03-17 | BUG-011: Replace receivedAt with non-nullable timestamp (DB migration v8→v9) | e5ce8c0 |
| 2026-03-17 | Task 1.2: Merchant data class with displayName field | 706d6e6 |
| 2026-03-17 | Task 1.3: Regex matching support in merchant categories | 706d6e6 |
| 2026-03-17 | DB migration v9→v10: category_merchants table (name→pattern, +displayName, +isRegex) | 706d6e6 |
| 2026-03-17 | Feature 1.1: Move merchant between categories via dialog | a1d62e1 |
| 2026-03-17 | Task 8.1: SmsProcessingService foreground service | (8.1) |
| 2026-03-17 | Task 8.2: Real-time SMS monitoring in background service | (8.2) |
