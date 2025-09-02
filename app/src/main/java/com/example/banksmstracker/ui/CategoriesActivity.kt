package com.example.banksmstracker.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.banksmstracker.R
import com.example.banksmstracker.serializer.ConfigLoader
import com.example.banksmstracker.data.Category
import com.example.banksmstracker.repository.ConfigRepository

class CategoriesActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CategoriesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categories)

        setupRecyclerView()
        loadCategories()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewCategories)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CategoriesAdapter()
        recyclerView.adapter = adapter
    }

    private fun loadCategories() {
        try {
            adapter.submitList(ConfigRepository.config.categories)
        } catch (e: Exception) {
            // Handle error - could show a toast or dialog
            e.printStackTrace()
        }
    }
}

class CategoriesAdapter : RecyclerView.Adapter<CategoriesAdapter.CategoryViewHolder>() {
    private var categories: List<Category> = emptyList()

    fun submitList(newCategories: List<Category>) {
        categories = newCategories
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): CategoryViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    class CategoryViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        fun bind(category: Category) {
            val text1 = itemView.findViewById<android.widget.TextView>(android.R.id.text1)
            val text2 = itemView.findViewById<android.widget.TextView>(android.R.id.text2)
            
            text1.text = category.name
            text2.text = category.merchants.joinToString(", ")
        }
    }
}
