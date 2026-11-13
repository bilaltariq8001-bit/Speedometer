package com.speedometer.app.feature.share.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedometer.app.core.data.SettingsRepository
import com.speedometer.app.core.database.PlaceEntity
import com.speedometer.app.core.database.PlacesRepository
import com.speedometer.app.core.location.CurrentLocation
import com.speedometer.app.core.location.GeoPoint
import com.speedometer.app.core.trip.GeoMath
import com.speedometer.app.core.trip.SpeedUnit
import com.speedometer.app.core.trip.TripPhase
import com.speedometer.app.core.trip.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Where you are now and, while a trip records, how fast you are going. */
data class ShareSnapshot(val point: GeoPoint, val speed: Int?, val unit: SpeedUnit)

/** A share in progress: the position it was last sent from, and when it ends. [stopOption]: 0 arrival, 1 hour, 2 manual. */
data class ShareSession(val lat: Double, val lon: Double, val startedAtMs: Long, val stopOption: Int, val speedKmh: Float?)

/** Keeps the share going while you leave and return to the screen. In memory only: it ends with the app. */
@Singleton
class ShareSessionHolder @Inject constructor() {
    val session = MutableStateFlow<ShareSession?>(null)
}

/** What the screen shows: the session, the saved destination it heads to, and the figures worked out from them. */
data class ShareUi(
    val session: ShareSession?,
    val destination: PlaceEntity?,
    val remainingKm: Float?,
    val arrivalMs: Long?,
    val unit: SpeedUnit,
)

private const val ARRIVED_M = 100.0
private const val HOUR_MS = 60 * 60 * 1000L
private const val ASSUMED_KMH = 40f

@HiltViewModel
class LiveShareViewModel @Inject constructor(
    private val here: CurrentLocation,
    private val trips: TripRepository,
    private val settings: SettingsRepository,
    places: PlacesRepository,
    private val holder: ShareSessionHolder,
) : ViewModel() {
    val ui: StateFlow<ShareUi> = combine(holder.session, places.places, settings.settings) { s, all, st ->
        // The newest saved destination from the Nav screen.
        val dest = all.firstOrNull { it.kind == "destination" }
        val remaining = if (s != null && dest != null) (GeoMath.distanceM(s.lat, s.lon, dest.lat, dest.lon) / 1000.0).toFloat() else null
        val speed = s?.speedKmh?.takeIf { it >= 5f } ?: ASSUMED_KMH
        val arrival = if (s != null && remaining != null) System.currentTimeMillis() + (remaining / speed * HOUR_MS).toLong() else null
        ShareUi(s, dest, remaining, arrival, st.unit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShareUi(null, null, null, null, SpeedUnit.Kmh))

    /** One position reading to send, or null when the phone cannot give a location right now. */
    suspend fun snapshot(): ShareSnapshot? {
        val point = here.get() ?: return null
        val unit = runCatching { settings.settings.first().unit }.getOrDefault(SpeedUnit.Kmh)
        val state = trips.state.value
        val speed = if (state.phase == TripPhase.Recording) unit.fromKmh(state.speedKmh).toInt() else null
        return ShareSnapshot(point, speed, unit)
    }

    /** Starts sharing, or moves an existing share to your position now. Returns the new snapshot, or null without a fix. */
    suspend fun startOrUpdate(): ShareSnapshot? {
        val snap = snapshot() ?: return null
        val state = trips.state.value
        val old = holder.session.value
        holder.session.value = ShareSession(
            snap.point.lat, snap.point.lon, old?.startedAtMs ?: System.currentTimeMillis(), old?.stopOption ?: 0,
            if (state.phase == TripPhase.Recording) state.speedKmh else null,
        )
        return snap
    }

    fun setStopOption(option: Int) = holder.session.update { it?.copy(stopOption = option) }

    fun stop() { holder.session.value = null }

    /** Ends the share when its stop rule is met: an hour has passed, or the last position is at the destination. */
    fun checkStopRule() {
        val s = holder.session.value ?: return
        val dest = ui.value.destination
        val done = when (s.stopOption) {
            1 -> System.currentTimeMillis() - s.startedAtMs >= HOUR_MS
            0 -> dest != null && GeoMath.distanceM(s.lat, s.lon, dest.lat, dest.lon) <= ARRIVED_M
            else -> false
        }
        if (done) stop()
    }
}
