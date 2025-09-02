package com.example.banksmstracker.repository

import com.example.banksmstracker.data.Category

// TODO Also need a PaymentRepository
object CategoryRepository {
    private val categories = mutableListOf<Category>()

    fun addCategory(category: Category) {
        categories.add(category)
    }

    fun getAll(): List<Category> = categories

    fun findByName(name: String): Category? =
        categories.find { it.name == name }
}