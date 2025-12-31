package com.example.banksmstracker.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.example.banksmstracker.R
import com.example.banksmstracker.data.PaymentRegexRule
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.repository.ConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegexBuilderActivity : BaseActivity() {

    private lateinit var etSampleSms: EditText
    private lateinit var etRegexPattern: EditText
    private lateinit var btnTestRegex: Button
    private lateinit var tvResults: TextView
    private lateinit var spinnerSenders: Spinner
    private lateinit var btnSaveRegex: Button

    private var senders: List<Sender> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_regex_builder)

        initViews()
        setupListeners()
        loadSenders()
    }

    private fun initViews() {
        etSampleSms = findViewById(R.id.etSampleSms)
        etRegexPattern = findViewById(R.id.etRegexPattern)
        btnTestRegex = findViewById(R.id.btnTestRegex)
        tvResults = findViewById(R.id.tvResults)
        spinnerSenders = findViewById(R.id.spinnerSenders)
        btnSaveRegex = findViewById(R.id.btnSaveRegex)
    }

    private fun setupListeners() {
        btnTestRegex.setOnClickListener {
            testRegex()
        }

        btnSaveRegex.setOnClickListener {
            saveRegexToSender()
        }
    }

    private fun loadSenders() {
        CoroutineScope(Dispatchers.Main).launch {
            senders = withContext(Dispatchers.IO) {
                ConfigRepository.getSenders()
            }

            if (senders.isEmpty()) {
                spinnerSenders.isEnabled = false
                btnSaveRegex.isEnabled = false
                val adapter = ArrayAdapter(
                    this@RegexBuilderActivity,
                    android.R.layout.simple_spinner_item,
                    listOf(getString(R.string.no_senders_available))
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerSenders.adapter = adapter
            } else {
                val senderNames = listOf(getString(R.string.select_sender_hint)) +
                    senders.map { it.name }
                val adapter = ArrayAdapter(
                    this@RegexBuilderActivity,
                    android.R.layout.simple_spinner_item,
                    senderNames
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerSenders.adapter = adapter
            }
        }
    }

    private fun saveRegexToSender() {
        val regexPattern = etRegexPattern.text.toString().trim()

        if (regexPattern.isBlank()) {
            Toast.makeText(this, R.string.error_empty_pattern, Toast.LENGTH_SHORT).show()
            return
        }

        // Validate regex
        try {
            Regex(regexPattern)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.regex_save_failed, e.message),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val selectedPosition = spinnerSenders.selectedItemPosition
        if (selectedPosition <= 0 || senders.isEmpty()) {
            Toast.makeText(this, R.string.no_sender_selected, Toast.LENGTH_SHORT).show()
            return
        }

        // Adjust index because first item is the hint
        val sender = senders[selectedPosition - 1]

        // Check if this regex already exists for the sender
        val existingRegexes = sender.rules.map { it.regex.trim() }.toSet()
        if (regexPattern in existingRegexes) {
            Toast.makeText(
                this,
                getString(R.string.regex_save_failed, "Pattern already exists"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Add new rule and save
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val updatedRules = sender.rules.toMutableList()
                updatedRules.add(PaymentRegexRule(regex = regexPattern))

                val updatedSender = sender.copy(rules = updatedRules)

                withContext(Dispatchers.IO) {
                    ConfigRepository.updateSender(updatedSender)
                }

                Toast.makeText(
                    this@RegexBuilderActivity,
                    getString(R.string.regex_saved, sender.name),
                    Toast.LENGTH_SHORT
                ).show()

                // Refresh senders list
                loadSenders()
            } catch (e: Exception) {
                Toast.makeText(
                    this@RegexBuilderActivity,
                    getString(R.string.regex_save_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun testRegex() {
        val sampleSms = etSampleSms.text.toString()
        val regexPattern = etRegexPattern.text.toString()

        if (sampleSms.isBlank()) {
            tvResults.text = getString(R.string.error_empty_sms)
            return
        }

        if (regexPattern.isBlank()) {
            tvResults.text = getString(R.string.error_empty_pattern)
            return
        }

        try {
            val regex = Regex(regexPattern)
            val match = regex.find(sampleSms)

            val result = StringBuilder()

            if (match != null) {
                result.append("MATCH FOUND\n")
                result.append("=".repeat(40))
                result.append("\n\n")

                result.append("Full match:\n")
                result.append("  \"${match.value}\"\n\n")

                if (match.groupValues.size > 1) {
                    result.append("Captured groups:\n")
                    for (i in 1 until match.groupValues.size) {
                        val groupValue = match.groupValues[i]
                        val groupName = getGroupName(i)
                        result.append("  Group $i ($groupName):\n")
                        result.append("    \"$groupValue\"\n")
                    }
                    result.append("\n")

                    result.append("Payment preview:\n")
                    result.append("-".repeat(40))
                    result.append("\n")
                    result.append(buildPaymentPreview(match.groupValues))
                } else {
                    result.append("No captured groups.\n")
                    result.append("Use parentheses () to capture groups.\n")
                }
            } else {
                result.append("NO MATCH\n")
                result.append("=".repeat(40))
                result.append("\n\n")
                result.append("The pattern did not match the SMS.\n\n")
                result.append("Tips:\n")
                result.append("- Check for typos in the pattern\n")
                result.append("- Use .* to match any characters\n")
                result.append("- Use \\d+ for numbers\n")
                result.append("- Escape special chars: . * + ? [ ] ( ) { } | \\ ^ $\n")
            }

            tvResults.text = result.toString()
        } catch (e: Exception) {
            val errorResult = StringBuilder()
            errorResult.append("REGEX ERROR\n")
            errorResult.append("=".repeat(40))
            errorResult.append("\n\n")
            errorResult.append("Invalid regex pattern:\n")
            errorResult.append("${e.message}\n\n")
            errorResult.append("Common issues:\n")
            errorResult.append("- Unbalanced parentheses\n")
            errorResult.append("- Unescaped special characters\n")
            errorResult.append("- Invalid escape sequences\n")

            tvResults.text = errorResult.toString()
        }
    }

    private fun getGroupName(index: Int): String = when (index) {
        1 -> "amount"
        2 -> "currency"
        3 -> "card"
        4 -> "merchant"
        5 -> "timestamp"
        6 -> "balance"
        else -> "extra"
    }

    private fun buildPaymentPreview(groupValues: List<String>): String {
        val preview = StringBuilder()

        val amount = groupValues.getOrNull(1)?.toDoubleOrNull()
        val currency = groupValues.getOrNull(2) ?: ""
        val card = groupValues.getOrNull(3) ?: ""
        val merchant = groupValues.getOrNull(4) ?: ""
        val timestamp = groupValues.getOrNull(5) ?: ""
        val balance = groupValues.getOrNull(6)?.toDoubleOrNull()

        if (amount != null) {
            preview.append("  Amount:    ${"%.2f".format(amount)} $currency\n")
        } else {
            preview.append("  Amount:    (invalid or missing)\n")
        }

        preview.append("  Card:      ${card.ifBlank { "(not captured)" }}\n")
        preview.append("  Merchant:  ${merchant.ifBlank { "(not captured)" }}\n")
        preview.append("  Timestamp: ${timestamp.ifBlank { "(not captured)" }}\n")

        if (balance != null) {
            preview.append("  Balance:   ${"%.2f".format(balance)} $currency\n")
        } else {
            preview.append("  Balance:   (not captured)\n")
        }

        return preview.toString()
    }
}
