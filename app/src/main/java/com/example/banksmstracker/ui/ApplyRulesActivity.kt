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
        setDefaultDateRange()

        if (checkSmsPermission()) {
            // Don't auto-process, wait for button click
        } else {
            requestSmsPermission()
        }
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
                    .mapNotNull { it.receivedAt }
                    .maxOrNull()
            }

            if (lastPaymentDate != null) {
                // Start from day after last payment
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = lastPaymentDate
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                startDate = calendar.timeInMillis
                btnStartDate.text = dateFormat.format(Date(startDate!!))
            }

            // End date is now
            endDate = System.currentTimeMillis()
            btnEndDate.text = dateFormat.format(Date(endDate!!))
        }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        val existingDate = if (isStartDate) startDate else endDate
        if (existingDate != null) {
            calendar.timeInMillis = existingDate
        }

        DatePickerDialog(
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
        ).show()
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
                addStatusText("SMS permission denied. Cannot apply rules.")
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
                    addStatusText("No SMS messages found from configured senders in selected date range.")
                    return@launch
                }

                var parsedCount = 0
                var failedCount = 0

                for ((sender, messages) in smsMessages) {
                    addSectionHeader("$sender (${messages.size} messages)")

                    for (message in messages) {
                        try {
                            val payment = withContext(Dispatchers.IO) {
                                processor.processMessage(message, sender)
                            }
                            parsedCount++
                            addSuccessItem(payment)
                        } catch (e: Exception) {
                            failedCount++
                            addErrorItem(sender, message, e.message ?: "Unknown error")
                        }
                    }
                }

                // Add summary at the top
                val summaryView = LayoutInflater.from(this@ApplyRulesActivity)
                    .inflate(android.R.layout.simple_list_item_1, resultsContainer, false) as TextView
                summaryView.text = "Summary: $parsedCount parsed, $failedCount failed"
                summaryView.setTextColor(getColor(R.color.text_primary))
                resultsContainer.addView(summaryView, 0)
            } catch (e: Exception) {
                resultsContainer.removeAllViews()
                addStatusText("Error: ${e.message}")
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

        view.findViewById<TextView>(R.id.tvMerchant).text = payment.merchant ?: "Unknown"
        view.findViewById<TextView>(R.id.tvAmount).text = "-${"%.2f".format(payment.amount)} ${payment.currency}"
        view.findViewById<TextView>(R.id.tvDetails).text = buildString {
            append("Category: ${payment.categoryId ?: "Uncategorized"}")
            if (!payment.timestamp.isNullOrBlank()) {
                append(" | ${payment.timestamp}")
            }
        }

        resultsContainer.addView(view)
    }

    private fun addErrorItem(sender: String, message: String, error: String) {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_apply_rules_error, resultsContainer, false)

        view.findViewById<TextView>(R.id.tvErrorTitle).text = getString(R.string.error_parsing)
        view.findViewById<TextView>(R.id.tvErrorMessage).text = buildString {
            append("From: $sender\n")
            append("Error: $error\n\n")
            append("Message:\n$message")
        }

        view.findViewById<Button>(R.id.btnOpenRegexBuilder).setOnClickListener {
            openRegexBuilder(message)
        }

        resultsContainer.addView(view)
    }

    private fun openRegexBuilder(message: String) {
        val intent = Intent(this, RegexBuilderActivity::class.java)
        intent.putExtra(EXTRA_SAMPLE_SMS, message)
        startActivity(intent)
    }

    data class SmsWithDate(val address: String, val body: String, val date: Long)

    private fun getSmsMessages(configuredSenders: Set<String>): Map<String, List<String>> {
        val messages = mutableMapOf<String, MutableList<String>>()

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

            while (it.moveToNext()) {
                val address = it.getString(addressColumn)
                val body = it.getString(bodyColumn)

                if (!address.isNullOrBlank() && !body.isNullOrBlank()) {
                    messages.getOrPut(address) { mutableListOf() }.add(body)
                }
            }
        }

        return messages
    }

    companion object {
        const val EXTRA_SAMPLE_SMS = "extra_sample_sms"
    }
}
