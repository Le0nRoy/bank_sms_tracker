# BankSMSTracker - Project TODO

## Legend
- [ ] Not started
- [~] In progress
- [x] Completed

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

## Phase 3: Extended Features (TODO)

### 3.1 Config Import/Export
- [x] Export configuration to JSON
- [ ] Import configuration from JSON file
- [ ] Merge imported rules with existing (append, not replace)
- [ ] Handle duplicate senders/categories during import

### 3.2 Enable/Disable Functionality
- [ ] Add `enabled` field to Sender entity
- [ ] Add `enabled` field to PaymentRegexRule entity
- [ ] Add `enabled` field to Category entity
- [ ] UI toggles for enable/disable
- [ ] Filter disabled items during processing

### 3.3 Regex Builder UI
- [ ] Create RegexBuilderActivity
- [ ] Real-time regex matching preview
- [ ] Highlight captured groups
- [ ] Save regex to sender rules

### 3.4 Payments Management
- [ ] Create PaymentsActivity (list view)
- [ ] Filter payments by sender
- [ ] Filter payments by category
- [ ] Filter payments by date range
- [ ] Payment CSV export

### 3.5 Category Cascade
- [ ] Track which rule parsed each payment
- [ ] Update payments when rule category changes
- [ ] Batch category reassignment

### 3.6 Retrospective SMS Processing
- [ ] Request READ_SMS permission
- [ ] Date range selector UI
- [ ] Query SMS inbox
- [ ] Process historical messages

### 3.7 Bug Report
- [ ] Configure bug report email in BuildConfig
- [ ] Create bug report activity/dialog
- [ ] Collect device info and logs
- [ ] Send via email intent

---

## Phase 4: Testing

### 4.1 Unit Tests
- [x] ConfigRepositoryTest
- [x] PaymentRepositoryTest
- [x] PaymentProcessorTest
- [x] ConfigLoaderTest
- [ ] Add missing edge case tests
- [ ] Achieve 80%+ code coverage

### 4.2 Integration Tests
- [x] ConfigRepositoryRoomTest
- [x] RoomPaymentRepositoryTest
- [ ] Config import/export integration
- [ ] Full SMS processing pipeline

### 4.3 E2E Tests
- [x] SmsReceptionE2ETest
- [x] SmsReceptionWithRoomE2ETest
- [x] ConfigPersistenceE2ETest
- [x] ConfigExportE2ETest
- [x] PaymentDeduplicationE2ETest
- [ ] Set up Appium for UI automation
- [ ] E2E: Category management flow
- [ ] E2E: Sender management flow
- [ ] E2E: Full SMS to payment flow

---

## Phase 5: UI Polish

### 5.1 Visual Improvements
- [ ] Material Design 3 components
- [ ] Consistent color scheme
- [ ] Loading indicators
- [ ] Empty state views
- [ ] Error state views

### 5.2 UX Improvements
- [ ] Input validation feedback
- [ ] Confirmation dialogs for destructive actions
- [ ] Undo functionality for deletions
- [ ] Keyboard navigation

---

## Phase 6: Production Readiness

### 6.1 Default Configurations
- [ ] Default senders for common banks
- [ ] Default categories
- [ ] Default regex patterns
- [ ] Build-time configuration (email, etc.)

### 6.2 Documentation
- [ ] User manual
- [ ] API documentation (for extensibility)
- [ ] Release notes

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
