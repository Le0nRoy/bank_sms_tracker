package com.example.banksmstracker.util

/**
 * Utility for matching SMS addresses against configured sender addresses.
 *
 * Handles common variations in SMS address formats:
 * - Case differences: "BANK" vs "bank"
 * - Format variations: "VM-BANK", "+91BANK", "AD-BANK" matching configured "BANK"
 */
object SmsAddressMatcher {

    /**
     * Checks if an SMS address matches a configured address.
     *
     * Matching logic:
     * - Case insensitive comparison
     * - Configured address can be found as substring within SMS address
     *   (e.g., "BANK" matches "VM-BANK", "+91BANK")
     *
     * @param smsAddress The address from the SMS message
     * @param configuredAddress The address configured for a sender
     * @return true if the SMS address matches the configured address
     */
    fun matches(smsAddress: String, configuredAddress: String): Boolean {
        return smsAddress.contains(configuredAddress, ignoreCase = true)
    }

    /**
     * Checks if an SMS address matches any of the configured addresses.
     *
     * @param smsAddress The address from the SMS message
     * @param configuredAddresses Set of configured sender addresses
     * @return true if the SMS address matches any configured address
     */
    fun matchesAny(smsAddress: String, configuredAddresses: Set<String>): Boolean {
        return configuredAddresses.any { matches(smsAddress, it) }
    }

    /**
     * Checks if an SMS address matches any of the configured addresses.
     *
     * @param smsAddress The address from the SMS message
     * @param configuredAddresses Collection of configured sender addresses
     * @return true if the SMS address matches any configured address
     */
    fun matchesAny(smsAddress: String, configuredAddresses: Collection<String>): Boolean {
        return configuredAddresses.any { matches(smsAddress, it) }
    }
}
