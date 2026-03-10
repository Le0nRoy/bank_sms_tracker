# Plan: PROMPT.md Feature Batch

**Date:** 2026-03-09
**Branch:** feature/create-pov
**Source:** PROMPT.md tasks 1–10

---

## Summary of Tasks

| # | Task | Complexity | Phase | Status |
|---|------|-----------|-------|--------|
| 1 | Named regex groups in builder | M | 1 | ✅ Done |
| 2.1–2.4 | Block-based pattern editor (presets as chips) | XL | 3 | ⬜ Pending |
| 2.5 | Clear buttons for Sample SMS and Regex Pattern | S | 1 | ✅ Done |
| 2.6 | Multiline Regex Pattern + trim newlines on save | S | 1 | ✅ Done |
| 2.7/2.8 | Space ↔ `\s` conversion | S | 1 | ✅ Done |
| 2.9 | Named groups for arbitrary group order | M | 2 | ✅ Done (folded into task 1) |
| 2.10 | Split Timestamp → Date + Time presets | S | 1 | ✅ Done |
| 2.11 | Optional fields (partial matches still valid) | M | 2 | ⬜ Pending |
| 2.12 | Auto-scroll focused text field to top | S | 1 | ✅ Done |
| 2.13 | Ignored messages shown with different color | S | 1 | ✅ Done |
| 3–3.2 | Perf test for rapid category assignment | M | 3 | ⬜ Pending |
| 4–4.1 | Re-categorize all payments; preserve filter/scroll | M | 3 | ⬜ Pending |
| 5.2–5.4 | Bug Report improvements + main menu group | M | 2 | ⬜ Pending |
| 6 | Remove "Cannot parse message:" prefix | XS | 1 | ✅ Done |
| 7–7.1 | Auto-fill sender in RegexBuilder from ProcessSMS | S | 2 | ⬜ Pending |
| 8 | Rules in separate card boxes in SendersActivity | S | 2 | ⬜ Pending |
| 9–9.3 | Personal data agreement + debug/prod split | L | 4 | ⬜ Pending |
| 10–10.3 | Allure reporting for tests | L | 4 | ⬜ Pending |

---

## Phase 1: Quick Wins (no architectural changes)

### Task 1 + 2.9: Named Regex Groups
**Current state:** Groups are positional integers (group 1 = amount, 2 = currency, etc.)
**Goal:** Use Java named groups `(?<amount>...)` so group order can vary per sender.

**Changes:**
1. `RegexPresets` class — update preset strings to use named groups:
   - Amount: `(?<amount>\\d+(?:[.]\\d{2}))`
   - Currency: `(?<currency>[A-Z]{3})`
   - Card: `(?<card>.+?)`
   - Merchant: `(?<merchant>.+?)`
   - Date: `(?<date>\\d{2}/\\d{2}/\\d{4})`  ← new split preset
   - Time: `(?<time>\\d{2}:\\d{2}:\\d{2})`   ← new split preset
   - Balance: `(?<balance>\\d+(?:[.]\\d{2}))`
2. `PaymentProcessor` — parse named groups via `match.groups["amount"]?.value` instead of `match.groupValues[1]`
3. `Constants.RegexGroups` — can be simplified to just string constants, remove int indices
4. Default config `default_rules.json` — update pattern to use named groups
5. Test fixtures `sms_tests.json` — patterns updated accordingly
6. All unit tests that reference positional groups

### Task 2.5: Clear Buttons
**Changes:**
- Add `btnClearSampleSms` and `btnClearRegexPattern` buttons in `activity_regex_builder.xml`
- Wire click listeners in `RegexBuilderActivity.kt`

### Task 2.6: Multiline Regex Pattern Field
**Changes:**
- In `activity_regex_builder.xml`: set `etRegexPattern` to `inputType="textMultiLine"` and `minLines="3"`
- In `RegexBuilderActivity.saveRegex()`: trim all `\n` and `\r` from the pattern before saving

