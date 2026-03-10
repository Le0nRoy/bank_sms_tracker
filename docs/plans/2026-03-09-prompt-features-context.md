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

## Phase 2 — Pending

Tasks: 2.11, 5.2–5.4, 7–7.1, 8

Key files to touch:
- `PaymentProcessor.kt` — optional field handling + date approximation
- `BugReportActivity.kt` — filter state section + payment attachment
- `MainActivity.kt` — menu reorganization ("Data & Reports" group)
- `ApplyRulesActivity.kt` — "Open in RegexBuilder" button with sender pre-fill
- `SendersActivity.kt` + adapter layout — `MaterialCardView` per rule

---

## Phase 3 — Pending

Tasks: 2.1–2.4 (block editor), 3–3.2 (perf test), 4–4.1 (re-categorize)

---

## Phase 4 — Pending

Tasks: 9–9.3 (personal data agreement, debug/prod split), 10–10.3 (Allure reporting + Makefile recipes)
