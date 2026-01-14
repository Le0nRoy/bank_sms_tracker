package com.example.banksmstracker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Rule(
    var id: Long? = null,
    var senderId: Long? = null,
    @SerialName("regex")
    var pattern: String,
    var description: String? = null,
    var enabled: Boolean = true,
    var ruleType: RuleType = RuleType.PAYMENT,
) {
    @kotlinx.serialization.Transient
    private var cachedPattern: Regex? = null

    @kotlinx.serialization.Transient
    private var cachedPatternString: String? = null

    val regexPattern: Regex
        get() {
            if (cachedPattern == null || cachedPatternString != pattern) {
                cachedPattern = pattern.toRegex()
                cachedPatternString = pattern
            }
            return cachedPattern!!
        }

    companion object {
        /**
         * Create a Rule from legacy IgnoreRule.
         */
        fun fromIgnoreRule(rule: IgnoreRule): Rule = Rule(
            id = rule.id,
            senderId = rule.senderId,
            pattern = rule.pattern,
            description = rule.description,
            enabled = rule.enabled,
            ruleType = RuleType.IGNORE,
        )
    }
}
