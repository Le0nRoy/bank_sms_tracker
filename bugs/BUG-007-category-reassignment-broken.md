# BUG-007: Category Re-assignment Does Not Update Existing Payments

**Status:** Fixed (see fix section)
**Severity:** High
**Component:** PaymentsActivity – `addMerchantToCategory`

---

## Description

When a user opens a payment, assigns it to a category, then opens it again and assigns it to a
**different** category via "Add to Category", the dialog closes but the payment's displayed category
does not change. The payment keeps its old category.

Additionally, the merchant is added to the new category's merchant list without being removed from
the old category, so the merchant ends up belonging to two categories simultaneously.

---

## Reproduction Steps

1. Open the **Payments** screen.
2. Tap a payment for merchant "Wolt" (currently uncategorized or in "Transport").
3. Choose "Delivery" from the spinner and tap **Add to Category**. Dialog closes.
4. Reload the payment list — "Wolt" payments now show "Delivery". ✓
5. Tap the same payment again.
6. Choose "Groceries" from the spinner and tap **Add to Category**. Dialog closes.
7. **Expected**: Payments for "Wolt" now show "Groceries".
8. **Actual**: Payments for "Wolt" still show "Delivery".

---

## Root Cause

`addMerchantToCategory` in `PaymentsActivity.kt`:

1. Only adds the merchant to the **new** category's merchant list; never removes it from the old one.
2. Calls `ConfigRepository.updateCategory(updatedCategory)` which updates the config, but does NOT
   update the `categoryName` column of existing `payments` rows in the database.
3. Has an early-exit guard: `if (!updatedMerchants.any { it.equals(merchant, ignoreCase = true) })`
   – if the merchant is already in the new category (e.g., re-selecting the same category), the
   function silently does nothing, not even dismissing the dialog.

The `loadData(preserveScroll = true)` at the end reloads payments from the DB, but since their
`categoryName` column is unchanged, the UI shows the old category.

---

## Fix

1. **`PaymentDao.kt`**: Added `updateCategoryForMerchant(merchant, categoryName)` query.
2. **`PaymentRepository`** interface + `InMemoryPaymentRepository`: Added the new method.
3. **`RoomPaymentRepository`**: Implemented delegation to DAO.
4. **`PaymentsActivity.addMerchantToCategory`**:
   - Removed the `if (!updatedMerchants.any {...})` early-exit guard.
   - Removed merchant from all other categories before adding to the new one.
   - After updating config, calls `paymentRepository.updateCategoryForMerchant(merchant, category.name)`
     to retroactively update all matching payments in the database.

---

## Tests

See `app/src/test/java/com/example/banksmstracker/repository/PaymentCategoryReassignmentTest.kt`.
