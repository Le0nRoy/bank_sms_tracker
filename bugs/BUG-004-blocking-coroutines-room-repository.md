# BUG-004: Blocking Coroutines in RoomPaymentRepository

## Status
- [x] Reported
- [x] In Progress
- [x] Fixed
- [x] Verified

## Description
RoomPaymentRepository uses `runBlocking {}` which blocks the calling thread. This violates coroutine best practices and can cause ANR on the main thread.

## Steps to Reproduce
1. Call `savePayment()` from main thread
2. Main thread blocks during database operation
3. UI becomes unresponsive

## Expected Behavior
All repository methods should be `suspend` functions to allow non-blocking execution.

## Actual Behavior
`runBlocking {}` blocks the calling thread, causing potential ANR.

## Root Cause
Lines 14-32, 35, 39, 43 in RoomPaymentRepository.kt use `runBlocking {}`.

## Fix
Convert methods to `suspend` functions and remove `runBlocking {}`.

## Verification
- [x] All repository methods are suspend
- [x] Callers use proper coroutine scope
- [x] Integration tests pass

## Related Files
- `app/src/main/java/com/example/banksmstracker/repository/RoomPaymentRepository.kt`
