# Group E — Database & Model Fixes

Date: 2026-03-17

---

## PF-6 · Regex compiled per-call via property access

**File:** `app/src/main/java/com/example/banksmstracker/data/Rule.kt` (lines 22–29)

### Finding

`Rule.regexPattern` already had a manual cache (`cachedPattern` / `cachedPatternString`) that
reuses the compiled `Regex` when `pattern` has not changed. The issue description assumed there was
no cache; in reality the cache was present but compiled with `pattern.toRegex()` which does NOT
apply `RegexOption.IGNORE_CASE`. This means case-insensitive matching was missing at the model
level (line 280 of `PaymentProcessor.kt` works around this for merchant matching by constructing its
own `Regex` with `IGNORE_CASE`).

`by lazy` is not a viable replacement because `Rule` is a `data class` with mutable `var` fields;
a `copy()` call would produce a new instance with a new uninitialized delegate, and changes to
`pattern` after construction would not be reflected. The manual-cache pattern is the correct
approach for a mutable data class.

### Change

Replaced `pattern.toRegex()` with `Regex(pattern, RegexOption.IGNORE_CASE)` so that the cached
pattern is compiled with case-insensitive matching, consistent with how `PaymentProcessor` builds
ad-hoc patterns elsewhere.

**Before (line 25):**
```kotlin
cachedPattern = pattern.toRegex()
```

**After:**
```kotlin
cachedPattern = Regex(pattern, RegexOption.IGNORE_CASE)
```

No structural change to the caching logic; compilation still happens at most once per distinct
`pattern` value.

---

## OC-1 · exportSchema = false prevents migration validation

**Files changed:**
- `app/src/main/java/com/example/banksmstracker/database/BankSmsDatabase.kt` (line 22)
- `app/build.gradle.kts` (inside `defaultConfig`)

### Change 1 — Enable schema export

**File:** `BankSmsDatabase.kt`, line 22

**Before:**
```kotlin
exportSchema = false
```

**After:**
```kotlin
exportSchema = true
```

### Change 2 — Point annotation processor at output directory

The build file uses `kapt` (line 7: `kotlin("kapt")`), not KSP. The schema location argument was
added inside `defaultConfig { javaCompileOptions { annotationProcessorOptions { ... } } }`.

**File:** `app/build.gradle.kts`, after `testInstrumentationRunner` line

**Added:**
```kotlin
javaCompileOptions {
    annotationProcessorOptions {
        arguments["room.schemaLocation"] = "$projectDir/schemas"
    }
}
```

Room will now write one JSON schema file per database version to `app/schemas/`. These files should
be committed to source control so that `MigrationTestHelper` can validate migration correctness in
instrumented tests.

---

## OC-4 · SenderWithDetails joins on legacy sender_rules table

**File:** `app/src/main/java/com/example/banksmstracker/database/BankSmsDatabase.kt`

### Finding

`SenderWithDetails` uses `@Relation(entity = RuleEntity::class)` which maps to the `rules` table
(the unified table created in migration 7→8). The old `sender_rules` table was not dropped in that
migration; comment at line 180 of the original file said it was kept "for backward compatibility".

Audit of all write paths:
- `ConfigDao` has no `INSERT`/`UPDATE`/`DELETE` targeting `sender_rules`.
- `RuleDao.insertRule()` targets `rules`.
- `sender_rules` does not appear in Room's `@Database(entities = [...])` list, so Room generates no
  DAO or entity class for it.
- The only remaining SQL references to `sender_rules` are in historical migration code (read-only
  `SELECT` in migration 7→8 to copy rows, and the `ALTER TABLE` in migration 1→2).

Conclusion: `sender_rules` has no live write paths. It is an orphaned table holding a static copy
of data that was migrated to `rules` in version 8.

### Change

- Bumped `@Database(version = ...)` from `10` to `11`.
- Added `MIGRATION_10_11` that executes `DROP TABLE IF EXISTS sender_rules`.
- Registered `MIGRATION_10_11` in the `addMigrations(...)` call.

**Added migration (before `getInstance`):**
```kotlin
private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS sender_rules")
    }
}
```

`IF EXISTS` guards against devices that somehow skipped migrations 1–7 (e.g., fresh installs on
version 8+) where `sender_rules` was never created.
