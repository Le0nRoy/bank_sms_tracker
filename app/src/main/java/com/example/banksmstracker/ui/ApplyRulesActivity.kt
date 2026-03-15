package com.example.banksmstracker.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.banksmstracker.R
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.processor.MessageIgnoredException
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.RoomPaymentRepository
import com.example.banksmstracker.util.Constants
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun parseTransactionTimestampMillis(timestamp: String): Long? =
    com.example.banksmstracker.ui.parseTransactionTimestamp(timestamp)

class ApplyRulesActivity : BaseActivity() {

    private lateinit var resultsContainer: LinearLayout
    private lateinit var btnStartDate: Button
    private lateinit var btnEndDate: Button
    private lateinit var btnClearDates: Button
    private lateinit var btnProcessSms: Button

    private var startDate: Long? = null
    private var endDate: Long? = null
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apply_rules)

        initViews()
        setupListeners()

        // Restore saved state if available
        if (savedInstanceState != null) {
            startDate = savedInstanceState.getLong(KEY_START_DATE, -1L).takeIf { it >= 0 }
            endDate = savedInstanceState.getLong(KEY_END_DATE, -1L).takeIf { it >= 0 }
            updateDateButtons()
        } else {
            setDefaultDateRange()
        }

        if (!checkSmsPermission()) {
            requestSmsPermission()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        startDate?.let { outState.putLong(KEY_START_DATE, it) }
        endDate?.let { outState.putLong(KEY_END_DATE, it) }
    }

    private fun updateDateButtons() {
        startDate?.let { btnStartDate.text = dateFormat.format(Date(it)) }
            ?: run { btnStartDate.text = getString(R.string.start_date) }
        endDate?.let { btnEndDate.text = dateFormat.format(Date(it)) }
            ?: run { btnEndDate.text = getString(R.string.end_date) }
    }

    private fun initViews() {
        resultsContainer = findViewById(R.id.resultsContainer)
        btnStartDate = findViewById(R.id.btnStartDate)
        btnEndDate = findViewById(R.id.btnEndDate)
        btnClearDates = findViewById(R.id.btnClearDates)
        btnProcessSms = findViewById(R.id.btnProcessSms)
    }

    private fun setupListeners() {
        btnStartDate.setOnClickListener { showDatePicker(isStartDate = true) }
        btnEndDate.setOnClickListener { showDatePicker(isStartDate = false) }
        btnClearDates.setOnClickListener {
            startDate = null
            endDate = null
            btnStartDate.text = getString(R.string.start_date)
            btnEndDate.text = getString(R.string.end_date)
        }
        btnProcessSms.setOnClickListener {
            if (checkSmsPermission()) {
                applyRules()
            } else {
                requestSmsPermission()
            }
        }
    }

    private fun setDefaultDateRange() {
        lifecycleScope.launch {
            ConfigRepository.load(application)
            val database = BankSmsDatabase.getInstance(this@ApplyRulesActivity)
            val paymentRepository = RoomPaymentRepository(database.paymentDao())

            val lastPaymentDate = withContext(Dispatchers.IO) {
                paymentRepository.getAllPayments()
                    .mapNotNull { parseTransactionTimestampMillis(it.timestamp) }
                    .maxOrNull()
            }

            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 23)
            today.set(Calendar.MINUTE, 59)
            today.set(Calendar.SECOND, 59)
            today.set(Calendar.MILLISECOND, 999)
            val todayEnd = today.timeInMillis

            if (lastPaymentDate != null) {
                // Start from day after last payment, but not later than today
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = lastPaymentDate
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)

                // Ensure start date is not later than today
                if (calendar.timeInMillis <= todayEnd) {
                    startDate = calendar.timeInMillis
                }
            } else {
                // No payments yet - default to start of current month
                val startOfMonth = Calendar.getInstance()
                startOfMonth.set(Calendar.DAY_OF_MONTH, 1)
                startOfMonth.set(Calendar.HOUR_OF_DAY, 0)
                startOfMonth.set(Calendar.MINUTE, 0)
                startOfMonth.set(Calendar.SECOND, 0)
                startOfMonth.set(Calendar.MILLISECOND, 0)
                startDate = startOfMonth.timeInMillis
            }

            // End date: minimum of current day and last day of current month
            val lastDayOfMonth = Calendar.getInstance()
            lastDayOfMonth.set(Calendar.DAY_OF_MONTH, lastDayOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH))
            lastDayOfMonth.set(Calendar.HOUR_OF_DAY, 23)
            lastDayOfMonth.set(Calendar.MINUTE, 59)
            lastDayOfMonth.set(Calendar.SECOND, 59)
            lastDayOfMonth.set(Calendar.MILLISECOND, 999)

            endDate = minOf(todayEnd, lastDayOfMonth.timeInMillis)
            updateDateButtons()
        }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        val existingDate = if (isStartDate) startDate else endDate
        if (existingDate != null) {
            calendar.timeInMillis = existingDate
        }

        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                    if (isStartDate) {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    } else {
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                }
                val timestamp = selectedCalendar.timeInMillis
                val dateText = dateFormat.format(Date(timestamp))

                if (isStartDate) {
                    startDate = timestamp
                    btnStartDate.text = dateText
                } else {
                    endDate = timestamp
                    btnEndDate.text = dateText
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Prevent selecting future dates
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
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
                applyRules()
            } else {
                resultsContainer.removeAllViews()
                addStatusText(getString(R.string.sms_permission_denied_apply))
            }
        }
    }

    private fun applyRules() {
        resultsContainer.removeAllViews()
        addStatusText(getString(R.string.loading))

        lifecycleScope.launch {
            try {
                ConfigRepository.load(application)
                val config = ConfigRepository.config
                val processor = ConfigRepository.getPaymentProcessor()

                val configuredSenders = config.senders.flatMap { it.addresses }.toSet()
                val smsMessages = getSmsMessages(configuredSenders)

                resultsContainer.removeAllViews()

                if (smsMessages.isEmpty()) {
                    addStatusText(getString(R.string.no_sms_in_range))
                    return@launch
                }

                var parsedCount = 0
                var failedCount = 0
                var ignoredCount = 0

                for ((sender, messages) in smsMessages) {
                    addSectionHeader("$sender (${messages.size} messages)")

                    for (smsWithDate in messages) {
                        try {
                            val payment = withContext(Dispatchers.IO) {
                                processor.processMessage(smsWithDate.body, sender, smsWithDate.date)
                            }
                            parsedCount++
                            addSuccessItem(payment)
                        } catch (e: MessageIgnoredException) {
                            ignoredCount++
                            addIgnoredItem(sender, smsWithDate.body, e.ruleName)
                        } catch (e: Exception) {
                            failedCount++
                            addErrorItem(sender, smsWithDate.body, e.message ?: getString(R.string.unknown_error))
                        }
                    }
                }

                // Add summary at the top
                val summaryView = LayoutInflater.from(this@ApplyRulesActivity)
                    .inflate(android.R.layout.simple_list_item_1, resultsContainer, false) as TextView
                summaryView.text = getString(R.string.summary_processed, parsedCount, failedCount, ignoredCount)
                summaryView.setTextColor(getColor(R.color.text_primary))
                resultsContainer.addView(summaryView, 0)
            } catch (e: Exception) {
                resultsContainer.removeAllViews()
                addStatusText(getString(R.string.error_with_message, e.message ?: ""))
                e.printStackTrace()
            }
        }
    }

    private fun addStatusText(text: String) {
        val textView = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
            setPadding(0, 16, 0, 16)
        }
        resultsContainer.addView(textView)
    }

    private fun addSectionHeader(text: String) {
        val headerView = TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
            setPadding(0, 24, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        resultsContainer.addView(headerView)
    }

    private fun addSuccessItem(payment: com.example.banksmstracker.data.Payment) {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_apply_rules_success, resultsContainer, false)

        view.findViewById<TextView>(R.id.tvMerchant).text = payment.merchant ?: getString(R.string.unknown)
        view.findViewById<TextView>(R.id.tvAmount).text = "-${"%.2f".format(payment.amount)} ${payment.currency}"
        view.findViewById<TextView>(R.id.tvDetails).text = buildString {
            append(getString(R.string.category_display, payment.categoryId ?: getString(R.string.uncategorized)))
            if (payment.timestamp.isNotBlank()) {
                append(" | ${payment.timestamp}")
            }
        }

        resultsContainer.addView(view)
    }

    private fun addIgnoredItem(sender: String, message: String, ruleName: String?) {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_apply_rules_error, resultsContainer, false)

        val titleView = view.findViewById<TextView>(R.id.tvErrorTitle)
        titleView.text = getString(R.string.message_ignored)
        titleView.setTextColor(0xFFFF8F00.toInt())

        view.findViewById<TextView>(R.id.tvErrorMessage).text = buildString {
            append(getString(R.string.from_sender, sender))
            if (!ruleName.isNullOrBlank()) {
                append("\n")
                append(ruleName)
            }
            append("\n\n")
            append(message)
        }

        view.findViewById<Button>(R.id.btnOpenRegexBuilder).setOnClickListener {
            openRegexBuilder(sender, message)
        }

        resultsContainer.addView(view)
    }

    private fun addErrorItem(sender: String, message: String, error: String) {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_apply_rules_error, resultsContainer, false)

        view.findViewById<TextView>(R.id.tvErrorTitle).text = getString(R.string.error_parsing)
        view.findViewById<TextView>(R.id.tvErrorMessage).text = buildString {
            append(getString(R.string.from_sender, sender))
            append("\n\n")
            append(message)
        }

        view.findViewById<Button>(R.id.btnOpenRegexBuilder).setOnClickListener {
            openRegexBuilder(sender, message)
        }

        resultsContainer.addView(view)
    }

    private fun openRegexBuilder(sender: String, message: String) {
        val intent = Intent(this, RegexBuilderActivity::class.java)
        intent.putExtra(EXTRA_SAMPLE_SMS, message)
        intent.putExtra(EXTRA_SENDER_ADDRESS, sender)
        startActivity(intent)
    }

    data class SmsWithDate(val address: String, val body: String, val date: Long)

    private fun getSmsMessages(configuredSenders: Set<String>): Map<String, List<SmsWithDate>> {
        val messages = mutableMapOf<String, MutableList<SmsWithDate>>()

        if (configuredSenders.isEmpty()) {
            return messages
        }

        val uri = Uri.parse("content://sms")

        // Use parameterized query to prevent SQL injection
        val placeholders = configuredSenders.joinToString(",") { "?" }
        val selectionArgs = configuredSenders.toTypedArray()

        // Build date filter
        val dateFilter = buildString {
            append("address IN ($placeholders)")
            if (startDate != null) {
                append(" AND date >= $startDate")
            }
            if (endDate != null) {
                append(" AND date <= $endDate")
            }
        }

        val cursor: Cursor? = contentResolver.query(
            uri,
            arrayOf("address", "body", "date"),
            dateFilter,
            selectionArgs,
            "date DESC"
        )

        cursor?.use {
            val addressColumn = it.getColumnIndex("address")
            val bodyColumn = it.getColumnIndex("body")
            val dateColumn = it.getColumnIndex("date")

            while (it.moveToNext()) {
                val address = it.getString(addressColumn)
                val body = it.getString(bodyColumn)
                val date = if (dateColumn >= 0) it.getLong(dateColumn) else System.currentTimeMillis()

                if (!address.isNullOrBlank() && !body.isNullOrBlank()) {
                    messages.getOrPut(address) { mutableListOf() }.add(SmsWithDate(address, body, date))
                }
            }
        }

        return messages
    }

    companion object {
        const val EXTRA_SAMPLE_SMS = "extra_sample_sms"
        const val EXTRA_SENDER_ADDRESS = "extra_sender_address"
        private const val KEY_START_DATE = "key_start_date"
        private const val KEY_END_DATE = "key_end_date"
    }
}
