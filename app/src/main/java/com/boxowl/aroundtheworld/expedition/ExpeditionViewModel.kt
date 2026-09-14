package com.boxowl.aroundtheworld.expedition

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

sealed interface JourneyState {
    data object Loading : JourneyState
    data object NotStarted : JourneyState
    data class Active(val expedition: Expedition) : JourneyState
    data object StorageError : JourneyState
}
class ExpeditionViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ExpeditionStore(application)
    private val mutableState = MutableStateFlow<JourneyState>(JourneyState.Loading)
    val state = mutableState.asStateFlow()
    init { reload() }
    fun reload() {
        mutableState.value = JourneyState.Loading
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { store.load() }
                mutableState.value = saved?.let { JourneyState.Active(it) } ?: JourneyState.NotStarted
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
                    // Never overwrite an expedition if the UI became stale.
                    store.load() ?: Expedition(Instant.now(), ZoneId.systemDefault(), mode).also(store::save)
                }
                mutableState.value = JourneyState.Active(saved)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = JourneyState.StorageError }
        }
    }
}
