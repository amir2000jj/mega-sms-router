package com.megasmsrouter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.megasmsrouter.data.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DestinationDao {
    @Query("SELECT * FROM api_destinations ORDER BY priority ASC, name ASC")
    fun observeAll(): Flow<List<ApiDestinationEntity>>

    @Query("SELECT * FROM api_destinations")
    suspend fun getAll(): List<ApiDestinationEntity>

    @Query("SELECT * FROM api_destinations WHERE id = :id")
    suspend fun getById(id: String): ApiDestinationEntity?

    @Upsert
    suspend fun upsert(e: ApiDestinationEntity)

    @Query("DELETE FROM api_destinations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE api_destinations SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE api_destinations SET lastStatus = :status, lastStatusAt = :at WHERE id = :id")
    suspend fun setLastStatus(id: String, status: String, at: Long)

    @Query("DELETE FROM api_destinations")
    suspend fun deleteAll()
}

@Dao
abstract class RuleDao {
    @Query("SELECT * FROM routing_rules ORDER BY priority ASC, name ASC")
    abstract fun observeAll(): Flow<List<RoutingRuleEntity>>

    @Query("SELECT * FROM rule_destinations")
    abstract fun observeRefs(): Flow<List<RuleDestinationCrossRef>>

    @Query("SELECT * FROM routing_rules")
    abstract suspend fun getAll(): List<RoutingRuleEntity>

    @Query("SELECT * FROM rule_destinations")
    abstract suspend fun getRefs(): List<RuleDestinationCrossRef>

    @Query("SELECT * FROM routing_rules WHERE id = :id")
    abstract suspend fun getById(id: String): RoutingRuleEntity?

    @Query("SELECT destinationId FROM rule_destinations WHERE ruleId = :id")
    abstract suspend fun getDestinationIds(id: String): List<String>

    @Upsert
    abstract suspend fun upsertRule(e: RoutingRuleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertRefs(refs: List<RuleDestinationCrossRef>)

    @Query("DELETE FROM rule_destinations WHERE ruleId = :id")
    abstract suspend fun deleteRefs(id: String)

    @Query("DELETE FROM routing_rules WHERE id = :id")
    abstract suspend fun deleteRule(id: String)

    @Query("UPDATE routing_rules SET enabled = :enabled WHERE id = :id")
    abstract suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM routing_rules")
    abstract suspend fun deleteAll()

    @Transaction
    open suspend fun save(rule: RoutingRuleEntity, destinationIds: List<String>) {
        upsertRule(rule)
        deleteRefs(rule.id)
        insertRefs(destinationIds.distinct().map { RuleDestinationCrossRef(rule.id, it) })
    }
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(e: RequestHistoryEntity): Long

    @Query("SELECT * FROM request_history ORDER BY timestamp DESC LIMIT 200")
    fun observeRecent(): Flow<List<RequestHistoryEntity>>

    @Query("SELECT * FROM request_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<RequestHistoryEntity>

    @Query("DELETE FROM request_history")
    suspend fun clear()

    @Query("DELETE FROM request_history WHERE id NOT IN (SELECT id FROM request_history ORDER BY timestamp DESC LIMIT 500)")
    suspend fun prune()
}

@Dao
interface PendingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: PendingDeliveryEntity)

    @Query("SELECT * FROM pending_deliveries WHERE id = :id")
    suspend fun get(id: String): PendingDeliveryEntity?

    @Query("SELECT COUNT(*) FROM pending_deliveries")
    suspend fun count(): Int

    @Query("DELETE FROM pending_deliveries WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pending_deliveries WHERE destinationId = :destinationId")
    suspend fun deleteForDestination(destinationId: String)

    @Query("DELETE FROM pending_deliveries WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM pending_deliveries")
    suspend fun deleteAll()
}

@Dao
interface ProcessedDao {
    /** Returns -1 if the key already exists (duplicate message). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: ProcessedMessageEntity): Long

    @Query("DELETE FROM processed_messages WHERE processedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM processed_messages")
    suspend fun deleteAll()
}
