# BankSMSTracker - High-Level Design Document

## 1. Overview

BankSMSTracker is an Android application that parses SMS messages from configured bank senders using regular expressions to track and categorize spending transactions. The app provides a fully configurable system where users can define senders, parsing rules, and spending categories.

## 2. Core Concepts

### 2.1 Domain Model

```
┌─────────────┐       ┌─────────────────┐       ┌────────────┐
│   Sender    │──────▶│ PaymentRegexRule│       │  Category  │
│             │ 1:N   │                 │       │            │
│ - name      │       │ - id            │       │ - id       │
│ - addresses │       │ - regex         │       │ - name     │
│ - rules     │       │ - categoryId    │       │ - merchants│
└─────────────┘       └─────────────────┘       └────────────┘
                              │                        │
                              ▼                        │
                       ┌──────────┐                    │
                       │ Payment  │◀───────────────────┘
                       │          │      categorized by
                       │ - amount │
                       │ - currency│
                       │ - card   │
                       │ - merchant│
                       │ - timestamp│
                       │ - balance│
                       │ - categoryId│
                       └──────────┘
```

### 2.2 Key Entities

| Entity | Description |
|--------|-------------|
| **Sender** | Bank or financial institution that sends SMS notifications. Has one or more phone addresses and parsing rules. |
| **PaymentRegexRule** | Regular expression pattern to extract payment data from SMS. Attached to a sender, optionally linked to a default category. |
| **Category** | Spending category (e.g., "Groceries", "Transport"). Contains merchant names for auto-categorization. |
| **Payment** | Parsed transaction with amount, currency, merchant, timestamp, and assigned category. |

## 3. Architecture

### 3.1 Layer Architecture

```
┌────────────────────────────────────────────────────────────┐
│                        UI Layer                             │
│  MainActivity, CategoriesActivity, SendersActivity, etc.   │
└───────────────────────────┬────────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────────┐
│                    Repository Layer                         │
│         ConfigRepository, PaymentRepository                 │
└───────────────────────────┬────────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────────┐
│                    Database Layer                           │
│            Room Database (BankSmsDatabase)                  │
│        ConfigDao, PaymentDao, Entities, Mappers             │
└────────────────────────────────────────────────────────────┘
```

### 3.2 Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Android System                              │
│  ┌──────────────┐                                                        │
│  │  SMS_RECEIVED│──────────────────────────────────────────┐             │
│  │   Broadcast  │                                          │             │
│  └──────────────┘                                          ▼             │
│                                                    ┌───────────────┐     │
│                                                    │  SmsReceiver  │     │
│                                                    │ (Broadcast)   │     │
│                                                    └───────┬───────┘     │
│                                                            │             │
│                                                            ▼             │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                        BankSMSTracker App                         │   │
│  │  ┌────────────────┐    ┌───────────────────┐                      │   │
│  │  │ ConfigRepository│◀──│ PaymentProcessor  │                      │   │
│  │  │                 │   │                   │                      │   │
│  │  │ - load()        │   │ - processMessage()│                      │   │
│  │  │ - getCategories()   │ - assignCategory()│                      │   │
│  │  │ - getSenders()  │   └───────┬───────────┘                      │   │
│  │  │ - addCategory() │           │                                  │   │
│  │  │ - updateSender()│           ▼                                  │   │
│  │  │ - exportConfig()│   ┌───────────────────┐                      │   │
│  │  └────────┬────────┘   │ PaymentRepository │                      │   │
│  │           │            │                   │                      │   │
│  │           ▼            │ - savePayment()   │                      │   │
│  │  ┌─────────────────────┴───────────────────┐                      │   │
│  │  │            Room Database                 │                      │   │
│  │  │  ┌──────────┐  ┌──────────┐  ┌────────┐ │                      │   │
│  │  │  │ConfigDao │  │PaymentDao│  │Entities│ │                      │   │
│  │  │  └──────────┘  └──────────┘  └────────┘ │                      │   │
│  │  └──────────────────────────────────────────┘                      │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

## 4. Data Flow

### 4.1 SMS Processing Flow

