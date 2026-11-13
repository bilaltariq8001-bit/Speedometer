package com.speedometer.app.feature.tripmap.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedometer.app.core.data.SettingsRepository
import com.speedometer.app.core.database.SavedTripsRepository
import com.speedometer.app.core.database.TripEntity
import com.speedometer.app.core.trip.RoutePoint
import com.speedometer.app.core.trip.SpeedUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MappedTrip(val trip: TripEntity, val points: List<RoutePoint>)

data class TripMapUi(val heatmap: Boolean, val trips: List<MappedTrip>, val unit: SpeedUnit, val singleTrip: Boolean)

@HiltViewModel
class TripMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    saved: SavedTripsRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val tripId: Long = savedStateHandle.get<Long>("id") ?: -1L

    /** "week", "month" or "all", sent by the Trips screen so the map shows the same period as the list. */
    private val sinceMs: Long = when (savedStateHandle.get<String>("range")) {
        "all" -> 0L
        "month" -> System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(30)
        else -> System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(7)
    }
    private val heatmap = MutableStateFlow(false)

    init {
        // Clears the made-up trips of earlier test builds once.
        viewModelScope.launch { saved.removeLegacyDemoTrips() }
    }

    val ui: StateFlow<TripMapUi?> = combine(saved.trips, heatmap, settings.settings) { all, h, s ->
        val shown = if (tripId != -1L) {
            all.filter { it.id == tripId }.map { MappedTrip(it, it.routePoints()) }.filter { it.points.size >= 2 }
        } else {
            all.filter { it.startMs >= sinceMs }.map { MappedTrip(it, it.routePoints()) }.filter { it.points.size >= 2 }
        }
        TripMapUi(h, shown, s.unit, tripId != -1L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleHeatmap() = heatmap.update { !it }
}
