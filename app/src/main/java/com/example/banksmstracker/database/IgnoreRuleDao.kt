package com.example.banksmstracker.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface IgnoreRuleDao {

    @Query("SELECT * FROM ignore_rules ORDER BY id DESC")
    suspend fun getAllIgnoreRules(): List<IgnoreRuleEntity>

    @Query("SELECT * FROM ignore_rules WHERE senderId = :senderId ORDER BY id DESC")
    suspend fun getIgnoreRulesBySender(senderId: Long): List<IgnoreRuleEntity>

    @Query("SELECT * FROM ignore_rules WHERE senderId = :senderId AND enabled = 1 ORDER BY id DESC")
    suspend fun getEnabledIgnoreRulesBySender(senderId: Long): List<IgnoreRuleEntity>

    @Insert
    suspend fun insertIgnoreRule(rule: IgnoreRuleEntity): Long

    @Update
    suspend fun updateIgnoreRule(rule: IgnoreRuleEntity)

    @Query("DELETE FROM ignore_rules WHERE id = :id")
    suspend fun deleteIgnoreRuleById(id: Long)
}
