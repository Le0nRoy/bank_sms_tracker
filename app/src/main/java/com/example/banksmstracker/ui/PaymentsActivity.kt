package com.example.banksmstracker.ui

import android.content.Intent
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
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.banksmstracker.R
import com.example.banksmstracker.data.Payment
import com.example.banksmstracker.repository.ConfigRepository
import com.example.banksmstracker.repository.RoomPaymentRepository
import java.io.File
import java.text.SimpleDateFormat
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
    private lateinit var btnExportCsv: Button

    private lateinit var paymentRepository: RoomPaymentRepository
    private var allPayments: List<Payment> = emptyList()
    private var filteredPayments: List<Payment> = emptyList()
    private var categories: List<String> = emptyList()
    private var selectedCategory: String? = null

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
        btnExportCsv = findViewById(R.id.btnExportCsv)

        btnExportCsv.setOnClickListener {
            exportToCsv()
        }
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

            setupCategorySpinner()
            applyFilter()
        }
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

    private fun applyFilter() {
        filteredPayments = if (selectedCategory == null) {
            allPayments
        } else {
            allPayments.filter { it.categoryId == selectedCategory }
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
        sb.appendLine("Amount,Currency,Card,Merchant,Timestamp,Balance,Category")
        // Data
        for (payment in payments) {
            sb.appendLine(
                listOf(
                    payment.amount.toString(),
                    escapeCsv(payment.currency),
                    escapeCsv(payment.card ?: ""),
                    escapeCsv(payment.merchant ?: ""),
                    escapeCsv(payment.timestamp ?: ""),
                    payment.balance?.toString() ?: "",
                    escapeCsv(payment.categoryId ?: "")
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
            }
        }
    }
}