### Task 2.7 / 2.8: Space ↔ `\s` Conversion
**Changes:**
- On **save**: replace literal spaces with `\s` in the pattern
- On **load/display**: replace `\s` with a visible space in the shown pattern
- Implement `encodePattern(raw: String): String` and `decodePattern(stored: String): String` helpers in `RegexBuilderActivity`

### Task 2.10: Split Timestamp Preset → Date + Time
**Changes:**
- Remove `btnPresetTimestamp`, add `btnPresetDate` and `btnPresetTime` in XML
- Update `RegexPresets` class with `date` and `time` fields (remove `timestamp`)
- Update `PaymentProcessor` to read `date` and `time` named groups, combine to `LocalDateTime`

### Task 2.12: Auto-Scroll Text Fields to Top When Focused
**Changes:**
- Add `OnFocusChangeListener` for `etSampleSms` and `etRegexPattern`
- On focus gained: `scrollView.post { scrollView.scrollTo(0, view.top) }`
- Requires wrapping the form content in a `ScrollView` if not already

### Task 2.13: Ignored Messages — Different Color
**Changes:**
- In `ApplyRulesActivity` (ProcessSMS), when a rule with type=IGNORE matches: show the message text with a different color (e.g., amber/yellow) instead of red error
- Currently shows "Cannot parse message: ..." in red → ignored messages should show something like "Message ignored (rule: IGNORE)" in gray/amber

### Task 6: Remove "Cannot parse message:" Prefix
**Changes:**
- In `ApplyRulesActivity.kt`: find where error messages are built with "Cannot parse message:" prefix and remove it, keeping only the message body

---

## Phase 2: Medium Changes

### Task 2.11: Optional Fields in Partial Matches
**Goal:** A regex can match without all named groups present. TBC transfer example lacks merchant, time, balance — should still produce a valid payment.

**Changes:**
1. `PaymentProcessor.tryPaymentRules()`: change from `groupValues[n]` to `groups["name"]?.value` (already from Phase 1)
2. Make amount + currency required (payment is invalid without them); all other fields optional
3. Remove any null-check failures on merchant, card, timestamp, balance
4. **Date/time approximation when absent:**
   - If both `date` and `time` groups are absent: query `RoomPaymentRepository` for the closest neighboring payments (by DB insert order / message hash order) that have a timestamp; use that date as an approximation; leave time component as `null`/midnight
   - If only `time` is absent: keep the matched date, leave time as midnight (00:00:00)
   - If only `date` is absent: cannot use just a time without a date; apply the same neighbor-proximity approximation for the date part, combine with the matched time
   - Approximation logic lives in a new helper `PaymentProcessor.approximateDate(payment: Payment, repository: PaymentRepository): Payment`
5. `Payment` data class: ensure all fields except amount and currency are nullable (they likely already are)
6. Update related tests for partial match scenarios, including a test with a message that has no date/time and verifies the approximated timestamp matches the nearest neighbor's date

### Task 5.2–5.4: Bug Report + Main Menu Reorganization
**5.2:** Add to Bug Report info about active filters (date range, category, sender) at time of report:
- Add a "Payments Filter State" optional section showing current filter values

**5.3:** Add checkbox "Attach payments data (JSON/CSV)" in BugReport:
- If checked, generates a temp file with all payments, attaches to the share intent
- File is JSON or CSV (let user pick, or default to JSON)

**5.4:** Main menu reorganization:
- Create a new section/group in `activity_main.xml` with label "Data & Reports"
- Move `btnSmsExport`, `btnPayments` CSV (or add `btnExportPayments`), and `btnBugReport` under this group
- Add visual separator/label in the layout

### Task 7–7.1: Auto-Fill Sender in RegexBuilder from ProcessSMS
**Changes:**
1. In `ApplyRulesActivity` (ProcessSMS): add a "Open in RegexBuilder" button per message
2. Launch `RegexBuilderActivity` with intent extra `EXTRA_SENDER_ADDRESS = "..."`
3. In `RegexBuilderActivity.onCreate()`: check for extra, pre-select that sender in spinner
4. If sender not in spinner list: show dialog "Sender X not configured. Create new?" → if yes, open create-sender flow

