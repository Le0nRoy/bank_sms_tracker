package com.example.banksmstracker.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.banksmstracker.R
import com.example.banksmstracker.data.MessageProcessResult
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

/** Types used to categorise each processed SMS result. */
private enum class ResultType { PAYMENT, INCOME, FAILED, IGNORED }

/** One processed SMS item stored for later filtering/rendering. */
private sealed class SmsResultItem {
    abstract val sender: String
    data class PaymentItem(
        override val sender: String,
        val payment: com.example.banksmstracker.data.Payment
    ) : SmsResultItem()
    data class IncomeItem(
        override val sender: String,
        val income: com.example.banksmstracker.data.Income
    ) : SmsResultItem()
    data class IgnoredItem(
        override val sender: String,
        val body: String,
        val ruleName: String?
    ) : SmsResultItem()
    data class FailedItem(
        override val sender: String,
        val body: String,
        val error: String
    ) : SmsResultItem()
}

class ApplyRulesActivity : BaseActivity() {

    private lateinit var resultsContainer: LinearLayout
    private lateinit var btnStartDate: Button
    private lateinit var btnEndDate: Button
    private lateinit var btnClearDates: Button
    private lateinit var btnProcessSms: Button
    private lateinit var filterScrollView: View
    private lateinit var btnFilterAll: Button
    private lateinit var btnFilterPayments: Button
    private lateinit var btnFilterIncomes: Button
    private lateinit var btnFilterFailed: Button
    private lateinit var btnFilterIgnored: Button

    private var startDate: Long? = null
    private var endDate: Long? = null
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    /** All processed items from the last run – retained for re-filtering. */
    private var processedItems: List<SmsResultItem> = emptyList()
    private var activeFilter: ResultType? = null  // null = show ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apply_rules)

        initViews()
        setupListeners()

        if (savedInstanceState != null) {
            startDate = savedInstanceState.getLong(KEY_START_DATE, -1L).takeIf { it >= 0 }
            endDate = savedInstanceState.getLong(KEY_END_DATE, -1L).takeIf { it >= 0 }
            updateDateButtons()
        } else {
            setDefaultDateRange()
        }

