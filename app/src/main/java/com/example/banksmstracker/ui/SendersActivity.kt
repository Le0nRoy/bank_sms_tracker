package com.example.banksmstracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.banksmstracker.R
import com.example.banksmstracker.data.PaymentRegexRule
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_senders)

        setupRecyclerView()
        loadSenders()

        findViewById<FloatingActionButton>(R.id.fabAddSender).setOnClickListener {
            lifecycleScope.launch {
                val sender = ConfigRepository.addSender()
                adapter.addSender(sender.clone())
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewSenders)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SendersAdapter(this)
        recyclerView.adapter = adapter
    }

    private fun loadSenders() {
        lifecycleScope.launch {
            val senders = ConfigRepository.getSenders()
                .map { it.clone() }
                .toMutableList()
            adapter.submitList(senders)
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SenderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sender, parent, false)
        return SenderViewHolder(view)
    }

    override fun onBindViewHolder(holder: SenderViewHolder, position: Int) {
        holder.bind(senders[position], callbacks)
    }

    override fun getItemCount(): Int = senders.size

    class SenderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val nameEditText: EditText = itemView.findViewById(R.id.senderNameEditText)
        private val switchSenderEnabled: Switch = itemView.findViewById(R.id.switchSenderEnabled)
        private val addressesContainer: LinearLayout = itemView.findViewById(R.id.addressesContainer)
        private val btnAddAddress: Button = itemView.findViewById(R.id.btnAddAddress)
        private val rulesContainer: LinearLayout = itemView.findViewById(R.id.rulesContainer)
        private val btnAddRule: Button = itemView.findViewById(R.id.btnAddRule)
        private val bindingInProgress = AtomicBoolean(false)

        fun bind(sender: Sender, callbacks: SenderCallbacks) {
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
                sender.rules.add(PaymentRegexRule(regex = ""))
                callbacks.onSenderUpdated(sender)
                addRuleField(sender.rules.lastIndex, sender.rules.last(), sender, callbacks)
            }
        }

        private fun addAddressField(index: Int, value: String, sender: Sender, callbacks: SenderCallbacks) {
            val editText = LayoutInflater.from(itemView.context)
                .inflate(R.layout.view_dynamic_edit_text, addressesContainer, false) as EditText
            editText.hint = itemView.context.getString(R.string.sender_address_hint, index + 1)
            editText.setText(value)
            editText.setSimpleWatcher { newValue ->
                if (index in sender.addresses.indices && sender.addresses[index] != newValue) {
                    sender.addresses[index] = newValue
                    callbacks.onSenderUpdated(sender)
                }
            }
            addressesContainer.addView(editText)
        }

        private fun addRuleField(index: Int, rule: PaymentRegexRule, sender: Sender, callbacks: SenderCallbacks) {
            val ruleView = LayoutInflater.from(itemView.context)
                .inflate(R.layout.view_rule_with_toggle, rulesContainer, false)
            val editText: EditText = ruleView.findViewById(R.id.etRuleRegex)
            val switchEnabled: Switch = ruleView.findViewById(R.id.switchRuleEnabled)

            editText.hint = itemView.context.getString(R.string.sender_rule_hint, index + 1)
            editText.setText(rule.regex)
            switchEnabled.isChecked = rule.enabled

            editText.setSimpleWatcher { newValue ->
                if (index in sender.rules.indices && sender.rules[index].regex != newValue) {
                    sender.rules[index].regex = newValue
                    callbacks.onSenderUpdated(sender)
                }
            }

            switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                if (index in sender.rules.indices && sender.rules[index].enabled != isChecked) {
                    sender.rules[index].enabled = isChecked
                    callbacks.onSenderUpdated(sender)
                }
            }

            rulesContainer.addView(ruleView)
        }
    }
}

private fun Sender.clone(): Sender = Sender(
    id = id,
    name = name,
    addresses = addresses.toMutableList(),
    rules = rules.map { PaymentRegexRule(id = it.id, regex = it.regex, enabled = it.enabled) }.toMutableList(),
    enabled = enabled
)
