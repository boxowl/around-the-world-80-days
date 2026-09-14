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
import com.boxowl.aroundtheworld.health.Availability
import com.boxowl.aroundtheworld.health.HealthConnectStepsGateway
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
    private val gateway = HealthConnectStepsGateway(application)
    private val synchronizer = ExpeditionSync(gateway, repository)
    private val mutableState = MutableStateFlow<JourneyState>(JourneyState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableAccess = MutableStateFlow<StepAccess>(StepAccess.Checking)
    val access = mutableAccess.asStateFlow()
    private var syncJob: Job? = null
    init { reload(); checkAccess() }
    fun checkAccess() {
        viewModelScope.launch {
            mutableAccess.value = try {
                when (gateway.availability()) {
                    Availability.UNAVAILABLE -> StepAccess.Unavailable
                    Availability.UPDATE_REQUIRED -> StepAccess.UpdateRequired
                    Availability.AVAILABLE -> if (gateway.hasPermission()) StepAccess.Granted else StepAccess.PermissionRequired
                }
            } catch (_: Exception) { StepAccess.ReadError }
        }
    }
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
    fun start(mode: JourneyMode, paceStepsPerDay: Int) {
        if (mutableState.value != JourneyState.NotStarted) return
        mutableState.value = JourneyState.Loading
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    repository.start(mode, Instant.now(), ZoneId.systemDefault(), paceStepsPerDay)
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
            checkAccess()
        }
    }
}

sealed interface StepAccess {
    data object Checking : StepAccess
    data object Granted : StepAccess
    data object PermissionRequired : StepAccess
    data object Unavailable : StepAccess
    data object UpdateRequired : StepAccess
    data object ReadError : StepAccess
}