```
1. SMS Received → SmsReceiver.onReceive()
2. Initialize PaymentProcessor from ConfigRepository
3. Match sender address against configured senders
4. Apply rule workflow:
   a. Try PAYMENT rules → If match, extract payment data (6 capture groups)
   b. Try INCOME rules → If match, extract income data (6 capture groups)
   c. Try IGNORE rules → If match, skip message (no capture groups required)
   d. If no match → Log as unparsed
5. For payments: Assign category based on merchant name
6. Save to appropriate repository (payment/income, dedupe by message hash)
7. Log result (PaymentResult/IncomeResult/Ignored/Error)
```

### 4.2 Configuration Management Flow

```
1. App Start → ConfigRepository.load()
2. If DB empty → Seed from assets/default_rules.json
3. Load config into memory cache
4. UI edits → Repository update methods
5. Persist changes → Refresh cache
6. Export → Serialize to JSON → Share via FileProvider
```

## 5. Database Schema

### 5.1 Entity-Relationship Diagram

```
┌───────────────┐         ┌────────────────────┐
│  categories   │         │ category_merchants │
├───────────────┤         ├────────────────────┤
│ id (PK)       │◀───┐    │ id (PK)            │
│ name          │    └────│ categoryId (FK)    │
└───────────────┘         │ name               │
                          └────────────────────┘

┌───────────────┐         ┌───────────────────┐         ┌───────────────┐
│   senders     │         │ sender_addresses  │         │ sender_rules  │
├───────────────┤         ├───────────────────┤         ├───────────────┤
│ id (PK)       │◀───┐    │ id (PK)           │         │ id (PK)       │
│ name          │    ├────│ senderId (FK)     │    ┌────│ senderId (FK) │
└───────────────┘    │    │ address           │    │    │ regex         │
                     │    └───────────────────┘    │    └───────────────┘
                     └─────────────────────────────┘

┌───────────────────────────────────────┐
│              payments                  │
├───────────────────────────────────────┤
│ id (PK)                               │
│ amount                                │
│ currency                              │
│ card                                  │
│ merchant                              │
│ timestamp                             │
│ balance                               │
│ categoryName                          │
│ messageHash (UNIQUE)                  │
│ senderAddress                         │
│ receivedAt                            │
│ ruleId (for cascade)                  │
└───────────────────────────────────────┘
```

### 5.2 Tables Description

| Table | Purpose |
|-------|---------|
| `categories` | Spending categories |
| `category_merchants` | Merchant names for auto-categorization |
| `senders` | Bank/financial institution senders |
| `sender_addresses` | Phone numbers/addresses for a sender |
| `rules` | Unified regex patterns for all rule types (PAYMENT, INCOME, IGNORE) |
| `payments` | Parsed and categorized transactions |
| `incomes` | Parsed income transactions |
| `sender_rules` | Legacy payment rules (retained for migration compatibility) |
| `ignore_rules` | Legacy ignore rules (retained for migration compatibility) |

## 6. Feature Specifications

### 6.1 Core Features (Implemented)

| Feature | Status | Description |
|---------|--------|-------------|
| SMS Reception | ✅ | Listen to incoming SMS via BroadcastReceiver |
| Regex Parsing | ✅ | Extract payment data using configurable regex (named capture groups) |
| Category Assignment | ✅ | Auto-assign category based on merchant name |
| Payment Deduplication | ✅ | Prevent duplicate payments via SHA-256 message hash |
| Config Export | ✅ | Export configuration as JSON via share intent |
| UI: Categories | ✅ | Add/edit/delete categories and merchants |
| UI: Senders | ✅ | Add/edit senders, addresses, and unified rules |
| Room Persistence | ✅ | SQLite database v8 for all data |

### 6.2 Extended Features (Implemented)

