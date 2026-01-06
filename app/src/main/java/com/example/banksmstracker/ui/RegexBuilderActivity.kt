package com.example.banksmstracker.ui

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.banksmstracker.R
import com.example.banksmstracker.data.PaymentRegexRule
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegexBuilderActivity : BaseActivity() {

    private lateinit var etSampleSms: EditText
    private lateinit var etRegexPattern: EditText
    private lateinit var btnTestRegex: Button
    private lateinit var btnSelectSms: Button
    private lateinit var tvResults: TextView
    private lateinit var spinnerSenders: Spinner
    private lateinit var btnSaveRegex: Button
    private lateinit var spinnerExistingPatterns: Spinner

    private var senders: List<Sender> = emptyList()
    private var smsMessages: List<SmsMessage> = emptyList()
    private var selectedSenderForFilter: Sender? = null

    data class SmsMessage(val address: String, val body: String, val date: Long = 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_regex_builder)

        initViews()
        setupListeners()
        loadSenders()

        // Handle incoming sample SMS from ApplyRulesActivity
        intent.getStringExtra(ApplyRulesActivity.EXTRA_SAMPLE_SMS)?.let { sampleSms ->
            etSampleSms.setText(sampleSms)
        }
    }

    private fun initViews() {
        etSampleSms = findViewById(R.id.etSampleSms)
        etRegexPattern = findViewById(R.id.etRegexPattern)
        btnTestRegex = findViewById(R.id.btnTestRegex)
        btnSelectSms = findViewById(R.id.btnSelectSms)
        tvResults = findViewById(R.id.tvResults)
        spinnerSenders = findViewById(R.id.spinnerSenders)
        btnSaveRegex = findViewById(R.id.btnSaveRegex)
        spinnerExistingPatterns = findViewById(R.id.spinnerExistingPatterns)
    }

    private fun setupListeners() {
        btnTestRegex.setOnClickListener {
            testRegex()
        }

        btnSaveRegex.setOnClickListener {
            saveRegexToSender()
        }

        btnSelectSms.setOnClickListener {
            if (checkSmsPermission()) {
                showSmsSelectionDialog()
            } else {
                requestSmsPermission()
            }
        }
    }

    private fun checkSmsPermission(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_SMS),
            Constants.RequestCodes.SMS_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.RequestCodes.SMS_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showSmsSelectionDialog()
            } else {
                Toast.makeText(this, R.string.sms_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSmsSelectionDialog() {
        // First, show sender filter dialog
        showSenderFilterDialog()
    }

    private fun showSenderFilterDialog() {
        val senderOptions = mutableListOf(getString(R.string.all_senders))
        senderOptions.addAll(senders.map { it.name })

        AlertDialog.Builder(this)
            .setTitle(R.string.filter_by_sender)
            .setItems(senderOptions.toTypedArray()) { _, which ->
                selectedSenderForFilter = if (which == 0) null else senders[which - 1]
                loadAndShowSmsMessages()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadAndShowSmsMessages() {
        CoroutineScope(Dispatchers.Main).launch {
            val configuredAddresses = if (selectedSenderForFilter != null) {
                selectedSenderForFilter!!.addresses.map { it.lowercase() }.toSet()
            } else {
                senders.flatMap { it.addresses }.map { it.lowercase() }.toSet()
            }

            smsMessages = withContext(Dispatchers.IO) {
                loadSmsMessages(configuredAddresses)
            }

            if (smsMessages.isEmpty()) {
                val message = if (selectedSenderForFilter != null) {
                    getString(R.string.no_sms_from_sender, selectedSenderForFilter!!.name)
                } else {
                    getString(R.string.no_sms_found)
                }
                Toast.makeText(this@RegexBuilderActivity, message, Toast.LENGTH_SHORT).show()
                return@launch
            }

            showSmsListDialog()
        }
    }

    private fun showSmsListDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sms_selection, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerSmsMessages)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.select_sms_message)
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .create()

        val adapter = SmsMessageAdapter(smsMessages) { selectedSms ->
            etSampleSms.setText(selectedSms.body)
            dialog.dismiss()
        }
        recyclerView.adapter = adapter

        dialog.show()
    }

    inner class SmsMessageAdapter(
        private val messages: List<SmsMessage>,
        private val onItemClick: (SmsMessage) -> Unit
    ) : RecyclerView.Adapter<SmsMessageAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvSender: TextView = itemView.findViewById(R.id.tvSmsSender)
            val tvBody: TextView = itemView.findViewById(R.id.tvSmsBody)
            val tvDate: TextView = itemView.findViewById(R.id.tvSmsDate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_sms_message, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val sms = messages[position]
            holder.tvSender.text = sms.address
            holder.tvBody.text = sms.body
            holder.tvDate.text = if (sms.date > 0) {
                java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(sms.date))
            } else ""
            holder.itemView.setOnClickListener { onItemClick(sms) }
        }

        override fun getItemCount(): Int = messages.size
    }

    private fun loadSmsMessages(filterAddresses: Set<String>? = null): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val uri = Uri.parse("content://sms/inbox")

        val cursor: Cursor? = contentResolver.query(
            uri,
            arrayOf("address", "body", "date"),
            null,
            null,
            "date DESC LIMIT 200"
        )

        cursor?.use {
            val addressColumn = it.getColumnIndex("address")
            val bodyColumn = it.getColumnIndex("body")
            val dateColumn = it.getColumnIndex("date")
            while (it.moveToNext()) {
                val address = it.getString(addressColumn) ?: continue
                val body = it.getString(bodyColumn) ?: continue
                val date = if (dateColumn >= 0) it.getLong(dateColumn) else 0L

                // Filter by configured addresses if specified
                if (filterAddresses != null && address.lowercase() !in filterAddresses) {
                    continue
                }

                messages.add(SmsMessage(address, body, date))
            }
        }

        return messages
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
                setupExistingPatternsSpinner(emptyList())
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

                // Setup existing patterns spinner
                spinnerSenders.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (position > 0) {
                            val selectedSender = senders[position - 1]
                            setupExistingPatternsSpinner(selectedSender.rules)
                        } else {
                            setupExistingPatternsSpinner(emptyList())
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        setupExistingPatternsSpinner(emptyList())
                    }
                }
                setupExistingPatternsSpinner(emptyList())
            }
        }
    }

    private fun setupExistingPatternsSpinner(rules: List<PaymentRegexRule>) {
        val patternOptions = mutableListOf(getString(R.string.new_pattern_hint))
        patternOptions.addAll(rules.map { truncatePattern(it.regex) })

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            patternOptions
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerExistingPatterns.adapter = adapter

        spinnerExistingPatterns.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0 && position <= rules.size) {
                    val selectedRule = rules[position - 1]
                    etRegexPattern.setText(selectedRule.regex)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun truncatePattern(pattern: String): String {
        return if (pattern.length > 40) {
            pattern.take(37) + "..."
        } else {
            pattern
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

        // Show confirmation dialog
        showSaveConfirmation(sender, regexPattern)
    }

    private fun showSaveConfirmation(sender: Sender, regexPattern: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.save_regex_confirm_title)
            .setMessage(getString(R.string.save_regex_confirm_message, sender.name, regexPattern))
            .setPositiveButton(R.string.confirm) { _, _ ->
                performSaveRegex(sender, regexPattern)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performSaveRegex(sender: Sender, regexPattern: String) {
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

    private fun getGroupName(index: Int): String =
        Constants.RegexGroups.getGroupName(index)

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
