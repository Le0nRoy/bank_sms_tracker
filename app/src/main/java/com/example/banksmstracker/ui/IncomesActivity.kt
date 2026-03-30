package com.example.banksmstracker.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.banksmstracker.R
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.database.ExchangeRateDao
import com.example.banksmstracker.database.IncomeEntity
import com.example.banksmstracker.util.ExchangeRateCache
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class IncomesActivity : BaseActivity() {

    private lateinit var recyclerIncomes: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var tvIncomeTotal: TextView
    private lateinit var spinnerSender: Spinner
    private lateinit var btnStartDate: Button
    private lateinit var btnEndDate: Button
    private lateinit var btnClearDates: Button
    private lateinit var btnIncomeReport: Button
    private lateinit var etSourceSearch: android.widget.EditText

    private var allIncomes: List<IncomeEntity> = emptyList()
    private var filteredIncomes: List<IncomeEntity> = emptyList()
    private var senderAddresses: List<String> = emptyList()
    private var selectedSender: String? = null
    private var startDate: Long? = null
    private var endDate: Long? = null
    private var sourceQuery: String? = null

    private lateinit var exchangeRateDao: ExchangeRateDao

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val adapter = IncomeAdapter()

    private class IncomeDiffCallback : DiffUtil.ItemCallback<IncomeEntity>() {
        override fun areItemsTheSame(oldItem: IncomeEntity, newItem: IncomeEntity): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: IncomeEntity, newItem: IncomeEntity): Boolean = oldItem == newItem
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incomes)

        initViews()
        recyclerIncomes.layoutManager = LinearLayoutManager(this)
        recyclerIncomes.adapter = adapter

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
        recyclerIncomes = findViewById(R.id.recyclerIncomes)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        tvIncomeTotal = findViewById(R.id.tvIncomeTotal)
        spinnerSender = findViewById(R.id.spinnerSender)
        btnStartDate = findViewById(R.id.btnStartDate)
        btnEndDate = findViewById(R.id.btnEndDate)
        btnClearDates = findViewById(R.id.btnClearDates)
        btnIncomeReport = findViewById(R.id.btnIncomeReport)
        etSourceSearch = findViewById(R.id.etSourceSearch)

        etSourceSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                sourceQuery = s?.toString()?.takeIf { it.isNotBlank() }
                applyFilter()
            }
        })

        btnStartDate.setOnClickListener { showDatePicker(isStartDate = true) }
        btnEndDate.setOnClickListener { showDatePicker(isStartDate = false) }
        btnClearDates.setOnClickListener {
            startDate = null; endDate = null
            btnStartDate.text = getString(R.string.start_date)
            btnEndDate.text = getString(R.string.end_date)
            applyFilter()
        }
        btnIncomeReport.setOnClickListener { showIncomeReport() }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        val existing = if (isStartDate) startDate else endDate
        if (existing != null) calendar.timeInMillis = existing

        DatePickerDialog(this, { _, year, month, day ->
            val cal = Calendar.getInstance().apply {
                set(year, month, day)
                if (isStartDate) {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                } else {
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                }
            }
            val ts = cal.timeInMillis
            if (isStartDate) { startDate = ts; btnStartDate.text = dateFormat.format(Date(ts)) }
            else { endDate = ts; btnEndDate.text = dateFormat.format(Date(ts)) }
            applyFilter()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun loadData() {
        lifecycleScope.launch {
            val database = BankSmsDatabase.getInstance(this@IncomesActivity)
            exchangeRateDao = database.exchangeRateDao()
            val incomes = withContext(Dispatchers.IO) {
                database.incomeDao().getAllIncomes()
            }
            allIncomes = incomes

            // Build sender list
            senderAddresses = listOf(getString(R.string.all_senders)) +
                incomes.mapNotNull { it.senderAddress }.distinct().sorted()
            setupSenderSpinner()

            // Default date range: current month
            if (startDate == null && endDate == null) setDefaultDateRange()

            applyFilter()
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

    private fun setupSenderSpinner() {
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, senderAddresses)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSender.adapter = spinnerAdapter
        spinnerSender.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSender = if (position == 0) null else senderAddresses[position]
                applyFilter()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedSender = null; applyFilter()
            }
        }
    }

    private fun applyFilter() {
        filteredIncomes = filterIncomes(allIncomes, selectedSender, startDate, endDate, sourceQuery)
        adapter.submitList(filteredIncomes)
        updateUI()
    }

    private fun filterIncomes(
        incomes: List<IncomeEntity>,
        sender: String?,
        start: Long?,
        end: Long?,
        query: String?
    ): List<IncomeEntity> = incomes.filter { income ->
        val matchesSender = sender == null || income.senderAddress == sender
        val matchesSource = query.isNullOrBlank() ||
            (income.source?.contains(query.trim(), ignoreCase = true) == true)
        val matchesDate = matchesIncomeDateRange(income, start, end)
        matchesSender && matchesSource && matchesDate
    }

    private fun matchesIncomeDateRange(income: IncomeEntity, start: Long?, end: Long?): Boolean {
        if (start == null && end == null) return true
        val ts = parseTransactionTimestamp(income.timestamp ?: "")
            ?: income.receivedAt
            ?: return false
        val afterStart = start?.let { ts >= it } ?: true
        val beforeEnd = end?.let { ts <= it } ?: true
        return afterStart && beforeEnd
    }

    private fun updateUI() {
        if (filteredIncomes.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
            recyclerIncomes.visibility = View.GONE
            tvIncomeTotal.text = getString(R.string.income_total_label, "0.00", "")
        } else {
            tvEmptyState.visibility = View.GONE
            recyclerIncomes.visibility = View.VISIBLE
            val total = filteredIncomes.sumOf { it.amount }
            val currency = filteredIncomes.first().currency
            tvIncomeTotal.text = getString(R.string.incomes_summary, filteredIncomes.size, "%.2f".format(total), currency)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Income report dialog (pie + bar chart by source)
    // ─────────────────────────────────────────────────────────────────────────

    private fun showIncomeReport() {
        if (filteredIncomes.isEmpty()) {
            Toast.makeText(this, R.string.no_incomes_filtered, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val hasUsd = filteredIncomes.any { it.currency == "USD" }
            val usdRate: Double? = if (hasUsd) {
                withTimeoutOrNull(3_000L) {
                    ExchangeRateCache.getUsdToGelRate(System.currentTimeMillis(), exchangeRateDao)
                }
            } else null

            val sourceTotals = buildSourceTotals()
            val totalAmount = filteredIncomes.sumOf { it.amount }
            val currency = filteredIncomes.first().currency
            val reportText = buildIncomeReportText(sourceTotals, totalAmount, currency, usdRate)

            val dialogView = LayoutInflater.from(this@IncomesActivity).inflate(R.layout.dialog_spending_report, null)
            dialogView.findViewById<TextView>(R.id.tvReportText).text = reportText
            setupPieChart(dialogView.findViewById(R.id.pieChart), sourceTotals, totalAmount)
            setupBarChart(dialogView.findViewById(R.id.barChart), sourceTotals)

            AlertDialog.Builder(this@IncomesActivity)
                .setTitle(R.string.income_report)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun buildSourceTotals(): List<Pair<String, Double>> =
        filteredIncomes
            .groupBy { it.source ?: getString(R.string.income_unknown_source) }
            .mapValues { (_, items) -> items.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

    private fun buildIncomeReportText(
        sourceTotals: List<Pair<String, Double>>,
        totalAmount: Double,
        currency: String,
        usdRate: Double?
    ): String {
        val sb = StringBuilder()
        sb.appendLine(getString(R.string.total_spending, "%.2f".format(totalAmount), currency))
        if (usdRate != null) {
            val gelTotal = filteredIncomes.sumOf { i ->
                if (i.currency == "USD") i.amount * usdRate else if (i.currency == "GEL") i.amount else 0.0
            }
            sb.appendLine(getString(R.string.total_in_gel, "%.2f".format(gelTotal)))
            sb.appendLine(getString(R.string.usd_gel_rate, "%.4f".format(usdRate)))
        }
        sb.appendLine()
        sb.appendLine(getString(R.string.by_source))
        sourceTotals.forEach { (source, amount) ->
            val pct = if (totalAmount > 0) (amount / totalAmount * 100).toInt() else 0
            sb.appendLine("  $source: ${"%.2f".format(amount)} $currency ($pct%)")
        }
        return sb.toString()
    }

    private fun setupPieChart(chart: PieChart, sourceTotals: List<Pair<String, Double>>, total: Double) {
        val entries = sourceTotals.map { (src, amt) -> PieEntry(amt.toFloat(), src) }
        val dataSet = PieDataSet(entries, "").apply {
            colors = CHART_COLORS; valueTextSize = 11f; valueTextColor = Color.WHITE; sliceSpace = 2f
        }
        val data = PieData(dataSet).apply { setValueFormatter(PercentFormatter(chart)) }
        chart.apply {
            this.data = data; description.isEnabled = false; isDrawHoleEnabled = true; holeRadius = 40f
            setUsePercentValues(true); setEntryLabelTextSize(10f); setEntryLabelColor(Color.WHITE)
            legend.isEnabled = false; setDrawCenterText(true)
            centerText = getString(R.string.total_spending, "%.0f".format(total), "")
            setCenterTextSize(12f); animateY(600); invalidate()
        }
    }

    private fun setupBarChart(chart: BarChart, sourceTotals: List<Pair<String, Double>>) {
        val entries = sourceTotals.mapIndexed { i, (_, amt) -> BarEntry(i.toFloat(), amt.toFloat()) }
        val dataSet = BarDataSet(entries, "").apply { setDrawValues(false) }
        chart.data = BarData(dataSet)
        chart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(sourceTotals.map { it.first })
            position = XAxis.XAxisPosition.BOTTOM; granularity = 1f
            setDrawGridLines(false); labelRotationAngle = -30f
        }
        chart.axisRight.isEnabled = false; chart.legend.isEnabled = false
        chart.description.isEnabled = false; chart.invalidate()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Adapter
    // ─────────────────────────────────────────────────────────────────────────

    inner class IncomeAdapter : ListAdapter<IncomeEntity, IncomeAdapter.IncomeViewHolder>(IncomeDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IncomeViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_income, parent, false)
            return IncomeViewHolder(view)
        }

        override fun onBindViewHolder(holder: IncomeViewHolder, position: Int) = holder.bind(getItem(position))

        inner class IncomeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvSource: TextView = itemView.findViewById(R.id.tvSource)
            private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
            private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)

            fun bind(income: IncomeEntity) {
                tvSource.text = income.source ?: getString(R.string.income_unknown_source)
                tvAmount.text = "+${"%.2f".format(income.amount)} ${income.currency}"
                val displayDate = income.timestamp ?: income.receivedAt?.let { dateFormat.format(Date(it)) }
                tvTimestamp.text = displayDate ?: ""
            }
        }
    }

    companion object {
        private const val KEY_START_DATE = "key_start_date"
        private const val KEY_END_DATE = "key_end_date"

        private val CHART_COLORS = listOf(
            Color.rgb(90, 190, 100), Color.rgb(64, 150, 220), Color.rgb(255, 140, 50),
            Color.rgb(160, 100, 210), Color.rgb(255, 200, 50), Color.rgb(80, 200, 200),
            Color.rgb(200, 100, 150), Color.rgb(130, 180, 80), Color.rgb(230, 80, 80),
            Color.rgb(180, 130, 80)
        )
    }
}
