package com.example.banksmstracker.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface ConfigDao {

    // --- Categories ---

    @Transaction
    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun getCategories(): List<CategoryWithMerchants>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoriesCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMerchant(merchant: CategoryMerchantEntity): Long

    @Query("DELETE FROM category_merchants WHERE categoryId = :categoryId")
    suspend fun deleteMerchantsForCategory(categoryId: Long)

    // --- Senders ---

    @Transaction
    @Query("SELECT * FROM senders ORDER BY id")
    suspend fun getSenders(): List<SenderWithDetails>

    @Query("SELECT COUNT(*) FROM senders")
    suspend fun getSendersCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSender(sender: SenderEntity): Long

    @Update
    suspend fun updateSender(sender: SenderEntity)

    @Delete
    suspend fun deleteSender(sender: SenderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddress(address: SenderAddressEntity): Long

    @Query("DELETE FROM sender_addresses WHERE senderId = :senderId")
    suspend fun deleteAddressesForSender(senderId: Long)
}