### Task 8: Rules in Card Boxes in SendersActivity
**Changes:**
- In `SendersAdapter`'s item layout: wrap each rule entry in a `MaterialCardView`
- Each card shows: rule type indicator (colored badge or spinner) + rule text + delete button
- Improves visual separation between rules

---

## Phase 3: Block-Based Pattern Editor (2.1–2.4) + Perf Test + Re-Categorize

### Task 2.1–2.4: Block-Based Pattern Editor (Simpler First Pass)
**Implement the simpler SpannableString placeholder approach. A full token-editor is deferred to a separate plan.**

**Design (this iteration):**
- Keep the `EditText` for Regex Pattern
- When a preset button is pressed: insert a placeholder marker `⟨amount⟩`, `⟨currency⟩`, etc. (Unicode angle-brackets — visually distinct, not typeable by normal keyboard)
- Use a `TextWatcher` + `SpannableStringBuilder` to re-render placeholders as colored, bold, non-editable spans after every text change
- Spans use `BackgroundColorSpan` (tinted with primary color) + `StyleSpan(BOLD)` + `ForegroundColorSpan(white)` to look like chips
- On **save**: `templateToRegex(text)` replaces each `⟨name⟩` with its corresponding named group regex `(?<name>...)`
- On **load**: `regexToTemplate(regex)` replaces known named-group patterns back to `⟨name⟩` markers
- Raw regex editing (without placeholders) still works — only named groups get converted to placeholders on load

**Conversion functions:**
- `templateToRegex(template: String): String`
- `regexToTemplate(regex: String): String`

**What is NOT in this iteration (deferred):**
- Full token list model with individually-editable literal segments
- Drag-and-drop reordering of tokens
- A custom `View` replacing the `EditText`

**Separate plan:** Create `docs/plans/2026-03-09-block-editor-v2.md` outlining the full custom token-view approach for the next iteration.

### Task 3–3.2: Category Assignment Perf Test
**Goal:** Verify that rapid category assignments don't leave payments as "Uncategorized".

**Test plan (`CategoryConcurrencyTest.kt`):**
1. Insert 50 payments with a known merchant
2. Rapidly assign a category to that merchant (10 times in quick succession via coroutine)
3. Wait for all DB operations to complete
4. Assert all 50 payments have the correct category
5. Verify no race condition with a timing stress test

**If bug found:** Fix the race condition in `PaymentProcessor.assignCategory()` or the coroutine dispatcher for DB writes.

### Task 4–4.1: Re-Categorize All Payments on Category Change
**Current:** Only the single payment being assigned gets re-categorized.
**Goal:** When a merchant is added to a category, re-run category assignment on ALL stored payments.

**Changes:**
1. `ConfigRepository`: after updating a category's merchant list, trigger a re-categorization event
2. `PaymentRepository`: add method `recategorizeAll(categories: List<Category>)` that updates all payments in the DB
3. `PaymentsActivity`: observe a re-categorization complete event and refresh the list
4. **Preserve filter/scroll:**
   - Save `recyclerView.layoutManager?.onSaveInstanceState()` before refresh
   - Reapply current spinner/date selections
   - Restore `layoutManager.onRestoreInstanceState(state)`

---

## Phase 4: Personal Data + Allure

### Task 9–9.3: Personal Data Processing Agreement
**9.1:** Features that involve sending or exporting raw personal data are **hidden** (not just disabled) in production builds:
- `SmsExportActivity` entry point in `MainActivity`: hide `btnSmsExport` when `!BuildConfig.DEBUG`
- `PaymentsActivity` CSV export button: `View.GONE` when `!BuildConfig.DEBUG`
- `BugReportActivity` "Attach payments data" checkbox: `View.GONE` when `!BuildConfig.DEBUG`
- No "not available" message shown — the options simply do not exist in the production UI

