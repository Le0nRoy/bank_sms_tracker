package com.example.banksmstracker.categories

import com.example.banksmstracker.data.Category

object CategoryRepository {
    private val categories = mutableListOf<Category>()

    fun addCategory(category: Category) {
        categories.add(category)
    }

    fun getAll(): List<Category> = categories

    fun findByName(name: String): Category? =
        categories.find { it.name == name }
}
