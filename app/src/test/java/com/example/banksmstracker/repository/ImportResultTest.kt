package com.example.banksmstracker.repository

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ImportResultTest {

    @Test
    fun `Success with all zeros should have zero total changes`() {
        val result = ImportResult.Success(
            sendersAdded = 0,
            sendersMerged = 0,
            categoriesAdded = 0,
            categoriesMerged = 0
        )
        assertEquals(0, result.totalChanges)
    }

    @Test
    fun `Success should calculate total changes correctly`() {
        val result = ImportResult.Success(
            sendersAdded = 2,
            sendersMerged = 3,
            categoriesAdded = 1,
            categoriesMerged = 4
        )
        assertEquals(10, result.totalChanges)
    }

    @Test
    fun `Success toString should contain all counts`() {
        val result = ImportResult.Success(
            sendersAdded = 2,
            sendersMerged = 3,
            categoriesAdded = 1,
            categoriesMerged = 4
        )
        val str = result.toString()
        assertTrue(str.contains("2"))
        assertTrue(str.contains("3"))
        assertTrue(str.contains("1"))
        assertTrue(str.contains("4"))
        assertTrue(str.contains("senders"))
        assertTrue(str.contains("categories"))
    }

    @Test
    fun `Error should store message`() {
        val errorMessage = "Something went wrong"
        val result = ImportResult.Error(errorMessage)
        assertEquals(errorMessage, result.message)
    }

    @Test
    fun `Success is instance of ImportResult`() {
        val result: ImportResult = ImportResult.Success(1, 2, 3, 4)
        assertTrue(result is ImportResult.Success)
    }

    @Test
    fun `Error is instance of ImportResult`() {
        val result: ImportResult = ImportResult.Error("error")
        assertTrue(result is ImportResult.Error)
    }
}
