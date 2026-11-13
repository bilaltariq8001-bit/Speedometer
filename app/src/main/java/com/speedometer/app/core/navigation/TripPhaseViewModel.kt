package com.speedometer.app.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedometer.app.core.trip.GuardLevel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.speedometer.app.core.alerts.GuardAlerts
import com.speedometer.app.core.trip.TripPhase
import com.speedometer.app.core.trip.TripRepository
import com.speedometer.app.core.trip.TripState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The live trip as the nav host needs it: the phase (to leave a trip screen when the trip was ended from the
 * notification) and the whole state, so the over-the-limit screen can be drawn over whichever screen is open.
 */
@HiltViewModel
class TripPhaseViewModel @Inject constructor(
    private val trips: TripRepository,
    private val alerts: GuardAlerts,
) : ViewModel() {
    /** True while a trip is recording or paused. */
    fun tripActive(): Boolean = trips.state.value.phase.let { it == TripPhase.Recording || it == TripPhase.Paused }

    val phase: Flow<TripPhase> = trips.state.map { it.phase }.distinctUntilChanged()

    /**
     * One boolean for the red edge glow: a trip is recording and the Limit Guard says the speed is over the limit. It reads the
     * guard level the engine already computes (with its own hysteresis), so there is no second threshold, and it only emits on change.
     */
    val isOverLimit: StateFlow<Boolean> = trips.state
        .map { it.phase == TripPhase.Recording && it.guard != GuardLevel.Off && it.limitKmh != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val trip: StateFlow<TripState> get() = trips.state

    /** Silences the alerts (screen, voice and vibration) for a few minutes. */
    fun mute() = alerts.mute()

    fun isMuted(): Boolean = alerts.isMuted
}
