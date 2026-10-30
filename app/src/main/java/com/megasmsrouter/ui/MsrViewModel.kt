package com.megasmsrouter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.megasmsrouter.MsrApp
import com.megasmsrouter.data.entities.RequestHistoryEntity
import com.megasmsrouter.domain.model.ApiDestination
import com.megasmsrouter.domain.model.MessagePayload
import com.megasmsrouter.domain.model.RoutingRule
import com.megasmsrouter.domain.validation.DestinationValidator
import com.megasmsrouter.network.FailureKind
import com.megasmsrouter.network.NetworkResult
import com.megasmsrouter.network.RequestBuilder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MsrViewModel(app: Application) : AndroidViewModel(app) {
    private val msr = app as MsrApp
    private val repo = msr.repo
    private val started = SharingStarted.WhileSubscribed(5_000)

    val destinations: StateFlow<List<ApiDestination>> = repo.destinations.stateIn(viewModelScope, started, emptyList())
    val rules: StateFlow<List<RoutingRule>> = repo.rules.stateIn(viewModelScope, started, emptyList())
    val history: StateFlow<List<RequestHistoryEntity>> = repo.history.stateIn(viewModelScope, started, emptyList())
    val forwarding: StateFlow<Boolean> = msr.prefs.forwardingEnabled

    fun setForwarding(on: Boolean) = msr.prefs.setForwarding(on)

    suspend fun getDestination(id: String): ApiDestination? = repo.getDestination(id)
    suspend fun getRule(id: String): RoutingRule? = repo.getRule(id)

    fun saveDestination(d: ApiDestination) { viewModelScope.launch { repo.saveDestination(d) } }
    fun deleteDestination(id: String) { viewModelScope.launch { repo.deleteDestination(id) } }
    fun setDestinationEnabled(id: String, on: Boolean) { viewModelScope.launch { repo.setDestinationEnabled(id, on) } }
    fun duplicateDestination(id: String, suffix: String) { viewModelScope.launch { repo.duplicateDestination(id, suffix) } }

    fun saveRule(r: RoutingRule) { viewModelScope.launch { repo.saveRule(r) } }
    fun deleteRule(id: String) { viewModelScope.launch { repo.deleteRule(id) } }
    fun setRuleEnabled(id: String, on: Boolean) { viewModelScope.launch { repo.setRuleEnabled(id, on) } }

    fun clearHistory() { viewModelScope.launch { repo.clearHistory() } }

    fun deleteAllData() {
        msr.prefs.setForwarding(false)
        WorkManager.getInstance(getApplication<Application>()).cancelAllWork()
        viewModelScope.launch { repo.deleteAllData() }
    }

    /** Sends a request with the fake code 000000 to the destination (user-initiated test). */
    suspend fun sendTest(d: ApiDestination): NetworkResult {
        val issues = DestinationValidator.validate(d)
        if (issues.isNotEmpty()) return NetworkResult.Failure(FailureKind.BUILD, issues.first().name, 0)
        val payload = MessagePayload(
            code = "000000",
            sender = "MSR-TEST",
            message = "MSR test message 000000",
            timestamp = System.currentTimeMillis(),
            destinationId = d.id,
            messageId = "test-" + UUID.randomUUID()
        )
        val request = try {
            RequestBuilder.build(d, payload)
        } catch (e: Exception) {
            return NetworkResult.Failure(FailureKind.BUILD, e.javaClass.simpleName, 0)
        }
        return msr.apiClient.send(request, d)
    }
}
