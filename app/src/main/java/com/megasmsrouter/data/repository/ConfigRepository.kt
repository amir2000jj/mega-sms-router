package com.megasmsrouter.data.repository

import com.megasmsrouter.data.AppDatabase
import com.megasmsrouter.data.entities.RequestHistoryEntity
import com.megasmsrouter.domain.model.ApiDestination
import com.megasmsrouter.domain.model.RoutingRule
import com.megasmsrouter.security.SecretCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

class ConfigRepository(private val db: AppDatabase, private val cipher: SecretCipher) {

    val destinations: Flow<List<ApiDestination>> =
        db.destinationDao().observeAll().map { list -> list.map { it.toDomain(cipher) } }

    val rules: Flow<List<RoutingRule>> =
        combine(db.ruleDao().observeAll(), db.ruleDao().observeRefs()) { rs, refs ->
            val byRule = refs.groupBy { it.ruleId }
            rs.map { r -> r.toDomain(byRule[r.id].orEmpty().map { it.destinationId }) }
        }

    val history: Flow<List<RequestHistoryEntity>> = db.historyDao().observeRecent()

    suspend fun getDestination(id: String): ApiDestination? =
        db.destinationDao().getById(id)?.toDomain(cipher)

    suspend fun saveDestination(d: ApiDestination) = db.destinationDao().upsert(d.toEntity(cipher))

    suspend fun deleteDestination(id: String) {
        db.pendingDao().deleteForDestination(id)
        db.destinationDao().delete(id) // rule links are removed by ON DELETE CASCADE
    }

    suspend fun setDestinationEnabled(id: String, enabled: Boolean) =
        db.destinationDao().setEnabled(id, enabled)

    /** The copy starts disabled so a duplicate can never start sending by surprise. */
    suspend fun duplicateDestination(id: String, nameSuffix: String) {
        val src = getDestination(id) ?: return
        saveDestination(
            src.copy(
                id = UUID.randomUUID().toString(),
                name = src.name + nameSuffix,
                enabled = false,
                lastStatus = null,
                lastStatusAt = null
            )
        )
    }

    suspend fun getRule(id: String): RoutingRule? {
        val e = db.ruleDao().getById(id) ?: return null
        return e.toDomain(db.ruleDao().getDestinationIds(id))
    }

    suspend fun saveRule(r: RoutingRule) = db.ruleDao().save(r.toEntity(), r.destinationIds)

    suspend fun deleteRule(id: String) = db.ruleDao().deleteRule(id)

    suspend fun setRuleEnabled(id: String, enabled: Boolean) = db.ruleDao().setEnabled(id, enabled)

    suspend fun clearHistory() = db.historyDao().clear()

    /** Deletes destinations, rules, history, queued work and duplicate-detection data. */
    suspend fun deleteAllData() {
        db.pendingDao().deleteAll()
        db.ruleDao().deleteAll()
        db.destinationDao().deleteAll()
        db.historyDao().clear()
        db.processedDao().deleteAll()
    }
}
