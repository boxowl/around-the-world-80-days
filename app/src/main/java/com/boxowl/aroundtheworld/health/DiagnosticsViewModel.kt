package com.boxowl.aroundtheworld.health

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {
    private val diagnostics = StepDiagnostics(HealthConnectStepsGateway(application))
    private val mutableState = MutableStateFlow<DiagnosticState>(DiagnosticState.Loading)
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null
    fun refresh() {
        refreshJob?.cancel()
        mutableState.value = DiagnosticState.Loading
        refreshJob = viewModelScope.launch {
            mutableState.value = diagnostics.read(Instant.now(), ZoneId.systemDefault())
        }
    }
    fun clear() {
        refreshJob?.cancel()
        mutableState.value = DiagnosticState.Loading
    }
}
