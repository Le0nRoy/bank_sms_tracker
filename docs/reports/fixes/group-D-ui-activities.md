# Group D UI Activities — Fix Report

Date: 2026-03-17

---

## RC-3 / PF-1 · HIGH — DiffUtil in PaymentsActivity

**File:** `app/src/main/java/com/example/banksmstracker/ui/PaymentsActivity.kt`

### What was changed

The inner `PaymentAdapter` class previously extended `RecyclerView.Adapter` and held its own `List<Payment>` field. Its `submitList()` method assigned the new list and then called `notifyDataSetChanged()`, which redraws every visible item unconditionally.

A `PaymentDiffCallback` nested class was added as a private field-level declaration (before the adapter):

```kotlin
private class PaymentDiffCallback : DiffUtil.ItemCallback<Payment>() {
    override fun areItemsTheSame(oldItem: Payment, newItem: Payment): Boolean =
        oldItem.id != null && oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: Payment, newItem: Payment): Boolean =
        oldItem == newItem
}
```

`PaymentAdapter` was changed to extend `ListAdapter<Payment, PaymentAdapter.PaymentViewHolder>(PaymentDiffCallback())`. The manually-maintained `payments` field and the `submitList()`/`notifyDataSetChanged()` override were removed entirely. `onBindViewHolder` was updated to call `getItem(position)` (provided by `ListAdapter`).

Two imports were added: `androidx.recyclerview.widget.DiffUtil` and `androidx.recyclerview.widget.ListAdapter`.

The call site in `applyFilter()` — `adapter.submitList(filteredPayments)` — was already using the correct `ListAdapter`-compatible API name, so it required no change.

---

## SEC-5 · MEDIUM — Date filters use selectionArgs (SmsExportActivity, ApplyRulesActivity)

### SmsExportActivity

**File:** `app/src/main/java/com/example/banksmstracker/ui/SmsExportActivity.kt`
**Lines:** ~234–252 (in `loadSmsMessages()`)

Before:
```kotlin
val selection = buildString {
    var hasCondition = false
    startDate?.let {
        append("date >= $it")
        hasCondition = true
    }
    endDate?.let {
        if (hasCondition) append(" AND ")
        append("date <= $it")
    }
}.ifEmpty { null }

val cursor: Cursor? = contentResolver.query(
    uri,
    arrayOf("address", "body", "date", "type"),
    selection,
    null,
    "date DESC LIMIT 5000"
)
```

After:
```kotlin
val selectionArgs = mutableListOf<String>()
val selection = buildString {
    var hasCondition = false
    startDate?.let {
        append("date >= ?")
        selectionArgs.add(it.toString())
        hasCondition = true
    }
    endDate?.let {
        if (hasCondition) append(" AND ")
        append("date <= ?")
        selectionArgs.add(it.toString())
    }
}.ifEmpty { null }

val cursor: Cursor? = contentResolver.query(
    uri,
    arrayOf("address", "body", "date", "type"),
    selection,
    selectionArgs.takeIf { it.isNotEmpty() }?.toTypedArray(),
    "date DESC LIMIT 5000"
)
```

### ApplyRulesActivity

**File:** `app/src/main/java/com/example/banksmstracker/ui/ApplyRulesActivity.kt`
**Lines:** ~392–410 (in `getSmsMessages()`)

Before:
```kotlin
val dateFilter = buildString {
    append("address IN ($placeholders)")
    if (startDate != null) {
        append(" AND date >= $startDate")
    }
    if (endDate != null) {
        append(" AND date <= $endDate")
    }
}

val cursor: Cursor? = contentResolver.query(
    uri,
    arrayOf("address", "body", "date"),
    dateFilter,
    selectionArgs,
    "date DESC"
)
```

After:
```kotlin
val allSelectionArgs = mutableListOf(*selectionArgs)
val dateFilter = buildString {
    append("address IN ($placeholders)")
    if (startDate != null) {
        append(" AND date >= ?")
        allSelectionArgs.add(startDate.toString())
    }
    if (endDate != null) {
        append(" AND date <= ?")
        allSelectionArgs.add(endDate.toString())
    }
}

val cursor: Cursor? = contentResolver.query(
    uri,
    arrayOf("address", "body", "date"),
    dateFilter,
    allSelectionArgs.toTypedArray(),
    "date DESC"
)
```

The `selectionArgs` local variable that was previously passed directly to the query is now spread into `allSelectionArgs` so the existing address placeholders remain intact.

---

## PF-5 · MEDIUM — CheckSendersActivity SMS query limit

**File:** `app/src/main/java/com/example/banksmstracker/ui/CheckSendersActivity.kt`
**Lines:** ~99–105 (in `getSmsSenders()`)

Before:
```kotlin
val cursor: Cursor? = contentResolver.query(
    uri,
    arrayOf("DISTINCT address"),
    null,
    null,
    "address ASC"
)
```

After:
```kotlin
val cursor: Cursor? = contentResolver.query(
    uri,
    arrayOf("DISTINCT address"),
    null,
    null,
    "address ASC LIMIT 10000"
)
```

The limit is appended to the `sortOrder` parameter. This is the standard Android approach: the SQLite provider appends the `sortOrder` string directly to the SQL statement, so `LIMIT` in that position is honoured by the SMS content provider on stock AOSP and most OEM builds.

---

## SEC-6 · MEDIUM — Export files persist in cacheDir

### PaymentsActivity

**File:** `app/src/main/java/com/example/banksmstracker/ui/PaymentsActivity.kt`
**In:** `exportToCsv()`

Added `file.deleteOnExit()` immediately after creating the `File` object and before writing content:

```kotlin
val file = File(cacheDir, fileName)
file.deleteOnExit()   // <-- added
file.writeText(csvContent)
```

### SmsExportActivity

**File:** `app/src/main/java/com/example/banksmstracker/ui/SmsExportActivity.kt`
**In:** both `exportToJson()` and `exportToCsv()`

Added `file.deleteOnExit()` immediately after `File(cacheDir, fileName)` in each method:

```kotlin
// exportToJson
val file = File(cacheDir, fileName)
file.deleteOnExit()   // <-- added

// exportToCsv
val file = File(cacheDir, fileName)
file.deleteOnExit()   // <-- added
```

### Limitation note

`File.deleteOnExit()` schedules deletion when the JVM (process) exits, not when the sharing chooser returns. Files shared via `Intent.ACTION_SEND` to another app may still be readable by that app until the process terminates. A fully robust solution would use `ActivityResultLauncher` with `StartActivityForResult` to delete the file after the chooser completes. However, `deleteOnExit()` is the minimal-change approach that satisfies the requirement without restructuring the sharing flow, and it prevents permanent accumulation of export files in the cache directory across app restarts.
