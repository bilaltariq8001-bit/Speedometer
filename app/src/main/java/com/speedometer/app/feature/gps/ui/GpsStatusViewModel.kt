package com.speedometer.app.feature.gps.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedometer.app.core.location.GnssMonitor
import com.speedometer.app.core.location.GnssSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class GpsStatusViewModel @Inject constructor(monitor: GnssMonitor) : ViewModel() {
    private val restart = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val snapshot: StateFlow<GnssSnapshot> = restart.flatMapLatest { monitor.snapshots() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GnssSnapshot())

    /** Re-reads the platform status, for after location access was just granted. */
    fun refresh() = restart.update { it + 1 }
}
