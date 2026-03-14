package com.example.banksmstracker.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.banksmstracker.R
import com.example.banksmstracker.data.Rule
import com.example.banksmstracker.data.RuleType
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.database.RuleEntity
import com.example.banksmstracker.database.SenderAddressEntity
import com.example.banksmstracker.database.SenderEntity
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.util.Constants
import com.example.banksmstracker.util.SmsAddressMatcher
import com.example.banksmstracker.util.applyPlaceholderSpans
import com.example.banksmstracker.util.decodeNewlines
import com.example.banksmstracker.util.encodeNewlines
import com.example.banksmstracker.util.regexToTemplate
import com.example.banksmstracker.util.templateToRegex
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
    private lateinit var spinnerRuleType: Spinner

    // Preset buttons
    private lateinit var btnPresetAmount: Button
    private lateinit var btnPresetCurrency: Button
    private lateinit var btnPresetCard: Button
    private lateinit var btnPresetMerchant: Button
    private lateinit var btnPresetDate: Button
    private lateinit var btnPresetTime: Button
    private lateinit var btnPresetBalance: Button

    // Clear buttons
    private lateinit var btnClearSampleSms: Button
    private lateinit var btnClearRegexPattern: Button

    // Scroll container
    private lateinit var scrollView: ScrollView

    private var senders: List<Sender> = emptyList()
    private var smsMessages: List<SmsMessage> = emptyList()
    private var selectedSenderForFilter: Sender? = null
    private var selectedRuleType: RuleType = RuleType.PAYMENT
    private var lastSelectedSmsAddress: String? = null
    private var pendingAutoSelectAddress: String? = null
    private var pendingRestoreSenderName: String? = null

    data class SmsMessage(val address: String, val body: String, val date: Long = 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_regex_builder)

        initViews()
        setupListeners()

        // Handle incoming sample SMS from ApplyRulesActivity
        intent.getStringExtra(ApplyRulesActivity.EXTRA_SAMPLE_SMS)?.let { sampleSms ->
            etSampleSms.setText(sampleSms)
        }

        // Store sender address for auto-selection after senders load
        pendingAutoSelectAddress = intent.getStringExtra(ApplyRulesActivity.EXTRA_SENDER_ADDRESS)

        loadSenders()
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
        spinnerRuleType = findViewById(R.id.spinnerRuleType)

        // Initialize preset buttons
        btnPresetAmount = findViewById(R.id.btnPresetAmount)
        btnPresetCurrency = findViewById(R.id.btnPresetCurrency)
        btnPresetCard = findViewById(R.id.btnPresetCard)
        btnPresetMerchant = findViewById(R.id.btnPresetMerchant)
        btnPresetDate = findViewById(R.id.btnPresetDate)
        btnPresetTime = findViewById(R.id.btnPresetTime)
        btnPresetBalance = findViewById(R.id.btnPresetBalance)

        // Initialize clear buttons
        btnClearSampleSms = findViewById(R.id.btnClearSampleSms)
        btnClearRegexPattern = findViewById(R.id.btnClearRegexPattern)

        // Initialize scroll container for auto-scroll on focus
        scrollView = findViewById(R.id.scrollViewRegexBuilder)

        // Setup rule type spinner
        val ruleTypes = listOf(
            getString(R.string.rule_type_payment),
            getString(R.string.rule_type_ignore_short),
            getString(R.string.rule_type_income)
        )
        val ruleTypeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ruleTypes)
        ruleTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRuleType.adapter = ruleTypeAdapter
        spinnerRuleType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedRuleType = when (position) {
                    0 -> RuleType.PAYMENT
                    1 -> RuleType.IGNORE
                    2 -> RuleType.INCOME
                    else -> RuleType.PAYMENT
                }
                // Refresh existing patterns when rule type changes
                refreshExistingPatterns()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
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

        // Clear buttons
        btnClearSampleSms.setOnClickListener { etSampleSms.setText("") }
        btnClearRegexPattern.setOnClickListener { etRegexPattern.setText("") }

        // Auto-scroll focused fields to top
        etSampleSms.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) scrollView.post { scrollView.smoothScrollTo(0, v.top) }
        }
        etRegexPattern.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) scrollView.post { scrollView.smoothScrollTo(0, v.top) }
        }

        // Render placeholder chips as colored spans
        etRegexPattern.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                s?.let { applyPlaceholderSpans(it, this@RegexBuilderActivity) }
            }
        })

        // Setup preset button listeners
        setupPresetListeners()
    }

    private fun setupPresetListeners() {
        btnPresetAmount.setOnClickListener { insertPresetAtCursor("⟨amount⟩") }
        btnPresetCurrency.setOnClickListener { insertPresetAtCursor("⟨currency⟩") }
        btnPresetCard.setOnClickListener { insertPresetAtCursor("⟨card⟩") }
        btnPresetMerchant.setOnClickListener { insertPresetAtCursor("⟨merchant⟩") }
        btnPresetDate.setOnClickListener { insertPresetAtCursor("⟨date⟩") }
        btnPresetTime.setOnClickListener { insertPresetAtCursor("⟨time⟩") }
        btnPresetBalance.setOnClickListener { insertPresetAtCursor("⟨balance⟩") }
    }

    private fun insertPresetAtCursor(preset: String) {
        val start = etRegexPattern.selectionStart.coerceAtLeast(0)
        val end = etRegexPattern.selectionEnd.coerceAtLeast(0)
        val editable = etRegexPattern.text
        editable.replace(start.coerceAtMost(end), start.coerceAtLeast(end), preset)

        // Move cursor to end of inserted text
        etRegexPattern.setSelection(start + preset.length)
        etRegexPattern.requestFocus()
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
                selectedSenderForFilter!!.addresses.toSet()
            } else {
                // Load all SMS, not just from configured senders
                null
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
            lastSelectedSmsAddress = selectedSms.address
            dialog.dismiss()

            // Check if the sender is registered
            checkAndOfferSenderRegistration(selectedSms.address)
        }
        recyclerView.adapter = adapter

        dialog.show()
    }

    private fun checkAndOfferSenderRegistration(smsAddress: String) {
        // Check if any sender has this address
        val isRegistered = senders.any { sender ->
            sender.addresses.any { SmsAddressMatcher.matches(smsAddress, it) }
        }

        if (!isRegistered) {
            showUnregisteredSenderDialog(smsAddress)
        }
    }

    private fun showUnregisteredSenderDialog(smsAddress: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.unregistered_sender_title)
            .setMessage(getString(R.string.unregistered_sender_message, smsAddress))
            .setPositiveButton(R.string.register_sender) { _, _ ->
                registerNewSender(smsAddress)
            }
            .setNegativeButton(R.string.skip, null)
            .show()
    }

    private fun registerNewSender(address: String) {
        // Create a simple sender with the address as the name
        val senderName = address.replace(Regex("[^a-zA-Z0-9]"), " ").trim()
            .ifEmpty { "Sender $address" }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    val db = BankSmsDatabase.getInstance(this@RegexBuilderActivity)

                    // Insert sender
                    val senderEntity = SenderEntity(name = senderName, enabled = true)
                    val senderId = db.configDao().insertSender(senderEntity)

                    // Insert address
                    val addressEntity = SenderAddressEntity(
                        senderId = senderId,
                        address = address
                    )
                    db.configDao().insertAddress(addressEntity)
                }

                // Reload senders
                loadSenders()

                Toast.makeText(
                    this@RegexBuilderActivity,
                    getString(R.string.rule_saved, senderName),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@RegexBuilderActivity,
                    getString(R.string.error_generic),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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
            } else {
                ""
            }
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

                // Filter by configured addresses using case-insensitive substring matching
                if (filterAddresses != null && !SmsAddressMatcher.matchesAny(address, filterAddresses)) {
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
                spinnerSenders.isEnabled = true
                btnSaveRegex.isEnabled = true
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
                        refreshExistingPatterns()
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        setupExistingPatternsSpinner(emptyList())
                    }
                }

                setupExistingPatternsSpinner(emptyList())

                // Restore sender selection after a save (feature 4.1: sender must not be erased).
                val restoreName = pendingRestoreSenderName
                if (restoreName != null) {
                    pendingRestoreSenderName = null
                    val index = senders.indexOfFirst { it.name == restoreName }
                    if (index >= 0) spinnerSenders.setSelection(index + 1)
                }

                // Auto-select sender if requested from ApplyRulesActivity
                val autoAddress = pendingAutoSelectAddress
                if (autoAddress != null) {
                    pendingAutoSelectAddress = null
                    val index = senders.indexOfFirst { sender ->
                        sender.addresses.any { it.equals(autoAddress, ignoreCase = true) }
                    }
                    if (index >= 0) {
                        spinnerSenders.setSelection(index + 1)
                    } else {
                        AlertDialog.Builder(this@RegexBuilderActivity)
                            .setTitle(R.string.unregistered_sender_title)
                            .setMessage(getString(R.string.unregistered_sender_message, autoAddress))
                            .setPositiveButton(R.string.register_sender) { _, _ ->
                                val intent = android.content.Intent(
                                    this@RegexBuilderActivity,
                                    SendersActivity::class.java
                                )
                                startActivity(intent)
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    }
                }
            }
        }
    }

    private fun refreshExistingPatterns() {
        val position = spinnerSenders.selectedItemPosition
        if (position > 0 && position <= senders.size) {
            val selectedSender = senders[position - 1]
            val rulesOfType = selectedSender.rules.filter { it.ruleType == selectedRuleType }
            setupExistingPatternsSpinner(rulesOfType)
        } else {
            setupExistingPatternsSpinner(emptyList())
        }
    }

    private fun setupExistingPatternsSpinner(rules: List<Rule>) {
        val patternOptions = mutableListOf(getString(R.string.new_pattern_hint))
        patternOptions.addAll(rules.map { truncatePattern(it.pattern) })

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
                    etRegexPattern.setText(decodeNewlines(regexToTemplate(decodePattern(selectedRule.pattern))))
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun truncatePattern(pattern: String): String = if (pattern.length > 40) {
        pattern.take(37) + "..."
    } else {
        pattern
    }

    private fun encodePattern(raw: String): String = raw.replace(" ", "\\s")

    private fun decodePattern(stored: String): String = stored.replace("\\s", " ")

    private fun saveRegexToSender() {
        val regexPattern = encodePattern(
            templateToRegex(encodeNewlines(etRegexPattern.text.toString().trim()).replace("\r", ""))
        )

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

        // Check if this pattern already exists for this sender and rule type
        val existingPatterns = sender.rules
            .filter { it.ruleType == selectedRuleType }
            .map { it.pattern.trim() }
            .toSet()
        if (regexPattern in existingPatterns) {
            Toast.makeText(
                this,
                getString(R.string.regex_save_failed, "Pattern already exists"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        showSaveConfirmation(sender, regexPattern)
    }

    private fun showSaveConfirmation(sender: Sender, regexPattern: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.save_regex_confirm_title)
            .setMessage(getString(R.string.save_regex_confirm_message, sender.name, regexPattern))
            .setPositiveButton(R.string.confirm) { _, _ ->
                performSaveRule(sender, regexPattern)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performSaveRule(sender: Sender, regexPattern: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val senderId = sender.id ?: return@launch
                withContext(Dispatchers.IO) {
                    val db = BankSmsDatabase.getInstance(this@RegexBuilderActivity)
                    db.ruleDao().insertRule(
                        RuleEntity(
                            senderId = senderId,
                            pattern = regexPattern,
                            ruleType = selectedRuleType.value
                        )
                    )
                }

                Toast.makeText(
                    this@RegexBuilderActivity,
                    getString(R.string.rule_saved, sender.name),
                    Toast.LENGTH_SHORT
                ).show()

                // Refresh senders list while preserving the current sender selection.
                pendingRestoreSenderName = sender.name
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

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { view ->
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun testRegex() {
        hideKeyboard()
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

                val namedGroups = listOf(
                    Constants.RegexGroups.AMOUNT,
                    Constants.RegexGroups.CURRENCY,
                    Constants.RegexGroups.CARD,
                    Constants.RegexGroups.MERCHANT,
                    Constants.RegexGroups.DATE,
                    Constants.RegexGroups.TIME,
                    Constants.RegexGroups.BALANCE
                )
                val capturedNamed = namedGroups.mapNotNull { name ->
                    namedGroup(match, name)?.let { name to it }
                }

                if (capturedNamed.isNotEmpty()) {
                    result.append("Captured groups:\n")
                    for ((name, value) in capturedNamed) {
                        result.append("  ($name):\n")
                        result.append("    \"$value\"\n")
                    }
                    result.append("\n")

                    result.append("Payment preview:\n")
                    result.append("-".repeat(40))
                    result.append("\n")
                    result.append(buildPaymentPreview(match))
                } else if (match.groupValues.size > 1) {
                    result.append("Captured groups (positional):\n")
                    for (i in 1 until match.groupValues.size) {
                        result.append("  Group $i:\n")
                        result.append("    \"${match.groupValues[i]}\"\n")
                    }
                    result.append("\n")
                    result.append("Tip: Use named groups like (?<amount>...) for payment preview.\n")
                } else {
                    result.append("No captured groups.\n")
                    result.append("Use named groups like (?<amount>...) to capture fields.\n")
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

    private fun namedGroup(match: MatchResult, name: String): String? = try {
        match.groups[name]?.value
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun buildPaymentPreview(match: MatchResult): String {
        val preview = StringBuilder()

        val amount = namedGroup(match, Constants.RegexGroups.AMOUNT)?.toDoubleOrNull()
        val currency = namedGroup(match, Constants.RegexGroups.CURRENCY) ?: ""
        val card = namedGroup(match, Constants.RegexGroups.CARD) ?: ""
        val merchant = namedGroup(match, Constants.RegexGroups.MERCHANT) ?: ""
        val dateStr = namedGroup(match, Constants.RegexGroups.DATE) ?: ""
        val timeStr = namedGroup(match, Constants.RegexGroups.TIME) ?: ""
        val balance = namedGroup(match, Constants.RegexGroups.BALANCE)?.toDoubleOrNull()

        val timestamp = when {
            dateStr.isNotBlank() && timeStr.isNotBlank() -> "$dateStr $timeStr"
            dateStr.isNotBlank() -> dateStr
            else -> ""
        }

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
