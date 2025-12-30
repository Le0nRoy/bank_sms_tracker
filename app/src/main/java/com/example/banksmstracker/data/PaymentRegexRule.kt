package com.example.banksmstracker.data

import kotlinx.serialization.Serializable

@Serializable
data class PaymentRegexRule(var id: Long? = null, var regex: String, var enabled: Boolean = true) {
    @kotlinx.serialization.Transient
    private var cachedPattern: Regex? = null

    @kotlinx.serialization.Transient
    private var cachedRegexString: String? = null

    val regexPattern: Regex
        get() {
            if (cachedPattern == null || cachedRegexString != regex) {
                cachedPattern = regex.toRegex()
                cachedRegexString = regex
            }
            return cachedPattern!!
        }
}
