# Bug Fix & Feature Plan — 2026-04-01

Covers 7 bugs/features reported against the current `feature/create-pov` branch.

---

## Bug 1 — Settings import appends duplicates

**Root cause:** `mergeConfig()` reads `config.senders` / `config.categories` from the
in-memory singleton without first refreshing from the DB. If the in-memory state is stale
the merge cannot find existing senders/categories and treats every imported item as new.

**Fix:** Call `refreshConfigInternal()` at the start of `importConfig()` (before
delegating to `mergeConfig()`), so the latest DB state is always used as the base.

**Files:** `repository/ConfigRepository.kt`

---

## Bug 2 — Unknown regex is invisible / unreadable in template mode

**Root cause (two parts):**

1. `regexToTemplate()` only converts the 7 *known* named capture groups
   (`amount`, `currency`, `card`, `merchant`, `date`, `time`, `balance`).
   Any `(?<name>…)` with an unknown name (e.g. `(?<code>\d+)`) is left as raw regex.
2. `applyPlaceholderSpans()` only applies the purple highlight to `⟨name⟩` tokens.
   Any other regex syntax (including `\d+` in a plain IGNORE rule) is shown as
   unstyled plain text, making it indistinguishable from a literal string and creating
   the impression the pattern has no effect.

**Fix (two parts):**

1. At the end of `regexToTemplate()`, after the known-name loop, add a second pass that
   converts **any remaining** `(?<name>…)` group — regardless of name — to `⟨name⟩`.
   Pattern: `\(\?<([^>]+)>(?:[^)(]|\([^)]*\))*\)` → `⟨$1⟩`

2. In `applyPlaceholderSpans()`, after applying purple spans for `⟨name⟩` tokens, also
   apply a **secondary style** (e.g. amber / light-gray background) to regex-special
   sequences that are *not* already inside a `⟨…⟩` span — sequences such as
   `\d`, `\w`, `\s`, `\D`, `\W`, `\S`, `[…]`, `(?:…)`, and quantifiers `+`, `*`, `?`,
   `{n,m}`. This makes it visually clear that those portions are still active regex
   elements, not literal text.

**Files:** `util/RegexTemplateUtils.kt`, `util/RegexSpanUtils.kt`

---

## Bug 3 — Incomes not saved when processing SMS via "Process SMS"

**Root cause:** In `ApplyRulesActivity.processSms()` (around line 288) the
`IncomeResult` branch only appends the income to the *display* list. Unlike
`SmsReceiver.saveIncome()` and `SmsProcessingService.saveIncome()`, no call to
`incomeDao().insertIncome()` is made, so the income never reaches the DB.

**Fix:** After the `IncomeResult` branch, build an `IncomeEntity` from `result.income`
(same fields as `SmsReceiver.saveIncome()`) and call
`database.incomeDao().insertIncome(incomeEntity)` with the IGNORE conflict strategy
already set on the DAO. Use `HashUtil.computeMessageHash(body, sender)` for the hash,
`smsWithDate.date` for `receivedAt`.

**Files:** `ui/ApplyRulesActivity.kt`

---

## Bug 4 + 4.1 — Currency conversion in Payments & Spending Report

### 4 — USD amounts shown as GEL in spending report

**Root cause:** `buildCategoryTotals()` sums `payment.amount` with no currency
conversion. The single exchange rate fetched for today is displayed as supplementary
text but is not applied to per-category totals or the pie/bar charts.

### 4.1.1 — Currency spinner + paging on Payments screen

**Fix — ExchangeRateCache (multi-currency):**

- Change the in-memory cache key from `dateStr` to `"$dateStr:$currency"` to prevent
  collisions between currencies.
- Add a public method
  `getRateToGel(dateMs: Long, currency: String, dao: ExchangeRateDao): Double?` that
  mirrors the existing USD-only logic but parameterises the currency.
- Update `fetchFromNetwork()` to accept a `currency` parameter and pass it as
  `?currency=$currency&date=$dateStr` in the NBG API URL.

**Fix — Payments screen (currency spinner):**

- Add `spinnerCurrency` to `activity_payments.xml` with entries: GEL (default), USD,
  EUR, RUB.
- Add `selectedDisplayCurrency: String` state (default `"GEL"`) to `PaymentsActivity`.
- When currency changes, re-render the current page (amounts are converted on-the-fly
  for display only; stored values are unchanged).