| Feature | Status | Description |
|---------|--------|-------------|
| Config Import | ✅ | Import and merge config from JSON file |
| Enable/Disable Rules | ✅ | Toggle individual senders, rules, categories |
| Regex Builder UI | ✅ | Visual tool to build and test regex patterns |
| Save Regex to Sender | ✅ | Save tested regex patterns directly to sender |
| Payments List UI | ✅ | View payments with filter by category |
| Filter by Sender | ✅ | Filter payments by sender address |
| Filter by Date Range | ✅ | Filter payments by date range with date picker UI |
| Category Cascade | ✅ | Re-categorize all payments on rule save or manual trigger |
| CSV Export | ✅ | Export payments to CSV file (debug builds only) |
| Bug Report | ✅ | Send bug reports via share intent with optional payment data attachment |
| Income Tracking | ✅ | Separate INCOME rule type; parsed incomes stored in `incomes` table |
| Ignore Rules | ✅ | IGNORE rule type suppresses matched SMS from processing |
| Settings Screen | ✅ | Theme (light/dark/system) and language selection |
| SMS Export | ✅ | Export raw SMS messages to JSON/CSV (debug builds only) |
| Personal Data Agreement | ✅ | Non-cancelable first-launch consent dialog |
| Debug/Prod Split | ✅ | Export/SMS features hidden in release builds |
| Allure Reporting | ✅ | `@Epic`/`@Feature`/`@Step` on Appium tests |
| Date Approximation | ✅ | Fill missing payment timestamp from nearest neighbor |

### 6.3 Planned Features

| Feature | Priority | Description |
|---------|----------|-------------|
| Background Service | High | Foreground service for permanent background SMS monitoring |
| Notification System | High | Notify when no applicable regex found for known sender |
| Onboarding Screen | Medium | First-time user flow explaining permissions |
| Data Retention | Medium | Automatic deletion of old payments (GDPR compliance) |
| Database Encryption | Low | SQLCipher integration for data at rest |
| Bank API Integration | Low | Research Open Banking / PSD2 APIs |

### 6.4 Feature Details

#### 6.4.1 Config Import (Merge Rules)
During import, rules should be **appended** to existing configuration:
- New senders → Add to database
- Existing sender (by name) → Merge addresses and rules
- New categories → Add to database
- Existing category (by name) → Merge merchants

#### 6.4.2 Enable/Disable Rules
All configurable entities should have an `enabled` boolean field:
- Disabled senders → Skip during SMS processing
- Disabled rules → Skip during regex matching
- Disabled categories → Exclude from category assignment

#### 6.4.3 Regex Builder (Implemented)
Interactive UI component with enhanced workflow:
- **Sender Selection**: First select the target sender
- **Rule Type Selection**: Choose PAYMENT, INCOME, or IGNORE rule type
- **Sample Message**: Paste or select from SMS inbox
- **Unregistered Sender Detection**: Prompts to register unknown senders when selecting SMS
- **Existing Patterns**: View and edit existing patterns for selected sender/type
- **Regex Pattern Input**: Enter or modify regex pattern
- **Preset Buttons**: Insert common regex components (Amount, Currency, Card, etc.)
- **Test Pattern**: Test regex against sample message (closes keyboard)
- **Results Display**: Shows match status, captured groups, and payment preview
- **Save to Sender**: Save the tested regex directly to selected sender's rules

#### 6.4.4 Category Cascade (Implemented)
Re-categorize all payments based on current category merchant mappings:
1. Payments track which rule parsed them via `ruleId` field
2. "Re-categorize All Payments" button in CategoriesActivity
3. When triggered, iterates all payments and re-assigns categories based on merchant name matching
4. Database schema v4 adds `ruleId` column to payments table

#### 6.4.5 Retrospective Parsing
Allow processing historical SMS:
1. Request SMS read permission
2. Query SMS inbox for date range
3. Filter by configured sender addresses
4. Process each matching message

## 7. Configuration Defaults

### 7.1 Build-Time Configuration
Located in `app/build.gradle.kts` or `BuildConfig`:
- Bug report email address
- Default timeout values
- Feature flags

### 7.2 Runtime Configuration
Located in `app/src/main/assets/default_rules.json`:
- Default senders with common bank formats
- Default categories (Food, Transport, Shopping, etc.)
- Default regex patterns for common SMS formats

### 7.3 Test Configuration
Test fixtures should mirror production defaults:
- `app/src/test/resources/default_rules.json` (unit tests)
- `app/src/androidTest/assets/` (instrumented tests)

Build system should sync these files automatically via Gradle task.

## 8. Testing Strategy

### 8.1 Test Pyramid

```
        ┌─────────┐
        │  E2E    │  ← Appium (116+ tests, 10 classes)
        │  Tests  │     Full user flows
       ─┼─────────┼─
       │Integration│  ← AndroidJUnit (77 tests)
       │   Tests   │     Room DB, Repositories
      ─┼───────────┼─
      │   Unit     │  ← JUnit 5 (195+ tests)
      │   Tests    │     Processors, Parsers, Logic
      ─┴───────────┴─
```

