package com.example.banksmstracker.repository

/**
 * Result of a configuration import operation.
 */
sealed class ImportResult {
    /**
     * Import completed successfully.
     */
    data class Success(
        val sendersAdded: Int,
        val sendersMerged: Int,
        val categoriesAdded: Int,
        val categoriesMerged: Int
    ) : ImportResult() {
        val totalChanges: Int
            get() = sendersAdded + sendersMerged + categoriesAdded + categoriesMerged

        override fun toString(): String = "Imported: $sendersAdded senders added, $sendersMerged merged; " +
            "$categoriesAdded categories added, $categoriesMerged merged"
    }

    /**
     * Import failed with an error.
     */
    data class Error(val message: String) : ImportResult()
}
