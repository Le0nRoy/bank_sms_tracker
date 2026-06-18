# BUG-001: SQL Injection Vulnerability in ApplyRulesActivity

## Status
- [x] Reported
- [x] In Progress
- [x] Fixed
- [x] Verified

## Description
The ApplyRulesActivity constructs SQL queries using string interpolation with user-controlled data (sender addresses), creating a SQL injection vulnerability.

## Steps to Reproduce
1. Configure a sender with address containing SQL injection: `' OR 1=1 --`
2. Go to Apply Rules activity
3. Grant SMS permission
4. The malicious SQL is executed

## Expected Behavior
SQL queries should use parameterized queries to prevent injection attacks.

## Actual Behavior
String interpolation builds SQL: `"address IN (${configuredSenders.joinToString(",") { "'$it'" }})"`
If sender addresses contain quotes, the SQL statement is corrupted or exploited.

## Root Cause
Line 119 in ApplyRulesActivity.kt uses string interpolation instead of parameterized queries.

## Fix
Use `selectionArgs` parameter in `contentResolver.query()` with placeholders.

## Verification
- [x] Unit test added/updated
- [x] Integration test passes
- [x] Manual test with special characters in sender

## Related Files
- `app/src/main/java/com/example/banksmstracker/ui/ApplyRulesActivity.kt:119`
