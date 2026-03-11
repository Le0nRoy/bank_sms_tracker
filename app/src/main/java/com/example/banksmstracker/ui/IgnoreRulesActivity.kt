package com.example.banksmstracker.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.banksmstracker.R
import com.example.banksmstracker.data.IgnoreRule
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.database.IgnoreRuleDao
import com.example.banksmstracker.database.IgnoreRuleEntity
import com.example.banksmstracker.repository.ConfigRepository
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IgnoreRulesActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var spinnerFilterSender: Spinner
    private lateinit var ignoreRuleDao: IgnoreRuleDao
    private lateinit var adapter: IgnoreRuleAdapter

    private var ignoreRules: List<IgnoreRule> = emptyList()
    private var senders: List<Sender> = emptyList()
    private var selectedFilterSenderId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ignore_rules)

        initViews()
        setupRecyclerView()
        loadData()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerIgnoreRules)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        fabAdd = findViewById(R.id.fabAddIgnoreRule)
        spinnerFilterSender = findViewById(R.id.spinnerFilterSender)

        val database = BankSmsDatabase.getInstance(this)
        ignoreRuleDao = database.ignoreRuleDao()

        fabAdd.setOnClickListener {
            if (senders.isEmpty()) {
                Toast.makeText(this, R.string.no_senders_available, Toast.LENGTH_SHORT).show()
            } else {
                showAddEditDialog(null)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = IgnoreRuleAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadData() {
        lifecycleScope.launch {
            // Load senders
            ConfigRepository.load(application)
            senders = ConfigRepository.getSenders()

            // Setup filter spinner
            setupFilterSpinner()

            // Load ignore rules
            loadIgnoreRules()
        }
    }

    private fun setupFilterSpinner() {
        val senderNames = mutableListOf(getString(R.string.ignore_rules_filter_all))
        senderNames.addAll(senders.map { it.name })

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, senderNames)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilterSender.adapter = spinnerAdapter

        spinnerFilterSender.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedFilterSenderId = if (position == 0) null else senders.getOrNull(position - 1)?.id
                loadIgnoreRules()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedFilterSenderId = null
            }
        }
    }

    private fun loadIgnoreRules() {
        lifecycleScope.launch {
            ignoreRules = withContext(Dispatchers.IO) {
                val entities = if (selectedFilterSenderId != null) {
                    ignoreRuleDao.getIgnoreRulesBySender(selectedFilterSenderId!!)
                } else {
                    ignoreRuleDao.getAllIgnoreRules()
                }
                entities.map { entity ->
                    IgnoreRule(
                        id = entity.id,
                        senderId = entity.senderId,
                        pattern = entity.pattern,
                        description = entity.description,
                        enabled = entity.enabled
                    )
                }
            }

            adapter.submitList(ignoreRules)
            updateEmptyState()
        }
    }

    private fun updateEmptyState() {
        if (ignoreRules.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showAddEditDialog(rule: IgnoreRule?) {
        val isEdit = rule != null
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ignore_rule, null)

        val spinnerSender = dialogView.findViewById<Spinner>(R.id.spinnerSender)
        val etPattern = dialogView.findViewById<EditText>(R.id.etPattern)
        val etDescription = dialogView.findViewById<EditText>(R.id.etDescription)

        // Setup sender spinner
        val senderNames = senders.map { it.name }
        val senderAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, senderNames)
        senderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSender.adapter = senderAdapter

        if (isEdit) {
            etPattern.setText(rule!!.pattern)
            etDescription.setText(rule.description ?: "")
            // Select the sender
            val senderIndex = senders.indexOfFirst { it.id == rule.senderId }
            if (senderIndex >= 0) {
                spinnerSender.setSelection(senderIndex)
            }
        } else if (selectedFilterSenderId != null) {
            // Pre-select current filter sender
            val senderIndex = senders.indexOfFirst { it.id == selectedFilterSenderId }
            if (senderIndex >= 0) {
                spinnerSender.setSelection(senderIndex)
            }
        }

        AlertDialog.Builder(this)
            .setTitle(if (isEdit) R.string.edit_ignore_rule else R.string.add_ignore_rule)
            .setView(dialogView)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val selectedSenderIndex = spinnerSender.selectedItemPosition
                if (selectedSenderIndex < 0 || selectedSenderIndex >= senders.size) {
                    Toast.makeText(this, R.string.sender_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val selectedSenderId = senders[selectedSenderIndex].id ?: return@setPositiveButton
                val pattern = etPattern.text.toString().trim()
                val description = etDescription.text.toString().trim().ifEmpty { null }

                if (pattern.isNotEmpty()) {
                    // Validate regex
                    try {
                        Regex(pattern)
                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            getString(R.string.invalid_regex_pattern, e.message),
                            Toast.LENGTH_LONG
                        ).show()
                        return@setPositiveButton
                    }

                    if (isEdit) {
                        updateIgnoreRule(
                            rule!!.copy(senderId = selectedSenderId, pattern = pattern, description = description)
                        )
                    } else {
                        addIgnoreRule(selectedSenderId, pattern, description)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addIgnoreRule(senderId: Long, pattern: String, description: String?) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                ignoreRuleDao.insertIgnoreRule(
                    IgnoreRuleEntity(senderId = senderId, pattern = pattern, description = description)
                )
            }
            loadIgnoreRules()
            Toast.makeText(this@IgnoreRulesActivity, R.string.ignore_rule_added, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateIgnoreRule(rule: IgnoreRule) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                ignoreRuleDao.updateIgnoreRule(
                    IgnoreRuleEntity(
                        id = rule.id ?: 0,
                        senderId = rule.senderId,
                        pattern = rule.pattern,
                        description = rule.description,
                        enabled = rule.enabled
                    )
                )
            }
            loadIgnoreRules()
        }
    }

    private fun deleteIgnoreRule(rule: IgnoreRule) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_ignore_rule)
            .setMessage(R.string.delete_ignore_rule_confirm)
            .setPositiveButton(R.string.confirm) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        rule.id?.let { ignoreRuleDao.deleteIgnoreRuleById(it) }
                    }
                    loadIgnoreRules()
                    Toast.makeText(this@IgnoreRulesActivity, R.string.ignore_rule_deleted, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun getSenderName(senderId: Long): String = senders.find {
        it.id == senderId
    }?.name ?: getString(R.string.unknown)

    inner class IgnoreRuleAdapter : RecyclerView.Adapter<IgnoreRuleAdapter.ViewHolder>() {

        private var rules: List<IgnoreRule> = emptyList()

        fun submitList(list: List<IgnoreRule>) {
            rules = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ignore_rule, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(rules[position])
        }

        override fun getItemCount(): Int = rules.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvPattern: TextView = itemView.findViewById(R.id.tvPattern)
            private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
            private val switchEnabled: Switch = itemView.findViewById(R.id.switchEnabled)
            private val btnDelete: Button = itemView.findViewById(R.id.btnDelete)

            fun bind(rule: IgnoreRule) {
                tvPattern.text = rule.pattern
                // Show sender name in description if viewing all senders
                val senderPrefix = if (selectedFilterSenderId == null) {
                    "[${getSenderName(rule.senderId)}] "
                } else {
                    ""
                }
                val descText = senderPrefix + (rule.description ?: "")
                tvDescription.text = descText
                tvDescription.visibility = if (descText.isEmpty()) View.GONE else View.VISIBLE
                switchEnabled.isChecked = rule.enabled

                switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                    updateIgnoreRule(rule.copy(enabled = isChecked))
                }

                itemView.setOnClickListener {
                    showAddEditDialog(rule)
                }

                btnDelete.setOnClickListener {
                    deleteIgnoreRule(rule)
                }
            }
        }
    }
}
