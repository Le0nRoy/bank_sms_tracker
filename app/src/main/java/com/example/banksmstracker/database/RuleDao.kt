package com.example.banksmstracker.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface RuleDao {

    // --- All Rules ---

    @Query("SELECT * FROM rules ORDER BY id DESC")
    suspend fun getAllRules(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE senderId = :senderId ORDER BY id DESC")
    suspend fun getRulesBySender(senderId: Long): List<RuleEntity>

    // --- By Type ---

    @Query("SELECT * FROM rules WHERE ruleType = :ruleType ORDER BY id DESC")
    suspend fun getRulesByType(ruleType: String): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE senderId = :senderId AND ruleType = :ruleType ORDER BY id DESC")
    suspend fun getRulesBySenderAndType(senderId: Long, ruleType: String): List<RuleEntity>

    // --- Enabled Rules ---

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY id DESC")
    suspend fun getEnabledRules(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE enabled = 1 AND ruleType = :ruleType ORDER BY id DESC")
    suspend fun getEnabledRulesByType(ruleType: String): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE senderId = :senderId AND enabled = 1 ORDER BY id DESC")
    suspend fun getEnabledRulesBySender(senderId: Long): List<RuleEntity>

    @Query(
        "SELECT * FROM rules WHERE senderId = :senderId AND enabled = 1 AND ruleType = :ruleType ORDER BY id DESC"
    )
    suspend fun getEnabledRulesBySenderAndType(senderId: Long, ruleType: String): List<RuleEntity>

    // --- Single Rule ---

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getRuleById(id: Long): RuleEntity?

    // --- CRUD Operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<RuleEntity>): List<Long>

    @Update
    suspend fun updateRule(rule: RuleEntity)

    @Delete
    suspend fun deleteRule(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)

    @Query("DELETE FROM rules WHERE senderId = :senderId")
    suspend fun deleteRulesForSender(senderId: Long)

    @Query("DELETE FROM rules WHERE senderId = :senderId AND ruleType = :ruleType")
    suspend fun deleteRulesForSenderByType(senderId: Long, ruleType: String)

    // --- Counts ---

    @Query("SELECT COUNT(*) FROM rules")
    suspend fun getRulesCount(): Int

    @Query("SELECT COUNT(*) FROM rules WHERE ruleType = :ruleType")
    suspend fun getRulesCountByType(ruleType: String): Int

    @Query("SELECT COUNT(*) FROM rules WHERE senderId = :senderId")
    suspend fun getRulesCountBySender(senderId: Long): Int

    // --- Type Updates ---

    @Query("UPDATE rules SET ruleType = :ruleType WHERE id = :id")
    suspend fun updateRuleType(id: Long, ruleType: String)
}
