package com.boxowl.aroundtheworld.expedition

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.boxowl.aroundtheworld.expedition.credit.V2CreditRepository
import com.boxowl.aroundtheworld.expedition.credit.V2CreditSnapshot
import com.boxowl.aroundtheworld.expedition.credit.V2StepSync
import com.boxowl.aroundtheworld.expedition.credit.V2SyncResult
import com.boxowl.aroundtheworld.health.HealthConnectStepsGateway
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface V2JourneyState {
    data object Loading : V2JourneyState
    data object NotStarted : V2JourneyState
    data class Active(
        val snapshot: V2CreditSnapshot,
        val sync: V2SyncResult? = null,
        val syncing: Boolean = false,
    ) : V2JourneyState
    data object StorageError : V2JourneyState
}

/** v2 is loaded before the legacy screen; a new attempt starts only after UI confirmation. */
class V2ExpeditionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = V2CreditRepository(ExpeditionDatabase.get(application))
    private val gateway = HealthConnectStepsGateway(application)
    private val synchronizer = V2StepSync(gateway, repository::reconcile)
    private val mutableState = MutableStateFlow<V2JourneyState>(V2JourneyState.Loading)
    val state = mutableState.asStateFlow()
    private var syncJob: Job? = null

    init { reload() }

    fun reload() {
        syncJob?.cancel()
        mutableState.value = V2JourneyState.Loading
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { repository.loadActive() }
                mutableState.value = saved?.let { V2JourneyState.Active(it) } ?: V2JourneyState.NotStarted
                if (saved != null) refresh()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = V2JourneyState.StorageError }
        }
    }

    fun startConfirmed(expectedActiveSeq: Long?, mode: JourneyMode, strideMillimeters: Long) {
        val current = mutableState.value
        if (current is V2JourneyState.Active && current.snapshot.expedition.id != expectedActiveSeq) return
        if (current == V2JourneyState.NotStarted && expectedActiveSeq != null) return
        if (current !is V2JourneyState.Active && current != V2JourneyState.NotStarted) return
        syncJob?.cancel()
        mutableState.value = V2JourneyState.Loading
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    repository.start(expectedActiveSeq, Instant.now(), ZoneId.systemDefault(), mode, strideMillimeters)
                }
                mutableState.value = V2JourneyState.Active(saved)
                refresh()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = V2JourneyState.StorageError }
        }
    }

    fun refresh() {
        val current = mutableState.value as? V2JourneyState.Active ?: return
        syncJob?.cancel()
        mutableState.value = current.copy(syncing = true)
        syncJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    synchronizer.refresh(current.snapshot, Instant.now())
                }
                val active = mutableState.value as? V2JourneyState.Active ?: return@launch
                mutableState.value = active.copy(
                    snapshot = (result as? V2SyncResult.Updated)?.snapshot ?: active.snapshot,
                    sync = result,
                    syncing = false,
                )
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                val active = mutableState.value as? V2JourneyState.Active ?: return@launch
                mutableState.value = active.copy(sync = V2SyncResult.ReadError, syncing = false)
            }
        }
    }
}
