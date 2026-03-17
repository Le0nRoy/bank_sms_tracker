# Group B — ConfigRepository fixes

**File:** `app/src/main/java/com/example/banksmstracker/repository/ConfigRepository.kt`
**Date:** 2026-03-17

---

## RC-1 · CRITICAL — ConfigRepository.load() TOCTOU

**Lines affected:** 51–64 (before fix)

**Problem:** The `if (_config != null) return` guard was outside the coroutine mutex, so two
concurrent callers could both observe `_config == null`, both pass the check, and both proceed
to initialise the database and seed the config, resulting in a double-init race.

**Fix:** Moved the entire `load()` body (guard + database init + seed + refresh) inside
`configMutex.withLock { ... }`. The guard is now the very first statement inside the lock, so
only the first thread that acquires the mutex will actually run the initialisation; any
subsequent caller will find `_config != null` and return immediately.

```kotlin
// Before
fun load(application: Application, seedIfEmpty: Boolean = true) {
    if (_config != null) return          // <-- outside any lock
    database = BankSmsDatabase.getInstance(application)
    ...
    runBlocking {
        if (seedIfEmpty && isConfigEmpty()) { seedFromAssets(application) }
        refreshConfigInternal()
    }
}

// After
fun load(application: Application, seedIfEmpty: Boolean = true) {
    runBlocking(Dispatchers.IO) {
        configMutex.withLock {
            if (_config != null) return@withLock   // <-- inside the lock
            database = BankSmsDatabase.getInstance(application)
            ...
            if (seedIfEmpty && isConfigEmpty()) { seedFromAssets(application) }
            refreshConfigInternal()
        }
    }
}
```

---

## RC-2 · HIGH — getPaymentProcessor() unsynchronized lazy init

**Lines affected:** 167–172 (before fix)

**Problem:** The `paymentProcessor ?: PaymentProcessor(...).also { paymentProcessor = it }`
expression is not atomic. Two threads can both evaluate the left-hand side as `null`,
both construct a `PaymentProcessor`, and then the assignment races — leaving one constructed
object orphaned.

**Fix:** Wrapped the entire expression in `synchronized(this) { ... }`. Only one thread at a
time can enter the block; subsequent threads will see the already-assigned value.

```kotlin
// Before
fun getPaymentProcessor(): PaymentProcessor =
    paymentProcessor ?: PaymentProcessor(...).also { paymentProcessor = it }

// After
fun getPaymentProcessor(): PaymentProcessor =
    synchronized(this) {
        paymentProcessor ?: PaymentProcessor(...).also { paymentProcessor = it }
    }
```

Note: `refreshConfigInternal()` already assigns `paymentProcessor` inside `configMutex`, so
the `synchronized(this)` block is an additional, complementary guard covering the
`getPaymentProcessor()` call-site specifically.

---

## PF-2 · MEDIUM — N+1 DB writes in recategorizeAllPayments()

**Lines affected:** 569–594 (before fix)

**Problem:** The original loop called `paymentRepository.updatePaymentCategory(paymentId, …)`
once per payment whose category changed. With N payments needing recategorization, that is N
individual `UPDATE … WHERE id = ?` statements.

**Fix:** Replaced the per-payment updates with a batch strategy using the existing
`updateCategoryForMerchant(merchant, newCategory)` DAO method, which issues a single
`UPDATE … WHERE merchant = ? COLLATE NOCASE` per distinct merchant name. The new
implementation:

1. Iterates all payments once to build a `Map<merchantKey, newCategory>` (resolving the
   correct category for each distinct merchant exactly once, reusing cached results for
   repeated merchants).
2. Counts how many payment rows will actually change (returned value).
3. Filters to only the merchants whose resolved category differs from at least one payment's
   current category, then calls `updateCategoryForMerchant` once per such merchant.

DB round-trips drop from O(payments) to O(distinct merchants that need a category change).

No new DAO methods were required; `updateCategoryForMerchant` already existed.

