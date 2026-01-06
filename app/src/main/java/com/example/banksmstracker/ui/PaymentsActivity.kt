package com.example.banksmstracker.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
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
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.banksmstracker.R
import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.RoomPaymentRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PaymentsActivity : BaseActivity() {

    private lateinit var recyclerPayments: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var tvPaymentCount: TextView
    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerSender: Spinner
    private lateinit var btnExportCsv: Button
    private lateinit var btnStartDate: Button
    private lateinit var btnEndDate: Button
    private lateinit var btnClearDates: Button
    private lateinit var btnSpendingReport: Button

    private lateinit var paymentRepository: RoomPaymentRepository
    private var allPayments: List<Payment> = emptyList()
    private var filteredPayments: List<Payment> = emptyList()
    private var categories: List<String> = emptyList()
    private var senderAddresses: List<String> = emptyList()
    private var selectedCategory: String? = null
    private var selectedSender: String? = null
    private var startDate: Long? = null
    private var endDate: Long? = null

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val adapter = PaymentAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payments)

        initViews()
        setupRecyclerView()
        loadData()
    }

    private fun initViews() {
        recyclerPayments = findViewById(R.id.recyclerPayments)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        tvPaymentCount = findViewById(R.id.tvPaymentCount)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        spinnerSender = findViewById(R.id.spinnerSender)
        btnExportCsv = findViewById(R.id.btnExportCsv)
        btnStartDate = findViewById(R.id.btnStartDate)
        btnEndDate = findViewById(R.id.btnEndDate)
        btnClearDates = findViewById(R.id.btnClearDates)

        btnExportCsv.setOnClickListener {
            exportToCsv()
        }

        btnSpendingReport = findViewById(R.id.btnSpendingReport)
        btnSpendingReport.setOnClickListener {
            showSpendingReport()
        }

        btnStartDate.setOnClickListener {
            showDatePicker(isStartDate = true)
        }

        btnEndDate.setOnClickListener {
            showDatePicker(isStartDate = false)
        }

        btnClearDates.setOnClickListener {
            startDate = null
            endDate = null
            btnStartDate.text = getString(R.string.start_date)
            btnEndDate.text = getString(R.string.end_date)
            applyFilter()
        }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()

        // Use current date or the existing selection
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
                applyFilter()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun setupRecyclerView() {
        recyclerPayments.layoutManager = LinearLayoutManager(this)
        recyclerPayments.adapter = adapter
    }

    private fun loadData() {
        lifecycleScope.launch {
            // Initialize repository from ConfigRepository's database
            ConfigRepository.load(application)
            val database = com.example.banksmstracker.database.BankSmsDatabase.getInstance(this@PaymentsActivity)
            paymentRepository = RoomPaymentRepository(database.paymentDao())

            // Load payments
            allPayments = withContext(Dispatchers.IO) {
                paymentRepository.getAllPayments()
            }

            // Load categories for filter
            val configCategories = ConfigRepository.getCategories()
            categories = listOf(getString(R.string.all_categories)) +
                configCategories.map { it.name }

            // Load sender addresses for filter
            senderAddresses = withContext(Dispatchers.IO) {
                listOf(getString(R.string.all_senders)) +
                    paymentRepository.getDistinctSenderAddresses()
            }

            // Set default date range to current month
            setDefaultDateRange()

            setupCategorySpinner()
            setupSenderSpinner()
            applyFilter()
        }
    }

    private fun setDefaultDateRange() {
        val calendar = Calendar.getInstance()

        // First day of current month
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        startDate = calendar.timeInMillis
        btnStartDate.text = dateFormat.format(Date(startDate!!))

        // Last day of current month
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        endDate = calendar.timeInMillis
        btnEndDate.text = dateFormat.format(Date(endDate!!))
    }

    private fun setupCategorySpinner() {
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            categories
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = spinnerAdapter

        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCategory = if (position == 0) null else categories[position]
                applyFilter()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedCategory = null
                applyFilter()
            }
        }
    }

    private fun setupSenderSpinner() {
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            senderAddresses
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSender.adapter = spinnerAdapter

        spinnerSender.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSender = if (position == 0) null else senderAddresses[position]
                applyFilter()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedSender = null
                applyFilter()
            }
        }
    }

    private fun applyFilter() {
        filteredPayments = allPayments.filter { payment ->
            val matchesCategory = selectedCategory == null || payment.categoryId == selectedCategory
            val matchesSender = selectedSender == null || payment.senderAddress == selectedSender
            val matchesDateRange = when {
                startDate != null && endDate != null -> {
                    val receivedAt = payment.receivedAt ?: return@filter false
                    receivedAt in startDate!!..endDate!!
                }
                startDate != null -> {
                    val receivedAt = payment.receivedAt ?: return@filter false
                    receivedAt >= startDate!!
                }
                endDate != null -> {
                    val receivedAt = payment.receivedAt ?: return@filter false
                    receivedAt <= endDate!!
                }
                else -> true
            }
            matchesCategory && matchesSender && matchesDateRange
        }

        adapter.submitList(filteredPayments)
        updateUI()
    }

    private fun updateUI() {
        if (filteredPayments.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
            recyclerPayments.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            recyclerPayments.visibility = View.VISIBLE
        }

        val total = filteredPayments.sumOf { it.amount }
        val currency = filteredPayments.firstOrNull()?.currency ?: ""
        tvPaymentCount.text = "${filteredPayments.size} payments - Total: ${"%.2f".format(total)} $currency"
    }

    private fun exportToCsv() {
        lifecycleScope.launch {
            try {
                val csvContent = withContext(Dispatchers.IO) {
                    buildCsvContent(filteredPayments)
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "payments_$timestamp.csv"
                val file = File(cacheDir, fileName)
                file.writeText(csvContent)

                val uri = FileProvider.getUriForFile(
                    this@PaymentsActivity,
                    "$packageName.fileprovider",
                    file
                )

                Toast.makeText(
                    this@PaymentsActivity,
                    getString(R.string.csv_export_success, file.absolutePath),
                    Toast.LENGTH_SHORT
                ).show()

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share CSV"))
            } catch (e: Exception) {
                Toast.makeText(
                    this@PaymentsActivity,
                    getString(R.string.csv_export_failed, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun buildCsvContent(payments: List<Payment>): String {
        val sb = StringBuilder()
        // Header
        sb.appendLine("Amount,Currency,Card,Merchant,Timestamp,Balance,Category,Sender,ReceivedAt")
        // Data
        for (payment in payments) {
            val receivedAtStr = payment.receivedAt?.let {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
            } ?: ""
            sb.appendLine(
                listOf(
                    payment.amount.toString(),
                    escapeCsv(payment.currency),
                    escapeCsv(payment.card ?: ""),
                    escapeCsv(payment.merchant ?: ""),
                    escapeCsv(payment.timestamp ?: ""),
                    payment.balance?.toString() ?: "",
                    escapeCsv(payment.categoryId ?: ""),
                    escapeCsv(payment.senderAddress ?: ""),
                    escapeCsv(receivedAtStr)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String =
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private fun showSpendingReport() {
        val reportBuilder = StringBuilder()

        // Determine date range to show
        val actualStartDate = startDate ?: allPayments.mapNotNull { it.receivedAt }.minOrNull()
        val actualEndDate = endDate ?: allPayments.mapNotNull { it.receivedAt }.maxOrNull()

        // Date range info
        val dateRangeText = when {
            actualStartDate != null && actualEndDate != null ->
                "${dateFormat.format(Date(actualStartDate))} - ${dateFormat.format(Date(actualEndDate))}"
            actualStartDate != null -> "From ${dateFormat.format(Date(actualStartDate))}"
            actualEndDate != null -> "Until ${dateFormat.format(Date(actualEndDate))}"
            else -> getString(R.string.all_time)
        }
        reportBuilder.appendLine("Period: $dateRangeText")
        reportBuilder.appendLine()

        if (filteredPayments.isEmpty()) {
            reportBuilder.appendLine(getString(R.string.no_spending_data))
            AlertDialog.Builder(this)
                .setTitle(R.string.spending_report)
                .setMessage(reportBuilder.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        // Group payments by category and calculate totals
        val categoryTotals = filteredPayments
            .groupBy { it.categoryId ?: "Uncategorized" }
            .mapValues { (_, payments) ->
                payments.sumOf { it.amount }
            }
            .toList()
            .sortedByDescending { it.second }

        val totalAmount = filteredPayments.sumOf { it.amount }
        val currency = filteredPayments.firstOrNull()?.currency ?: ""

        // Total
        reportBuilder.appendLine(getString(R.string.total_spending, "%.2f".format(totalAmount), currency))
        reportBuilder.appendLine()

        // Category breakdown
        reportBuilder.appendLine("By Category:")
        categoryTotals.forEach { (category, amount) ->
            val percentage = if (totalAmount > 0) (amount / totalAmount * 100).toInt() else 0
            reportBuilder.appendLine("  $category: ${"%.2f".format(amount)} $currency ($percentage%)")
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.spending_report)
            .setMessage(reportBuilder.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showPaymentDetail(payment: Payment) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment_detail, null)

        dialogView.findViewById<TextView>(R.id.tvMerchant).text = payment.merchant ?: "Unknown"
        dialogView.findViewById<TextView>(R.id.tvAmount).text = "-${"%.2f".format(payment.amount)} ${payment.currency}"
        dialogView.findViewById<TextView>(R.id.tvCategory).text = payment.categoryId ?: "Uncategorized"
        dialogView.findViewById<TextView>(R.id.tvCard).text = payment.card?.let { "****$it" } ?: "-"
        dialogView.findViewById<TextView>(R.id.tvTimestamp).text = payment.timestamp ?: "-"
        dialogView.findViewById<TextView>(R.id.tvBalance).text = payment.balance?.let { "${"%.2f".format(it)} ${payment.currency}" } ?: "-"
        dialogView.findViewById<TextView>(R.id.tvSender).text = payment.senderAddress ?: "-"

        val spinnerCategories = dialogView.findViewById<Spinner>(R.id.spinnerCategories)
        val btnAddToCategory = dialogView.findViewById<Button>(R.id.btnAddToCategory)
        val btnCreateCategory = dialogView.findViewById<Button>(R.id.btnCreateCategory)

        // Load categories for spinner
        lifecycleScope.launch {
            val configCategories = ConfigRepository.getCategories()
            val categoryNames = listOf(getString(R.string.select_category_hint)) +
                configCategories.map { it.name }

            val spinnerAdapter = ArrayAdapter(
                this@PaymentsActivity,
                android.R.layout.simple_spinner_item,
                categoryNames
            )
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategories.adapter = spinnerAdapter

            val dialog = AlertDialog.Builder(this@PaymentsActivity)
                .setTitle(R.string.payment_details)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, null)
                .create()

            btnAddToCategory.setOnClickListener {
                val selectedPosition = spinnerCategories.selectedItemPosition
                if (selectedPosition > 0 && payment.merchant != null) {
                    val selectedCategory = configCategories[selectedPosition - 1]
                    addMerchantToCategory(payment.merchant, selectedCategory, dialog)
                } else {
                    Toast.makeText(this@PaymentsActivity, R.string.no_sender_selected, Toast.LENGTH_SHORT).show()
                }
            }

            btnCreateCategory.setOnClickListener {
                if (payment.merchant != null) {
                    showCreateCategoryDialog(payment.merchant, dialog)
                }
            }

            dialog.show()
        }
    }

    private fun addMerchantToCategory(merchant: String, category: Category, parentDialog: AlertDialog) {
        lifecycleScope.launch {
            try {
                val updatedMerchants = category.merchants.toMutableList()
                if (!updatedMerchants.any { it.equals(merchant, ignoreCase = true) }) {
                    updatedMerchants.add(merchant)
                    val updatedCategory = category.copy(merchants = updatedMerchants)
                    withContext(Dispatchers.IO) {
                        ConfigRepository.updateCategory(updatedCategory)
                    }

                    // Re-categorize this payment
                    val payment = filteredPayments.find { it.merchant == merchant }
                    if (payment != null) {
                        payment.id?.let { paymentId ->
                            withContext(Dispatchers.IO) {
                                paymentRepository.updatePaymentCategory(paymentId, category.name)
                            }
                        }
                    }

                    Toast.makeText(
                        this@PaymentsActivity,
                        getString(R.string.merchant_added_to_category, merchant, category.name),
                        Toast.LENGTH_SHORT
                    ).show()

                    parentDialog.dismiss()
                    loadData() // Refresh the list
                }
            } catch (e: Exception) {
                Toast.makeText(this@PaymentsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCreateCategoryDialog(merchant: String, parentDialog: AlertDialog) {
        val input = EditText(this).apply {
            hint = getString(R.string.enter_category_name)
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.create_new_category)
            .setView(input)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val categoryName = input.text.toString().trim()
                if (categoryName.isNotEmpty()) {
                    createCategoryWithMerchant(categoryName, merchant, parentDialog)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun createCategoryWithMerchant(categoryName: String, merchant: String, parentDialog: AlertDialog) {
        lifecycleScope.launch {
            try {
                // Create new category
                val newCategory = withContext(Dispatchers.IO) {
                    ConfigRepository.addCategory()
                }

                // Update with name and merchant
                val updatedCategory = newCategory.copy(
                    name = categoryName,
                    merchants = mutableListOf(merchant)
                )
                withContext(Dispatchers.IO) {
                    ConfigRepository.updateCategory(updatedCategory)
                }

                // Re-categorize the payment
                val payment = filteredPayments.find { it.merchant == merchant }
                if (payment != null) {
                    payment.id?.let { paymentId ->
                        withContext(Dispatchers.IO) {
                            paymentRepository.updatePaymentCategory(paymentId, categoryName)
                        }
                    }
                }

                Toast.makeText(
                    this@PaymentsActivity,
                    getString(R.string.category_created, categoryName),
                    Toast.LENGTH_SHORT
                ).show()

                parentDialog.dismiss()
                loadData() // Refresh the list
            } catch (e: Exception) {
                Toast.makeText(this@PaymentsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // RecyclerView Adapter
    inner class PaymentAdapter : RecyclerView.Adapter<PaymentAdapter.PaymentViewHolder>() {

        private var payments: List<Payment> = emptyList()

        fun submitList(list: List<Payment>) {
            payments = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_payment, parent, false)
            return PaymentViewHolder(view)
        }

        override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
            holder.bind(payments[position])
        }

        override fun getItemCount(): Int = payments.size

        inner class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvMerchant: TextView = itemView.findViewById(R.id.tvMerchant)
            private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
            private val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
            private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
            private val tvCard: TextView = itemView.findViewById(R.id.tvCard)

            fun bind(payment: Payment) {
                tvMerchant.text = payment.merchant ?: "Unknown"
                tvAmount.text = "-${"%.2f".format(payment.amount)} ${payment.currency}"
                tvCategory.text = payment.categoryId ?: "Uncategorized"
                tvTimestamp.text = payment.timestamp ?: ""

                if (!payment.card.isNullOrBlank()) {
                    tvCard.visibility = View.VISIBLE
                    tvCard.text = "Card: ****${payment.card}"
                } else {
                    tvCard.visibility = View.GONE
                }

                itemView.setOnClickListener {
                    showPaymentDetail(payment)
                }
            }
        }
    }
}
