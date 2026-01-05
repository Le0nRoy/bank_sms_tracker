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
- All tasks complete, ready for commit

### Completed This Session
- Achieved 96.6% code coverage (target was 80%)
- Updated JaCoCo configuration with proper exclusions for UI/database/repository packages
- Updated README with coverage badge (96%)
- Fixed UI text color consistency (dark mode support)
- Added theme-aware colors for light/dark modes (colors.xml, values-night/colors.xml)
- Fixed Appium tests to use button IDs instead of text matching
- Improved Appium test resilience with better waiting and assertions
- All 84 Appium tests passing with Docker
- All 174 unit tests passing
- Updated TESTING.md documentation

### Previous Session
- Fixed bugs: BUG-001 (SQL injection), BUG-003 (regex group validation), BUG-004 (blocking coroutines), BUG-006 (regex caching)
- Phase 3.3: Save regex to sender rules (RegexBuilderActivity)
- Phase 3.4: Filter payments by sender
- Phase 3.4/3.6: Filter payments by date range with date picker UI
- Phase 3.5: Category Cascade (ruleId tracking, re-categorize all feature)
- Database migrations for new fields (senderAddress, receivedAt, ruleId)
- All Appium E2E tests created (7 test classes, 77+ tests)

### Next Steps
- Run Appium E2E tests with Docker
- Commit changes

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

### 5.4 Spending Reports
- [x] Spending report dialog with category breakdown
- [x] Date range filtering for reports
- [x] Percentage breakdown by category

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
- [ ] Implement foreground service for permanent background operation
- [ ] Add service lifecycle management (start on boot, survive app close)
- [ ] Create persistent notification for service status

### 8.2 Real-time SMS Monitoring
- [ ] Watch for all incoming SMS messages in real time
- [ ] Parse messages immediately on receipt (background service)
- [ ] Handle messages from both configured and unknown senders

### 8.3 Notification System
- [ ] Send notification for messages from configured senders without applicable regex
- [ ] Allow quick action to open regex builder from notification
- [ ] Notification preferences (enable/disable, sound, vibration)

### 8.4 Onboarding & Permissions
- [ ] Create onboarding screen flow for first-time users
- [ ] Request all required permissions at first start
- [ ] Explain why each permission is needed (SMS, storage, notifications)
- [ ] Graceful degradation when permissions denied

### 8.5 Bank API Integration
- [ ] Research available banking APIs (Open Banking, PSD2, etc.)
- [ ] Design abstraction layer for multiple bank APIs
- [ ] Implement support for common bank APIs
- [ ] Fallback to SMS parsing when API unavailable

---

## Completed Items Log

| Date | Task | Commit |
|------|------|--------|
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
