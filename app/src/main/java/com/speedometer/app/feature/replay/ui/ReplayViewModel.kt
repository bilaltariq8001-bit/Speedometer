package com.speedometer.app.feature.replay.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedometer.app.core.data.SettingsRepository
import com.speedometer.app.core.database.SavedTripsRepository
import com.speedometer.app.core.database.TripEntity
import com.speedometer.app.core.trip.RoutePoint
import com.speedometer.app.core.trip.SpeedUnit
import com.speedometer.app.feature.replay.domain.Moment
import com.speedometer.app.feature.replay.domain.ReplayMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Replay runs at this multiple of real time; the design has no speed control. */
const val REPLAY_SPEED = 10f
private const val SKIP_SEC = 10f
private const val TICK_MS = 100L

data class ReplayUi(
    val trip: TripEntity,
    val points: List<RoutePoint>,
    val moments: List<Moment>,
    val unit: SpeedUnit,
    val tSec: Float = 0f,
    val playing: Boolean = false,
) {
    val durationSec: Int get() = ReplayMath.durationSec(points)
}

@HiltViewModel
class ReplayViewModel @Inject constructor(
    state: SavedStateHandle,
    private val saved: SavedTripsRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val tripId: Long = checkNotNull(state["id"])
    private val _ui = MutableStateFlow<ReplayUi?>(null)
    val ui: StateFlow<ReplayUi?> = _ui.asStateFlow()
    private var job: Job? = null
    private var missing = MutableStateFlow(false)
    val tripMissing: StateFlow<Boolean> = missing.asStateFlow()

    init {
        viewModelScope.launch {
            val trip = saved.get(tripId)
            if (trip == null) {
                missing.update { true }
                return@launch
            }
            val points = trip.routePoints()
            _ui.value = ReplayUi(trip, points, ReplayMath.hardBrakes(points), settings.settings.first().unit)
        }
    }

    fun toggle() {
        val u = _ui.value ?: return
        if (u.playing) pause() else play()
    }

    private fun play() {
        val u = _ui.value ?: return
        if (u.durationSec <= 0) return
        if (u.tSec >= u.durationSec) seek(0f)
        _ui.update { it?.copy(playing = true) }
        job?.cancel()
        job = viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                val cur = _ui.value ?: break
                val next = cur.tSec + REPLAY_SPEED * TICK_MS / 1000f
                if (next >= cur.durationSec) { _ui.update { it?.copy(tSec = it.durationSec.toFloat(), playing = false) }; break }
                _ui.update { it?.copy(tSec = next) }
            }
        }
    }

    private fun pause() {
        job?.cancel()
        _ui.update { it?.copy(playing = false) }
    }

    fun seek(t: Float) = _ui.update { u -> u?.copy(tSec = t.coerceIn(0f, u.durationSec.toFloat())) }
    fun skip(deltaSec: Float) { _ui.value?.let { seek(it.tSec + deltaSec) } }
    fun back() = skip(-SKIP_SEC)
    fun forward() = skip(SKIP_SEC)

    override fun onCleared() { job?.cancel() }
}