### 8.2 Test Categories

| Category | Framework | Location | Scope |
|----------|-----------|----------|-------|
| Unit | JUnit 5 | `src/test/` | Business logic, parsing, data classes |
| Integration | AndroidJUnit5 | `src/androidTest/` | Room DB, Repositories, Config loading |
| E2E/Appium | Appium + JUnit 5 | `src/test/appium/` | Full UI flows, navigation |

### 8.3 Appium UI Tests

Docker-based Appium setup for UI automation:
- **116+ tests** across 10 test classes
- Run with: `make test-appium` or `./gradlew testDebugUnitTest --tests "*.appium.*"`
- Smoke subset (~18 tests): `make test-smoke`
- Allure reporting: `make allure-report` / `make allure-serve`

| Test Class | Tests | Coverage |
|------------|-------|----------|
| MainNavigationAppiumTest | 16 | Main screen navigation + SMS Export + Settings |
| CategoryManagementAppiumTest | 11 | Category CRUD operations |
| SenderManagementAppiumTest | 11 | Sender CRUD operations |
| SmsToPaymentFlowAppiumTest | 10 | End-to-end payment flow |
| BugReportAppiumTest | 12 | Bug report feature |
| RegexBuilderAppiumTest | 17 | Regex builder + preset buttons + save-to-sender |
| PaymentsFilterAppiumTest | 12 | Payment filtering + date range |
| CategoryCascadeAppiumTest | 5 | Re-categorize all payments feature |
| IgnoreRulesAppiumTest | 10 | Ignore rules CRUD operations |
| SettingsAppiumTest | 12 | Theme / language selection |

### 8.4 Test Data Management
- Shared test fixtures in `src/test/resources/`
- Gradle task to copy to `src/androidTest/assets/` during build
- SMS test cases in `sms_tests.json`

## 9. UI Screens

### 9.1 Screen Map

```
┌────────────────┐
│  MainActivity  │
├────────────────┤
│ • Categories   │──────▶ CategoriesActivity
│ • Senders      │──────▶ SendersActivity
│ • Check Senders│──────▶ CheckSendersActivity
│ • Apply Rules  │──────▶ ApplyRulesActivity
│ • Export Config│──────▶ Share Intent
│ • Import Config│──────▶ File Picker
│ • Payments     │──────▶ PaymentsActivity
│ • Regex Builder│──────▶ RegexBuilderActivity
│ • Bug Report   │──────▶ BugReportActivity
│ • SMS Export   │──────▶ SmsExportActivity (debug only)
│ • Settings     │──────▶ SettingsActivity
└────────────────┘
    │
    └── CategoriesActivity ──▶ IgnoreRulesActivity (per-sender ignore rules)
```

### 9.2 Screen Descriptions

| Screen | Purpose |
|--------|---------|
| **MainActivity** | Navigation hub with buttons for all features |
| **CategoriesActivity** | List and edit spending categories with merchants |
| **SendersActivity** | List and edit senders with addresses and unified rules (PAYMENT/INCOME/IGNORE) |
| **CheckSendersActivity** | Show which SMS senders in inbox match configured senders |
| **ApplyRulesActivity** | Manually process historical SMS inbox against current rules |
| **PaymentsActivity** | View parsed payments with filters (category, sender, date range); spending report |
| **RegexBuilderActivity** | Visual regex testing tool; save-to-sender; select from SMS inbox |
| **BugReportActivity** | Bug report form with device info collection and optional payment data attachment |
| **SmsExportActivity** | Export raw SMS messages to JSON/CSV (debug builds only) |
| **SettingsActivity** | Theme (light/dark/system) and language selection; view privacy notice |
| **IgnoreRulesActivity** | Manage ignore rules per sender (legacy; rules now unified in SendersActivity) |

## 10. Security Considerations

### 10.1 Permissions
- `RECEIVE_SMS` - Required for real-time SMS processing
- `READ_SMS` - Required for retrospective parsing (optional)

