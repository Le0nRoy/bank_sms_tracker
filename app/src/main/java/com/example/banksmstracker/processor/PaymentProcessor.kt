package com.example.banksmstracker.processor

import android.util.Log
import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Income
import com.example.banksmstracker.data.MessageProcessResult
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.data.RuleType
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.repository.PaymentRepository
import com.example.banksmstracker.util.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class UnparsedMessageException(message: String) : Exception("Cannot parse message: $message")

class MessageIgnoredException(val ruleName: String?) : Exception("Message ignored by rule: $ruleName")

class PaymentProcessor(
    private val senders: List<Sender>,
    private val categories: List<Category>,
    val paymentRepository: PaymentRepository
) {
    companion object {
        private const val TAG = "PaymentProcessor"
        private const val REGEX_TIMEOUT_MS = 500L
        private val regexExecutor = Executors.newSingleThreadExecutor()

        private fun MatchResult.namedGroup(name: String): String? = try {
            groups[name]?.value
        } catch (e: IllegalArgumentException) {
            null
        }

        /**
         * Runs [block] on a worker thread and returns null if it does not complete within
         * [REGEX_TIMEOUT_MS] ms. Protects against ReDoS from user-supplied patterns (OC-3).
         */
        private fun <T> safeRegex(patternStr: String, block: () -> T?): T? {
            val future = regexExecutor.submit(Callable { block() })
            return try {
                future.get(REGEX_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                future.cancel(true)
                Log.w(TAG, "Regex timeout (>${REGEX_TIMEOUT_MS}ms) for pattern: $patternStr")
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    // Filter to only enabled senders and categories
    private val enabledSenders: List<Sender>
        get() = senders.filter { it.enabled }

    private val enabledCategories: List<Category>
        get() = categories.filter { it.enabled }

    /**
     * Process a message following the workflow:
     * 1. Try payment rules -> if match, return Payment
     * 2. Try income rules -> if match, return Income
     * 3. Try ignore rules -> if match, signal message should be ignored
     * 4. If no match, throw UnparsedMessageException
     */
    fun getMessageResult(message: String, address: String): MessageProcessResult {
        val sender = enabledSenders.find { sender ->
            sender.addresses.any { it.equals(address, ignoreCase = true) }
        } ?: throw UnparsedMessageException("No sender found for address: $address")

        // 1. Try payment rules
        val paymentResult = tryPaymentRules(sender, message)
        if (paymentResult != null) {
            return MessageProcessResult.PaymentResult(paymentResult)
        }

        // 2. Try income rules
        val incomeResult = tryIncomeRules(sender, message, address)
        if (incomeResult != null) {
            return MessageProcessResult.IncomeResult(incomeResult)
        }

        // 3. Try ignore rules (no groups expected)
        val ignoreResult = tryIgnoreRules(sender, message)
        if (ignoreResult != null) {
            return ignoreResult
        }

        // 4. No match found
        throw UnparsedMessageException(message)
    }

    private fun tryPaymentRules(sender: Sender, message: String): Payment? {
        val paymentRules = sender.rules.filter { it.enabled && it.ruleType == RuleType.PAYMENT }

        for (rule in paymentRules) {
            val pattern = rule.regexPattern
            val match = safeRegex(rule.pattern) { pattern.find(message) } ?: continue

            val amount = match.namedGroup(Constants.RegexGroups.AMOUNT)?.toDoubleOrNull()
            val currency = match.namedGroup(Constants.RegexGroups.CURRENCY) ?: ""
            val card = match.namedGroup(Constants.RegexGroups.CARD)
            val merchant = match.namedGroup(Constants.RegexGroups.MERCHANT)
            val dateStr = match.namedGroup(Constants.RegexGroups.DATE)
            val timeStr = match.namedGroup(Constants.RegexGroups.TIME)
            val balance = match.namedGroup(Constants.RegexGroups.BALANCE)?.toDoubleOrNull()

            val timestamp = when {
                dateStr != null && timeStr != null -> "$dateStr $timeStr"
                dateStr != null -> dateStr
                else -> null
            }

            if (amount != null) {
                return Payment(
                    amount = amount,
                    currency = currency,
                    card = card,
                    merchant = merchant,
                    timestamp = timestamp ?: "",
                    balance = balance,
                    categoryId = null,
                    ruleId = rule.id
                )
            }
        }
        return null
    }

    private fun tryIncomeRules(sender: Sender, message: String, address: String): Income? {
        val incomeRules = sender.rules.filter { it.enabled && it.ruleType == RuleType.INCOME }

        for (rule in incomeRules) {
            val pattern = rule.regexPattern
            val match = safeRegex(rule.pattern) { pattern.find(message) } ?: continue

            val amount = match.namedGroup(Constants.RegexGroups.AMOUNT)?.toDoubleOrNull()
            val currency = match.namedGroup(Constants.RegexGroups.CURRENCY) ?: ""
            val source = match.namedGroup(Constants.RegexGroups.MERCHANT)
            val dateStr = match.namedGroup(Constants.RegexGroups.DATE)
            val timeStr = match.namedGroup(Constants.RegexGroups.TIME)
            val balance = match.namedGroup(Constants.RegexGroups.BALANCE)?.toDoubleOrNull()

            val timestamp = when {
                dateStr != null && timeStr != null -> "$dateStr $timeStr"
                dateStr != null -> dateStr
                else -> null
            }

            if (amount != null) {
                return Income(
                    amount = amount,
                    currency = currency,
                    source = source,
                    timestamp = timestamp,
                    balance = balance,
                    senderAddress = address,
                    receivedAt = System.currentTimeMillis(),
                    ruleId = rule.id
                )
            }
        }
        return null
    }

    private fun tryIgnoreRules(sender: Sender, message: String): MessageProcessResult.Ignored? {
        val ignoreRules = sender.rules.filter { it.enabled && it.ruleType == RuleType.IGNORE }

        for (rule in ignoreRules) {
            val pattern = rule.regexPattern
            // Ignore rules don't expect any groups - just check if the pattern matches.
            // safeRegex returns null on timeout, which we treat as non-match (OC-3).
            val matched = safeRegex(rule.pattern) { pattern.containsMatchIn(message) } ?: false
            if (matched) {
                Log.d(TAG, "Message matched ignore rule: ${rule.description ?: rule.pattern}")
                return MessageProcessResult.Ignored(rule.description ?: rule.pattern)
            }
        }
        return null
    }

    suspend fun processMessage(
        message: String,
        address: String,
        smsReceivedAt: Long = System.currentTimeMillis(),
        existingPayments: List<Payment>? = null
    ): Payment {
        val result = getMessageResult(message, address)

        return when (result) {
            is MessageProcessResult.PaymentResult -> {
                val categorizedPayment = assignCategory(result.payment)
                val datedPayment = approximateDate(categorizedPayment, smsReceivedAt, existingPayments)
                val inserted = paymentRepository.savePayment(datedPayment, message, address)
                if (!inserted) {
                    Log.d(TAG, "Duplicate payment skipped for sender $address")
                }
                datedPayment
            }
            is MessageProcessResult.IncomeResult -> {
                Log.d(TAG, "Income detected from $address: ${result.income}")
                // Income handling should be done via processMessageFull()
                throw UnparsedMessageException("Income message - use processMessageFull()")
            }
            is MessageProcessResult.Ignored -> {
                Log.d(TAG, "Message ignored by rule: ${result.ruleName}")
                throw MessageIgnoredException(result.ruleName)
            }
        }
    }

    /**
     * Full message processing that handles all result types.
     * Returns the processing result for proper handling by the caller.
     */
    suspend fun processMessageFull(
        message: String,
        address: String,
        smsReceivedAt: Long = System.currentTimeMillis(),
        existingPayments: List<Payment>? = null
    ): MessageProcessResult {
        val result = getMessageResult(message, address)

        return when (result) {
            is MessageProcessResult.PaymentResult -> {
                val categorizedPayment = assignCategory(result.payment)
                val datedPayment = approximateDate(categorizedPayment, smsReceivedAt, existingPayments)
                val inserted = paymentRepository.savePayment(datedPayment, message, address)
                if (!inserted) {
                    Log.d(TAG, "Duplicate payment skipped for sender $address")
                }
                MessageProcessResult.PaymentResult(datedPayment)
            }
            is MessageProcessResult.IncomeResult -> {
                Log.d(TAG, "Income detected from $address: ${result.income}")
                // Income saving should be handled by the caller with IncomeDao
                result
            }
            is MessageProcessResult.Ignored -> {
                Log.d(TAG, "Message ignored by rule: ${result.ruleName}")
                result
            }
        }
    }

    private suspend fun approximateDate(
        payment: Payment,
        referenceTime: Long,
        existingPayments: List<Payment>? = null
    ): Payment {
        if (payment.timestamp.isNotBlank()) return payment

        // Use the pre-fetched list when provided to avoid repeated full table scans (PF-3).
        val allDated = (existingPayments ?: paymentRepository.getAllPayments())
            .filter { it.timestamp.isNotBlank() && it.id != null }

        // Find the neighbor whose parsed timestamp is closest to referenceTime (DD-6).
        // This ensures historical SMS get a contextual date rather than always the newest.
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val neighbor = allDated.minByOrNull { p ->
            val parsed = runCatching {
                fmt.parse(p.timestamp.substringBefore(" ").ifEmpty { p.timestamp })?.time
            }.getOrNull()
            if (parsed != null) kotlin.math.abs(parsed - referenceTime) else Long.MAX_VALUE
        }

        return if (neighbor != null) {
            val approxDate = neighbor.timestamp.substringBefore(" ").ifEmpty { neighbor.timestamp }
            payment.copy(timestamp = approxDate)
        } else {
            // Last resort: format SMS receive time as a date string
            payment.copy(timestamp = fmt.format(Date(referenceTime)))
        }
    }

    private fun assignCategory(payment: Payment): Payment {
        val merchant = payment.merchant ?: return payment

        // Only match against enabled categories
        val category = enabledCategories.find { category ->
            category.merchants.any { m ->
                if (m.isRegex) {
                    Regex(m.pattern, setOf(RegexOption.IGNORE_CASE)).containsMatchIn(merchant)
                } else {
                    m.pattern.equals(merchant, ignoreCase = true)
                }
            }
        }

        return if (category != null) {
            payment.copy(categoryId = category.name)
        } else {
            payment
        }
    }

    /**
     * Shuts down the shared regex executor. Should be called when the processor is
     * no longer needed (e.g., from Application.onTerminate or ConfigRepository.shutdown).
     */
    fun shutdown() {
        regexExecutor.shutdown()
    }
}