**9.2:** Tests for debug vs prod:
- Instrumented test: assert export buttons visible in debug, hidden/disabled in release
- Unit test: verify `BuildConfig.DEBUG`-gated code paths

**9.3:** Documentation:
- Create `docs/debug-vs-prod.md` listing feature differences

**Personal Data Agreement:**
- Add a first-launch dialog explaining what data is stored locally (SMS → payments, no external transmission)
- SharedPreferences flag `"user_agreed_to_terms"` — if false on launch, show dialog
- Dialog text in `strings.xml` (localizable)
- Include in SettingsActivity for re-reading

### Task 10–10.3: Allure Reporting
**Framework:** Allure JUnit5 adapter (`io.qameta.allure:allure-junit5`) for unit/Appium tests; Allure for Android for instrumented tests.

**Changes:**
1. Add Allure JUnit5 dependency to `build.gradle.kts` (test scope)
2. Annotate all test classes with `@Feature`, `@Story`, `@DisplayName`
3. Add `@Step` annotations to helper methods in `AppiumBaseTest`
4. On test failure: capture screenshot and attach via `Allure.addAttachment()`
5. Add `@AfterEach` method in `AppiumBaseTest` that takes screenshot if test failed
6. Map test files/classes to suites using `@Suite`, `@Feature`, `@Story` hierarchy

**Makefile recipes:**
- `make allure-install` — creates a project-scoped Python `.venv` at `.venv/` (if not present), installs `allure-pytest` (which bundles the Allure CLI) into it. Does NOT install globally.
- `make allure-serve` — runs `.venv/bin/allure serve build/reports/allure-results` to open the report in a browser locally (no server upload needed)
- `make allure-report` — runs `.venv/bin/allure generate build/reports/allure-results -o build/reports/allure-report --clean` to generate a static HTML report

**Python .venv note:** The `.venv/` directory is project-scoped (inside the repo root or the `app/` module dir). It is added to `.gitignore`. The `allure` CLI binary from the Python package is used to serve/generate reports, so no separate JVM/npm installation of Allure CLI is needed.

---

## Implementation Order

1. **Phase 1 first** (tasks: 1, 2.5, 2.6, 2.7/2.8, 2.10, 2.12, 2.13, 6)
   - All independent quick changes, low risk
   - Named groups is foundational for later work

2. **Phase 2** (tasks: 2.11, 5.2–5.4, 7–7.1, 8)
   - Medium complexity, builds on Phase 1 named groups

3. **Phase 3** (tasks: 2.1–2.4, 3–3.2, 4–4.1)
   - Complex UI and concurrency work

4. **Phase 4** (tasks: 9–9.3, 10–10.3)
   - Infrastructure work, least urgent

---

## Key Files to Modify

| File | Reason |
|------|--------|
| `ui/RegexBuilderActivity.kt` | Named groups, presets, clear buttons, scroll, block editor |
| `ui/ApplyRulesActivity.kt` | Remove error prefix, color for ignored, sender fill |
| `ui/SendersActivity.kt` | Rules in card boxes |
| `ui/PaymentsActivity.kt` | Re-categorize + filter/scroll preserve |
| `ui/BugReportActivity.kt` | Attachments, filter state |
| `ui/MainActivity.kt` | Menu reorganization |
| `processor/PaymentProcessor.kt` | Named groups, optional fields |
| `data/Constants.kt` | Named group string constants |
| `serializer/ConfigLoader.kt` | Default pattern update |
| `assets/default_rules.json` | Pattern update to named groups |
| `res/layout/activity_regex_builder.xml` | Clear buttons, multiline field |
| `res/layout/activity_senders_item.xml` | Card boxes |
| `res/layout/activity_main.xml` | Menu groups |

---

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Named group migration breaks existing saved configs | Write migration that converts old positional patterns to named group patterns at load time |
| Block editor too complex for one PR | Use simpler placeholder approach first |
| Re-categorize performance on large datasets | Run in background coroutine, show progress indicator |
| Allure Android compatibility with JUnit5 | Test with a simple annotation first before wiring screenshots |
