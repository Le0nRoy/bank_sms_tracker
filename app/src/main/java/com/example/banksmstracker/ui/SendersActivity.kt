package com.example.banksmstracker.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.banksmstracker.R
import com.example.banksmstracker.data.Rule
import com.example.banksmstracker.data.RuleType
import com.example.banksmstracker.data.Sender
import com.example.banksmstracker.repository.ConfigRepository
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

class SendersActivity :
    BaseActivity(),
    SendersAdapter.SenderCallbacks {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SendersAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var errorStateView: LinearLayout
    private lateinit var btnRetry: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_senders)

        setupViews()
        setupRecyclerView()
        loadSenders()

        findViewById<FloatingActionButton>(R.id.fabAddSender).setOnClickListener {
            lifecycleScope.launch {
                val sender = ConfigRepository.addSender()
                adapter.addSender(sender.clone())
                updateEmptyState()
            }
        }
    }

    override fun onSenderDeleteRequested(sender: Sender, position: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_sender_confirm_title)
            .setMessage(R.string.delete_sender_confirm_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteSender(sender, position)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteSender(sender: Sender, position: Int) {
        lifecycleScope.launch {
            try {
                val senderId = sender.id ?: return@launch
                ConfigRepository.deleteSender(senderId)
                adapter.removeSender(position)
                updateEmptyState()
                Toast.makeText(
                    this@SendersActivity,
                    R.string.sender_deleted,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@SendersActivity,
                    getString(R.string.error_generic),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupViews() {
        progressBar = findViewById(R.id.progressBar)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        errorStateView = findViewById(R.id.errorStateView)
        btnRetry = findViewById(R.id.btnRetry)
        btnRetry.setOnClickListener { loadSenders() }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewSenders)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SendersAdapter(this)
        recyclerView.adapter = adapter
    }

    private fun loadSenders() {
        lifecycleScope.launch {
            showLoadingState()
            try {
                val senders = ConfigRepository.getSenders()
                    .map { it.clone() }
                    .toMutableList()
                adapter.submitList(senders)
                showContentState()
            } catch (e: Exception) {
                showErrorState(getString(R.string.error_loading_senders))
            }
        }
    }

    private fun showLoadingState() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvEmptyState.visibility = View.GONE
        errorStateView.visibility = View.GONE
    }

    private fun showContentState() {
        progressBar.visibility = View.GONE
        errorStateView.visibility = View.GONE
        updateEmptyState()
    }

    private fun showErrorState(message: String) {
        progressBar.visibility = View.GONE
        recyclerView.visibility = View.GONE
        tvEmptyState.visibility = View.GONE
        errorStateView.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvErrorMessage).text = message
    }

    private fun updateEmptyState() {
        if (adapter.itemCount == 0) {
            recyclerView.visibility = View.GONE
            tvEmptyState.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            tvEmptyState.visibility = View.GONE
        }
    }

    override fun onSenderUpdated(sender: Sender) {
        lifecycleScope.launch {
            ConfigRepository.updateSender(sender)
        }
    }
}

class SendersAdapter(private val callbacks: SenderCallbacks) : RecyclerView.Adapter<SendersAdapter.SenderViewHolder>() {

    interface SenderCallbacks {
        fun onSenderUpdated(sender: Sender)
        fun onSenderDeleteRequested(sender: Sender, position: Int)
    }

    private val senders: MutableList<Sender> = mutableListOf()

    fun submitList(newSenders: MutableList<Sender>) {
        senders.clear()
        senders.addAll(newSenders)
        notifyDataSetChanged()
    }

    fun addSender(sender: Sender) {
        senders.add(sender)
        notifyItemInserted(senders.lastIndex)
    }

    fun removeSender(position: Int) {
        if (position in senders.indices) {
            senders.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, senders.size)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SenderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sender, parent, false)
        return SenderViewHolder(view)
    }

    override fun onBindViewHolder(holder: SenderViewHolder, position: Int) {
        holder.bind(senders[position], position, callbacks)
    }

    override fun getItemCount(): Int = senders.size

    class SenderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val nameEditText: EditText = itemView.findViewById(R.id.senderNameEditText)
        private val switchSenderEnabled: Switch = itemView.findViewById(R.id.switchSenderEnabled)
        private val btnDeleteSender: android.widget.ImageButton = itemView.findViewById(R.id.btnDeleteSender)
        private val addressesContainer: LinearLayout = itemView.findViewById(R.id.addressesContainer)
        private val btnAddAddress: Button = itemView.findViewById(R.id.btnAddAddress)
        private val rulesContainer: LinearLayout = itemView.findViewById(R.id.rulesContainer)
        private val btnAddRule: Button = itemView.findViewById(R.id.btnAddRule)
        private val bindingInProgress = AtomicBoolean(false)

        fun bind(sender: Sender, position: Int, callbacks: SenderCallbacks) {
            bindingInProgress.set(true)
            if (nameEditText.text.toString() != sender.name) {
                nameEditText.setText(sender.name)
            }
            switchSenderEnabled.isChecked = sender.enabled
            bindingInProgress.set(false)

            nameEditText.setSimpleWatcher { newValue ->
                if (bindingInProgress.get().not() && sender.name != newValue) {
                    sender.name = newValue
                    callbacks.onSenderUpdated(sender)
                }
            }

            switchSenderEnabled.setOnCheckedChangeListener { _, isChecked ->
                if (!bindingInProgress.get() && sender.enabled != isChecked) {
                    sender.enabled = isChecked
                    callbacks.onSenderUpdated(sender)
                }
            }

            btnDeleteSender.setOnClickListener {
                callbacks.onSenderDeleteRequested(sender, position)
            }

            addressesContainer.removeAllViews()
            sender.addresses.forEachIndexed { index, address ->
                addAddressField(index, address, sender, callbacks)
            }
            btnAddAddress.setOnClickListener {
                sender.addresses.add("")
                callbacks.onSenderUpdated(sender)
                addAddressField(sender.addresses.lastIndex, "", sender, callbacks)
            }

            rulesContainer.removeAllViews()
            sender.rules.forEachIndexed { index, rule ->
                addRuleField(index, rule, sender, callbacks)
            }
            btnAddRule.setOnClickListener {
                sender.rules.add(Rule(pattern = "", ruleType = RuleType.PAYMENT))
                callbacks.onSenderUpdated(sender)
                addRuleField(sender.rules.lastIndex, sender.rules.last(), sender, callbacks)
            }
        }

        private fun addAddressField(index: Int, value: String, sender: Sender, callbacks: SenderCallbacks) {
            val view = LayoutInflater.from(itemView.context)
                .inflate(R.layout.view_dynamic_edit_text_with_delete, addressesContainer, false)
            val editText: EditText = view.findViewById(R.id.etValue)
            val btnDelete: android.widget.ImageButton = view.findViewById(R.id.btnDelete)

            editText.hint = itemView.context.getString(R.string.sender_address_hint, index + 1)
            editText.setText(value)
            editText.setSimpleWatcher { newValue ->
                if (index in sender.addresses.indices && sender.addresses[index] != newValue) {
                    sender.addresses[index] = newValue
                    callbacks.onSenderUpdated(sender)
                }
            }

            btnDelete.setOnClickListener {
                if (index in sender.addresses.indices) {
                    sender.addresses.removeAt(index)
                    callbacks.onSenderUpdated(sender)
                    addressesContainer.removeView(view)
                    refreshAddressFields(sender, callbacks)
                }
            }

            addressesContainer.addView(view)
        }

        private fun refreshAddressFields(sender: Sender, callbacks: SenderCallbacks) {
            addressesContainer.removeAllViews()
            sender.addresses.forEachIndexed { index, address ->
                addAddressField(index, address, sender, callbacks)
            }
        }

        private fun addRuleField(index: Int, rule: Rule, sender: Sender, callbacks: SenderCallbacks) {
            val ruleView = LayoutInflater.from(itemView.context)
                .inflate(R.layout.view_rule_with_toggle, rulesContainer, false)
            val editText: EditText = ruleView.findViewById(R.id.etRuleRegex)
            val switchEnabled: Switch = ruleView.findViewById(R.id.switchRuleEnabled)
            val btnDeleteRule: android.widget.ImageButton = ruleView.findViewById(R.id.btnDeleteRule)
            val spinnerRuleType: android.widget.Spinner = ruleView.findViewById(R.id.spinnerRuleType)

            editText.hint = itemView.context.getString(R.string.sender_rule_hint, index + 1)
            editText.setText(rule.pattern)
            switchEnabled.isChecked = rule.enabled

            // Set up rule type spinner
            val ruleTypeLabels = listOf(
                itemView.context.getString(R.string.rule_type_payment),
                itemView.context.getString(R.string.rule_type_ignore_short),
                itemView.context.getString(R.string.rule_type_income)
            )
            val ruleTypes = listOf(RuleType.PAYMENT, RuleType.IGNORE, RuleType.INCOME)
            val adapter = android.widget.ArrayAdapter(
                itemView.context,
                android.R.layout.simple_spinner_item,
                ruleTypeLabels
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerRuleType.adapter = adapter
            spinnerRuleType.setSelection(ruleTypes.indexOf(rule.ruleType).coerceAtLeast(0))

            spinnerRuleType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    if (index in sender.rules.indices && sender.rules[index].ruleType != ruleTypes[position]) {
                        sender.rules[index].ruleType = ruleTypes[position]
                        callbacks.onSenderUpdated(sender)
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

            editText.setSimpleWatcher { newValue ->
                if (index in sender.rules.indices && sender.rules[index].pattern != newValue) {
                    sender.rules[index].pattern = newValue
                    callbacks.onSenderUpdated(sender)
                }
            }

            switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                if (index in sender.rules.indices && sender.rules[index].enabled != isChecked) {
                    sender.rules[index].enabled = isChecked
                    callbacks.onSenderUpdated(sender)
                }
            }

            btnDeleteRule.setOnClickListener {
                if (index in sender.rules.indices) {
                    sender.rules.removeAt(index)
                    callbacks.onSenderUpdated(sender)
                    rulesContainer.removeView(ruleView)
                    refreshRuleFields(sender, callbacks)
                }
            }

            rulesContainer.addView(ruleView)
        }

        private fun refreshRuleFields(sender: Sender, callbacks: SenderCallbacks) {
            rulesContainer.removeAllViews()
            sender.rules.forEachIndexed { index, rule ->
                addRuleField(index, rule, sender, callbacks)
            }
        }
    }
}

private fun Sender.clone(): Sender = Sender(
    id = id,
    name = name,
    addresses = addresses.toMutableList(),
    rules = rules.map { it.copy() }.toMutableList(),
    enabled = enabled
)
