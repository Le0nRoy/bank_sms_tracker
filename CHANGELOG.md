# Changelog

All notable changes to Bank SMS Tracker will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Phase 5.9: SMS History Export**
  - SmsExportActivity with date range filtering
  - Sender filtering (all or specific sender)
  - Export to JSON format with full message data
  - Export to CSV format for spreadsheet compatibility
  - Message count preview before export
  - Share exported file via Intent

- **Phase 5.10: Income Tracking (Database Infrastructure)**
  - Income data class with kotlinx.serialization
  - IncomeEntity database entity with unique messageHash
  - IncomeDao with full CRUD operations
  - Date range and sender filtering queries
  - Database migration v6→v7 (incomes table)

- **Phase 5.11: Light/Dark Mode Toggle**
  - System Default / Light / Dark theme options
  - SharedPreferences persistence
  - Theme applied on app startup via BankSmsTrackerApp
  - Settings dialog accessible from main menu

- **Phase 5.3: Regex Builder Enhancements**
  - Filter SMS selection by configured senders
  - Sender selection dialog before showing messages
  - Full message content display with visual separation
  - Existing patterns spinner to edit saved regex patterns
  - Reorganized layout (SMS input → Pattern → Test → Results → Save)

- **Phase 5.4: Spending Reports Improvements**
  - Default date range set to current month (first to last day)
  - Show actual dates in report even for empty ranges

- **Phase 5.5: Apply Rules Improvements**
  - Time range selection UI for rule application
  - Default time range since last processed payment
  - "Open Regex Builder" button for failed messages
  - Enhanced error formatting with CardView items

- **Phase 5.6: Payment Details & Categorization**
  - Click on payment opens detail dialog
  - Add merchant to existing category from payment
  - Create new category with merchant from payment

- **Phase 5.7: Ignore Rules for Spam**
  - IgnoreRule database entity and DAO
  - IgnoreRulesActivity with full CRUD operations
  - Toggle enabled/disabled state for rules
  - Delete confirmation dialog

- Spending report feature with category breakdown in PaymentsActivity
- Error state views with retry functionality for Categories and Senders screens
- Grouped main menu layout for better navigation
- AI reviewer prompt file for automated code review
- Comprehensive README with build instructions
- User manual documentation
- Creative Commons BY-NC license for non-commercial use
- PROMPT_TEMPLATE.md for session continuation

### Changed
- Main menu reorganized into logical groups (Data, Tools, Settings)
- Added SMS Export and Settings buttons to main menu
- Improved empty state messages
- Enhanced loading indicators
- JaCoCo coverage configuration optimized (excludes UI/database/repository tested by E2E)
- JaCoCo coverage verification fixed (handles DOCTYPE in XML)
- Database version 7 with incomes table migration

### Quality
- Code coverage: 87.50% (unit tests)
- Appium E2E tests: 104 tests, 100% pass rate (9 test suites)
- New tests: SMS Export navigation, Theme Toggle dialog
- Unit tests for Income data class (+9 tests)
- Enhanced MainNavigationAppiumTest (+2 tests)
- Fixed Appium test flakiness with driver reconnection logic
- Bug documentation linked in TODO.md

## [1.0.0] - 2025-12-31

### Added
- **Core Features**
  - SMS parsing with configurable regex patterns
  - Category-based spending organization
  - Multi-sender support for different banks
  - Payment deduplication by message hash
  - Real-time SMS processing via BroadcastReceiver

- **Configuration Management**
  - JSON-based configuration import/export
  - Room database persistence
  - Default rules seeding from assets
  - Merge import (append, not replace)

- **User Interface**
  - Main navigation hub
  - Categories management with merchants
  - Senders management with rules
  - Payments list with filtering
  - Regex Builder with live preview
  - Bug Report with device info collection

- **Phase 3 Features**
  - Enable/disable toggles for senders, rules, and categories
  - Payment filtering by category, sender, and date range
  - Category cascade (re-categorize payments when rules change)
  - Retrospective SMS processing
  - Save regex patterns to sender rules
  - CSV export for payments

- **Testing Infrastructure**
  - JUnit 5 unit tests
  - AndroidJUnit5 instrumented tests
  - Appium E2E UI automation
  - JaCoCo code coverage
  - Docker support for Appium server

- **Quality Assurance**
  - ktlint code style enforcement
  - GitHub Actions CI pipeline
  - Bug tracking infrastructure
  - Comprehensive documentation

### Fixed
- BUG-001: SQL injection vulnerability in ApplyRulesActivity
- BUG-002: Race condition in ConfigRepository
- BUG-003: Missing regex group validation
- BUG-004: Blocking coroutines in RoomPaymentRepository
- BUG-005: Race condition in PaymentsActivity
- BUG-006: Regex pattern not cached

### Security
- Parameterized queries for database operations
- Input validation for user data
- No hardcoded secrets or credentials

---

## Version History Summary

| Version | Date | Highlights |
|---------|------|------------|
| 1.0.0 | 2025-12-31 | Initial release with full feature set |

---

## Upgrade Notes

### Upgrading to 1.0.0

This is the initial release. No upgrade path required.

### Database Migrations

The app includes automatic database migrations:
- v1 → v2: Added `enabled` field to senders and rules
- v2 → v3: Added `senderAddress` and `receivedAt` to payments
- v3 → v4: Added `ruleId` for category cascade
- v4 → v5: Added `ignore_rules` table for spam filtering
- v5 → v6: Added `senderId` to ignore_rules (foreign key)
- v6 → v7: Added `incomes` table for income tracking

---

## Known Issues

1. Appium tests may be flaky on slower emulators
2. Large SMS history processing can be slow
3. Some edge cases in regex matching for unusual SMS formats

---

## Contributing

See [README.md](README.md) for contribution guidelines.

## License

This project is licensed under CC BY-NC 4.0. See [LICENSE](LICENSE) for details.
