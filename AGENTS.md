# BankSMSTracker Agent Playbook

## Architecture Highlights

- **Entry points**  
  - `MainActivity` routes to category/sender management screens (`app/src/main/java/com/example/banksmstracker/ui/MainActivity.kt`).  
  - `SmsReceiver` processes incoming SMS, bootstrapping `PaymentProcessor` from `ConfigRepository` (`app/src/main/java/com/example/banksmstracker/parser/SmsReceiver.kt`).
- **Persistence**  
  - Configuration + payments live in a Room database (`app/src/main/java/com/example/banksmstracker/database/*`).  
  - `ConfigRepository` is the single source of truth; always use its suspend helpers to read/update categories or senders.
- **Domain model**  
  - Configuration lives in `SmsConfig` (`data/` package) and is loaded from `assets/default_rules.json`.  
  - `PaymentProcessor` parses/categorises messages using regex rules supplied by the config and persists via `PaymentRepository`.
- **Repositories**  
  - `ConfigRepository` lazily loads + caches config; always call `ConfigRepository.load(app)` before accessing `config`.  
  - `InMemoryPaymentRepository` is the only implementation; plans for a real DB should respect existing interface methods.

## Coding Rules

- **Language & style**  
  - All app code is Kotlin; match existing nullability + `data class` conventions.  
  - Keep Android logging via `android.util.Log`; unit tests rely on the stub at `app/src/test/java/android/util/Log.kt`.  
  - When modifying configuration models, update both asset (`app/src/main/assets/default_rules.json`) and mirrored test fixtures (`app/src/test/resources/default_rules.json`, `sms_tests.json`).
- **Payment parsing**  
  - Extend parsing by adding regex rules to `PaymentRegexRule` instances; ensure `SmsConfig.validate()` continues to pass (no duplicate category names, duplicate regex per sender, or merchants in multiple categories).  
  - For categorisation, update `categories` in config; `PaymentProcessor.assignCategory` performs case-insensitive merchant lookup.  
  - `PaymentRepository.savePayment` requires the raw message + sender for deduping; duplicates are ignored by hash.
- **Config editing**  
  - Use `ConfigRepository.addCategory/updateCategory` and `addSender/updateSender`; they handle DB writes and refresh in-memory caches/processor.  
  - UI helpers call these on every edit; keep payloads mutable so adapters can mirror local state before persistence.
- **Broadcast receiver**  
  - `SmsReceiver` now supports debug extras (`EXTRA_TEST_SENDER`, `EXTRA_TEST_BODY`) for instrumentation tests. Preserve this pathway when refactoring; it is how `SmsReceptionE2ETest` injects messages.

## Testing Expectations

- **Frameworks**  
  - Unit tests use JUnit 5 + Kotlin test assertions; Mockito handles Android dependencies (`app/src/test/java/com/example/banksmstracker/repository/ConfigRepositoryTest.kt`).  
  - Instrumented tests also rely on JUnit 5 via the Mannodermaus plugin (`app/build.gradle.kts`).
- **SMS scenarios**  
  - Parser tests load cases from `app/src/test/resources/sms_tests.json`; extend that file and reuse `SmsTestLoader` when adding cases.  
  - `SmsReceptionE2ETest` constructs intents via the new extras; keep helper methods aligned if receiver signatures change.
- **Config export**  
  - Sharing/exporting uses `FileProvider` and JSON from `ConfigRepository.shareConfigFile`; respect cache-dir writes and grant URI permissions when adding new share flows.
- **Utilities**  
  - `send_sms_tests.sh` replays JSON cases against a running emulator using `adb emu sms send`; ensure new scenarios are compatible with this script (no spaces in sender addresses, or adjust script accordingly).

## Operational Notes

- Always run `./gradlew test` for unit coverage and `./gradlew connectedAndroidTest --tests com.example.banksmstracker.SmsReceiverE2ETest` after touching SMS flow.  
- Config loading is idempotent; if a test needs a clean slate, call `ConfigRepository.reset()` (currently `internal`).  
- Keep UI changes aligned with `BaseActivity` navigation/back behaviour to maintain consistent titles/back stack handling.
