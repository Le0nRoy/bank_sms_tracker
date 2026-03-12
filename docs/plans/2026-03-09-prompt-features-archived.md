# Archived: PROMPT.md Feature Batch — Completed Tasks

> **STATUS: ARCHIVED — PENDING DELETION**
> All tasks from `2026-03-09-prompt-features.md` are complete.
> This file contains the implementation notes for completed phases (2, 3, 4).
> After human review of the updated design docs, this file may be deleted.
>
> **Source plan:** `docs/plans/2026-03-09-prompt-features.md`
> **Commits:** Phase 2 → 803bbdc | Phase 3 → 6f42259 | Phase 4 → da0aeb4

---

## Phase 2 (commit 803bbdc): Medium Changes

### Task 2.11: Optional Fields in Partial Matches

- `PaymentProcessor.tryPaymentRules()`: changed from positional `groupValues[n]` to `groups["name"]?.value`
- Amount + currency required; all other fields (merchant, card, timestamp, balance) optional
- `PaymentProcessor.approximateDate(payment, repository)`: queries nearest-neighbor payment for timestamp approximation when date/time groups absent
- Called in `processMessage()` and `processMessageFull()`

### Task 5.2–5.4: Bug Report + Main Menu Reorganization

- **5.2:** `PaymentsActivity.saveFilterState()` persists active filter (date range, category, sender) to SharedPreferences (`payments_filter_state`); `BugReportActivity` reads it when `cbIncludeFilterState` checked
- **5.3:** `BugReportActivity.cbAttachPaymentsData` — if checked, generates temp JSON of all payments; `ACTION_SEND_MULTIPLE` via FileProvider
- **5.4:** `activity_main.xml` — new "Data & Reports" group with `btnPayments`, `btnSmsExport`, `btnBugReport`; `menu_group_data_reports` string resource added

### Task 7–7.1: Auto-Fill Sender in RegexBuilder from ProcessSMS

- `ApplyRulesActivity`: "Open in RegexBuilder" button per failed/ignored message
- Intent extra `EXTRA_SENDER_ADDRESS` passed to `RegexBuilderActivity`
- `RegexBuilderActivity.onCreate()`: checks for extra, auto-selects sender in spinner
- If sender not found: dialog "Sender X not configured. Create new?" → navigates to create-sender flow

### Task 8: Rules in Card Boxes in SendersActivity

- `view_rule_with_toggle.xml` root wrapped in `MaterialCardView` (8dp margin, 8dp corner radius, 2dp elevation)
- Each card shows: rule type badge + rule text + delete button

---

## Phase 3 (commit 6f42259): Block Editor + Perf Test + Re-Categorize

### Task 2.1–2.4: Block-Based Pattern Editor (Placeholder Chip Approach)

- Preset buttons insert `⟨amount⟩`, `⟨currency⟩`, etc. (Unicode angle-bracket markers) into the regex EditText
- `TextWatcher` + `SpannableStringBuilder` renders placeholders as colored, bold spans (chips) after each text change
- On **save**: `templateToRegex(text)` replaces each `⟨name⟩` with its named-group regex `(?<name>...)`
- On **load**: `regexToTemplate(regex)` replaces known named-group patterns back to `⟨name⟩` markers
- Raw regex editing still works — only named groups convert to placeholders on load
- Full custom token-view approach deferred (see separate plan if created)

### Task 3–3.2: Category Assignment Perf Test

- `CategoryConcurrencyTest.kt` created
- Inserts 50 payments with a known merchant; rapidly assigns a category 10 times concurrently
- Waits for all DB ops; asserts all 50 payments have correct category
- Verifies no race condition via timing stress test

### Task 4–4.1: Re-Categorize All Payments on Merchant Add

- Adding a merchant to a category now triggers re-categorization of ALL stored payments
- `PaymentRepository`: `recategorizeAll(categories)` method added
- `PaymentsActivity`: observes re-categorization complete event and refreshes list
- Filter/scroll preserved: `recyclerView.layoutManager?.onSaveInstanceState()` before refresh; current spinner/date selections reapplied; `onRestoreInstanceState()` after

---

## Phase 4 (commit da0aeb4): Personal Data + Allure

### Task 9–9.3: Personal Data Processing Agreement + Debug/Prod Split

- **9.1 Debug/Prod split:**
  - `btnSmsExport` in `MainActivity`: `View.GONE` when `!BuildConfig.DEBUG`
  - `PaymentsActivity` CSV export button: `View.GONE` when `!BuildConfig.DEBUG`
  - `BugReportActivity` `cbAttachPaymentsData`: `View.GONE` when `!BuildConfig.DEBUG`
- **Personal Data Agreement:**
  - First-launch non-cancelable dialog explaining local-only data storage
  - SharedPreferences: `"app_terms"` key, `"user_agreed_to_terms"` flag
  - Dialog text in `strings.xml` (localizable; EN + RU)
  - Re-readable in `SettingsActivity` via `btnViewTerms`
- **9.2:** `DebugProdVisibilityTest` instrumented test; pre-sets `KEY_TERMS_AGREED=true` in `@BeforeEach`; asserts export buttons visible in debug, `View.GONE` in release
- **9.3:** `docs/debug-vs-prod.md` lists all debug/release feature differences

### Task 10–10.3: Allure Reporting

- Dependency: `allure-junit5:2.27.0` added to test scope in `build.gradle.kts`
- All 9 Appium test classes annotated with `@Epic`, `@Feature`
- `@Step` annotations on `AppiumBaseTest` helpers
- `@AfterEach` in `AppiumBaseTest` captures screenshot via `Allure.addAttachment()` on test failure
- `dismissTermsDialogIfPresent()` in `AppiumBaseTest` handles first-launch agreement dialog (detects EN/RU title, clicks `android:id/button1`)
- **Makefile recipes:**
  - `make allure-install` — creates project-scoped `.venv/`, installs `allure-pytest` (bundles Allure CLI)
  - `make allure-report` — generates static HTML: `build/reports/allure-report/`
  - `make allure-serve` — opens live report in browser from `build/reports/allure-results/`