### 10.2 Data Privacy
- All data stored locally in Room database (SQLite, **not encrypted** at rest)
- No network communication (except optional bug report via share intent)
- Config export uses secure FileProvider (app-private cache dir)
- `android:allowBackup="true"` — financial data may be included in Android cloud backup;
  backup_rules.xml should be configured to exclude the database if this is a concern
- SMS bodies are logged to Logcat in debug builds (amounts, card last-4, merchants)

## 11. Future Enhancements

### 11.1 Phase 7: Background Service & Real-time Processing (Planned)

| Feature | Priority | Description |
|---------|----------|-------------|
| **Background Service** | High | Foreground service for permanent background operation |
| **Real-time SMS Monitoring** | High | Watch and parse all incoming SMS in real time |
| **Notification System** | High | Notify when configured sender has no applicable regex |
| **Onboarding Screen** | Medium | First-time user flow explaining permissions |
| **Bank API Integration** | Low | Research and implement Open Banking / PSD2 APIs |

### 11.2 Other Future Enhancements

1. **Multi-currency support** - Handle currency conversion
2. **Cloud backup** - Optional encrypted cloud sync
3. **Analytics dashboard** - Spending trends and charts
4. **Widget** - Home screen spending summary
5. **Notifications** - Alerts for unusual spending

## 12. File Structure

```
app/src/main/java/com/example/banksmstracker/
├── BankSmsTrackerApp.kt          # Application class
├── data/                          # Domain models
│   ├── Category.kt
│   ├── IgnoreRule.kt
│   ├── Income.kt
│   ├── MessageProcessResult.kt
│   ├── Payment.kt
│   ├── Rule.kt
│   ├── RuleType.kt               # PAYMENT / INCOME / IGNORE enum
│   ├── Sender.kt
│   └── SmsConfig.kt
├── database/                      # Room database (schema v8)
│   ├── BankSmsDatabase.kt         # DB singleton + migrations 1-8
│   ├── ConfigDao.kt
│   ├── Entities.kt
│   ├── IgnoreRuleDao.kt
│   ├── IncomeDao.kt
│   ├── Mappers.kt
│   ├── PaymentDao.kt
│   └── RuleDao.kt
├── parser/                        # SMS BroadcastReceiver
│   └── SmsReceiver.kt
├── processor/                     # Business logic
│   └── PaymentProcessor.kt
├── repository/                    # Data access
│   ├── ConfigRepository.kt
│   ├── ImportResult.kt
│   ├── PaymentRepository.kt
│   └── RoomPaymentRepository.kt
├── serializer/                    # JSON serialization
│   └── ConfigLoader.kt
├── util/                          # Utilities
│   ├── Constants.kt
│   ├── HashUtil.kt
│   └── SmsAddressMatcher.kt
└── ui/                           # Activities
    ├── BaseActivity.kt
    ├── EditTextExtensions.kt
    ├── MainActivity.kt
    ├── CategoriesActivity.kt
    ├── SendersActivity.kt
    ├── CheckSendersActivity.kt
    ├── ApplyRulesActivity.kt
    ├── PaymentsActivity.kt
    ├── RegexBuilderActivity.kt
    ├── BugReportActivity.kt
    ├── IgnoreRulesActivity.kt
    ├── SettingsActivity.kt
    └── SmsExportActivity.kt       # Debug builds only

app/src/test/java/com/example/banksmstracker/
├── appium/                        # Appium UI tests (116+ tests, 10 classes)
│   ├── AppiumBaseTest.kt
│   ├── BugReportAppiumTest.kt
│   ├── CategoryCascadeAppiumTest.kt
│   ├── CategoryManagementAppiumTest.kt
│   ├── IgnoreRulesAppiumTest.kt
│   ├── MainNavigationAppiumTest.kt
│   ├── PaymentsFilterAppiumTest.kt
│   ├── RegexBuilderAppiumTest.kt
│   ├── SenderManagementAppiumTest.kt
│   ├── SettingsAppiumTest.kt
│   └── SmsToPaymentFlowAppiumTest.kt
└── ...                            # Unit tests (195+ tests)

app/src/androidTest/java/com/example/banksmstracker/
└── ...                            # Instrumented tests (77 tests)

# Project root
├── docker-compose.yml             # Appium + Gradle build cache
├── Makefile                       # Build automation
├── AGENTS.md                      # AI agent guidelines
└── TODO.md                        # Project progress tracking
```