```kotlin
// Before (simplified)
for (payment in allPayments) {
    val newCategory = resolveCategory(payment.merchant)
    if (newCategory != payment.categoryId) {
        paymentRepository.updatePaymentCategory(payment.id!!, newCategory)  // N+1
        count++
    }
}

// After (simplified)
val merchantToNewCategory = mutableMapOf<String, String?>()
for (payment in allPayments) {
    val newCategory = merchantToNewCategory.getOrPut(merchant.lowercase()) { resolveCategory(merchant) }
    if (newCategory != payment.categoryId) count++
}
// One UPDATE per distinct changed merchant
for ((key, paymentsForMerchant) in merchantsToUpdate) {
    paymentRepository.updateCategoryForMerchant(paymentsForMerchant.first().merchant!!, merchantToNewCategory[key])
}
```

---

## PF-4 · MEDIUM — Redundant config refreshes

**Lines affected:** 66–72 (getCategories / getSenders, before fix); refreshConfigInternal (added clear flag)

**Problem:** `getCategories()` and `getSenders()` called `refreshConfigInternal()` on every
invocation, even when no mutation had taken place since the last load. Each call triggered two
DAO queries and a new `SmsConfig` + `PaymentProcessor` construction for no benefit.

**Fix:** Added a `@Volatile private var configDirty = true` flag to the object.

- `configDirty` is set to `true` in every mutation path:
  `addCategory`, `updateCategory`, `deleteCategory`, `addSender`, `updateSender`,
  `deleteSender`, `mergeConfig`, `reset`, `clearAllData`.
- `refreshConfigInternal()` sets `configDirty = false` at the end of its mutex-protected block
  (after `_config` and `paymentProcessor` have been written).
- `getCategories()` and `getSenders()` now call `refreshConfigInternal()` only when
  `configDirty == true`.

```kotlin
// Added field
@Volatile
private var configDirty = true

// getCategories / getSenders — before
refreshConfigInternal()   // always

// getCategories / getSenders — after
if (configDirty) refreshConfigInternal()

// refreshConfigInternal — added at end of withLock block
configDirty = false

// each mutation method — added before refreshConfigInternal()
configDirty = true
```

---

## OC-2 · MEDIUM — runBlocking in ConfigRepository.load()

**Lines affected:** 58–63 (before fix, combined with RC-1 fix)

**Problem:** `load()` is called from `Application.onCreate()` on the main thread (and from
`SmsReceiver`, `SmsProcessingService`, and several Activities). The original `runBlocking { … }`
dispatched coroutines on the calling thread's dispatcher. When called from the main thread
that means DB I/O ran on the UI dispatcher, violating Android's strict-mode policy and
blocking the UI thread for the duration of the database initialisation.

**Fix:** Changed `runBlocking { … }` to `runBlocking(Dispatchers.IO) { … }`. The
`Dispatchers.IO` context is passed as the coroutine context so all work inside the block
(including the nested `configMutex.withLock`, `isConfigEmpty()`, `seedFromAssets()`, and
`refreshConfigInternal()`) runs on the IO thread pool. The calling thread is still blocked
(because `load()` is a regular, non-suspend function and must complete synchronously before
`Application.onCreate()` returns), but the blocking work no longer occupies the UI dispatcher.

Making `load()` a `suspend` function was considered but rejected: it would require every
call-site (`SmsReceiver.onReceive`, `Application.onCreate`, multiple Activity `onCreate`
overrides) to be converted, which is a much larger refactor outside the scope of these fixes.

```kotlin
// Before
runBlocking {
    if (seedIfEmpty && isConfigEmpty()) { seedFromAssets(application) }
    refreshConfigInternal()
}

// After
runBlocking(Dispatchers.IO) {
    configMutex.withLock {
        if (_config != null) return@withLock
        ...
        if (seedIfEmpty && isConfigEmpty()) { seedFromAssets(application) }
        refreshConfigInternal()
    }
}
```
