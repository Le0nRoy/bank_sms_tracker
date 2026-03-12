# Debug vs Production Feature Differences

This document lists UI features and behaviours that differ between debug and release builds.

## UI Elements Hidden in Production

The following UI controls are set to `View.GONE` in release builds (`!BuildConfig.DEBUG`).
They are fully absent from the production UI — no "not available" message is shown.

| Feature | Element | Activity | Reason |
|---------|---------|----------|--------|
| SMS Export | `btnSmsExport` | `MainActivity` | Exports raw SMS — personal data |
| CSV Payments Export | `btnExportCsv` | `PaymentsActivity` | Exports all payments — personal data |
| Attach Payments (bug report) | `cbAttachPaymentsData` | `BugReportActivity` | Attaches payment JSON — personal data |

### Implementation

Each activity gates the element visibility immediately after `findViewById`:

```kotlin
if (!BuildConfig.DEBUG) {
    btnSmsExport.visibility = View.GONE
}
```

## Personal Data Agreement

On first launch, `MainActivity` shows a non-dismissible `AlertDialog` explaining that:

- SMS messages are read and parsed **locally** on the device.
- Payment records are stored in a **local** Room database.
- **No data is transmitted** to external servers.

The user must tap **I Understand** to proceed. The agreement is stored in
`SharedPreferences("app_terms")` under key `"user_agreed_to_terms"`.

The privacy notice can be re-read at any time from **Settings → Privacy → View Privacy Notice**.

## Testing

| Test class | What it verifies |
|-----------|-----------------|
| `DebugProdVisibilityTest` | Debug-build buttons are `VISIBLE`; terms dialog appears on first launch; suppressed after agreement |

Release-build visibility (all three elements are `GONE`) is enforced by code review and
the `BuildConfig.DEBUG` gate — it cannot be tested in the same debug APK.
