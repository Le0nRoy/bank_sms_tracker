package com.example.banksmstracker.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RuleDao {

    @Query("SELECT * FROM rules WHERE senderId = :senderId ORDER BY id DESC")
    suspend fun getRulesBySender(senderId: Long): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE senderId = :senderId AND ruleType = :ruleType ORDER BY id DESC")
    suspend fun getRulesBySenderAndType(senderId: Long, ruleType: String): List<RuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RuleEntity): Long

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)

    @Query("DELETE FROM rules WHERE senderId = :senderId")
    suspend fun deleteRulesForSender(senderId: Long)
}
