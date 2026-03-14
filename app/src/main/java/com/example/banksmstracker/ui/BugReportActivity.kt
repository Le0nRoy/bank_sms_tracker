package com.example.banksmstracker.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.banksmstracker.BuildConfig
import com.example.banksmstracker.R
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.RoomPaymentRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BugReportActivity : BaseActivity() {

    private lateinit var etBugDescription: EditText
    private lateinit var cbIncludeConfig: CheckBox
    private lateinit var cbIncludeDeviceInfo: CheckBox
    private lateinit var cbIncludePaymentStats: CheckBox
    private lateinit var cbIncludeFilterState: CheckBox
    private lateinit var cbAttachPaymentsData: CheckBox
    private lateinit var btnPreviewReport: Button
    private lateinit var tvReportPreview: TextView
    private lateinit var btnSendReport: Button

    private var currentReport: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bug_report)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        etBugDescription = findViewById(R.id.etBugDescription)
        cbIncludeConfig = findViewById(R.id.cbIncludeConfig)
        cbIncludeDeviceInfo = findViewById(R.id.cbIncludeDeviceInfo)
        cbIncludePaymentStats = findViewById(R.id.cbIncludePaymentStats)
        cbIncludeFilterState = findViewById(R.id.cbIncludeFilterState)
        cbAttachPaymentsData = findViewById(R.id.cbAttachPaymentsData)
        if (!BuildConfig.DEBUG) {
            cbAttachPaymentsData.visibility = View.GONE
        }
        btnPreviewReport = findViewById(R.id.btnPreviewReport)
        tvReportPreview = findViewById(R.id.tvReportPreview)
        btnSendReport = findViewById(R.id.btnSendReport)
    }

    private fun setupListeners() {
        btnPreviewReport.setOnClickListener {
            generateReport()
        }

        btnSendReport.setOnClickListener {
            sendReport()
        }
    }

    private fun generateReport() {
        lifecycleScope.launch {
            val report = buildReport()
            currentReport = report
            tvReportPreview.text = report
        }
    }

    private suspend fun buildReport(): String = withContext(Dispatchers.IO) {
        val report = StringBuilder()

        report.append("=".repeat(50))
        report.append("\n")
        report.append(getString(R.string.report_header))
        report.append("\n")
        report.append("=".repeat(50))
        report.append("\n\n")

        // User description
        val description = etBugDescription.text.toString().trim()
        report.append(getString(R.string.user_description_header).trimStart())
        report.append("\n")
        report.append("-".repeat(30))
        report.append("\n")
        if (description.isNotEmpty()) {
            report.append(description)
        } else {
            report.append(getString(R.string.no_description_provided))
        }
        report.append("\n\n")

        // Device info
        if (cbIncludeDeviceInfo.isChecked) {
            report.append(getString(R.string.device_info_header).trimStart())
            report.append("\n")
            report.append("-".repeat(30))
            report.append("\n")
            report.append(getString(R.string.app_version_label, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
            report.append("\n")
            report.append(getString(R.string.build_type_label, BuildConfig.BUILD_TYPE))
            report.append("\n")
            report.append(getString(R.string.device_model_label, Build.MANUFACTURER, Build.MODEL))
            report.append("\n")
            report.append(getString(R.string.android_version_label, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
            report.append("\n")
            report.append(getString(R.string.product_label, Build.PRODUCT))
            report.append("\n")
            report.append(getString(R.string.hardware_label, Build.HARDWARE))
            report.append("\n\n")
        }

        // Config summary
        if (cbIncludeConfig.isChecked) {
            report.append(getString(R.string.config_summary_header).trimStart())
            report.append("\n")
            report.append("-".repeat(30))
            report.append("\n")
            try {
                val senders = ConfigRepository.getSenders()
                val categories = ConfigRepository.getCategories()

                report.append(getString(R.string.senders_count_label, senders.size))
                report.append("\n")
                senders.forEach { sender ->
                    val status = if (sender.enabled) {
                        getString(
                            R.string.enabled_status
                        )
                    } else {
                        getString(R.string.disabled_status)
                    }
                    report.append("  - ${sender.name} ($status)\n")
                    report.append("    ${getString(R.string.addresses_count, sender.addresses.size)}\n")
                    report.append("    ${getString(R.string.rules_count, sender.rules.size)}\n")
                }

                report.append("\n")
                report.append(getString(R.string.categories_count_label, categories.size))
                report.append("\n")
                categories.forEach { category ->
                    val status = if (category.enabled) {
                        getString(
                            R.string.enabled_status
                        )
                    } else {
                        getString(R.string.disabled_status)
                    }
                    report.append("  - ${category.name} ($status)\n")
                    report.append("    ${getString(R.string.merchants_count, category.merchants.size)}\n")
                }
            } catch (e: Exception) {
                report.append(getString(R.string.error_loading_config, e.message ?: ""))
                report.append("\n")
            }
            report.append("\n")
        }

        // Payment stats
        if (cbIncludePaymentStats.isChecked) {
            report.append(getString(R.string.payment_stats_header).trimStart())
            report.append("\n")
            report.append("-".repeat(30))
            report.append("\n")
            try {
                val database = BankSmsDatabase.getInstance(this@BugReportActivity)
                val paymentRepository = RoomPaymentRepository(database.paymentDao())
                val payments = paymentRepository.getAllPayments()

                report.append(getString(R.string.total_payments_count, payments.size))
                report.append("\n")
                if (payments.isNotEmpty()) {
                    val categorized = payments.count { it.categoryId != null }
                    val uncategorized = payments.size - categorized
                    report.append(getString(R.string.categorized_count, categorized))
                    report.append("\n")
                    report.append(getString(R.string.uncategorized_count, uncategorized))
                    report.append("\n")

                    val currencies = payments.map { it.currency }.distinct()
                    report.append(getString(R.string.currencies_label, currencies.joinToString(", ")))
                    report.append("\n")

                    val totalByCurrency = payments.groupBy { it.currency }
                        .mapValues { (_, list) -> list.sumOf { it.amount } }
                    report.append(getString(R.string.totals_label))
                    report.append("\n")
                    totalByCurrency.forEach { (currency, total) ->
                        report.append("  ${getString(R.string.currency_total, "%.2f".format(total), currency)}\n")
                    }
                }
            } catch (e: Exception) {
                report.append(getString(R.string.error_with_message, e.message ?: ""))
                report.append("\n")
            }
            report.append("\n")
        }

        // Filter state
        if (cbIncludeFilterState.isChecked) {
            report.append(getString(R.string.filter_state_header).trimStart())
            report.append("\n")
            report.append("-".repeat(30))
            report.append("\n")
            try {
                val prefs = getSharedPreferences(
                    PaymentsActivity.PREFS_FILTER_STATE,
                    android.content.Context.MODE_PRIVATE
                )
                val category = prefs.getString(PaymentsActivity.KEY_FILTER_CATEGORY, null)
                val sender = prefs.getString(PaymentsActivity.KEY_FILTER_SENDER, null)
                val startDate = prefs.getLong(PaymentsActivity.KEY_FILTER_START_DATE, -1L).takeIf { it >= 0 }
                val endDate = prefs.getLong(PaymentsActivity.KEY_FILTER_END_DATE, -1L).takeIf { it >= 0 }
                val merchant = prefs.getString(PaymentsActivity.KEY_FILTER_MERCHANT, null)
                val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                report.append("  Category: ${category ?: "(all)"}\n")
                report.append("  Sender: ${sender ?: "(all)"}\n")
                report.append("  Start: ${startDate?.let { dateFmt.format(Date(it)) } ?: "(none)"}\n")
                report.append("  End: ${endDate?.let { dateFmt.format(Date(it)) } ?: "(none)"}\n")
                report.append("  Merchant: ${merchant ?: "(all)"}\n")
            } catch (e: Exception) {
                report.append(getString(R.string.error_with_message, e.message ?: ""))
                report.append("\n")
            }
            report.append("\n")
        }

        report.append("=".repeat(50))
        report.append(getString(R.string.end_of_report))
        report.append("\n")
        report.append("=".repeat(50))

        report.toString()
    }

    private fun sendReport() {
        if (currentReport.isEmpty()) {
            Toast.makeText(this, getString(R.string.generate_preview_first), Toast.LENGTH_SHORT).show()
            return
        }

        val subject = "Bug Report - Bank SMS Tracker v${BuildConfig.VERSION_NAME}"

        if (cbAttachPaymentsData.isChecked) {
            lifecycleScope.launch {
                val paymentsUri = generatePaymentsFile()
                val shareIntent = if (paymentsUri != null) {
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, currentReport)
                        putParcelableArrayListExtra(
                            Intent.EXTRA_STREAM,
                            arrayListOf(paymentsUri)
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, currentReport)
                    }
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_report_title)))
            }
        } else {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, currentReport)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_report_title)))
        }
    }

    private suspend fun generatePaymentsFile(): android.net.Uri? = withContext(Dispatchers.IO) {
        try {
            val db = BankSmsDatabase.getInstance(this@BugReportActivity)
            val payments = RoomPaymentRepository(db.paymentDao()).getAllPayments()
            val json = Json.encodeToString(payments)
            val file = File(cacheDir, "payments_export.json")
            file.writeText(json)
            FileProvider.getUriForFile(this@BugReportActivity, "$packageName.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }
}
