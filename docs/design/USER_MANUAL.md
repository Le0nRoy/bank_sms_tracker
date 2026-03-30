# Bank SMS Tracker - User Manual

## Table of Contents

1. [Getting Started](#getting-started)
2. [Main Screen](#main-screen)
3. [Managing Categories](#managing-categories)
4. [Managing Senders](#managing-senders)
5. [Viewing Payments](#viewing-payments)
6. [Viewing Incomes](#viewing-incomes)
7. [Using the Regex Builder](#using-the-regex-builder)
8. [Testing Rules](#testing-rules)
9. [Processing SMS History](#processing-sms-history)
10. [Import/Export Configuration](#importexport-configuration)
11. [Settings](#settings)
12. [Troubleshooting](#troubleshooting)

---

## Getting Started

### First Launch

When you first open Bank SMS Tracker, you'll need to:

1. **Grant SMS Permission** - The app needs access to read SMS messages to track your bank transactions
2. **Configure Senders** - Add your bank's SMS sender addresses
3. **Set Up Categories** - Create spending categories for your transactions
4. **Create Regex Rules** - Define patterns to extract transaction data from SMS messages

### Required Permissions

- **SMS** - Required to read incoming bank SMS messages
- **Storage** (optional) - For importing/exporting configuration files

---

## Main Screen

The main screen is organized into three groups:

### Configuration
- **Categories** - Manage spending categories and merchants
- **Senders** - Configure bank sender addresses and parsing rules (PAYMENT, INCOME, IGNORE)

### Data & Reports
- **Payments** - View and analyze your transaction history
- **Bug Report** - Report issues with device information

### Tools
- **Regex Builder** - Create and test regex patterns visually
- **Test Rules** - Check which SMS senders match your configured senders
- **Process SMS History** - Parse historical SMS messages

### Settings
- **Export Config** - Save your configuration to a file
- **Import Config** - Load configuration from a file
- **Settings** - Change theme (light/dark/system) and language

---

## Managing Categories

Categories help organize your spending by type (e.g., Food, Transport, Shopping).

### Creating a Category

1. Tap **Categories** from the main screen
2. Tap the **+** button (bottom right)
3. Enter a category name
4. Add merchants associated with this category

### Adding Merchants

Merchants are automatically matched to categories based on name. For example:
- Category: "Food"
- Merchants: "McDonalds", "Starbucks", "Pizza Hut"

When a transaction mentions "McDonalds", it will automatically be categorized as "Food".

Each merchant has two fields:
- **Pattern** — the exact string (or regex if "Is regex" is enabled) matched against the merchant name in parsed SMS messages.
- **Display Name** *(optional)* — a human-friendly label shown in the payment list when display names are toggled on. Leave blank to show the raw pattern.

### Enable/Disable Categories

Toggle the switch next to a category to enable or disable it. Disabled categories won't be used for automatic categorization.

### Re-categorize All Payments

Tap **Re-categorize All Payments** to update all existing payments based on current merchant rules. This is useful after adding new merchants.

---

## Managing Senders

Senders represent the SMS addresses your bank uses to send transaction notifications.

### Creating a Sender

1. Tap **Senders** from the main screen
2. Tap the **+** button (bottom right)
3. Enter the sender name (e.g., "My Bank")
4. Add SMS addresses used by this sender
5. Add regex rules for parsing messages

### Adding Addresses

Banks may use different addresses for different notification types. Common formats:
- Short codes: `12345`, `MYBANK`
- Phone numbers: `+1234567890`
- Alphanumeric: `MyBank`, `BANK-SMS`

### Adding Rules

Rules are unified by type:

- **PAYMENT** — extracts spending transactions (debit/purchase)
- **INCOME** — extracts incoming transfers (salary, refunds)
- **IGNORE** — suppresses promotional or non-financial SMS

PAYMENT and INCOME rules use named capture groups. IGNORE rules just need to match anywhere in the message body.

Named groups for PAYMENT/INCOME rules:
- `(?P<amount>...)` — transaction amount
- `(?P<currency>...)` — currency code (e.g., USD, EUR)
- `(?P<card>...)` — card identifier (optional)
- `(?P<merchant>...)` — merchant/counterparty name (optional)
- `(?P<date>...)` — date portion of timestamp (optional)
- `(?P<time>...)` — time portion of timestamp (optional)
- `(?P<balance>...)` — remaining balance (optional)

### Enable/Disable Rules

Toggle rules individually to test different patterns without deleting them.

---

## Viewing Payments

The Payments screen shows all parsed transactions with powerful filtering options.

### Filtering Payments

- **By Category** — Tap **Categories: All** to open a checkbox dialog. Select one or more categories, including **Uncategorized only** for payments with no assigned category. The selection applies to both the payment list and the spending report simultaneously.
- **By Sender** — Filter by bank sender address using the spinner.
- **By Date Range** — Tap **Start Date** / **End Date** to set a range. Tap **Clear** to remove it.
- **By Merchant** — Type in the merchant search field to filter by name (case-insensitive substring match).

### Display Names Toggle

Tap **Show Display Names** / **Show Raw Names** to switch between the friendly display name set in Categories and the raw merchant string extracted from the SMS body.

### Spending Report

Tap **Report** to see a breakdown of spending for the currently filtered payments. The report shows:
- Total spending amount
- GEL equivalent and live exchange rate when USD payments are present (fetched from the National Bank of Georgia API and cached locally)
- Per-category breakdown with percentage, pie chart, and bar chart

### Exporting to CSV

Tap **Export CSV** to save filtered payments to a CSV file for use in spreadsheets.

---

## Using the Regex Builder

The Regex Builder helps you create parsing patterns for bank SMS messages.

### Steps to Create a Pattern

1. Tap **Regex Builder** from the main screen
2. Paste a sample SMS message or tap **Select from Inbox**
3. Enter your regex pattern
4. Tap **Test Pattern** to see results
5. Adjust the pattern until all fields are captured correctly
6. Save the pattern to a sender

### Regex Pattern Requirements

Use **named capture groups** (`(?P<name>...)`). All groups are optional except `amount`:

| Group | Example | Notes |
|-------|---------|-------|
| `amount` | `(?P<amount>\d+\.\d{2})` | Required for PAYMENT/INCOME |
| `currency` | `(?P<currency>[A-Z]{3})` | |
| `card` | `(?P<card>\*\d{4})` | Last 4 digits |
| `merchant` | `(?P<merchant>.+?)` | |
| `date` | `(?P<date>\d{2}/\d{2}/\d{4})` | |
| `time` | `(?P<time>\d{2}:\d{2})` | |
| `balance` | `(?P<balance>\d+\.\d{2})` | |

IGNORE rules need no capture groups — they just match the message body.

### Example Pattern

For SMS: `Payment of 50.00 USD from *1234 at McDonalds on 01/15/2024. Balance: 500.00`

Pattern:
```
Payment of (\d+\.\d{2}) (\w+) from (\*\d+) at (.+?) on (\d{2}/\d{2}/\d{4})\. Balance: (\d+\.\d{2})
```

---

## Testing Rules

The Check Senders screen allows you to verify your rules work correctly.

### How to Test

1. Tap **Test Rules** from the main screen
2. Enter or paste an SMS message
3. Select the sender to test against
4. View the parsed results

This is useful for debugging patterns without affecting your actual payment data.

---

## Viewing Incomes

The Incomes screen shows all parsed income transactions (salary, transfers, refunds).

### Filtering Incomes

- **By Sender** — Select a bank sender from the spinner.
- **By Date Range** — Tap **Start Date** / **End Date** (defaults to current month). Tap **Clear** to remove.
- **By Source** — Type in the source search field to filter by income source name.

### Income Report

Tap **Report** to see a breakdown of incomes by source. The report shows:
- Total income amount
- GEL equivalent when USD incomes are present
- Per-source breakdown with pie chart and bar chart

---

## Processing SMS History

Process historical SMS messages to import past transactions.

### Steps

1. Tap **Process SMS History** from the main screen
2. Grant SMS read permission if prompted
3. Select a date range (optional)
4. Tap **Apply Rules**
5. The app will process all matching messages

### Result Filter

After processing, a filter row appears above the results. Tap a button to show only a specific result type:
- **All** — every processed message
- **Payments** — successfully parsed payment transactions
- **Incomes** — successfully parsed income transactions
- **Failed** — messages that matched a sender but no rule extracted data
- **Ignored** — messages matched by an IGNORE rule

### Notes

- Only messages from configured senders are processed
- Duplicate messages are automatically skipped
- Processing large histories may take some time

---

## Import/Export Configuration

### Exporting Configuration

1. Tap **Export Config** from the main screen
2. Choose where to save the file
3. Share the file via email, cloud storage, etc.

The exported file includes:
- All senders and their rules
- All categories and merchants

### Importing Configuration

1. Tap **Import Config** from the main screen
2. Select a JSON configuration file
3. The configuration will be merged with your existing settings

Import behavior:
- New senders/categories are added
- Existing items are not overwritten
- Duplicate rules are skipped

---

## Troubleshooting

### SMS Not Being Parsed

1. Verify the sender address matches exactly
2. Check that the sender and rule are enabled
3. Test the regex pattern in the Regex Builder
4. Ensure the pattern has exactly 6 capture groups

### Categories Not Applying

1. Verify merchant names match exactly (case-insensitive)
2. Check that the category is enabled
3. Use "Re-categorize All Payments" to update existing payments

### App Crashes or Errors

1. Tap **Bug Report** from the main screen
2. Describe the issue
3. Enable all checkboxes to include diagnostic information
4. Send the report

### Regex Pattern Not Working

Common issues:
- Missing escape characters for special characters (`.`, `*`, `+`)
- Not enough capture groups (need exactly 6)
- Group order doesn't match expected fields
- Greedy matching capturing too much

Use the Regex Builder to test patterns with real SMS messages.

---

## Tips

1. **Start Simple** - Begin with basic patterns and add complexity as needed
2. **Test Thoroughly** - Use multiple sample messages to verify patterns
3. **Backup Regularly** - Export your configuration periodically
4. **Use Categories** - Proper categorization helps track spending habits
5. **Check Sender Addresses** - Banks may change addresses; add all variants

---

## Settings

Access **Settings** from the main screen to:

- **Theme** - Choose Light, Dark, or System default
- **Language** - Select app language
- **Privacy → View Privacy Notice** - Re-read the data usage agreement shown at first launch

---

## Support

For issues or feature requests, use the Bug Report feature in the app or visit the project repository.
