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
import com.example.banksmstracker.data.Merchant
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

    override fun onCategoryDeleteRequested(category: Category, position: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_category_confirm_title)
            .setMessage(R.string.delete_category_confirm_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteCategory(category, position)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteCategory(category: Category, position: Int) {
        lifecycleScope.launch {
            try {
                val categoryId = category.id ?: return@launch
                ConfigRepository.deleteCategory(categoryId)
                adapter.removeCategory(position)
                updateEmptyState()
                Toast.makeText(
                    this@CategoriesActivity,
                    R.string.category_deleted,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@CategoriesActivity,
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

    override fun onMoveMerchantRequested(merchant: Merchant, currentCategory: Category) {
        lifecycleScope.launch {
            val allCategories = ConfigRepository.getCategories()
                .filter { it.id != currentCategory.id }
            if (allCategories.isEmpty()) {
                Toast.makeText(this@CategoriesActivity, R.string.no_other_categories, Toast.LENGTH_SHORT).show()
                return@launch
            }
            showMoveMerchantDialog(merchant, currentCategory, allCategories)
        }
    }

    private fun showMoveMerchantDialog(
        merchant: Merchant,
        currentCategory: Category,
        targetCategories: List<Category>
    ) {
        val displayName = merchant.displayName ?: merchant.pattern
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.move_merchant_dialog_title, displayName))
            .setItems(targetCategories.map { it.name }.toTypedArray()) { _, idx ->
                moveMerchantToCategory(merchant, currentCategory, targetCategories[idx])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun moveMerchantToCategory(merchant: Merchant, currentCategory: Category, targetCategory: Category) {
        lifecycleScope.launch {
            try {
                currentCategory.merchants.removeAll { it.pattern == merchant.pattern }
                targetCategory.merchants.add(merchant)
                ConfigRepository.updateCategory(currentCategory)
                ConfigRepository.updateCategory(targetCategory)
                val displayName = merchant.displayName ?: merchant.pattern
                Toast.makeText(
                    this@CategoriesActivity,
                    getString(R.string.merchant_moved, displayName, targetCategory.name),
                    Toast.LENGTH_SHORT
                ).show()
                loadCategories()
            } catch (e: Exception) {
                Toast.makeText(
                    this@CategoriesActivity,
                    getString(R.string.error_generic),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

class CategoriesAdapter(private val callbacks: CategoryCallbacks) :
    RecyclerView.Adapter<CategoriesAdapter.CategoryViewHolder>() {

    interface CategoryCallbacks {
        fun onCategoryUpdated(category: Category)
        fun onCategoryDeleteRequested(category: Category, position: Int)
        fun onMoveMerchantRequested(merchant: Merchant, currentCategory: Category)
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

    fun removeCategory(position: Int) {
        if (position in categories.indices) {
            categories.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, categories.size)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position], position, callbacks)
    }

    override fun getItemCount(): Int = categories.size

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val nameEditText: EditText = itemView.findViewById(R.id.nameEditText)
        private val switchEnabled: Switch = itemView.findViewById(R.id.switchEnabled)
        private val btnDeleteCategory: android.widget.ImageButton = itemView.findViewById(R.id.btnDeleteCategory)
        private val merchantsContainer: LinearLayout = itemView.findViewById(R.id.merchantsContainer)
        private val btnAddMerchant: Button = itemView.findViewById(R.id.btnAddMerchant)
        private val bindingInProgress = AtomicBoolean(false)

        fun bind(category: Category, position: Int, callbacks: CategoryCallbacks) {
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

            btnDeleteCategory.setOnClickListener {
                callbacks.onCategoryDeleteRequested(category, position)
            }

            merchantsContainer.removeAllViews()
            category.merchants.forEachIndexed { index, merchant ->
                addMerchantField(index, merchant, category, callbacks)
            }

            btnAddMerchant.setOnClickListener {
                category.merchants.add(Merchant(pattern = ""))
                callbacks.onCategoryUpdated(category)
                addMerchantField(category.merchants.lastIndex, Merchant(pattern = ""), category, callbacks)
            }
        }

        private fun addMerchantField(index: Int, merchant: Merchant, category: Category, callbacks: CategoryCallbacks) {
            val ctx = itemView.context

            // Wrapper holds both the pattern row and the display-name field below it.
            val wrapper = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            // Pattern row (existing layout: EditText + Move + Delete buttons)
            val view = LayoutInflater.from(ctx)
                .inflate(R.layout.view_dynamic_edit_text_with_delete, wrapper, false)
            val editText: EditText = view.findViewById(R.id.etValue)
            val btnMove: android.widget.ImageButton = view.findViewById(R.id.btnMove)
            val btnDelete: android.widget.ImageButton = view.findViewById(R.id.btnDelete)

            editText.hint = ctx.getString(R.string.merchant_hint, index + 1)
            editText.setText(merchant.pattern)
            editText.setSimpleWatcher { newValue ->
                if (index in category.merchants.indices) {
                    val current = category.merchants[index]
                    if (current.pattern != newValue) {
                        category.merchants[index] = current.copy(pattern = newValue)
                        callbacks.onCategoryUpdated(category)
                    }
                }
            }

            // Display-name field (optional, shown below the pattern)
            val displayNameField = EditText(ctx).apply {
                hint = ctx.getString(R.string.merchant_display_name_hint)
                setText(merchant.displayName ?: "")
                textSize = 12f
                setPadding(8, 4, 8, 4)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { lp -> lp.setMargins(8, 0, 8, 4) }
                setSimpleWatcher { newValue ->
                    if (index in category.merchants.indices) {
                        val current = category.merchants[index]
                        val newDisplayName = newValue.takeIf { it.isNotBlank() }
                        if (current.displayName != newDisplayName) {
                            category.merchants[index] = current.copy(displayName = newDisplayName)
                            callbacks.onCategoryUpdated(category)
                        }
                    }
                }
            }

            btnMove.setOnClickListener {
                if (index in category.merchants.indices) {
                    callbacks.onMoveMerchantRequested(category.merchants[index], category)
                }
            }

            btnDelete.setOnClickListener {
                if (index in category.merchants.indices) {
                    category.merchants.removeAt(index)
                    callbacks.onCategoryUpdated(category)
                    merchantsContainer.removeView(wrapper)
                    refreshMerchantFields(category, callbacks)
                }
            }

            wrapper.addView(view)
            wrapper.addView(displayNameField)
            merchantsContainer.addView(wrapper)
        }

        private fun refreshMerchantFields(category: Category, callbacks: CategoryCallbacks) {
            merchantsContainer.removeAllViews()
            category.merchants.forEachIndexed { index, merchant ->
                addMerchantField(index, merchant, category, callbacks)
            }
        }
    }
}

private fun Category.clone(): Category = Category(
    id = id,
    name = name,
    merchants = merchants.map { it.copy() }.toMutableList(),
    enabled = enabled
)