- Display formula per payment:
  - same currency as selected → show as-is
  - different currency → convert via `ExchangeRateCache.getRateToGel()` for that
    payment's parsed date, then convert GEL→target if needed (for USD/EUR/RUB display
    target, chain two rates: source→GEL→target).

**Fix — Paging (25 payments per page):**

- Add state: `currentPage = 0`, `pageSize = 25`.
- Add `pagedPayments` derived from `filteredPayments.subList(pageStart, pageEnd)`.
- Add Prev / Next buttons and a page indicator label (`"Page X / Y"`) to
  `activity_payments.xml`.
- The adapter receives `pagedPayments`; `filteredPayments` stays unchanged.
- Resetting filter always resets `currentPage = 0`.

**Fix — Spending report uses all filtered payments:**

- `showSpendingReport()` explicitly passes `filteredPayments` (not `pagedPayments`).
- Inside the report, convert every payment to GEL (or the selected display currency)
  using the exchange rate for **that payment's individual date**, fetched via the
  updated `ExchangeRateCache.getRateToGel()`.

### 4.1 — Performance tests for currency conversion

- Add `PaymentConversionPerfTest.kt` (unit test, no device needed).
- Test: create a list of 25 `Payment` objects with known dates and currencies; run
  conversion through a mocked / pre-seeded `ExchangeRateCache`; assert completion
  within a time budget (e.g. 200 ms) and log elapsed time per payment.

**Files:**
`util/ExchangeRateCache.kt`,
`ui/PaymentsActivity.kt`,
`res/layout/activity_payments.xml`,
`res/values/strings.xml`,
`test/…/PaymentConversionPerfTest.kt`

---

## Bug 5 — "Uncategorized only" label should be "Uncategorized"

**Root cause:** `R.string.uncategorized_only` is `"Uncategorized only"`. This is
misleading because the filter can be combined with named categories.

**Fix:** Change the string value to `"Uncategorized"` in both
`res/values/strings.xml` and `res/values-ru/strings.xml`.
`updateCategoryButtonLabel()` already uses `R.string.uncategorized_only` and requires
no code change.

**Files:**
`res/values/strings.xml`,
`res/values-ru/strings.xml`

---

## Bug 6 — Payments not sorted by date

**Root cause:** `PaymentDao.getAllPayments()` returns rows `ORDER BY id DESC`
(database insertion order). `filterPayments()` preserves that order. SMS imported in
batches can arrive out of chronological sequence.

**Fix:** In `PaymentsActivity.applyFilter()`, after calling `filterPayments()`, sort
the result ascending by parsed transaction timestamp:

```kotlin
filteredPayments = filterPayments(…).sortedBy {
    parseTransactionTimestamp(it.timestamp) ?: Long.MAX_VALUE
}
```

Payments with unparseable timestamps sort to the end.

**Files:** `ui/PaymentsActivity.kt`

---

## Bug 7 — Date display format uses `/` instead of `.`

**Root cause:** `payment.timestamp` stores the raw string captured by the regex from
the SMS body (e.g. `24/03/2026 14:30:00` or `24/03/2026`). The adapter calls
`tvTimestamp.text = payment.timestamp` without reformatting.

**Fix:** Add a helper function `formatDisplayTimestamp(raw: String): String` that:

1. Tries to parse with `SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)`.
   If successful → formats as `"dd.MM.yyyy HH:mm:ss"`.
2. Otherwise tries `SimpleDateFormat("dd/MM/yyyy", Locale.US)`.
   If successful → formats as `"dd.MM.yyyy"`.
3. Falls back to `raw` unchanged.

Apply in:
- `PaymentsActivity.PaymentAdapter.bind()` for `tvTimestamp`
- `IncomesActivity.IncomeAdapter.bind()` for `tvTimestamp` (using `income.timestamp`)

**Files:** `ui/PaymentsActivity.kt`, `ui/IncomesActivity.kt`
(helper can live in `util/DateFormatUtils.kt` if reused elsewhere)

---

## Implementation Order

| Step | Bug  | Why this order |
|------|------|----------------|
| 1    | 5    | Trivial string change, no deps |
| 2    | 7    | Isolated display helper, no deps |
| 3    | 6    | One-line sort after filter, no deps |
| 4    | 3    | Self-contained DB write in ApplyRulesActivity |
| 5    | 1    | Self-contained refresh call in ConfigRepository |
| 6    | 2    | Isolated util changes (RegexTemplateUtils + RegexSpanUtils) |
| 7    | 4+4.1| Most complex; depends on ExchangeRateCache extension |
