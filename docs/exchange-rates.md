# Exchange Rates Feature

## Overview

BankSMSTracker fetches daily exchange rates from the National Bank of Georgia (NBG) and caches them locally. This enables:

- On-demand currency conversion in the Payments screen ("Display in" spinner)
- Automatic prefetch after processing SMS messages
- A dedicated **Exchange Rates** screen to browse, filter, and download stored rates

---

## NBG API

**Endpoint:**  
`https://nbg.gov.ge/gw/api/ct/monetarypolicy/currities/en/json/?currency=USD&date=YYYY-MM-DD`

**Method:** GET  
**Query parameters:**
- `currency` — 3-letter code (USD, EUR, RUB, …)
- `date` — ISO date `YYYY-MM-DD`

**Response format:**
```json
[{
  "currencies": [
    { "code": "USD", "rate": 2.7812 },
    { "code": "EUR", "rate": 3.1024 }
  ]
}]
```

`rate` = how many GEL per 1 unit of `currency`. I.e. `1 USD = 2.7812 GEL`.

**Timeouts:** 5 s connect + 5 s read.  
**Error handling:** Returns `null` on any network or parse failure.

---

## Caching Architecture (ExchangeRateCache)

Three-tier lookup (fastest first):

| Tier | Storage | Key | Lifetime |
|------|---------|-----|----------|
| 1 | `ConcurrentHashMap` (in-memory) | `"yyyy-MM-dd:CURRENCY"` | App session |
| 2 | Room `exchange_rates` table | `(date, currency)` composite PK | Persistent |
| 3 | NBG REST API | — | fetched on miss, then persisted |

**GEL special case:** always returns `1.0` without any I/O.

### Key methods

| Method | Description |
|--------|-------------|
| `getRateToGel(dateMs, currency, dao)` | Rate for epoch-ms date |
| `getRateToGelForDate(dateStr, currency, dao)` | Rate for "yyyy-MM-dd" date |
| `prefetchRates(pairs, dao)` | Parallel batch prefetch; returns failed pairs |
| `clearMemoryCache()` | Purge in-memory cache (used in tests) |

---

## Database Schema

Table: `exchange_rates` (added in migration v12→v13)

| Column | Type | Notes |
|--------|------|-------|
| `date` | TEXT | `yyyy-MM-dd`, part of PK |
| `currency` | TEXT | 3-letter code, part of PK |
| `rateToGel` | REAL | `1 CURRENCY = rateToGel GEL` |

---

## Payments Screen — Currency Conversion

**Default state:** "Display in" spinner shows `— original —` (position 0). All payments display their stored original currency. No network calls.

**When a currency is selected:**
1. `loadAndRenderPage()` collects all unique `(date, srcCurrency)` and `(date, displayCurrency)` pairs for the current page (25 payments).
2. `ExchangeRateCache.prefetchRates()` is called — parallel fetches, DB first then network.
3. A horizontal `ProgressBar` (`pbConversionLoading`) is shown while prefetch runs; prev/next page buttons disabled.
4. On success: adapter receives a `ratesMap: Map<String, Double>` (key `"yyyy-MM-dd:CURRENCY"`) and renders all amounts synchronously — no async flicker.
5. On any failure: spinner resets to "— original —", error notification shown via `NotificationHelper`.

**Page navigation:** selecting prev/next page triggers the same batch-prefetch flow for the new page.

**Cross-currency conversion:**  
`convertedAmount = payment.amount × (srcRate / displayRate)`  
where both rates are in GEL. E.g. USD→EUR: `amount_usd × (usd_to_gel / eur_to_gel)`.

---

## Post-Processing Prefetch

After any SMS processing, exchange rates are pre-fetched for all unique dates found in the processed batch (USD, EUR, RUB by default — see `ExchangeRateCache.PREFETCH_CURRENCIES`).

**Key design:** dates are collected across **all messages in a batch** first; a single `ExchangeRateCache.prefetchRates()` call is made once after the entire batch is processed — not once per message. This avoids redundant parallel fetches when multiple messages share the same date.

| Trigger | Batch scope | Where |
|---------|-------------|-------|
| `ApplyRulesActivity.applyRules()` completes | All items returned by the apply-rules pass | `prefetchExchangeRatesForProcessedItems()` |
| `SmsProcessingService` SMS broadcast | All PDUs in the broadcast (one `serviceScope.launch`) | `prefetchRatesForDates()` after the message loop |
| `SmsReceiver` SMS broadcast | All PDUs in the broadcast (one `goAsync()` scope) | `prefetchRatesForDates()` after the message loop |

Errors are reported via the `exchange_rate_channel` notification channel.

---

## Exchange Rates Activity

Accessible from `MainActivity` → **"Exchange Rates"** button.

**Features:**
- Lists all stored rates, newest first.
- Multi-currency filter (checkbox dialog, same UX as category filter).
- Date-range filter (start / end date pickers).
- **Download Missing** button: iterates the selected date range × currencies, finds absent pairs, batch-fetches them.
- **Re-fetch** per-row button: deletes from DB, clears memory cache, re-fetches from network.

---

## Notification Channels

| Channel ID | Name | Used for |
|------------|------|----------|
| `unmatched_sms_channel` | Unmatched SMS | SMS with no matching rule |
| `exchange_rate_channel` | Exchange Rate Errors | Rate fetch failures (page load, post-processing) |

---

## Supported Currencies

GEL, USD, EUR, RUB (matches `currency_entries` spinner array in `strings.xml`).  
Add entries to both the spinner array and `PREFETCH_CURRENCIES` in `ExchangeRateCache` to extend support.