        if (!checkSmsPermission()) requestSmsPermission()
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
        filterScrollView = findViewById(R.id.filterScrollView)
        btnFilterAll = findViewById(R.id.btnFilterAll)
        btnFilterPayments = findViewById(R.id.btnFilterPayments)
        btnFilterIncomes = findViewById(R.id.btnFilterIncomes)
        btnFilterFailed = findViewById(R.id.btnFilterFailed)
        btnFilterIgnored = findViewById(R.id.btnFilterIgnored)
    }

    private fun setupListeners() {
        btnStartDate.setOnClickListener { showDatePicker(isStartDate = true) }
        btnEndDate.setOnClickListener { showDatePicker(isStartDate = false) }
        btnClearDates.setOnClickListener {
            startDate = null; endDate = null
            btnStartDate.text = getString(R.string.start_date)
            btnEndDate.text = getString(R.string.end_date)
        }
        btnProcessSms.setOnClickListener {
            if (checkSmsPermission()) applyRules() else requestSmsPermission()
        }

        // Filter buttons
        btnFilterAll.setOnClickListener { setFilter(null) }
        btnFilterPayments.setOnClickListener { setFilter(ResultType.PAYMENT) }
        btnFilterIncomes.setOnClickListener { setFilter(ResultType.INCOME) }
        btnFilterFailed.setOnClickListener { setFilter(ResultType.FAILED) }
        btnFilterIgnored.setOnClickListener { setFilter(ResultType.IGNORED) }
    }

    private fun setFilter(type: ResultType?) {
        activeFilter = type
        updateFilterButtonStates()
        renderResults()
    }

    private fun updateFilterButtonStates() {
        val active = activeFilter
        btnFilterAll.alpha = if (active == null) 1f else 0.5f
        btnFilterPayments.alpha = if (active == ResultType.PAYMENT) 1f else 0.5f
        btnFilterIncomes.alpha = if (active == ResultType.INCOME) 1f else 0.5f
        btnFilterFailed.alpha = if (active == ResultType.FAILED) 1f else 0.5f
        btnFilterIgnored.alpha = if (active == ResultType.IGNORED) 1f else 0.5f
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

            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
            }
            val todayEnd = today.timeInMillis

            if (lastPaymentDate != null) {
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = lastPaymentDate
                    add(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                if (calendar.timeInMillis <= todayEnd) startDate = calendar.timeInMillis
            } else {
                val startOfMonth = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                startDate = startOfMonth.timeInMillis
            }

            val lastDayOfMonth = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
            }
            endDate = minOf(todayEnd, lastDayOfMonth.timeInMillis)
            updateDateButtons()
        }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        val existing = if (isStartDate) startDate else endDate
        if (existing != null) calendar.timeInMillis = existing

        val dialog = DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance().apply {
                set(year, month, dayOfMonth)
                if (isStartDate) {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                } else {
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                }
            }
            val ts = cal.timeInMillis
            val dateText = dateFormat.format(Date(ts))
            if (isStartDate) { startDate = ts; btnStartDate.text = dateText }
            else { endDate = ts; btnEndDate.text = dateText }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun checkSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), Constants.RequestCodes.SMS_PERMISSION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.RequestCodes.SMS_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) applyRules()
            else {
                resultsContainer.removeAllViews()
                addStatusText(getString(R.string.sms_permission_denied_apply))
            }
        }
    }

    private fun applyRules() {
        resultsContainer.removeAllViews()
        filterScrollView.visibility = View.GONE
        processedItems = emptyList()
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

                val database = BankSmsDatabase.getInstance(this@ApplyRulesActivity)
                val paymentRepository = RoomPaymentRepository(database.paymentDao())
                val existingPayments = withContext(Dispatchers.IO) { paymentRepository.getAllPayments() }

                val items = mutableListOf<SmsResultItem>()

                for ((sender, messages) in smsMessages) {
                    for (smsWithDate in messages) {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                processor.processMessageFull(smsWithDate.body, sender, smsWithDate.date, existingPayments)
                            }
                            when (result) {
                                is MessageProcessResult.PaymentResult ->
                                    items += SmsResultItem.PaymentItem(sender, result.payment)
                                is MessageProcessResult.IncomeResult ->
                                    items += SmsResultItem.IncomeItem(sender, result.income)
                                is MessageProcessResult.Ignored ->
                                    items += SmsResultItem.IgnoredItem(sender, smsWithDate.body, result.ruleName)
                            }
                        } catch (e: MessageIgnoredException) {
                            items += SmsResultItem.IgnoredItem(sender, smsWithDate.body, e.ruleName)
                        } catch (e: Exception) {
                            items += SmsResultItem.FailedItem(sender, smsWithDate.body, e.message ?: getString(R.string.unknown_error))
                        }
                    }
                }

                processedItems = items

                // Show filter row
                filterScrollView.visibility = View.VISIBLE
                activeFilter = null
                updateFilterButtonStates()
                renderResults()

            } catch (e: Exception) {
                resultsContainer.removeAllViews()
                addStatusText(getString(R.string.error_with_message, e.message ?: ""))
                e.printStackTrace()
            }
        }
    }

    /** Re-renders [resultsContainer] with the current [activeFilter]. */
    private fun renderResults() {
        resultsContainer.removeAllViews()

        val visible = when (activeFilter) {
            null -> processedItems
            ResultType.PAYMENT -> processedItems.filterIsInstance<SmsResultItem.PaymentItem>()
            ResultType.INCOME -> processedItems.filterIsInstance<SmsResultItem.IncomeItem>()
            ResultType.FAILED -> processedItems.filterIsInstance<SmsResultItem.FailedItem>()
            ResultType.IGNORED -> processedItems.filterIsInstance<SmsResultItem.IgnoredItem>()
        }

        // Summary line
        val total = processedItems
        val summary = getString(
            R.string.summary_processed_full,
            total.filterIsInstance<SmsResultItem.PaymentItem>().size,
            total.filterIsInstance<SmsResultItem.IncomeItem>().size,
            total.filterIsInstance<SmsResultItem.FailedItem>().size,
            total.filterIsInstance<SmsResultItem.IgnoredItem>().size
        )
        addStatusText(summary)

        if (visible.isEmpty()) {
            addStatusText(getString(R.string.no_spending_data))
            return
        }

        // Group by sender for section headers (only in ALL view)
        if (activeFilter == null) {
            val bySender = visible.groupBy { it.sender }
            for ((sender, items) in bySender) {
                addSectionHeader("$sender (${items.size})")
                items.forEach { renderItem(it) }
            }
        } else {
            visible.forEach { renderItem(it) }
        }
    }

    private fun renderItem(item: SmsResultItem) {
        when (item) {
            is SmsResultItem.PaymentItem -> addSuccessItem(item.payment)
            is SmsResultItem.IncomeItem -> addIncomeItem(item.sender, item.income)
            is SmsResultItem.IgnoredItem -> addIgnoredItem(item.sender, item.body, item.ruleName)
            is SmsResultItem.FailedItem -> addErrorItem(item.sender, item.body, item.error)
        }
    }

    private fun addStatusText(text: String) {
        val textView = TextView(this).apply {
            this.text = text; textSize = 14f
            setTextColor(getColor(R.color.text_primary)); setPadding(0, 16, 0, 16)
        }
        resultsContainer.addView(textView)
    }

    private fun addSectionHeader(text: String) {
        val headerView = TextView(this).apply {
            this.text = text; textSize = 16f
            setTextColor(getColor(R.color.text_primary)); setPadding(0, 24, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        resultsContainer.addView(headerView)
    }

    private fun addSuccessItem(payment: com.example.banksmstracker.data.Payment) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_apply_rules_success, resultsContainer, false)
        view.findViewById<TextView>(R.id.tvMerchant).text = payment.merchant ?: getString(R.string.unknown)
        view.findViewById<TextView>(R.id.tvAmount).text = "-${"%.2f".format(payment.amount)} ${payment.currency}"
        view.findViewById<TextView>(R.id.tvDetails).text = buildString {
            append(getString(R.string.category_display, payment.categoryId ?: getString(R.string.uncategorized)))
            if (payment.timestamp.isNotBlank()) append(" | ${payment.timestamp}")
        }
        resultsContainer.addView(view)
    }

    private fun addIncomeItem(sender: String, income: com.example.banksmstracker.data.Income) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_apply_rules_success, resultsContainer, false)
        view.findViewById<TextView>(R.id.tvMerchant).text = income.source ?: getString(R.string.income_unknown_source)
        view.findViewById<TextView>(R.id.tvAmount).text = "+${"%.2f".format(income.amount)} ${income.currency}"
        view.findViewById<TextView>(R.id.tvAmount).setTextColor(getColor(R.color.amount_positive))
        view.findViewById<TextView>(R.id.tvDetails).text = buildString {
            append(getString(R.string.message_income))
            if (!income.timestamp.isNullOrBlank()) append(" | ${income.timestamp}")
        }
        resultsContainer.addView(view)
    }

    private fun addIgnoredItem(sender: String, message: String, ruleName: String?) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_apply_rules_error, resultsContainer, false)
        val titleView = view.findViewById<TextView>(R.id.tvErrorTitle)
        titleView.text = getString(R.string.message_ignored)
        titleView.setTextColor(0xFFFF8F00.toInt())
        view.findViewById<TextView>(R.id.tvErrorMessage).text = buildString {
            append(getString(R.string.from_sender, sender))
            if (!ruleName.isNullOrBlank()) { append("\n"); append(ruleName) }
            append("\n\n"); append(message)
        }
        view.findViewById<Button>(R.id.btnOpenRegexBuilder).setOnClickListener { openRegexBuilder(sender, message) }
        resultsContainer.addView(view)
    }

    private fun addErrorItem(sender: String, message: String, error: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_apply_rules_error, resultsContainer, false)
        view.findViewById<TextView>(R.id.tvErrorTitle).text = getString(R.string.error_parsing)
        view.findViewById<TextView>(R.id.tvErrorMessage).text = buildString {
            append(getString(R.string.from_sender, sender)); append("\n\n"); append(message)
        }
        view.findViewById<Button>(R.id.btnOpenRegexBuilder).setOnClickListener { openRegexBuilder(sender, message) }
        resultsContainer.addView(view)
    }

    private fun openRegexBuilder(sender: String, message: String) {
        startActivity(Intent(this, RegexBuilderActivity::class.java).apply {
            putExtra(EXTRA_SAMPLE_SMS, message); putExtra(EXTRA_SENDER_ADDRESS, sender)
        })
    }

    data class SmsWithDate(val address: String, val body: String, val date: Long)

    private fun getSmsMessages(configuredSenders: Set<String>): Map<String, List<SmsWithDate>> {
        val messages = mutableMapOf<String, MutableList<SmsWithDate>>()
        if (configuredSenders.isEmpty()) return messages

        val uri = Uri.parse("content://sms")
        val placeholders = configuredSenders.joinToString(",") { "?" }
        val selectionArgs = configuredSenders.toTypedArray()
        val allArgs = mutableListOf(*selectionArgs)
        val dateFilter = buildString {
            append("address IN ($placeholders)")
            if (startDate != null) { append(" AND date >= ?"); allArgs.add(startDate.toString()) }
            if (endDate != null) { append(" AND date <= ?"); allArgs.add(endDate.toString()) }
        }

        val cursor: Cursor? = contentResolver.query(
            uri, arrayOf("address", "body", "date"), dateFilter, allArgs.toTypedArray(), "date DESC LIMIT 5000"
        )
        cursor?.use {
            val addrCol = it.getColumnIndex("address")
            val bodyCol = it.getColumnIndex("body")
            val dateCol = it.getColumnIndex("date")
            while (it.moveToNext()) {
                val address = it.getString(addrCol)
                val body = it.getString(bodyCol)
                val date = if (dateCol >= 0) it.getLong(dateCol) else System.currentTimeMillis()
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
        private const val TAG = "ApplyRulesActivity"
    }
}
