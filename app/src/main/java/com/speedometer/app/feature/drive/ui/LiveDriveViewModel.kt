package com.speedometer.app.feature.drive.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedometer.app.core.alerts.GuardAlerts
import com.speedometer.app.core.data.MeterStyle
import com.speedometer.app.core.data.Settings
import com.speedometer.app.core.database.SavedTripsRepository
import com.speedometer.app.core.trip.ZeroToHundred
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import com.speedometer.app.core.data.SettingsRepository
import com.speedometer.app.core.trip.SpeedUnit
import com.speedometer.app.core.trip.TripPhase
import com.speedometer.app.core.trip.TripRepository
import com.speedometer.app.core.trip.TripState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveDriveViewModel @Inject constructor(
    private val trips: TripRepository,
    private val settingsRepo: SettingsRepository,
    saved: SavedTripsRepository,
    private val alerts: GuardAlerts,
    private val monitor: com.speedometer.app.core.location.GnssMonitor,
    private val places: com.speedometer.app.core.location.PlaceNames,
    private val current: com.speedometer.app.core.location.CurrentLocation,
    zonesRepo: com.speedometer.app.core.database.ZonesRepository,
) : ViewModel() {
    val trip: StateFlow<TripState> = trips.state

    /** Satellites and accuracy, read only while the "Searching for GPS" card is on screen. */
    val gnss: StateFlow<com.speedometer.app.core.location.GnssSnapshot> = monitor.snapshots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.speedometer.app.core.location.GnssSnapshot())
    fun deviceLocationOn() = monitor.isDeviceLocationOn()

    /** Street or area name of the last recorded point, for "Last seen" when GPS is lost. */
    suspend fun lastSeenPlace(): String? = trips.route().lastOrNull()?.let { places.at(it.lat, it.lon) }

    /** The nearest saved zone ahead within 400 m, or null. "Ahead" is judged by distance only, not by heading. */
    val nextZone: StateFlow<NextZone?> = combine(trips.state, zonesRepo.zones) { _, zones ->
        val here = trips.route().lastOrNull() ?: return@combine null
        zones.filter { it.enabled }
            .map { z -> z to (com.speedometer.app.core.trip.GeoMath.distanceM(here.lat, here.lon, z.lat, z.lon) - z.radiusM) }
            .filter { (_, edge) -> edge > 0 && edge <= NEXT_ZONE_WITHIN_M }
            .minByOrNull { it.second }
            ?.let { (z, edge) -> NextZone(z.name, z.limitKmh, edge.toInt()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val loadedSettings: StateFlow<Settings?> = settingsRepo.settings
        .map<Settings, Settings?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings: StateFlow<Settings> = loadedSettings
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    private val _gTrail = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())

    /** Last few (lateral, longitudinal) forces in g, oldest first, for the G-force pad trail. */
    val gTrail: StateFlow<List<Pair<Float, Float>>> = _gTrail.asStateFlow()

    private val zeroTo100 = ZeroToHundred()
    private val _zeroTo100Sec = MutableStateFlow<Int?>(null)

    /** Last standing-start run to 100 km/h, in seconds, for the sports coupe cluster. */
    val zeroTo100Sec: StateFlow<Int?> = _zeroTo100Sec.asStateFlow()

    /** Highest speed today across saved trips and the current trip, km/h. */
    val topTodayKmh: StateFlow<Float> = combine(saved.trips, trips.state) { all, s ->
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        maxOf(s.maxKmh, all.filter { it.startMs >= startOfDay }.maxOfOrNull { it.maxKmh } ?: 0f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    /** Speeds of the last 60 seconds, oldest first, for the live graph. */
    private val _history = MutableStateFlow<List<Float>>(emptyList())
    val history: StateFlow<List<Float>> = _history.asStateFlow()

    init {
        viewModelScope.launch {
            var lastSec = -1
            trips.state.collect { s ->
                if (s.phase == TripPhase.Idle || s.durationSec < lastSec) {
                    _history.value = emptyList()
                    _gTrail.value = emptyList()
                    zeroTo100.reset()
                    _zeroTo100Sec.value = null
                }
                if (s.phase == TripPhase.Recording && s.durationSec != lastSec) {
                    _history.value = (_history.value + s.speedKmh).takeLast(HISTORY_SECONDS)
                    _zeroTo100Sec.value = zeroTo100.update(s.speedKmh, s.durationSec)
                    _gTrail.value = (_gTrail.value + (s.lateralMs2 / G_MS2 to s.accelMs2 / G_MS2)).takeLast(G_TRAIL_POINTS)
                }
                lastSec = s.durationSec
            }
        }
    }

    init {
        // Look the start address up while the phone is probably online, so the trip details already have it when the trip ends.
        viewModelScope.launch {
            trips.state.first { it.phase == TripPhase.Recording && it.routeSize > 0 }
            val start = trips.route().firstOrNull() ?: return@launch
            repeat(5) {
                if (places.address(start.lat, start.lon) != null) return@launch
                kotlinx.coroutines.delay(15_000)
            }
        }
    }

    /** The latest position of this trip from any location source, or null before the first one. */
    fun here() = trips.endPoint()

    /** The phone's last known position, for centring the live map before the trip has a position of its own. */
    suspend fun lastKnownPoint() = current.last()

    /** A fresh position from GPS or the fused provider, whichever answers first; works offline. */
    suspend fun freshPoint() = current.get()

    fun setMeterStyle(style: MeterStyle) { viewModelScope.launch { settingsRepo.setMeterStyle(style) } }
    fun setUnit(unit: SpeedUnit) { viewModelScope.launch { settingsRepo.setUnit(unit) } }

    /** Silences Limit Guard alerts for five minutes. */
    fun muteAlerts() = alerts.mute()

    /** When the user paused the trip, in epoch milliseconds. */
    fun pausedAtMs() = trips.pausedAtMs

    /** Route recorded so far, for the live map. */
    fun route() = trips.route()

    fun pause() = trips.pause()
    fun resume() = trips.resume()
    fun end() { trips.finish() }

    private companion object {
        const val HISTORY_SECONDS = 60
        const val NEXT_ZONE_WITHIN_M = 400.0
        const val G_TRAIL_POINTS = 8
        const val G_MS2 = 9.81f
    }
}

/** A saved zone coming up: its name, limit in km/h and the distance to its edge in metres. */
data class NextZone(val name: String, val limitKmh: Int, val distanceM: Int)
