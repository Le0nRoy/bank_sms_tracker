package com.example.banksmstracker.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface IgnoreRuleDao {

    @Query("SELECT * FROM ignore_rules ORDER BY id DESC")
    suspend fun getAllIgnoreRules(): List<IgnoreRuleEntity>

    @Query("SELECT * FROM ignore_rules WHERE senderId = :senderId ORDER BY id DESC")
    suspend fun getIgnoreRulesBySender(senderId: Long): List<IgnoreRuleEntity>

    @Query("SELECT * FROM ignore_rules WHERE enabled = 1 ORDER BY id DESC")
    suspend fun getEnabledIgnoreRules(): List<IgnoreRuleEntity>

    @Query("SELECT * FROM ignore_rules WHERE senderId = :senderId AND enabled = 1 ORDER BY id DESC")
    suspend fun getEnabledIgnoreRulesBySender(senderId: Long): List<IgnoreRuleEntity>

    @Query("SELECT * FROM ignore_rules WHERE id = :id")
    suspend fun getIgnoreRuleById(id: Long): IgnoreRuleEntity?

    @Insert
    suspend fun insertIgnoreRule(rule: IgnoreRuleEntity): Long

    @Update
    suspend fun updateIgnoreRule(rule: IgnoreRuleEntity)

    @Delete
    suspend fun deleteIgnoreRule(rule: IgnoreRuleEntity)

    @Query("DELETE FROM ignore_rules WHERE id = :id")
    suspend fun deleteIgnoreRuleById(id: Long)

    @Query("DELETE FROM ignore_rules WHERE senderId = :senderId")
    suspend fun deleteIgnoreRulesForSender(senderId: Long)

    @Query("SELECT COUNT(*) FROM ignore_rules")
    suspend fun getIgnoreRulesCount(): Int
}
