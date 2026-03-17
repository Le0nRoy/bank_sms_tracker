# Group C — PaymentProcessor Fixes

## PF-3 · MEDIUM — approximateDate() full table scan per untimestamped payment

**Files changed:**
- `app/src/main/java/com/example/banksmstracker/processor/PaymentProcessor.kt`
- `app/src/main/java/com/example/banksmstracker/ui/ApplyRulesActivity.kt`

### What was changed

`approximateDate()` previously called `paymentRepository.getAllPayments()` unconditionally on every invocation. When `ApplyRulesActivity` processed 100 historical SMS without timestamps, this produced 100 sequential full table scans.

**PaymentProcessor.kt** — `approximateDate` signature extended with an optional pre-fetched list:

```kotlin
// Before
private suspend fun approximateDate(payment: Payment, smsReceivedAt: Long): Payment {
    if (payment.timestamp.isNotBlank()) return payment
    val allDated = paymentRepository.getAllPayments()
        .filter { it.timestamp.isNotBlank() && it.id != null }
    ...
}

// After
private suspend fun approximateDate(
    payment: Payment,
    referenceTime: Long,
    existingPayments: List<Payment>? = null   // pre-fetched list, null = fetch once
): Payment {
    if (payment.timestamp.isNotBlank()) return payment
    val allDated = (existingPayments ?: paymentRepository.getAllPayments())
        .filter { it.timestamp.isNotBlank() && it.id != null }
    ...
}
```

`processMessage` and `processMessageFull` each gained a matching `existingPayments: List<Payment>? = null` parameter that is forwarded to `approximateDate`.

**ApplyRulesActivity.kt** — `getAllPayments()` is now called once before the processing loop and the result is passed to `processMessageFull`:

```kotlin
// Added before the outer for-loop
val database = BankSmsDatabase.getInstance(this@ApplyRulesActivity)
val paymentRepository = RoomPaymentRepository(database.paymentDao())
val existingPayments = withContext(Dispatchers.IO) {
    paymentRepository.getAllPayments()
}

// Inside the loop
processor.processMessageFull(smsWithDate.body, sender, smsWithDate.date, existingPayments)
```

Single-message callers (`SmsReceiver`, `SmsProcessingService`) pass no `existingPayments` argument and continue to fetch on demand — their call frequency is low enough that this is acceptable.

---

## DD-5 · LOW — processMessage() vs processMessageFull() API confusion

**File changed:** `app/src/main/java/com/example/banksmstracker/ui/ApplyRulesActivity.kt`

### What was changed

`ApplyRulesActivity` called `processor.processMessage()`, which throws `UnparsedMessageException("Income message - use processMessageFull()")` for income SMS, causing income messages to silently fall into the error bucket.

The minimal fix (option b) was chosen: update `ApplyRulesActivity` to call `processMessageFull()` and handle all three result variants.

```kotlin
// Before
val payment = withContext(Dispatchers.IO) {
    processor.processMessage(smsWithDate.body, sender, smsWithDate.date)
}
parsedCount++
addSuccessItem(payment)

// After
val result = withContext(Dispatchers.IO) {
    processor.processMessageFull(smsWithDate.body, sender, smsWithDate.date, existingPayments)
}
when (result) {
    is MessageProcessResult.PaymentResult -> {
        parsedCount++
        addSuccessItem(result.payment)
    }
    is MessageProcessResult.IncomeResult -> {
        incomeCount++
        addIgnoredItem(sender, smsWithDate.body, "Income: +${result.income.amount} ${result.income.currency}")
    }
    is MessageProcessResult.Ignored -> {
        ignoredCount++
        addIgnoredItem(sender, smsWithDate.body, result.ruleName)
    }
}
```

`processMessage()` itself was not changed; it still throws for income results, preserving its documented contract for other callers.

---

## DD-6 · LOW — approximateDate() uses wrong reference point

**File changed:** `app/src/main/java/com/example/banksmstracker/processor/PaymentProcessor.kt`

### What was changed

The original `approximateDate` selected the neighbor with the highest database id (`maxByOrNull { it.id!! }`), which always returns the globally newest payment regardless of when the current SMS was received. For batches of historical SMS processed in sequence, all untimestamped payments ended up with the same (newest) neighbor's date.

The fix selects the neighbor whose parsed timestamp is closest to `referenceTime` (the SMS `receivedAt` epoch ms), which is now passed explicitly instead of using `System.currentTimeMillis()` implicitly.

```kotlin
// Before
val neighbor = allDated.maxByOrNull { it.id!! }

// After — referenceTime is the SMS receivedAt timestamp passed by callers
val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
val neighbor = allDated.minByOrNull { p ->
    val parsed = runCatching {
        fmt.parse(p.timestamp.substringBefore(" ").ifEmpty { p.timestamp })?.time
    }.getOrNull()
    if (parsed != null) kotlin.math.abs(parsed - referenceTime) else Long.MAX_VALUE
}
```

The `smsReceivedAt` parameter (already present at call sites) is now forwarded as `referenceTime` to `approximateDate`. The fallback date string also uses `referenceTime` (was already using `smsReceivedAt` in the original, so no change there).

---

## OC-3 · MEDIUM — ReDoS risk from user-supplied regex

**File changed:** `app/src/main/java/com/example/banksmstracker/processor/PaymentProcessor.kt`

### What was changed

All three `pattern.find()` / `pattern.containsMatchIn()` call sites in `tryPaymentRules`, `tryIncomeRules`, and `tryIgnoreRules` are now guarded by a thread-based 500 ms timeout via a shared `safeRegex` helper. A `withTimeout` / coroutine approach was not used here because `getMessageResult` and the three private helpers remain non-suspend (they are also called from non-coroutine test code), so a Java `ExecutorService` future with `get(timeout, unit)` was used instead.

**Added to companion object:**

```kotlin
private const val REGEX_TIMEOUT_MS = 500L
private val regexExecutor = Executors.newCachedThreadPool()

private fun <T> safeRegex(patternStr: String, block: () -> T?): T? {
    val future = regexExecutor.submit(Callable { block() })
    return try {
        future.get(REGEX_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
        future.cancel(true)
        Log.w(TAG, "Regex timeout (>${REGEX_TIMEOUT_MS}ms) for pattern: $patternStr")
        null
    } catch (e: Exception) {
        null
    }
}
```

**Updated call sites (examples):**

```kotlin
// Before
val match = pattern.find(message) ?: continue

// After
val match = safeRegex(rule.pattern) { pattern.find(message) } ?: continue
```

```kotlin
// Before (ignore rules)
if (pattern.containsMatchIn(message)) { ... }

// After
val matched = safeRegex(rule.pattern) { pattern.containsMatchIn(message) } ?: false
if (matched) { ... }
```

A timed-out rule is treated as a non-match (the message continues to the next rule). The `regexExecutor` is a cached thread pool shared across all `PaymentProcessor` instances; the interrupted future thread continues running until the regex engine respects the interrupt, which is a known limitation of Java regex — the timeout prevents the *caller* from blocking, not necessarily the regex thread itself.
