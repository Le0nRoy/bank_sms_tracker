package com.example.banksmstracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.banksmstracker.R
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.database.IncomeEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IncomesActivity : BaseActivity() {

    private lateinit var recyclerIncomes: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var tvIncomeTotal: TextView

    private val adapter = IncomeAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incomes)

        recyclerIncomes = findViewById(R.id.recyclerIncomes)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        tvIncomeTotal = findViewById(R.id.tvIncomeTotal)

        recyclerIncomes.layoutManager = LinearLayoutManager(this)
        recyclerIncomes.adapter = adapter

        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            val incomes = withContext(Dispatchers.IO) {
                BankSmsDatabase.getInstance(this@IncomesActivity).incomeDao().getAllIncomes()
            }

            adapter.submitList(incomes)

            if (incomes.isEmpty()) {
                tvEmptyState.visibility = View.VISIBLE
                recyclerIncomes.visibility = View.GONE
                tvIncomeTotal.text = getString(R.string.income_total_label, "0.00", "")
            } else {
                tvEmptyState.visibility = View.GONE
                recyclerIncomes.visibility = View.VISIBLE
                val total = incomes.sumOf { it.amount }
                val currency = incomes.first().currency
                tvIncomeTotal.text = getString(R.string.income_total_label, "%.2f".format(total), currency)
            }
        }
    }

    private class IncomeDiffCallback : DiffUtil.ItemCallback<IncomeEntity>() {
        override fun areItemsTheSame(oldItem: IncomeEntity, newItem: IncomeEntity): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: IncomeEntity, newItem: IncomeEntity): Boolean = oldItem == newItem
    }

    inner class IncomeAdapter : ListAdapter<IncomeEntity, IncomeAdapter.IncomeViewHolder>(IncomeDiffCallback()) {

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IncomeViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_income, parent, false)
            return IncomeViewHolder(view)
        }

        override fun onBindViewHolder(holder: IncomeViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class IncomeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvSource: TextView = itemView.findViewById(R.id.tvSource)
            private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
            private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)

            fun bind(income: IncomeEntity) {
                tvSource.text = income.source ?: getString(R.string.income_unknown_source)
                tvAmount.text = "+${"%.2f".format(income.amount)} ${income.currency}"

                val displayDate = income.timestamp
                    ?: income.receivedAt?.let { dateFormat.format(Date(it)) }
                tvTimestamp.text = displayDate ?: ""
            }
        }
    }
}
