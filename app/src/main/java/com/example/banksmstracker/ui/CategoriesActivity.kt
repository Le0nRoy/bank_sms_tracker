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
import com.example.banksmstracker.data.Category
import com.example.banksmstracker.repository.ConfigRepository
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CategoriesActivity :
    BaseActivity(),
    CategoriesAdapter.CategoryCallbacks {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CategoriesAdapter
    private lateinit var btnRecategorize: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var errorStateView: LinearLayout
    private lateinit var btnRetry: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categories)

        setupViews()
        setupRecyclerView()
        setupRecategorizeButton()
        loadCategories()

        findViewById<FloatingActionButton>(R.id.fabAddCategory).setOnClickListener {
            lifecycleScope.launch {
                val newCategory = ConfigRepository.addCategory()
                adapter.addCategory(newCategory.clone())
                updateEmptyState()
            }
        }
    }

    private fun setupViews() {
        progressBar = findViewById(R.id.progressBar)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        errorStateView = findViewById(R.id.errorStateView)
        btnRetry = findViewById(R.id.btnRetry)
        btnRetry.setOnClickListener { loadCategories() }
    }

    private fun setupRecategorizeButton() {
        btnRecategorize = findViewById(R.id.btnRecategorize)
        btnRecategorize.setOnClickListener {
            showRecategorizeConfirmation()
        }
    }

    private fun showRecategorizeConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.recategorize_confirm_title)
            .setMessage(R.string.recategorize_confirm_message)
            .setPositiveButton(R.string.confirm) { _, _ ->
                performRecategorize()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performRecategorize() {
        lifecycleScope.launch {
            btnRecategorize.isEnabled = false
            progressBar.visibility = View.VISIBLE
            try {
                val count = withContext(Dispatchers.IO) {
                    ConfigRepository.recategorizeAllPayments()
                }
                Toast.makeText(
                    this@CategoriesActivity,
                    getString(R.string.recategorize_success, count),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@CategoriesActivity,
                    getString(R.string.recategorize_failed, e.message ?: "Unknown error"),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                btnRecategorize.isEnabled = true
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewCategories)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CategoriesAdapter(this)
        recyclerView.adapter = adapter
    }

    private fun loadCategories() {
        lifecycleScope.launch {
            showLoadingState()
            try {
                val categories = ConfigRepository.getCategories()
                    .map { it.clone() }
                    .toMutableList()
                adapter.submitList(categories)
                showContentState()
            } catch (e: Exception) {
                showErrorState(getString(R.string.error_loading_categories))
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

    override fun onCategoryUpdated(category: Category) {
        lifecycleScope.launch {
            ConfigRepository.updateCategory(category)
        }
    }
}

class CategoriesAdapter(private val callbacks: CategoryCallbacks) :
    RecyclerView.Adapter<CategoriesAdapter.CategoryViewHolder>() {

    interface CategoryCallbacks {
        fun onCategoryUpdated(category: Category)
    }

    private val categories: MutableList<Category> = mutableListOf()

    fun submitList(newCategories: MutableList<Category>) {
        categories.clear()
        categories.addAll(newCategories)
        notifyDataSetChanged()
    }

    fun addCategory(category: Category) {
        categories.add(category)
        notifyItemInserted(categories.lastIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position], callbacks)
    }

    override fun getItemCount(): Int = categories.size

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val nameEditText: EditText = itemView.findViewById(R.id.nameEditText)
        private val switchEnabled: Switch = itemView.findViewById(R.id.switchEnabled)
        private val merchantsContainer: LinearLayout = itemView.findViewById(R.id.merchantsContainer)
        private val btnAddMerchant: Button = itemView.findViewById(R.id.btnAddMerchant)
        private val bindingInProgress = AtomicBoolean(false)

        fun bind(category: Category, callbacks: CategoryCallbacks) {
            bindingInProgress.set(true)
            if (nameEditText.text.toString() != category.name) {
                nameEditText.setText(category.name)
            }
            switchEnabled.isChecked = category.enabled
            bindingInProgress.set(false)

            nameEditText.setSimpleWatcher { newValue ->
                if (!bindingInProgress.get() && category.name != newValue) {
                    category.name = newValue
                    callbacks.onCategoryUpdated(category)
                }
            }

            switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                if (!bindingInProgress.get() && category.enabled != isChecked) {
                    category.enabled = isChecked
                    callbacks.onCategoryUpdated(category)
                }
            }

            merchantsContainer.removeAllViews()
            category.merchants.forEachIndexed { index, merchant ->
                addMerchantField(index, merchant, category, callbacks)
            }

            btnAddMerchant.setOnClickListener {
                category.merchants.add("")
                callbacks.onCategoryUpdated(category)
                addMerchantField(category.merchants.lastIndex, "", category, callbacks)
            }
        }

        private fun addMerchantField(index: Int, value: String, category: Category, callbacks: CategoryCallbacks) {
            val editText = LayoutInflater.from(itemView.context)
                .inflate(R.layout.view_dynamic_edit_text, merchantsContainer, false) as EditText
            editText.hint = itemView.context.getString(R.string.merchant_hint, index + 1)
            editText.setText(value)
            editText.setSimpleWatcher { newValue ->
                if (index in category.merchants.indices && category.merchants[index] != newValue) {
                    category.merchants[index] = newValue
                    callbacks.onCategoryUpdated(category)
                }
            }
            merchantsContainer.addView(editText)
        }
    }
}

private fun Category.clone(): Category = Category(
    id = id,
    name = name,
    merchants = merchants.toMutableList(),
    enabled = enabled
)
