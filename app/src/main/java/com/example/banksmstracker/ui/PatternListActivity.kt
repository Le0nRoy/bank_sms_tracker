package com.example.banksmstracker.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.banksmstracker.R
import com.example.banksmstracker.database.BankSmsDatabase
import com.example.banksmstracker.database.RuleEntity
import com.example.banksmstracker.util.applyPlaceholderSpans
import com.example.banksmstracker.util.decodeNewlines
import com.example.banksmstracker.util.regexToTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PatternListActivity : BaseActivity() {

    private lateinit var rvPatterns: RecyclerView
    private lateinit var tvPatternListTitle: TextView
    private lateinit var tvNoPatterns: TextView

    private var senderId: Long = -1L
    private var senderName: String = ""
    private var ruleType: String = ""

    private val rules = mutableListOf<RuleEntity>()

    companion object {
        const val EXTRA_SENDER_ID = "extra_sender_id"
        const val EXTRA_SENDER_NAME = "extra_sender_name"
        const val EXTRA_RULE_TYPE = "extra_rule_type"
        const val EXTRA_SELECTED_PATTERN = "extra_selected_pattern"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pattern_list)

        senderId = intent.getLongExtra(EXTRA_SENDER_ID, -1L)
        senderName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: ""
        ruleType = intent.getStringExtra(EXTRA_RULE_TYPE) ?: ""

        initViews()
        loadPatterns()
    }

    private fun initViews() {
        rvPatterns = findViewById(R.id.rvPatterns)
        tvPatternListTitle = findViewById(R.id.tvPatternListTitle)
        tvNoPatterns = findViewById(R.id.tvNoPatterns)

        tvPatternListTitle.text = getString(R.string.pattern_list_title, senderName, ruleType)
        rvPatterns.layoutManager = LinearLayoutManager(this)
    }

    private fun loadPatterns() {
        if (senderId < 0) {
            showNoPatterns()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val loaded = withContext(Dispatchers.IO) {
                BankSmsDatabase.getInstance(this@PatternListActivity)
                    .ruleDao()
                    .getRulesBySenderAndType(senderId, ruleType)
            }
            rules.clear()
            rules.addAll(loaded)

            if (rules.isEmpty()) {
                showNoPatterns()
            } else {
                tvNoPatterns.visibility = View.GONE
                rvPatterns.visibility = View.VISIBLE
                rvPatterns.adapter = PatternAdapter(rules)
            }
        }
    }

    private fun showNoPatterns() {
        tvNoPatterns.visibility = View.VISIBLE
        rvPatterns.visibility = View.GONE
    }

    private fun onLoadPattern(rule: RuleEntity) {
        val resultIntent = Intent().apply {
            putExtra(EXTRA_SELECTED_PATTERN, rule.pattern)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun onDeletePattern(rule: RuleEntity, position: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_pattern_confirm_title)
            .setMessage(R.string.delete_pattern_confirm_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                performDeletePattern(rule, position)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performDeletePattern(rule: RuleEntity, position: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    BankSmsDatabase.getInstance(this@PatternListActivity)
                        .ruleDao()
                        .deleteRuleById(rule.id)
                }
                rules.removeAt(position)
                rvPatterns.adapter?.notifyItemRemoved(position)
                if (rules.isEmpty()) showNoPatterns()
                Toast.makeText(this@PatternListActivity, R.string.pattern_deleted, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@PatternListActivity, R.string.error_generic, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatPatternForDisplay(rawPattern: String): SpannableStringBuilder {
        val decoded = decodePattern(rawPattern)
        val template = decodeNewlines(regexToTemplate(decoded))
        val spannable = SpannableStringBuilder(template)
        applyPlaceholderSpans(spannable, this)
        return spannable
    }

    private fun decodePattern(stored: String): String = stored.replace("\\s", " ")

    inner class PatternAdapter(private val items: MutableList<RuleEntity>) :
        RecyclerView.Adapter<PatternAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvPatternText: TextView = itemView.findViewById(R.id.tvPatternText)
            val btnLoad: Button = itemView.findViewById(R.id.btnLoadPattern)
            val btnDelete: Button = itemView.findViewById(R.id.btnDeletePattern)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pattern, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val rule = items[position]
            holder.tvPatternText.text = formatPatternForDisplay(rule.pattern)
            holder.btnLoad.setOnClickListener { onLoadPattern(rule) }
            holder.btnDelete.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) onDeletePattern(rule, pos)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
