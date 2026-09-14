package com.boxowl.aroundtheworld.expedition

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

sealed interface JourneyState {
    data object Loading : JourneyState
    data object NotStarted : JourneyState
    data class Active(val expedition: Expedition, val sync: SyncResult? = null, val syncing: Boolean = false) : JourneyState
    data object StorageError : JourneyState
}
class ExpeditionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ExpeditionRepository(ExpeditionDatabase.get(application), ExpeditionStore(application))
    private val synchronizer = ExpeditionSync(com.boxowl.aroundtheworld.health.HealthConnectStepsGateway(application), repository)
    private val mutableState = MutableStateFlow<JourneyState>(JourneyState.Loading)
    val state = mutableState.asStateFlow()
    private var syncJob: Job? = null
    init { reload() }
    fun reload() {
        syncJob?.cancel()
        mutableState.value = JourneyState.Loading
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { repository.load() }
                mutableState.value = saved?.let { JourneyState.Active(it) } ?: JourneyState.NotStarted
                if (saved != null) refresh()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = JourneyState.StorageError }
        }
    }
    fun start(mode: JourneyMode) {
        if (mutableState.value != JourneyState.NotStarted) return
        mutableState.value = JourneyState.Loading
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    repository.start(mode, Instant.now(), ZoneId.systemDefault())
                }
                mutableState.value = JourneyState.Active(saved)
                refresh()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = JourneyState.StorageError }
        }
    }
    fun refresh() {
        val current = mutableState.value as? JourneyState.Active ?: return
        syncJob?.cancel()
        mutableState.value = current.copy(syncing = true)
        syncJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { synchronizer.refresh(current.expedition, Instant.now()) }
            val active = mutableState.value as? JourneyState.Active ?: return@launch
            mutableState.value = active.copy(
                expedition = (result as? SyncResult.Updated)?.expedition ?: active.expedition,
                sync = result, syncing = false,
            )
        }
    }
}
