# Group A Security Fixes — Receiver & Backup

Date: 2026-03-17

---

## SEC-1 · CRITICAL — SMS body logged in plaintext

**File:** `app/src/main/java/com/example/banksmstracker/parser/SmsReceiver.kt`

**Lines changed:** `handleMessage()` (approx. lines 129–163), `saveIncome()` (approx. line 185)

**What was changed:**

Added `import com.example.banksmstracker.BuildConfig` at the top of the file.

All `Log.d` / `Log.e` calls in `handleMessage()` that include SMS sender, body, or parsed payment/income fields were wrapped in `if (BuildConfig.DEBUG)` blocks. The duplicate-income log in `saveIncome()` was changed to use a combined condition.

Before (example — PaymentResult branch):
```kotlin
is MessageProcessResult.PaymentResult -> {
    Log.d(
        TAG,
        "SMS from $sender processed as payment.\nMessage: $body\nParsed: ${result.payment}"
    )
}
```

After:
```kotlin
is MessageProcessResult.PaymentResult -> {
    if (BuildConfig.DEBUG) {
        Log.d(
            TAG,
            "SMS from $sender processed as payment.\nMessage: $body\nParsed: ${result.payment}"
        )
    }
}
```

The same wrapping was applied to the `IncomeResult`, `Ignored`, and `catch` branches. The `saveIncome()` duplicate-skip log was changed from:
```kotlin
if (insertedId == -1L) {
    Log.d(TAG, "Duplicate income skipped for sender $sender")
}
```
to:
```kotlin
if (insertedId == -1L && BuildConfig.DEBUG) {
    Log.d(TAG, "Duplicate income skipped for sender $sender")
}
```

Note: the service-delegation log at line 55 (`"SmsProcessingService is running — delegating real-time SMS to service"`) does not include any sensitive data and was left unguarded intentionally.

---

## SEC-4 · HIGH — Test-payload injection via exported receiver

**File:** `app/src/main/java/com/example/banksmstracker/parser/SmsReceiver.kt`

**Lines changed:** `onReceive()`, test-extras handling block (approx. line 65)

**What was changed:**

Added `if (!BuildConfig.DEBUG) return` as the first statement inside the `if (!testSender.isNullOrBlank() && !testBody.isNullOrBlank())` block, so the test injection pathway is entirely unreachable in production (release) builds.

Before:
```kotlin
if (!testSender.isNullOrBlank() && !testBody.isNullOrBlank()) {
    val pendingResult = goAsync()
    ...
```

After:
```kotlin
if (!testSender.isNullOrBlank() && !testBody.isNullOrBlank()) {
    if (!BuildConfig.DEBUG) return
    val pendingResult = goAsync()
    ...
```

---

## SEC-3 · HIGH — allowBackup=true with no database exclusion

**File:** `app/src/main/res/xml/backup_rules.xml`

**What was changed:**

The file previously contained only a commented-out example with no active exclusion rules. The active content was replaced to exclude the Room database and the terms-agreement shared preferences file from Android Auto Backup.

Before (active content only):
```xml
<full-backup-content>
    <!--
   <include domain="sharedpref" path="."/>
   <exclude domain="sharedpref" path="device.xml"/>
-->
</full-backup-content>
```

After:
```xml
<full-backup-content>
    <exclude domain="database" path="bank_sms_tracker.db" />
    <exclude domain="sharedpref" path="app_terms.xml" />
</full-backup-content>
```

Note: Additional shared-preference files (e.g., `payments_filter_state`) were not excluded by the issue specification. They contain only UI state (filter selections, not payment data) so excluding them was not required. They can be added in a follow-up if desired.

---

## LC-5 · MEDIUM — Bug report shares device hardware fingerprint

**Files changed:**
- `app/src/main/java/com/example/banksmstracker/ui/BugReportActivity.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-ru/strings.xml`

**What was changed:**

Removed the three identifying hardware fields (`Build.MANUFACTURER` / `Build.MODEL`, `Build.PRODUCT`, `Build.HARDWARE`) and the human-readable Android version string (`Build.VERSION.RELEASE`). Only the Android API level (`Build.VERSION.SDK_INT`) is retained.

Before (in `buildReport()`, device info section):
```kotlin
report.append(getString(R.string.device_model_label, Build.MANUFACTURER, Build.MODEL))
report.append("\n")
report.append(getString(R.string.android_version_label, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
report.append("\n")
report.append(getString(R.string.product_label, Build.PRODUCT))
report.append("\n")
report.append(getString(R.string.hardware_label, Build.HARDWARE))
report.append("\n\n")
```

After:
```kotlin
report.append(getString(R.string.android_version_label, Build.VERSION.SDK_INT))
report.append("\n\n")
```

String resources updated to match the new single-argument signature:

`values/strings.xml` before:
```xml
<string name="android_version_label">Android Version: %1$s (API %2$d)</string>
```
After:
```xml
<string name="android_version_label">Android API Level: %1$d</string>
```

`values-ru/strings.xml` before:
```xml
<string name="android_version_label">Версия Android: %1$s (API %2$d)</string>
```
After:
```xml
<string name="android_version_label">Уровень API Android: %1$d</string>
```

The now-unused string resources `device_model_label`, `product_label`, and `hardware_label` remain defined in `strings.xml` but are no longer referenced from `BugReportActivity`. They can be removed in a separate cleanup pass if desired.

---

## SEC-6 · MEDIUM — Export files persist in cacheDir (BugReportActivity only)

**File:** `app/src/main/java/com/example/banksmstracker/ui/BugReportActivity.kt`

**What was changed:**

Added an `ActivityResultLauncher<Intent>` registered via `registerForActivityResult(ActivityResultContracts.StartActivityForResult())` that deletes the exported file after the share chooser returns. A `pendingExportFile: File?` field tracks the file between `generatePaymentsFile()` and the callback.

`generatePaymentsFile()` return type changed from `android.net.Uri?` to `Pair<android.net.Uri?, File?>` so the `File` reference is available for deletion.

The share chooser for the payments-attached path was changed from `startActivity(Intent.createChooser(...))` to `shareChooserLauncher.launch(Intent.createChooser(...))`.

New field and launcher (added before `onCreate`):
```kotlin
private var pendingExportFile: File? = null

private val shareChooserLauncher: ActivityResultLauncher<Intent> =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pendingExportFile?.delete()
        pendingExportFile = null
    }
```

In `sendReport()`, the destructuring and launcher call:
```kotlin
val (paymentsUri, exportFile) = generatePaymentsFile()
pendingExportFile = exportFile
// ...
shareChooserLauncher.launch(Intent.createChooser(shareIntent, getString(R.string.share_report_title)))
```

The text-only share path (no file attachment) continues to use `startActivity` unchanged, as there is no file to clean up in that branch.

New imports added:
```kotlin
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
```
