# Changelog

All notable changes to Bank SMS Tracker will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Phase PROMPT-3: Reporting, Conversion & Interface Enhancements**
  - **Multi-select category filter** — `btnSelectCategories` opens a checkbox dialog on the Payments screen; selected categories filter both the payment list and spending report; includes an "Uncategorized only" option (`UNCATEGORIZED_FILTER` sentinel) combinable with named categories
  - **USD/GEL exchange-rate conversion** — `ExchangeRateCache` fetches live rates from the National Bank of Georgia REST API; two-level cache (in-memory + Room `exchange_rates` table) survives app restarts; spending and income reports show GEL equivalent and exchange rate when USD payments present
  - **Merchant display names** — `Merchant.displayName` editable in `CategoriesActivity` (separate field below the pattern); `btnToggleDisplayNames` in `PaymentsActivity` switches the list between raw merchant names and display names
  - **Full Incomes interface** — `IncomesActivity` rewritten with sender spinner, date-range buttons (defaults to current month), source search, income total label, and income report (pie + bar chart with GEL conversion)
  - **Process SMS result-type filter** — `ApplyRulesActivity` stores typed results (`SmsResultItem` sealed class: `PaymentItem`, `IncomeItem`, `IgnoredItem`, `FailedItem`); after processing, a `HorizontalScrollView` filter row appears with All / Payments / Incomes / Failed / Ignored buttons
  - Database migration v12 → v13: new `exchange_rates` table (`date TEXT, currency TEXT, rateToGel REAL, PRIMARY KEY(date, currency)`)
  - New Appium test class `IncomesAppiumTest` (11 tests, 5 smoke-tagged) covering all new Incomes screen elements
  - Smoke suite expanded from 21 to 29 tests

### Fixed
- `IncomesActivity` was calling `ExchangeRateCache.getUsdToGelRate()` without the required `dao` parameter; fixed by initialising `exchangeRateDao` from `BankSmsDatabase` in `loadData()`
- Appium tests referencing removed `spinnerCategory` ID updated to `btnSelectCategories` across `PaymentsFilterAppiumTest`, `SmsToPaymentFlowAppiumTest`, and `MainNavigationAppiumTest`

### Database Migrations (continued)
- v12 → v13: Added `exchange_rates` table for persistent USD/GEL exchange-rate cache

- **Phase PROMPT-2: Merchant Model & Pattern Management**
  - `Merchant` data class with `pattern`, `displayName` (optional), and `isRegex` fields (Task 1.2)
  - Regex matching for merchants — `isRegex=true` enables regex pattern matching in addition to exact string matching (Task 1.3)
  - Backward-compatible `MerchantSerializer` supports both legacy plain-string and new object format in JSON config
  - "Move to Category" dialog for each merchant in `CategoriesActivity` (Task 1.1)
  - `PatternListActivity` — dedicated screen for browsing, previewing (with span highlights and real newlines), loading, and deleting patterns from `RegexBuilderActivity` (Task 4.2)
  - Merchant search field in `PaymentsActivity` with real-time filtering (Task 2.1)
  - Formatted regex display in `SendersActivity` — shows human-readable `⟨preset⟩` tokens and decoded newlines (Task 3.0)
  - `RegexTemplateUtils` — shared utility for `regexToTemplate`, `templateToRegex`, `encodeNewlines`, `decodeNewlines` (replaces duplicated logic in `RegexBuilderActivity`)
  - `RegexSpanUtils` — shared utility for `applyPlaceholderSpans` (used in both `RegexBuilderActivity` and `SendersActivity`)

### Fixed
- **BUG-011**: Made `payments.timestamp` non-nullable; removed `receivedAt` column from `Payment` and `PaymentEntity`; `PaymentProcessor` now guarantees a non-null timestamp (from SMS body, nearest neighbour, or device SMS receive time)
- **BUG-007**: `addMerchantToCategory` now removes the merchant from all other categories before adding and retroactively updates `categoryName` on existing payment rows
- **BUG-008/009**: Date filter in `PaymentsActivity` now uses parsed transaction `timestamp` instead of `receivedAt` (batch-import time); extracted to `PaymentsFilter.kt`
- **BUG-010**: `regexToTemplate` now correctly converts any `(?<name>...)` capture group form to `⟨name⟩` token using regex-based matching
- **Feature 4.1**: Sender selection preserved after saving a regex pattern in `RegexBuilderActivity`
- **Feature 4.4**: `\\n` literals in stored regex patterns are decoded to actual newlines when loaded into the Regex Builder text box and re-encoded on save

### Changed
- `Category.merchants` type changed from `MutableList<String>` to `MutableList<Merchant>`
- `RegexBuilderActivity` patterns spinner replaced by "Browse Patterns" button launching `PatternListActivity`
- Database version bumped from v8 to v10 (two migrations)

### Database Migrations
- v8 → v9: `payments.timestamp` made NOT NULL; `receivedAt` column removed; NULL timestamps back-filled from `receivedAt` as date string
- v9 → v10: `category_merchants.name` renamed to `pattern`; `displayName TEXT` and `isRegex INTEGER NOT NULL DEFAULT 0` columns added

- **Phase 5.12: Localization & Multi-Language Support**
  - SettingsActivity with dedicated settings screen (replaces popup dialog)
  - Per-app language switching using AppCompatDelegate.setApplicationLocales()
  - Russian language support with complete translations (331 strings)
  - Language options: System Default, English, Russian
  - Theme settings moved to dedicated Settings screen
  - Extracted 90+ hardcoded strings to resources

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
- Appium E2E tests: 116 tests, 100% pass rate (10 test suites)
- New tests: SMS Export navigation, Settings screen, Locale tests
- Unit tests for Income data class (+9 tests)
- BankSmsTrackerAppTest for preference constants (+13 tests)
- SettingsAppiumTest for Settings UI (12 tests)
- LocaleE2ETest for locale functionality (11 tests)
- Enhanced MainNavigationAppiumTest for Settings screen
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
- v7 → v8: Unified rules table (payment + ignore rules consolidated)
- v8 → v9: Made `payments.timestamp` NOT NULL; removed `receivedAt` column
- v9 → v10: Extended `category_merchants` with `displayName` and `isRegex` columns; renamed `name` → `pattern`
- v10 → v11: Unified payment + ignore rules into single `rules` table
- v11 → v12: (see prior phase notes)
- v12 → v13: Added `exchange_rates` table for persistent USD/GEL rate cache

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
