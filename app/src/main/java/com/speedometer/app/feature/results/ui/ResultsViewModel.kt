package com.speedometer.app.feature.results.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedometer.app.core.data.SettingsRepository
import com.speedometer.app.core.ads.AdGate
import com.speedometer.app.core.database.SavedTripsRepository
import com.speedometer.app.core.location.PlaceNames
import com.speedometer.app.core.trip.SpeedUnit
import com.speedometer.app.core.trip.RouteGaps
import com.speedometer.app.core.trip.RoutePoint
import com.speedometer.app.core.trip.TripExport
import com.speedometer.app.core.trip.TripFormat
import com.speedometer.app.core.trip.TripRepository
import com.speedometer.app.core.trip.TripState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data object Saved : SaveState
    data class Failed(val storageFull: Boolean) : SaveState
}

data class ResultsUi(
    val trip: TripState,
    val title: String,
    val startedAtMs: Long,
    val route: List<RoutePoint>,
    val startPoint: RoutePoint?,
    val endPoint: RoutePoint?,
    val startPlace: String?,
    val endPlace: String?,
    /** Average score of trips saved in the last 7 days, or null when there are none. */
    val sevenDayAvg: Int?,
    val unit: SpeedUnit,
    /** True while the From and To addresses are still being looked up. */
    val placesLoading: Boolean = false,
)

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val live: TripRepository,
    private val saved: SavedTripsRepository,
    private val places: PlaceNames,
    settings: SettingsRepository,
    private val analytics: com.speedometer.app.core.analytics.Analytics,
) : ViewModel() {
    private val _startPlace = MutableStateFlow<String?>(null)
    private val _endPlace = MutableStateFlow<String?>(null)
    private val _placesLoading = MutableStateFlow(true)

    val ui: StateFlow<ResultsUi?> = combine(live.state, saved.trips, _startPlace, _endPlace, settings.settings, _placesLoading) { values ->
        val trip = values[0] as com.speedometer.app.core.trip.TripState
        @Suppress("UNCHECKED_CAST") val trips = values[1] as List<com.speedometer.app.core.database.TripEntity>
        val sp = values[2] as String?
        val ep = values[3] as String?
        val s = values[4] as com.speedometer.app.core.data.Settings
        val loading = values[5] as Boolean
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val recent = trips.filter { it.startMs >= since }
        ResultsUi(
            trip = trip,
            title = TripFormat.title(live.startedAtMs),
            startedAtMs = live.startedAtMs,
            route = live.route(),
            startPoint = live.startPoint(),
            endPoint = live.endPoint(),
            startPlace = sp,
            endPlace = ep,
            sevenDayAvg = recent.takeIf { it.isNotEmpty() }?.map { it.score }?.average()?.toInt(),
            unit = s.unit,
            placesLoading = loading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val startPt = live.startPoint()
            val endPt = live.endPoint()
            // A few tries, in case the phone has just come back online.
            repeat(LOOKUP_TRIES) { attempt ->
                if (_startPlace.value == null && startPt != null) _startPlace.value = places.address(startPt.lat, startPt.lon)
                if (_endPlace.value == null && endPt != null) _endPlace.value = places.address(endPt.lat, endPt.lon)
                val done = (startPt == null || _startPlace.value != null) && (endPt == null || _endPlace.value != null)
                if (done) return@repeat
                if (attempt < LOOKUP_TRIES - 1) kotlinx.coroutines.delay(LOOKUP_RETRY_MS)
            }
            _placesLoading.value = false
        }
    }

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)

    /** Idle, writing to the database, or failed (the trip is kept so it can be retried, exported or discarded). */
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()
    private var pendingThen: ((Long) -> Unit)? = null

    /** Saves the finished trip, clears the live trip, then calls [then] with the new trip's id. */
    fun save(then: (Long) -> Unit) {
        val ui = ui.value ?: return
        if (ui.trip.tooShortToSave || _saveState.value != SaveState.Idle) return
        pendingThen = then
        AdGate.tripSaving = true
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                val startedMs = System.currentTimeMillis()
                val id = saved.save(ui.trip, live.startedAtMs, ui.title, RouteGaps.fill(live.routeForSave()), live.vehicle)
                // Keep the Saving screen up for a moment so it reads as a step instead of flashing.
                val left = MIN_SAVING_MS - (System.currentTimeMillis() - startedMs)
                if (left > 0) kotlinx.coroutines.delay(left)
                AdGate.tripSaving = false
                _saveState.value = SaveState.Saved
                then(id)
            } catch (e: android.database.sqlite.SQLiteFullException) {
                _saveState.value = SaveState.Failed(storageFull = true)
            } catch (e: java.io.IOException) {
                _saveState.value = SaveState.Failed(storageFull = true)
            } catch (e: android.database.sqlite.SQLiteException) {
                _saveState.value = SaveState.Failed(storageFull = false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Anything else that goes wrong while saving also lands on the Save error screen, never a stuck Saving screen.
                _saveState.value = SaveState.Failed(storageFull = false)
            } finally {
                AdGate.tripSaving = false
            }
        }
    }

    /** Clears the in-memory finished trip only after the saved summary is closed. */
    fun finishSaved() {
        if (_saveState.value == SaveState.Saved) {
            live.reset()
            _saveState.value = SaveState.Idle
        }
    }

    /** Tries the failed save again. */
    fun retry() {
        val then = pendingThen ?: return
        _saveState.value = SaveState.Idle
        save(then)
    }

    /** The unsaved trip as CSV text, for Export as file. */
    fun exportCsv(): String? {
        val ui = ui.value ?: return null
        return TripExport.toCsv(ui.title, live.startedAtMs, ui.trip, live.route())
    }

    fun discard(then: () -> Unit) {
        _saveState.value = SaveState.Idle
        live.reset()
        analytics.event("trip_discarded")
        then()
    }
}

private const val MIN_SAVING_MS = 1700L

private const val LOOKUP_TRIES = 20
private const val LOOKUP_RETRY_MS = 5000L
