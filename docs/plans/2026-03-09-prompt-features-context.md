# Context: PROMPT.md Feature Batch

**Branch:** feature/create-pov
**Plan:** docs/plans/2026-03-09-prompt-features.md

---

## Build & Test Commands

```bash
# Build
./gradlew assembleDebug

# Non-Appium unit tests (fast, no device)
./gradlew testDebugUnitTest \
  --tests "com.example.banksmstracker.processor.*" \
  --tests "com.example.banksmstracker.util.*" \
  --tests "com.example.banksmstracker.data.*" \
  --tests "com.example.banksmstracker.serializer.*"

# Full suite including Appium (requires device + Appium server)
make cluster-start
./gradlew installDebug
make test-appium

# Instrumented tests (requires connected device)
./gradlew connectedAndroidTest
```

---

## Phase 1 — Completed (commit: see git log)

### Task 1 + 2.9: Named regex groups

- `Constants.RegexGroups`: int indices replaced with string name constants (`AMOUNT = "amount"`, `DATE = "date"`, `TIME = "time"`, etc.). `GROUP_NAMES` map and `getGroupName(int)` removed.
- `PaymentProcessor`: uses `MatchResult.namedGroup(name)` extension (wraps `groups[name]?.value` in try-catch to avoid `IllegalArgumentException` for absent groups). Old `groupValues[n]` calls removed. `size < 7` guard removed; implicit validation via null `amount`.
- Timestamp reconstructed from separate `date` + `time` named groups: `"$date $time"` if both present; just date if time absent; null if both absent.
- `default_rules.json` (assets + test resources): pattern uses named groups; `\2` backreference changed to `\k<currency>`.

### Task 2.5: Clear buttons

- `btnClearSampleSms` and `btnClearRegexPattern` added to `activity_regex_builder.xml` and wired in `RegexBuilderActivity`.

### Task 2.6: Multiline regex pattern field

- `etRegexPattern`: `minLines=3`, `maxLines=6`, `scrollbars=vertical`.
- On save: `.replace("\n", "").replace("\r", "")` applied before encoding.

### Task 2.7/2.8: Space ↔ `\s`

- `encodePattern(raw)` → replaces spaces with `\s` (called on save).
- `decodePattern(stored)` → replaces `\s` with space (called on load into EditText).

### Task 2.10: Split Timestamp → Date + Time

- `btnPresetTimestamp` removed; `btnPresetDate` and `btnPresetTime` added to layout and wired.
- `RegexPresets.date = "(?<date>\\d{2}/\\d{2}/\\d{4})"`, `time = "(?<time>\\d{2}:\\d{2}:\\d{2})"`.
- String resources updated (EN + RU).

### Task 2.12: Auto-scroll on focus

- `ScrollView` given `id=scrollViewRegexBuilder`.
- `etSampleSms` and `etRegexPattern` call `scrollView.smoothScrollTo(0, v.top)` on focus gain.

### Task 2.13: Ignored messages in amber

- `ApplyRulesActivity` catches `MessageIgnoredException` separately → `addIgnoredItem()` renders in amber (`0xFFFF8F00`).
- Summary string includes `ignoredCount`.

### Task 6: Remove "Cannot parse message:" prefix

- `addErrorItem()` no longer prepends the exception message string.

### Test updates (Phase 1)

- All test regex patterns converted to named groups in `PaymentProcessorEdgeCaseTest`, `PaymentProcessorEnabledTest`, `PaymentProcessorWorkflowTest`.
- `ConstantsTest` updated to verify string constants.
- `RegexBuilderAppiumTest` extended with tests for new clear buttons and date/time preset buttons.

### Known Appium state

All Appium tests fail when run in batch because the Appium Docker container is `unhealthy`. This is pre-existing and unrelated to Phase 1 changes. Non-Appium unit tests pass cleanly.

```bash
docker ps                               # shows appium-server: unhealthy
docker logs appium-server --tail=50
make cluster-stop && make cluster-start
```

---

## Phase 2 — ✅ COMPLETE (commit 803bbdc)

Tasks: 2.11, 5.2–5.4, 7–7.1, 8

Implemented:
- `PaymentProcessor.approximateDate()` — fills missing timestamp from nearest neighbor
- `BugReportActivity` — filter state section + payments JSON attachment via `cbAttachPaymentsData`
- `MainActivity` — "Data & Reports" group (`btnPayments`, `btnSmsExport`, `btnBugReport`)
- `ApplyRulesActivity.EXTRA_SENDER_ADDRESS` — pre-selects sender in `RegexBuilderActivity`
- `view_rule_with_toggle.xml` — `MaterialCardView` wrapping per rule

---

## Phase 3 — ✅ COMPLETE (commit 6f42259)

Tasks: 2.1–2.4 (block editor), 3–3.2 (perf test), 4–4.1 (re-categorize)

Implemented:
- Block-based placeholder chip approach: `⟨amount⟩` markers rendered as colored spans in regex EditText
- `templateToRegex()` / `regexToTemplate()` conversion functions in `RegexBuilderActivity`
- `CategoryConcurrencyTest.kt` — rapid category assignment perf test
- Re-categorize on save: adding merchant to category triggers full payment re-categorization
- Filter/scroll state preserved via `layoutManager.onSaveInstanceState()` in `PaymentsActivity`

---

## Phase 4 — ✅ COMPLETE (commit da0aeb4)

Tasks: 9–9.3 (personal data agreement, debug/prod split), 10–10.3 (Allure reporting + Makefile recipes)

Implemented:
- `btnSmsExport`, `btnExportCsv`, `cbAttachPaymentsData` hidden (`View.GONE`) when `!BuildConfig.DEBUG`
- First-launch non-cancelable agreement dialog; prefs: `"app_terms"` / `"user_agreed_to_terms"`
- `DebugProdVisibilityTest` instrumented test
- `docs/debug-vs-prod.md` created
- `allure-junit5:2.27.0`; `@Epic`/`@Feature` on all Appium classes; `@Step` + screenshot `@AfterEach`
- Makefile: `make allure-install`, `make allure-report`, `make allure-serve`
