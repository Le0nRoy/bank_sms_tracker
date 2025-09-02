package com.example.banksmstracker.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.banksmstracker.R
import com.example.banksmstracker.data.Category
import com.example.banksmstracker.repository.ConfigRepository
import com.google.android.material.floatingactionbutton.FloatingActionButton

class CategoriesActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CategoriesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categories)

        setupRecyclerView()

        findViewById<FloatingActionButton>(R.id.fabAddCategory).setOnClickListener {
            val newCategory = Category("", mutableListOf())
            ConfigRepository.config.categories.add(newCategory)
            adapter.notifyItemInserted(ConfigRepository.config.categories.size - 1)
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewCategories)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CategoriesAdapter { updatedCategory ->
            // Category instances are edited in-place as we keep the same list reference.
            // You can persist here if needed (e.g., repository save).
        }
        recyclerView.adapter = adapter

        // Keep reference to the same MutableList so edits reflect in the config
        adapter.submitList(ConfigRepository.config.categories)
    }
}

class CategoriesAdapter(
    private val onCategoryChanged: (Category) -> Unit
) : RecyclerView.Adapter<CategoriesAdapter.CategoryViewHolder>() {

    private var categories: MutableList<Category> = mutableListOf()

    fun submitList(newCategories: MutableList<Category>) {
        categories = newCategories
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position], onCategoryChanged)
    }

    override fun getItemCount(): Int = categories.size

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val nameEditText: EditText = itemView.findViewById(R.id.nameEditText)
        private val merchantsContainer: LinearLayout = itemView.findViewById(R.id.merchantsContainer)
        private val btnAddMerchant: Button = itemView.findViewById(R.id.btnAddMerchant)

        fun bind(category: Category, onCategoryChanged: (Category) -> Unit) {
            // Set name (avoid redundant set to reduce callbacks)
            if (nameEditText.text.toString() != category.name) {
                nameEditText.setText(category.name)
            }
            nameEditText.addTextChangedListener(object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    val newName = s?.toString() ?: ""
                    if (category.name != newName) {
                        category.name = newName
                        onCategoryChanged(category)
                    }
                }
            })

            // Render merchants
            merchantsContainer.removeAllViews()
            category.merchants.forEachIndexed { index, merchant ->
                addOrBindMerchantRow(index, merchant, category, onCategoryChanged)
            }

            // Add new merchant row
            btnAddMerchant.setOnClickListener {
                category.merchants.add("")
                onCategoryChanged(category)
                val newIndex = category.merchants.lastIndex
                addOrBindMerchantRow(newIndex, "", category, onCategoryChanged)
            }
        }

        private fun addOrBindMerchantRow(
            index: Int,
            merchantValue: String,
            category: Category,
            onCategoryChanged: (Category) -> Unit
        ) {
            val et = EditText(itemView.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                hint = itemView.context.getString(R.string.merchant_hint, index + 1)
                setText(merchantValue)
                addTextChangedListener(object : SimpleTextWatcher() {
                    override fun afterTextChanged(s: Editable?) {
                        val newValue = s?.toString() ?: ""
                        if (index in category.merchants.indices && category.merchants[index] != newValue) {
                            category.merchants[index] = newValue
                            onCategoryChanged(category)
                        }
                    }
                })
            }
            merchantsContainer.addView(et)
        }
    }
}

private abstract class SimpleTextWatcher : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable?) {}
}
