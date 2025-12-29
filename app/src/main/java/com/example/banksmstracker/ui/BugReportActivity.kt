package com.example.banksmstracker.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.banksmstracker.BuildConfig
import com.example.banksmstracker.R
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.RoomPaymentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BugReportActivity : BaseActivity() {

    private lateinit var etBugDescription: EditText
    private lateinit var cbIncludeConfig: CheckBox
    private lateinit var cbIncludeDeviceInfo: CheckBox
    private lateinit var cbIncludePaymentStats: CheckBox
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
        report.append("\nBANK SMS TRACKER - BUG REPORT\n")
        report.append("=".repeat(50))
        report.append("\n\n")

        // User description
        val description = etBugDescription.text.toString().trim()
        report.append("USER DESCRIPTION:\n")
        report.append("-".repeat(30))
        report.append("\n")
        if (description.isNotEmpty()) {
            report.append(description)
        } else {
            report.append("(No description provided)")
        }
        report.append("\n\n")

        // Device info
        if (cbIncludeDeviceInfo.isChecked) {
            report.append("DEVICE INFORMATION:\n")
            report.append("-".repeat(30))
            report.append("\n")
            report.append("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
            report.append("Build Type: ${BuildConfig.BUILD_TYPE}\n")
            report.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            report.append("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            report.append("Product: ${Build.PRODUCT}\n")
            report.append("Hardware: ${Build.HARDWARE}\n")
            report.append("\n")
        }

        // Config summary
        if (cbIncludeConfig.isChecked) {
            report.append("CONFIGURATION SUMMARY:\n")
            report.append("-".repeat(30))
            report.append("\n")
            try {
                val senders = ConfigRepository.getSenders()
                val categories = ConfigRepository.getCategories()

                report.append("Senders: ${senders.size}\n")
                senders.forEach { sender ->
                    val status = if (sender.enabled) "enabled" else "disabled"
                    report.append("  - ${sender.name} ($status)\n")
                    report.append("    Addresses: ${sender.addresses.size}\n")
                    report.append("    Rules: ${sender.rules.size}\n")
                }

                report.append("\nCategories: ${categories.size}\n")
                categories.forEach { category ->
                    val status = if (category.enabled) "enabled" else "disabled"
                    report.append("  - ${category.name} ($status)\n")
                    report.append("    Merchants: ${category.merchants.size}\n")
                }
            } catch (e: Exception) {
                report.append("Error loading config: ${e.message}\n")
            }
            report.append("\n")
        }

        // Payment stats
        if (cbIncludePaymentStats.isChecked) {
            report.append("PAYMENT STATISTICS:\n")
            report.append("-".repeat(30))
            report.append("\n")
            try {
                val database = BankSmsDatabase.getInstance(this@BugReportActivity)
                val paymentRepository = RoomPaymentRepository(database.paymentDao())
                val payments = paymentRepository.getAllPayments()

                report.append("Total Payments: ${payments.size}\n")
                if (payments.isNotEmpty()) {
                    val categorized = payments.count { it.categoryId != null }
                    val uncategorized = payments.size - categorized
                    report.append("Categorized: $categorized\n")
                    report.append("Uncategorized: $uncategorized\n")

                    val currencies = payments.map { it.currency }.distinct()
                    report.append("Currencies: ${currencies.joinToString(", ")}\n")

                    val totalByCurrency = payments.groupBy { it.currency }
                        .mapValues { (_, list) -> list.sumOf { it.amount } }
                    report.append("Totals:\n")
                    totalByCurrency.forEach { (currency, total) ->
                        report.append("  ${"%.2f".format(total)} $currency\n")
                    }
                }
            } catch (e: Exception) {
                report.append("Error loading payments: ${e.message}\n")
            }
            report.append("\n")
        }

        report.append("=".repeat(50))
        report.append("\nEnd of Report\n")
        report.append("=".repeat(50))

        report.toString()
    }

    private fun sendReport() {
        if (currentReport.isEmpty()) {
            Toast.makeText(this, getString(R.string.generate_preview_first), Toast.LENGTH_SHORT).show()
            return
        }

        val subject = "Bug Report - Bank SMS Tracker v${BuildConfig.VERSION_NAME}"

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, currentReport)
        }

        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_report_title)))
    }
}
