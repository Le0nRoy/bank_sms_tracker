package com.example.banksmstracker.util

import java.security.MessageDigest

/**
 * Utility for computing hashes used for message deduplication.
 */
object HashUtil {

    /**
     * Computes a SHA-256 hash from a message and sender combination.
     *
     * This is used for detecting duplicate messages to prevent
     * processing the same SMS multiple times.
     *
     * @param message The SMS message body
     * @param sender The sender address
     * @return A lowercase hex string representation of the SHA-256 hash
     */
    fun computeMessageHash(message: String, sender: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val combined = "$sender::$message"
        val hash = digest.digest(combined.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
