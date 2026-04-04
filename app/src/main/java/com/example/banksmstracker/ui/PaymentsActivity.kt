package com.example.banksmstracker.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.banksmstracker.BuildConfig
import com.example.banksmstracker.R
import com.example.banksmstracker.data.Category
import com.example.banksmstracker.data.Merchant
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.database.ExchangeRateDao
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.RoomPaymentRepository
import com.example.banksmstracker.util.ExchangeRateCache
import com.example.banksmstracker.util.formatDisplayTimestamp
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class PaymentsActivity : BaseActivity() {

    private lateinit var recyclerPayments: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var tvPaymentCount: TextView
    private lateinit var btnSelectCategories: Button
    private lateinit var spinnerSender: Spinner
    private lateinit var spinnerCurrency: Spinner
    private lateinit var btnExportCsv: Button
    private lateinit var btnStartDate: Button
    private lateinit var btnEndDate: Button
    private lateinit var btnClearDates: Button
    private lateinit var btnSpendingReport: Button
    private lateinit var btnToggleDisplayNames: Button
    private lateinit var btnPrevPage: Button
    private lateinit var btnNextPage: Button
    private lateinit var tvPageIndicator: TextView
    private lateinit var etMerchantSearch: android.widget.EditText

    private lateinit var paymentRepository: RoomPaymentRepository
    private lateinit var exchangeRateDao: ExchangeRateDao

    private var allPayments: List<Payment> = emptyList()
    private var filteredPayments: List<Payment> = emptyList()

    /** All known category names (plus [UNCATEGORIZED_FILTER] sentinel for uncategorized). */
    private var allCategoryItems: List<String> = emptyList()

    /**
     * Currently selected category filter items (names + optional [UNCATEGORIZED_FILTER]).
     * Empty set = show all.
     */
    private var selectedCategories: MutableSet<String> = mutableSetOf()

    private var senderAddresses: List<String> = emptyList()
    private var selectedSender: String? = null
    private var startDate: Long? = null
    private var endDate: Long? = null
    private var merchantSearchQuery: String? = null

    /** The currency in which amounts are displayed (conversion is on-the-fly; stored values unchanged). */
    private var selectedDisplayCurrency: String = "GEL"

    /** Map from raw merchant pattern → human-readable display name. */
    private var merchantDisplayNames: Map<String, String> = emptyMap()
    private var showDisplayNames: Boolean = false

    // ── Paging ────────────────────────────────────────────────────────────────
    private var currentPage: Int = 0
    private val pageSize: Int = 25

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val adapter = PaymentAdapter()

    private class PaymentDiffCallback : DiffUtil.ItemCallback<Payment>() {
        override fun areItemsTheSame(oldItem: Payment, newItem: Payment): Boolean =
            oldItem.id != null && oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Payment, newItem: Payment): Boolean = oldItem == newItem
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payments)

        initViews()
        setupRecyclerView()

        if (savedInstanceState != null) {
            startDate = savedInstanceState.getLong(KEY_START_DATE, -1L).takeIf { it >= 0 }
            endDate = savedInstanceState.getLong(KEY_END_DATE, -1L).takeIf { it >= 0 }
            updateDateButtons()
        }

        loadData()
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
        recyclerPayments = findViewById(R.id.recyclerPayments)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        tvPaymentCount = findViewById(R.id.tvPaymentCount)
        btnSelectCategories = findViewById(R.id.btnSelectCategories)
        spinnerSender = findViewById(R.id.spinnerSender)
        spinnerCurrency = findViewById(R.id.spinnerCurrency)
        btnExportCsv = findViewById(R.id.btnExportCsv)
        btnStartDate = findViewById(R.id.btnStartDate)
        btnEndDate = findViewById(R.id.btnEndDate)
        btnClearDates = findViewById(R.id.btnClearDates)
        btnToggleDisplayNames = findViewById(R.id.btnToggleDisplayNames)
        btnPrevPage = findViewById(R.id.btnPrevPage)
        btnNextPage = findViewById(R.id.btnNextPage)
        tvPageIndicator = findViewById(R.id.tvPageIndicator)
        etMerchantSearch = findViewById(R.id.etMerchantSearch)

        btnSelectCategories.setOnClickListener { showCategorySelectionDialog() }

        etMerchantSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                merchantSearchQuery = s?.toString()?.takeIf { it.isNotBlank() }
                currentPage = 0
                applyFilter()
            }
        })

        btnToggleDisplayNames.setOnClickListener {
            showDisplayNames = !showDisplayNames
            btnToggleDisplayNames.text = if (showDisplayNames)
                getString(R.string.toggle_display_names_on)
            else
                getString(R.string.toggle_display_names_off)
            adapter.notifyDataSetChanged()
        }

        if (!BuildConfig.DEBUG) {
            btnExportCsv.visibility = View.GONE
        } else {
            btnExportCsv.setOnClickListener { exportToCsv() }
        }

        btnSpendingReport = findViewById(R.id.btnSpendingReport)
        btnSpendingReport.setOnClickListener { showSpendingReport() }

        btnStartDate.setOnClickListener { showDatePicker(isStartDate = true) }
        btnEndDate.setOnClickListener { showDatePicker(isStartDate = false) }
        btnClearDates.setOnClickListener {
            startDate = null; endDate = null
            btnStartDate.text = getString(R.string.start_date)
            btnEndDate.text = getString(R.string.end_date)
            currentPage = 0
            applyFilter()
        }

        setupCurrencySpinner()

        btnPrevPage.setOnClickListener {
            if (currentPage > 0) { currentPage--; renderPage() }
        }
        btnNextPage.setOnClickListener {
            val totalPages = totalPageCount()
            if (currentPage < totalPages - 1) { currentPage++; renderPage() }
        }
    }

    private fun setupCurrencySpinner() {
        spinnerCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val currencies = resources.getStringArray(R.array.currency_entries)
                val chosen = if (position in currencies.indices) currencies[position] else "GEL"
                if (chosen != selectedDisplayCurrency) {
                    selectedDisplayCurrency = chosen
                    renderPage()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        val existingDate = if (isStartDate) startDate else endDate
        if (existingDate != null) calendar.timeInMillis = existingDate

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                    if (isStartDate) {
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    } else {
                        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                    }
                }
                val timestamp = selectedCalendar.timeInMillis
                val dateText = dateFormat.format(Date(timestamp))
                if (isStartDate) {
                    startDate = timestamp; btnStartDate.text = dateText
                } else {
                    endDate = timestamp; btnEndDate.text = dateText
                }
                currentPage = 0
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

    private fun loadData(preserveScroll: Boolean = false) {
        val savedScrollState = if (preserveScroll)
            (recyclerPayments.layoutManager as? LinearLayoutManager)?.onSaveInstanceState()
        else null

        lifecycleScope.launch {
            ConfigRepository.load(application)
            val database = BankSmsDatabase.getInstance(this@PaymentsActivity)
            paymentRepository = RoomPaymentRepository(database.paymentDao())
            exchangeRateDao = database.exchangeRateDao()

            allPayments = withContext(Dispatchers.IO) { paymentRepository.getAllPayments() }

            val configCategories = ConfigRepository.getCategories()

            merchantDisplayNames = configCategories
                .flatMap { it.merchants }
                .filter { !it.isRegex && it.displayName != null }
                .associate { it.pattern to it.displayName!! }

            // Category items: real category names + special sentinel for uncategorized
            allCategoryItems = configCategories.map { it.name } +
                listOf(UNCATEGORIZED_FILTER)

            senderAddresses = withContext(Dispatchers.IO) {
                listOf(getString(R.string.all_senders)) + paymentRepository.getDistinctSenderAddresses()
            }

            if (startDate == null && endDate == null) setDefaultDateRange()

            setupSenderSpinner()
            applyFilter()
            savedScrollState?.let { recyclerPayments.layoutManager?.onRestoreInstanceState(it) }
        }
    }

    private fun setDefaultDateRange() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
        startDate = calendar.timeInMillis
        btnStartDate.text = dateFormat.format(Date(startDate!!))

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59); calendar.set(Calendar.MILLISECOND, 999)
        endDate = calendar.timeInMillis
        btnEndDate.text = dateFormat.format(Date(endDate!!))
    }

    // ── Category multi-select ─────────────────────────────────────────────────

    private fun showCategorySelectionDialog() {
        if (allCategoryItems.isEmpty()) return

        val labels = allCategoryItems.map { item ->
            if (item == UNCATEGORIZED_FILTER) getString(R.string.uncategorized_only) else item
        }.toTypedArray()
        val checked = BooleanArray(allCategoryItems.size) { selectedCategories.contains(allCategoryItems[it]) }

        AlertDialog.Builder(this)
            .setTitle(R.string.all_categories)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                selectedCategories = allCategoryItems
                    .filterIndexed { i, _ -> checked[i] }
                    .toMutableSet()
                updateCategoryButtonLabel()
                currentPage = 0
                applyFilter()
            }
            .setNeutralButton(R.string.clear) { _, _ ->
                selectedCategories.clear()
                updateCategoryButtonLabel()
                currentPage = 0
                applyFilter()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateCategoryButtonLabel() {
        val label = when {
            selectedCategories.isEmpty() -> getString(R.string.all_categories)
            selectedCategories.size == 1 -> {
                val single = selectedCategories.first()
                if (single == UNCATEGORIZED_FILTER) getString(R.string.uncategorized_only) else single
            }
            else -> getString(R.string.categories_n_selected, selectedCategories.size)
        }
        btnSelectCategories.text = getString(R.string.categories_button_label, label)
    }

    private fun setupSenderSpinner() {
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, senderAddresses)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSender.adapter = spinnerAdapter

        spinnerSender.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSender = if (position == 0) null else senderAddresses[position]
                currentPage = 0
                applyFilter()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedSender = null; currentPage = 0; applyFilter()
            }
        }
    }

    private fun applyFilter() {
        filteredPayments = filterPayments(
            allPayments,
            selectedCategories.takeIf { it.isNotEmpty() },
            selectedSender, startDate, endDate, merchantSearchQuery
        ).sortedBy { parseTransactionTimestamp(it.timestamp) ?: Long.MAX_VALUE }
        renderPage()
        saveFilterState()
    }

    // ── Paging ────────────────────────────────────────────────────────────────

    private fun totalPageCount(): Int =
        if (filteredPayments.isEmpty()) 1
        else (filteredPayments.size + pageSize - 1) / pageSize

    private fun pagedPayments(): List<Payment> {
        if (filteredPayments.isEmpty()) return emptyList()
        val totalPages = totalPageCount()
        if (currentPage >= totalPages) currentPage = totalPages - 1
        val start = currentPage * pageSize
        val end = minOf(start + pageSize, filteredPayments.size)
        return filteredPayments.subList(start, end)
    }

    private fun renderPage() {
        val paged = pagedPayments()
        adapter.submitList(paged)
        updateUI()
    }

    private fun saveFilterState() {
        val prefs = getSharedPreferences(PREFS_FILTER_STATE, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_FILTER_CATEGORIES, selectedCategories.joinToString(SEP))
            .putString(KEY_FILTER_SENDER, selectedSender)
            .putLong(KEY_FILTER_START_DATE, startDate ?: -1L)
            .putLong(KEY_FILTER_END_DATE, endDate ?: -1L)
            .putString(KEY_FILTER_MERCHANT, merchantSearchQuery)
            .apply()
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
        tvPaymentCount.text = getString(R.string.payments_summary, filteredPayments.size, "%.2f".format(total), currency)

        // Update paging controls
        val totalPages = totalPageCount()
        tvPageIndicator.text = getString(R.string.page_indicator, currentPage + 1, totalPages)
        btnPrevPage.isEnabled = currentPage > 0
        btnNextPage.isEnabled = currentPage < totalPages - 1
    }

    private fun exportToCsv() {
        lifecycleScope.launch {
            try {
                val csvContent = withContext(Dispatchers.IO) { buildCsvContent(filteredPayments) }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(cacheDir, "payments_$timestamp.csv").also { it.deleteOnExit() }
                file.writeText(csvContent)
                val uri = FileProvider.getUriForFile(this@PaymentsActivity, "$packageName.fileprovider", file)
                Toast.makeText(this@PaymentsActivity, getString(R.string.csv_export_success, file.absolutePath), Toast.LENGTH_SHORT).show()
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_csv)))
            } catch (e: Exception) {
                Toast.makeText(this@PaymentsActivity, getString(R.string.csv_export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun buildCsvContent(payments: List<Payment>): String {
        val sb = StringBuilder()
        sb.appendLine(getString(R.string.csv_header_payments))
        for (p in payments) {
            sb.appendLine(listOf(
                p.amount.toString(), escapeCsv(p.currency), escapeCsv(p.card ?: ""),
                escapeCsv(p.merchant ?: ""), escapeCsv(p.timestamp), p.balance?.toString() ?: "",
                escapeCsv(p.categoryId ?: ""), escapeCsv(p.senderAddress ?: "")
            ).joinToString(","))
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String =
        if (value.contains(",") || value.contains("\"") || value.contains("\n"))
            "\"${value.replace("\"", "\"\"")}\"" else value

    // ── Spending report (uses filteredPayments — all pages, categories already applied) ──

    private fun showSpendingReport() {
        if (filteredPayments.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.spending_report)
                .setMessage("${buildDateRangeText()}\n\n${getString(R.string.no_spending_data)}")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        lifecycleScope.launch {
            // Convert every payment to GEL using per-payment date exchange rates.
            val categoryGelTotals = buildCategoryTotalsInGel()
            val totalGel = categoryGelTotals.sumOf { it.second }

            val dateRangeText = buildDateRangeText()
            val reportText = buildReportText(dateRangeText, categoryGelTotals, totalGel)

            val dialogView = LayoutInflater.from(this@PaymentsActivity).inflate(R.layout.dialog_spending_report, null)
            dialogView.findViewById<TextView>(R.id.tvReportText).text = reportText
            setupPieChart(dialogView.findViewById(R.id.pieChart), categoryGelTotals, totalGel)
            setupBarChart(dialogView.findViewById(R.id.barChart), categoryGelTotals)

            AlertDialog.Builder(this@PaymentsActivity)
                .setTitle(R.string.spending_report)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun buildDateRangeText(): String {
        val actualStart = startDate ?: filteredPayments.mapNotNull { parseTransactionTimestamp(it.timestamp) }.minOrNull()
        val actualEnd = endDate ?: filteredPayments.mapNotNull { parseTransactionTimestamp(it.timestamp) }.maxOrNull()
        return when {
            actualStart != null && actualEnd != null ->
                "${dateFormat.format(Date(actualStart))} - ${dateFormat.format(Date(actualEnd))}"
            actualStart != null -> "From ${dateFormat.format(Date(actualStart))}"
            actualEnd != null -> "Until ${dateFormat.format(Date(actualEnd))}"
            else -> getString(R.string.all_time)
        }
    }

    /**
     * Builds category totals where every payment is converted to GEL using the exchange rate
     * for that payment's individual transaction date.
     * Payments whose rate cannot be fetched are converted at rate 1.0 (i.e. treated as GEL).
     */
    private suspend fun buildCategoryTotalsInGel(): List<Pair<String, Double>> {
        val categoryGelAmounts = mutableMapOf<String, Double>()
        for (payment in filteredPayments) {
            val gelAmount = convertToGel(payment)
            val category = payment.categoryId ?: getString(R.string.uncategorized)
            categoryGelAmounts[category] = (categoryGelAmounts[category] ?: 0.0) + gelAmount
        }
        return categoryGelAmounts.toList().sortedByDescending { it.second }
    }

    /**
     * Converts a single payment's amount to GEL.
     * GEL payments return amount as-is.
     * Other currencies fetch the rate via [ExchangeRateCache] with a 3-second timeout.
     */
    private suspend fun convertToGel(payment: Payment): Double {
        if (payment.currency == "GEL") return payment.amount
        val dateMs = parseTransactionTimestamp(payment.timestamp) ?: System.currentTimeMillis()
        val rate = withTimeoutOrNull(3_000L) {
            ExchangeRateCache.getRateToGel(dateMs, payment.currency, exchangeRateDao)
        } ?: 1.0
        return payment.amount * rate
    }

    private fun buildReportText(
        dateRangeText: String,
        categoryGelTotals: List<Pair<String, Double>>,
        totalGel: Double
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Period: $dateRangeText")
        sb.appendLine()
        sb.appendLine(getString(R.string.total_spending, "%.2f".format(totalGel), "GEL"))
        sb.appendLine()
        sb.appendLine(getString(R.string.by_category))
        categoryGelTotals.forEach { (category, gelAmount) ->
            val pct = if (totalGel > 0) (gelAmount / totalGel * 100).toInt() else 0
            sb.appendLine("  $category: ${"%.2f".format(gelAmount)} GEL ($pct%)")
        }
        return sb.toString()
    }

    private fun setupPieChart(chart: PieChart, categoryTotals: List<Pair<String, Double>>, totalAmount: Double) {
        val entries = categoryTotals.map { (category, amount) -> PieEntry(amount.toFloat(), category) }
        val dataSet = PieDataSet(entries, "").apply {
            colors = CHART_COLORS; valueTextSize = 11f; valueTextColor = Color.WHITE; sliceSpace = 2f
        }
        val data = PieData(dataSet).apply { setValueFormatter(PercentFormatter(chart)) }
        chart.apply {
            this.data = data; description.isEnabled = false; isDrawHoleEnabled = true; holeRadius = 40f
            setUsePercentValues(true); setEntryLabelTextSize(10f); setEntryLabelColor(Color.WHITE)
            legend.isEnabled = false; setDrawCenterText(true)
            centerText = getString(R.string.total_spending, "%.0f".format(totalAmount), "GEL")
            setCenterTextSize(12f); animateY(600); invalidate()
        }
    }

    private fun setupBarChart(chart: BarChart, categoryTotals: List<Pair<String, Double>>) {
        val entries = categoryTotals.mapIndexed { index, (_, amount) -> BarEntry(index.toFloat(), amount.toFloat()) }
        val dataSet = BarDataSet(entries, "").apply { setDrawValues(false) }
        chart.data = BarData(dataSet)
        chart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(categoryTotals.map { it.first })
            position = XAxis.XAxisPosition.BOTTOM; granularity = 1f
            setDrawGridLines(false); labelRotationAngle = -30f
        }
        chart.axisRight.isEnabled = false; chart.legend.isEnabled = false
        chart.description.isEnabled = false; chart.invalidate()
    }

    private fun showPaymentDetail(payment: Payment) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment_detail, null)

        val displayMerchant = if (showDisplayNames)
            merchantDisplayNames[payment.merchant] ?: payment.merchant ?: getString(R.string.unknown)
        else payment.merchant ?: getString(R.string.unknown)

        dialogView.findViewById<TextView>(R.id.tvMerchant).text = displayMerchant
        dialogView.findViewById<TextView>(R.id.tvAmount).text = "-${"%.2f".format(payment.amount)} ${payment.currency}"
        dialogView.findViewById<TextView>(R.id.tvCategory).text = payment.categoryId ?: getString(R.string.uncategorized)
        dialogView.findViewById<TextView>(R.id.tvCard).text = payment.card?.let { "****$it" } ?: "-"
        dialogView.findViewById<TextView>(R.id.tvTimestamp).text = payment.timestamp.ifBlank { "-" }
        dialogView.findViewById<TextView>(R.id.tvBalance).text =
            payment.balance?.let { "${"%.2f".format(it)} ${payment.currency}" } ?: "-"
        dialogView.findViewById<TextView>(R.id.tvSender).text = payment.senderAddress ?: "-"

        val spinnerCategories = dialogView.findViewById<Spinner>(R.id.spinnerCategories)
        val btnAddToCategory = dialogView.findViewById<Button>(R.id.btnAddToCategory)
        val btnCreateCategory = dialogView.findViewById<Button>(R.id.btnCreateCategory)

        lifecycleScope.launch {
            val configCategories = ConfigRepository.getCategories()
            val categoryNames = listOf(getString(R.string.select_category_hint)) + configCategories.map { it.name }
            val spinnerAdapter = ArrayAdapter(this@PaymentsActivity, android.R.layout.simple_spinner_item, categoryNames)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategories.adapter = spinnerAdapter

            val dialog = AlertDialog.Builder(this@PaymentsActivity)
                .setTitle(R.string.payment_details).setView(dialogView)
                .setNegativeButton(R.string.cancel, null).create()

            btnAddToCategory.setOnClickListener {
                val pos = spinnerCategories.selectedItemPosition
                if (pos > 0 && payment.merchant != null)
                    addMerchantToCategory(payment.merchant, configCategories[pos - 1], dialog)
                else Toast.makeText(this@PaymentsActivity, R.string.no_sender_selected, Toast.LENGTH_SHORT).show()
            }
            btnCreateCategory.setOnClickListener {
                if (payment.merchant != null) showCreateCategoryDialog(payment.merchant, dialog)
            }
            dialog.show()
        }
    }

    private fun addMerchantToCategory(merchant: String, category: Category, parentDialog: AlertDialog) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val allCategories = ConfigRepository.getCategories()
                    for (cat in allCategories) {
                        val hadMerchant = cat.merchants.any { m -> !m.isRegex && m.pattern.equals(merchant, ignoreCase = true) }
                        if (hadMerchant) {
                            ConfigRepository.updateCategory(cat.copy(merchants = cat.merchants.filterNot { m ->
                                !m.isRegex && m.pattern.equals(merchant, ignoreCase = true)
                            }.toMutableList()))
                        }
                    }
                    val refreshed = ConfigRepository.getCategories().first { it.id == category.id }
                    if (!refreshed.merchants.any { m -> !m.isRegex && m.pattern.equals(merchant, ignoreCase = true) }) {
                        ConfigRepository.updateCategory(
                            refreshed.copy(merchants = (refreshed.merchants + Merchant(merchant)).toMutableList())
                        )
                    }
                    paymentRepository.updateCategoryForMerchant(merchant, category.name)
                }
                Toast.makeText(this@PaymentsActivity, getString(R.string.merchant_added_to_category, merchant, category.name), Toast.LENGTH_SHORT).show()
                parentDialog.dismiss()
                loadData(preserveScroll = true)
            } catch (e: Exception) {
                Toast.makeText(this@PaymentsActivity, getString(R.string.error_with_message, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCreateCategoryDialog(merchant: String, parentDialog: AlertDialog) {
        val input = EditText(this).apply { hint = getString(R.string.enter_category_name); setPadding(48, 32, 48, 32) }
        AlertDialog.Builder(this)
            .setTitle(R.string.create_new_category).setView(input)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) createCategoryWithMerchant(name, merchant, parentDialog)
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun createCategoryWithMerchant(categoryName: String, merchant: String, parentDialog: AlertDialog) {
        lifecycleScope.launch {
            try {
                val newCategory = withContext(Dispatchers.IO) { ConfigRepository.addCategory() }
                withContext(Dispatchers.IO) {
                    ConfigRepository.updateCategory(
                        newCategory.copy(name = categoryName, merchants = mutableListOf(Merchant(merchant)))
                    )
                }
                Toast.makeText(this@PaymentsActivity, getString(R.string.category_created, categoryName), Toast.LENGTH_SHORT).show()
                parentDialog.dismiss()
                loadData(preserveScroll = true)
            } catch (e: Exception) {
                Toast.makeText(this@PaymentsActivity, getString(R.string.error_with_message, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── RecyclerView Adapter ───────────────────────────────────────────────────

    inner class PaymentAdapter : ListAdapter<Payment, PaymentAdapter.PaymentViewHolder>(PaymentDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_payment, parent, false)
            return PaymentViewHolder(view)
        }

        override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) = holder.bind(getItem(position))

        inner class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvMerchant: TextView = itemView.findViewById(R.id.tvMerchant)
            private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
            private val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
            private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
            private val tvCard: TextView = itemView.findViewById(R.id.tvCard)
            private val tvConversionRate: TextView = itemView.findViewById(R.id.tvConversionRate)

            fun bind(payment: Payment) {
                tvMerchant.text = if (showDisplayNames)
                    merchantDisplayNames[payment.merchant] ?: payment.merchant ?: getString(R.string.unknown)
                else payment.merchant ?: getString(R.string.unknown)
                // Show original amount immediately as a placeholder, then update after conversion.
                tvAmount.text = "-${"%.2f".format(payment.amount)} ${payment.currency}"
                tvCategory.text = payment.categoryId ?: getString(R.string.uncategorized)
                tvTimestamp.text = formatDisplayTimestamp(payment.timestamp)
                if (!payment.card.isNullOrBlank()) {
                    tvCard.visibility = View.VISIBLE; tvCard.text = getString(R.string.card_display, payment.card)
                } else {
                    tvCard.visibility = View.GONE
                }
                // Hide conversion rate label until we confirm a successful conversion.
                tvConversionRate.visibility = View.GONE
                itemView.setOnClickListener { showPaymentDetail(payment) }

                // Convert amount to selectedDisplayCurrency on-the-fly (display only; stored values unchanged).
                if (payment.currency != selectedDisplayCurrency) {
                    lifecycleScope.launch {
                        val dateMs = parseTransactionTimestamp(payment.timestamp) ?: System.currentTimeMillis()
                        val gelRate = withTimeoutOrNull(3_000L) {
                            ExchangeRateCache.getRateToGel(dateMs, payment.currency, exchangeRateDao)
                        }
                        // If gelRate is null the fetch failed — leave the placeholder unchanged and
                        // keep tvConversionRate hidden to avoid showing a misleading currency label.
                        if (gelRate == null) return@launch
                        val gelAmount = payment.amount * gelRate

                        val displayAmount: Double
                        val rateLabel: String
                        if (selectedDisplayCurrency == "GEL") {
                            displayAmount = gelAmount
                            // payment.currency → GEL: show "1 USD = 2.7812 GEL"
                            rateLabel = "1 ${payment.currency} = ${"%.4f".format(gelRate)} GEL"
                        } else {
                            val displayRate = withTimeoutOrNull(3_000L) {
                                ExchangeRateCache.getRateToGel(dateMs, selectedDisplayCurrency, exchangeRateDao)
                            }
                            if (displayRate == null || displayRate <= 0.0) return@launch
                            displayAmount = gelAmount / displayRate
                            // Show cross rate: "1 USD ≈ 0.9234 EUR"
                            rateLabel = "1 ${payment.currency} ≈ ${"%.4f".format(gelRate / displayRate)} $selectedDisplayCurrency"
                        }

                        tvAmount.text = "-${"%.2f".format(displayAmount)} $selectedDisplayCurrency"
                        tvConversionRate.text = rateLabel
                        tvConversionRate.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    companion object {
        private const val KEY_START_DATE = "key_start_date"
        private const val KEY_END_DATE = "key_end_date"
        const val PREFS_FILTER_STATE = "payments_filter_state"
        /** Replaces the old single KEY_FILTER_CATEGORY. Stored as pipe-separated values. */
        const val KEY_FILTER_CATEGORIES = "filter_categories"
        @Deprecated("Replaced by KEY_FILTER_CATEGORIES") const val KEY_FILTER_CATEGORY = "filter_category"
        const val KEY_FILTER_SENDER = "filter_sender"
        const val KEY_FILTER_START_DATE = "filter_start_date"
        const val KEY_FILTER_END_DATE = "filter_end_date"
        const val KEY_FILTER_MERCHANT = "filter_merchant"
        private const val SEP = "\u001F"  // ASCII Unit Separator — safe in category names

        private val CHART_COLORS = listOf(
            Color.rgb(64, 150, 220), Color.rgb(255, 140, 50), Color.rgb(90, 190, 100),
            Color.rgb(230, 80, 80), Color.rgb(160, 100, 210), Color.rgb(255, 200, 50),
            Color.rgb(80, 200, 200), Color.rgb(200, 100, 150), Color.rgb(130, 180, 80),
            Color.rgb(180, 130, 80)
        )
    }
}
