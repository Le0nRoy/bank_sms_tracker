package com.example.banksmstracker.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
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
import com.example.banksmstracker.database.ExchangeRateEntity
import com.example.banksmstracker.service.NotificationHelper
import com.example.banksmstracker.util.ExchangeRateCache
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExchangeRatesActivity : BaseActivity() {

    private lateinit var btnSelectCurrencies: Button
    private lateinit var btnErStartDate: Button
    private lateinit var btnErEndDate: Button
    private lateinit var btnErClearFilters: Button
    private lateinit var btnDownloadMissing: Button
    private lateinit var pbErLoading: ProgressBar
    private lateinit var tvErCount: TextView
    private lateinit var tvErEmpty: TextView
    private lateinit var recyclerExchangeRates: RecyclerView

    private lateinit var exchangeRateDao: ExchangeRateDao
    private val adapter = ExchangeRateAdapter()

    private val dateDisplayFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Available currencies sourced from spinner array (all non-GEL + GEL). */
    private val allCurrencies = listOf("GEL", "USD", "EUR", "RUB")

    private var selectedCurrencies: MutableSet<String> = mutableSetOf()
    private var startDate: String? = null // "yyyy-MM-dd"
    private var endDate: String? = null // "yyyy-MM-dd"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exchange_rates)

        btnSelectCurrencies = findViewById(R.id.btnSelectCurrencies)
        btnErStartDate = findViewById(R.id.btnErStartDate)
        btnErEndDate = findViewById(R.id.btnErEndDate)
        btnErClearFilters = findViewById(R.id.btnErClearFilters)
        btnDownloadMissing = findViewById(R.id.btnDownloadMissing)
        pbErLoading = findViewById(R.id.pbErLoading)
        tvErCount = findViewById(R.id.tvErCount)
        tvErEmpty = findViewById(R.id.tvErEmpty)
        recyclerExchangeRates = findViewById(R.id.recyclerExchangeRates)

        recyclerExchangeRates.layoutManager = LinearLayoutManager(this)
        recyclerExchangeRates.adapter = adapter

        lifecycleScope.launch {
            exchangeRateDao = BankSmsDatabase.getInstance(this@ExchangeRatesActivity).exchangeRateDao()
            loadRates()
        }

        btnSelectCurrencies.setOnClickListener { showCurrencySelectionDialog() }
        btnErStartDate.setOnClickListener { showDatePicker(isStart = true) }
        btnErEndDate.setOnClickListener { showDatePicker(isStart = false) }
        btnErClearFilters.setOnClickListener {
            selectedCurrencies.clear()
            startDate = null
            endDate = null
            btnSelectCurrencies.text = getString(R.string.exchange_rates_currencies_all)
            btnErStartDate.text = getString(R.string.start_date)
            btnErEndDate.text = getString(R.string.end_date)
            lifecycleScope.launch { loadRates() }
        }
        btnDownloadMissing.setOnClickListener { downloadMissingRates() }
    }

    private suspend fun loadRates() {
        val rates = withContext(Dispatchers.IO) {
            when {
                selectedCurrencies.isNotEmpty() && startDate != null && endDate != null -> {
                    exchangeRateDao.getByDateRange(startDate!!, endDate!!)
                        .filter { it.currency in selectedCurrencies }
                }
                selectedCurrencies.isNotEmpty() -> {
                    exchangeRateDao.getByCurrencies(selectedCurrencies.toList())
                }
                startDate != null && endDate != null -> {
                    exchangeRateDao.getByDateRange(startDate!!, endDate!!)
                }
                else -> exchangeRateDao.getAll()
            }
        }
        adapter.submitList(rates)
        tvErCount.text = "${rates.size} rate(s) stored"
        tvErEmpty.visibility = if (rates.isEmpty()) View.VISIBLE else View.GONE
        recyclerExchangeRates.visibility = if (rates.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showCurrencySelectionDialog() {
        val labels = allCurrencies.toTypedArray()
        val checked = BooleanArray(allCurrencies.size) { selectedCurrencies.contains(allCurrencies[it]) }

        AlertDialog.Builder(this)
            .setTitle(R.string.exchange_rates)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                selectedCurrencies = allCurrencies.filterIndexed { i, _ -> checked[i] }.toMutableSet()
                updateCurrencyButtonLabel()
                lifecycleScope.launch { loadRates() }
            }
            .setNeutralButton(R.string.clear) { _, _ ->
                selectedCurrencies.clear()
                updateCurrencyButtonLabel()
                lifecycleScope.launch { loadRates() }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateCurrencyButtonLabel() {
        val label = when {
            selectedCurrencies.isEmpty() -> getString(R.string.exchange_rates_currencies_all)
            selectedCurrencies.size == 1 -> selectedCurrencies.first()
            else -> "${selectedCurrencies.size} currencies"
        }
        btnSelectCurrencies.text = getString(R.string.btn_select_currencies, label)
    }

    private fun showDatePicker(isStart: Boolean) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val cal = Calendar.getInstance().apply { set(year, month, day) }
                val dateKey = dateKeyFormat.format(cal.time)
                val dateDisplay = dateDisplayFormat.format(cal.time)
                if (isStart) {
                    startDate = dateKey
                    btnErStartDate.text = dateDisplay
                } else {
                    endDate = dateKey
                    btnErEndDate.text = dateDisplay
                }
                lifecycleScope.launch { loadRates() }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /**
     * Determines which (date, currency) pairs are missing for the currently filtered
     * date range and selected currencies, then downloads them from NBG API.
     */
    private fun downloadMissingRates() {
        val targetCurrencies = selectedCurrencies.takeIf { it.isNotEmpty() }
            ?: allCurrencies.filter { it != "GEL" }.toSet()

        val start = startDate ?: run {
            Toast.makeText(this, getString(R.string.start_date), Toast.LENGTH_SHORT).show()
            return
        }
        val end = endDate ?: dateKeyFormat.format(Date())

        lifecycleScope.launch {
            pbErLoading.visibility = View.VISIBLE
            btnDownloadMissing.isEnabled = false

            val existing = withContext(Dispatchers.IO) {
                exchangeRateDao.getByDateRange(start, end)
            }.map { it.date to it.currency }.toSet()

            // Generate all (date, currency) pairs in the range
            val allPairs = mutableListOf<Pair<String, String>>()
            val cal = Calendar.getInstance()
            cal.time = dateKeyFormat.parse(start) ?: Date()
            val endDate = dateKeyFormat.parse(end) ?: Date()
            while (!cal.time.after(endDate)) {
                val dateStr = dateKeyFormat.format(cal.time)
                for (currency in targetCurrencies) {
                    if (currency != "GEL" && (dateStr to currency) !in existing) {
                        allPairs.add(dateStr to currency)
                    }
                }
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }

            val failed = if (allPairs.isNotEmpty()) {
                ExchangeRateCache.prefetchRates(allPairs, exchangeRateDao)
            } else {
                emptyList()
            }

            pbErLoading.visibility = View.GONE
            btnDownloadMissing.isEnabled = true

            val fetched = allPairs.size - failed.size
            Toast.makeText(
                this@ExchangeRatesActivity,
                getString(R.string.download_missing_done, fetched, failed.size),
                Toast.LENGTH_LONG
            ).show()

            if (failed.isNotEmpty()) {
                NotificationHelper.sendExchangeRateErrorNotification(
                    this@ExchangeRatesActivity,
                    getString(R.string.exchange_rate_prefetch_error)
                )
            }

            loadRates()
        }
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    inner class ExchangeRateAdapter :
        ListAdapter<ExchangeRateEntity, ExchangeRateAdapter.VH>(RateDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_exchange_rate, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvErDate: TextView = itemView.findViewById(R.id.tvErDate)
            private val tvErCurrency: TextView = itemView.findViewById(R.id.tvErCurrency)
            private val tvErRate: TextView = itemView.findViewById(R.id.tvErRate)
            private val btnRefetchRate: Button = itemView.findViewById(R.id.btnRefetchRate)

            fun bind(entity: ExchangeRateEntity) {
                tvErDate.text = entity.date
                tvErCurrency.text = entity.currency
                tvErRate.text = "%.4f GEL".format(entity.rateToGel)
                btnRefetchRate.setOnClickListener { refetchRate(entity) }
            }
        }

        private fun refetchRate(entity: ExchangeRateEntity) {
            lifecycleScope.launch {
                pbErLoading.visibility = View.VISIBLE
                // Force re-fetch by deleting from DB and clearing memory cache entry
                withContext(Dispatchers.IO) {
                    exchangeRateDao.deleteRate(entity.date, entity.currency)
                }
                ExchangeRateCache.clearMemoryCache()
                val rate = ExchangeRateCache.getRateToGelForDate(entity.date, entity.currency, exchangeRateDao)
                pbErLoading.visibility = View.GONE
                if (rate == null) {
                    Toast.makeText(
                        this@ExchangeRatesActivity,
                        getString(R.string.exchange_rate_fetch_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
                loadRates()
            }
        }
    }

    private class RateDiffCallback : DiffUtil.ItemCallback<ExchangeRateEntity>() {
        override fun areItemsTheSame(old: ExchangeRateEntity, new: ExchangeRateEntity) =
            old.date == new.date && old.currency == new.currency
        override fun areContentsTheSame(old: ExchangeRateEntity, new: ExchangeRateEntity) = old == new
    }
}
