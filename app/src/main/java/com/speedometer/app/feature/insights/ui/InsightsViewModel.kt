package com.speedometer.app.feature.insights.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedometer.app.core.data.SettingsRepository
import com.speedometer.app.core.database.SavedTripsRepository
import com.speedometer.app.core.database.TripEntity
import com.speedometer.app.core.trip.SpeedUnit
import com.speedometer.app.feature.insights.domain.Insights
import com.speedometer.app.feature.insights.domain.InsightsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(private val savedTrips: SavedTripsRepository, settings: SettingsRepository) : ViewModel() {
    private val saved get() = savedTrips

    init {
        // Clears the made-up trips of earlier test builds once.
        viewModelScope.launch { savedTrips.removeLegacyDemoTrips() }
    }

    // Each starts from the in-memory trips, so Insights is filled on its first frame instead of popping in mid-transition.
    val insights: StateFlow<Insights?> = saved.trips
        .map { InsightsCalculator.compute(it, LocalDate.now()) }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), savedTrips.summaries.value?.let { InsightsCalculator.compute(it, LocalDate.now()) })

    /** Saved trips grouped by the day they started, for the day sheet opened from a bar. */
    val tripsByDay: StateFlow<Map<LocalDate, List<TripEntity>>> = saved.trips
        .map(::groupByDay)
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), savedTrips.summaries.value?.let(::groupByDay) ?: emptyMap())

    val unit: StateFlow<SpeedUnit> = settings.settings
        .map { it.unit }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.current.value?.unit ?: SpeedUnit.Kmh)
}

private fun groupByDay(list: List<TripEntity>): Map<LocalDate, List<TripEntity>> {
    val zone = ZoneId.systemDefault()
    return list.groupBy { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }
        .mapValues { (_, trips) -> trips.sortedBy { it.startMs } }
}
