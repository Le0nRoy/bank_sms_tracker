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
4. Apply regex rules to extract payment data
5. Assign category based on merchant name
6. Save payment to database (dedupe by message hash)
7. Log result
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
| `sender_rules` | Regex patterns for parsing SMS |
| `payments` | Parsed and categorized transactions |

## 6. Feature Specifications

### 6.1 Core Features (Implemented)

| Feature | Status | Description |
|---------|--------|-------------|
| SMS Reception | ✅ | Listen to incoming SMS via BroadcastReceiver |
| Regex Parsing | ✅ | Extract payment data using configurable regex |
| Category Assignment | ✅ | Auto-assign category based on merchant name |
| Payment Deduplication | ✅ | Prevent duplicate payments via message hash |
| Config Export | ✅ | Export configuration as JSON via share intent |
| UI: Categories | ✅ | Add/edit/delete categories and merchants |
| UI: Senders | ✅ | Add/edit senders, addresses, and rules |
| Room Persistence | ✅ | SQLite database for all data |

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
| Category Cascade | ✅ | Re-categorize all payments based on current merchant mappings |
| CSV Export | ✅ | Export payments to CSV file |
| Bug Report | ✅ | Send bug reports via built-in email |

### 6.3 Planned Features (TODO)

| Feature | Priority | Description |
|---------|----------|-------------|
| Default Configs | Low | Build-time email config, runtime sender/category defaults |

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
Interactive UI component that:
- Shows regex input field
- Shows sample message input
- Highlights matches in real-time
- Displays captured groups with labels
- **Save to Sender**: Select a sender and save the tested regex directly to its rules

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
        │  E2E    │  ← Appium (84 tests)
        │  Tests  │     Full user flows
       ─┼─────────┼─
       │Integration│  ← AndroidJUnit (66 tests)
       │   Tests   │     Room DB, Repositories
      ─┼───────────┼─
      │   Unit     │  ← JUnit 5 (30+ tests)
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
- **84 tests** across 8 test classes
- Run with: `make test-appium` or `./gradlew testDebugUnitTest --tests "*.appium.*"`

| Test Class | Tests | Coverage |
|------------|-------|----------|
| MainNavigationAppiumTest | 14 | Main screen navigation |
| CategoryManagementAppiumTest | 11 | Category CRUD operations |
| SenderManagementAppiumTest | 11 | Sender CRUD operations |
| SmsToPaymentFlowAppiumTest | 10 | End-to-end payment flow |
| BugReportAppiumTest | 12 | Bug report feature |
| RegexBuilderAppiumTest | 13 | Regex builder feature + save-to-sender |
| PaymentsFilterAppiumTest | 8 | Payment filtering by sender/date range |
| CategoryCascadeAppiumTest | 5 | Re-categorize all payments feature |

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
└────────────────┘
```

### 9.2 Screen Descriptions

| Screen | Purpose |
|--------|---------|
| **MainActivity** | Navigation hub with buttons for all features |
| **CategoriesActivity** | List and edit spending categories with merchants |
| **SendersActivity** | List and edit senders with addresses and rules |
| **CheckSendersActivity** | Test regex patterns against sample messages |
| **ApplyRulesActivity** | Manually trigger rule application on SMS inbox |
| **PaymentsActivity** | View parsed payments with filters (category, sender, date range) |
| **RegexBuilderActivity** | Visual regex testing tool with save-to-sender feature |
| **BugReportActivity** | Bug report form with device info collection |

## 10. Security Considerations

### 10.1 Permissions
- `RECEIVE_SMS` - Required for real-time SMS processing
- `READ_SMS` - Required for retrospective parsing (optional)

### 10.2 Data Privacy
- All data stored locally in encrypted Room database
- No network communication (except optional bug report)
- Config export uses secure FileProvider

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
│   ├── Payment.kt
│   ├── PaymentRegexRule.kt
│   ├── Sender.kt
│   └── SmsConfig.kt
├── database/                      # Room database
│   ├── BankSmsDatabase.kt
│   ├── ConfigDao.kt
│   ├── Entities.kt
│   ├── Mappers.kt
│   └── PaymentDao.kt
├── parser/                        # SMS parsing
│   └── SmsReceiver.kt
├── processor/                     # Business logic
│   └── PaymentProcessor.kt
├── repository/                    # Data access
│   ├── ConfigRepository.kt
│   ├── PaymentRepository.kt
│   └── RoomPaymentRepository.kt
├── serializer/                    # JSON serialization
│   └── ConfigLoader.kt
└── ui/                           # Activities
    ├── BaseActivity.kt
    ├── MainActivity.kt
    ├── CategoriesActivity.kt
    ├── SendersActivity.kt
    ├── CheckSendersActivity.kt
    ├── ApplyRulesActivity.kt
    ├── PaymentsActivity.kt
    ├── RegexBuilderActivity.kt
    └── BugReportActivity.kt

app/src/test/java/com/example/banksmstracker/
├── appium/                        # Appium UI tests
│   ├── AppiumBaseTest.kt
│   ├── MainNavigationAppiumTest.kt
│   ├── CategoryManagementAppiumTest.kt
│   ├── SenderManagementAppiumTest.kt
│   ├── SmsToPaymentFlowAppiumTest.kt
│   ├── BugReportAppiumTest.kt
│   └── RegexBuilderAppiumTest.kt
└── repository/                    # Unit tests
    ├── ConfigRepositoryTest.kt
    └── PaymentRepositoryTest.kt

# Project root
├── docker-compose.appium.yml      # Appium Docker setup
├── Makefile                       # Build automation
├── AGENTS.md                      # AI agent guidelines
└── TODO.md                        # Project progress tracking
```
