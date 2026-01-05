# Changelog

All notable changes to Bank SMS Tracker will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Spending report feature with category breakdown in PaymentsActivity
- Error state views with retry functionality for Categories and Senders screens
- Grouped main menu layout for better navigation
- AI reviewer prompt file for automated code review
- Comprehensive README with build instructions
- User manual documentation
- Creative Commons BY-NC license for non-commercial use

### Changed
- Main menu reorganized into logical groups (Data, Tools, Settings)
- Improved empty state messages
- Enhanced loading indicators
- JaCoCo coverage configuration optimized (excludes UI/database/repository tested by E2E)

### Quality
- Code coverage: 96.6% (174 unit tests)
- Appium E2E tests: 80/84 passing with Docker

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
