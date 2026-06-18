package com.example.banksmstracker.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.banksmstracker.R
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.util.Constants
import com.example.banksmstracker.util.SmsAddressMatcher
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SmsExportActivity : BaseActivity() {

    private lateinit var btnStartDate: Button
    private lateinit var btnEndDate: Button
    private lateinit var btnClearDates: Button
    private lateinit var spinnerSender: Spinner
    private lateinit var rgExportFormat: RadioGroup
    private lateinit var rbFormatJson: RadioButton
    private lateinit var rbFormatCsv: RadioButton
    private lateinit var tvMessageCount: TextView
    private lateinit var btnExport: Button
    private lateinit var progressBar: ProgressBar

    private var startDate: Long? = null
    private var endDate: Long? = null
    private var selectedSender: Sender? = null
    private var senders: List<Sender> = emptyList()
    private var smsMessages: List<SmsMessage> = emptyList()

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    data class SmsMessage(val address: String, val body: String, val date: Long, val type: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_export)

        initViews()
        setupListeners()
        loadSenders()
    }

    private fun initViews() {
        btnStartDate = findViewById(R.id.btnStartDate)
        btnEndDate = findViewById(R.id.btnEndDate)
        btnClearDates = findViewById(R.id.btnClearDates)
        spinnerSender = findViewById(R.id.spinnerSender)
        rgExportFormat = findViewById(R.id.rgExportFormat)
        rbFormatJson = findViewById(R.id.rbFormatJson)
        rbFormatCsv = findViewById(R.id.rbFormatCsv)
        tvMessageCount = findViewById(R.id.tvMessageCount)
        btnExport = findViewById(R.id.btnExport)
        progressBar = findViewById(R.id.progressBar)

        setDefaultDateRange()
    }

    private fun setDefaultDateRange() {
        val calendar = Calendar.getInstance()

        // Start: first day of current month
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        startDate = calendar.timeInMillis
        btnStartDate.text = dateFormat.format(Date(startDate!!))

        // End: today
        calendar.timeInMillis = System.currentTimeMillis()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        endDate = calendar.timeInMillis
        btnEndDate.text = dateFormat.format(Date(endDate!!))
    }

    private fun setupListeners() {
        btnStartDate.setOnClickListener { showDatePicker(isStartDate = true) }
        btnEndDate.setOnClickListener { showDatePicker(isStartDate = false) }

        btnClearDates.setOnClickListener {
            startDate = null
            endDate = null
            btnStartDate.text = getString(R.string.start_date)
            btnEndDate.text = getString(R.string.end_date)
            updateMessageCount()
        }

        btnExport.setOnClickListener {
            if (checkSmsPermission()) {
                exportSms()
            } else {
                requestSmsPermission()
            }
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
                updateMessageCount()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadSenders() {
        lifecycleScope.launch {
            ConfigRepository.load(application)
            senders = ConfigRepository.getSenders()

            val senderNames = mutableListOf(getString(R.string.all_senders))
            senderNames.addAll(senders.map { it.name })

            val adapter = ArrayAdapter(
                this@SmsExportActivity,
                android.R.layout.simple_spinner_item,
                senderNames
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerSender.adapter = adapter

            spinnerSender.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedSender = if (position == 0) null else senders.getOrNull(position - 1)
                    updateMessageCount()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    selectedSender = null
                }
            }

            updateMessageCount()
        }
    }

    private fun updateMessageCount() {
        if (!checkSmsPermission()) {
            tvMessageCount.text = getString(R.string.sms_permission_denied)
            return
        }

        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            smsMessages = withContext(Dispatchers.IO) {
                loadSmsMessages()
            }
            progressBar.visibility = View.GONE
            tvMessageCount.text = getString(R.string.messages_to_export, smsMessages.size)
        }
    }

    private fun loadSmsMessages(): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val uri = Uri.parse("content://sms")

        val configuredAddresses = if (selectedSender != null) {
            selectedSender!!.addresses.toSet()
        } else {
            senders.flatMap { it.addresses }.toSet()
        }

        if (configuredAddresses.isEmpty()) {
            return messages
        }

        // Build selection with only date filters (address filtering done in Kotlin for flexibility)
        val selectionArgs = mutableListOf<String>()
        val selection = buildString {
            var hasCondition = false
            startDate?.let {
                append("date >= ?")
                selectionArgs.add(it.toString())
                hasCondition = true
            }
            endDate?.let {
                if (hasCondition) append(" AND ")
                append("date <= ?")
                selectionArgs.add(it.toString())
            }
        }.ifEmpty { null }

        // Use "date DESC" without LIMIT — LIMIT in sortOrder is a SQLite extension not
        // guaranteed on all OEM content-provider implementations. The hard cap is enforced
        // below in Kotlin after cursor processing.
        val cursor: Cursor? = contentResolver.query(
            uri,
            arrayOf("address", "body", "date", "type"),
            selection,
            selectionArgs.takeIf { it.isNotEmpty() }?.toTypedArray(),
            "date DESC"
        )

        cursor?.use {
            val addressColumn = it.getColumnIndex("address")
            val bodyColumn = it.getColumnIndex("body")
            val dateColumn = it.getColumnIndex("date")
            val typeColumn = it.getColumnIndex("type")

            while (it.moveToNext()) {
                val address = it.getString(addressColumn) ?: continue
                val body = it.getString(bodyColumn) ?: continue
                val date = it.getLong(dateColumn)
                val type = it.getInt(typeColumn)

                // Filter using case-insensitive substring matching
                if (SmsAddressMatcher.matchesAny(address, configuredAddresses)) {
                    messages.add(SmsMessage(address, body, date, type))
                }
            }
        }

        // Safety cap: prevent processing an unbounded number of messages.
        val cap = 5000
        if (messages.size > cap) {
            android.util.Log.w("SmsExportActivity", "SMS result truncated from ${messages.size} to $cap messages")
            return messages.take(cap).toMutableList()
        }

        return messages
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
                updateMessageCount()
            } else {
                Toast.makeText(this, R.string.sms_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportSms() {
        if (smsMessages.isEmpty()) {
            Toast.makeText(this, R.string.no_messages_to_export, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            btnExport.isEnabled = false

            try {
                val file = withContext(Dispatchers.IO) {
                    if (rbFormatJson.isChecked) {
                        exportToJson()
                    } else {
                        exportToCsv()
                    }
                }

                val uri = FileProvider.getUriForFile(
                    this@SmsExportActivity,
                    "$packageName.fileprovider",
                    file
                )

                Toast.makeText(
                    this@SmsExportActivity,
                    getString(R.string.sms_export_success, file.name),
                    Toast.LENGTH_SHORT
                ).show()

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = if (rbFormatJson.isChecked) "application/json" else "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_sms_export)))
            } catch (e: Exception) {
                Toast.makeText(
                    this@SmsExportActivity,
                    getString(R.string.sms_export_failed, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressBar.visibility = View.GONE
                btnExport.isEnabled = true
            }
        }
    }

    private fun exportToJson(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "sms_export_$timestamp.json"
        val file = File(cacheDir, fileName)
        file.deleteOnExit()

        val jsonArray = JSONArray()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault())

        for (sms in smsMessages) {
            val jsonObject = JSONObject().apply {
                put("address", sms.address)
                put("body", sms.body)
                put("date", sms.date)
                put("date_formatted", isoFormat.format(Date(sms.date)))
                put("type", sms.type)
                put("type_name", getSmsTypeName(sms.type))
            }
            jsonArray.put(jsonObject)
        }

        val exportObject = JSONObject().apply {
            put("export_date", isoFormat.format(Date()))
            put("message_count", smsMessages.size)
            put("start_date", startDate?.let { isoFormat.format(Date(it)) })
            put("end_date", endDate?.let { isoFormat.format(Date(it)) })
            put("sender_filter", selectedSender?.name ?: "all")
            put("messages", jsonArray)
        }

        file.writeText(exportObject.toString(2))
        return file
    }

    private fun exportToCsv(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "sms_export_$timestamp.csv"
        val file = File(cacheDir, fileName)
        file.deleteOnExit()

        val sb = StringBuilder()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        // Header
        sb.appendLine(getString(R.string.csv_header_sms))

        // Data
        for (sms in smsMessages) {
            sb.appendLine(
                listOf(
                    escapeCsv(sms.address),
                    isoFormat.format(Date(sms.date)),
                    getSmsTypeName(sms.type),
                    escapeCsv(sms.body)
                ).joinToString(",")
            )
        }

        file.writeText(sb.toString())
        return file
    }

    private fun escapeCsv(value: String): String =
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private fun getSmsTypeName(type: Int): String = when (type) {
        1 -> getString(R.string.sms_type_inbox)
        2 -> getString(R.string.sms_type_sent)
        3 -> getString(R.string.sms_type_draft)
        4 -> getString(R.string.sms_type_outbox)
        5 -> getString(R.string.sms_type_failed)
        6 -> getString(R.string.sms_type_queued)
        else -> getString(R.string.unknown)
    }
}
