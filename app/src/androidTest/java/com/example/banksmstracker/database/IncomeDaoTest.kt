package com.example.banksmstracker.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IncomeDaoTest {

    private lateinit var database: BankSmsDatabase
    private lateinit var incomeDao: IncomeDao

    @BeforeEach
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            BankSmsDatabase::class.java
        ).allowMainThreadQueries().build()
        incomeDao = database.incomeDao()
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    private fun makeIncome(
        amount: Double = 100.0,
        currency: String = "GEL",
        source: String? = "Salary",
        messageHash: String = "hash-1"
    ) = IncomeEntity(
        amount = amount,
        currency = currency,
        source = source,
        timestamp = null,
        balance = null,
        messageHash = messageHash
    )

    @Test
    @DisplayName("getAllIncomes returns empty list when no data")
    fun getAllIncomesReturnsEmptyListWhenNoData() = runBlocking {
        assertTrue(incomeDao.getAllIncomes().isEmpty())
    }

    @Test
    @DisplayName("insertIncome and getAllIncomes returns inserted income")
    fun insertIncomeAndGetAllIncomesReturnsInsertedIncome() = runBlocking {
        val income = makeIncome(amount = 500.0, source = "Salary", messageHash = "hash-salary")
        incomeDao.insertIncome(income)

        val result = incomeDao.getAllIncomes()
        assertEquals(1, result.size)
        assertEquals(500.0, result[0].amount)
        assertEquals("Salary", result[0].source)
    }

    @Test
    @DisplayName("getAllIncomes returns multiple incomes ordered by id desc")
    fun getAllIncomesReturnsMultipleIncomesOrderedByIdDesc() = runBlocking {
        incomeDao.insertIncome(makeIncome(amount = 100.0, messageHash = "hash-1"))
        incomeDao.insertIncome(makeIncome(amount = 200.0, messageHash = "hash-2"))
        incomeDao.insertIncome(makeIncome(amount = 300.0, messageHash = "hash-3"))

        val result = incomeDao.getAllIncomes()
        assertEquals(3, result.size)
        assertTrue(result[0].id >= result[1].id)
        assertTrue(result[1].id >= result[2].id)
    }

    @Test
    @DisplayName("duplicate messageHash (IGNORE conflict) does not insert duplicate")
    fun duplicateMessageHashDoesNotInsertDuplicate() = runBlocking {
        val income = makeIncome(amount = 100.0, messageHash = "same-hash")
        val firstInsert = incomeDao.insertIncome(income)
        val secondInsert = incomeDao.insertIncome(income.copy(amount = 999.0))

        assertTrue(firstInsert > 0)
        assertEquals(-1L, secondInsert)
        val result = incomeDao.getAllIncomes()
        assertEquals(1, result.size)
        assertEquals(100.0, result[0].amount)
    }
}
