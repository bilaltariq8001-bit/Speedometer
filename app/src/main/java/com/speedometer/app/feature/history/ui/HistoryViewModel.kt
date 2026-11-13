package com.speedometer.app.feature.history.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedometer.app.core.data.SettingsRepository
import com.speedometer.app.core.database.SavedTripsRepository
import com.speedometer.app.core.database.TripEntity
import com.speedometer.app.core.trip.SpeedUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class Range { Week, Month, All }

/** Trips with a score under this are "flagged" (shown in red). */
const val FLAG_BELOW_SCORE = 60

data class HistoryUi(
    val range: Range,
    val flaggedOnly: Boolean,
    val hasAnyTrips: Boolean,
    val trips: List<TripEntity>,
    val totalKm: Float,
    val unit: SpeedUnit,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val saved: SavedTripsRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val range = MutableStateFlow(Range.Week)
    private val flagged = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            saved.removeLegacyDemoTrips()
        }
    }

    val ui: StateFlow<HistoryUi?> = combine(saved.trips, range, flagged, settings.settings) { all, r, f, s ->
        val since = when (r) {
            Range.Week -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            Range.Month -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            Range.All -> 0L
        }
        val shown = all.filter { it.startMs >= since && (!f || it.score < FLAG_BELOW_SCORE) }
        HistoryUi(r, f, all.isNotEmpty(), shown, shown.sumOf { it.distanceKm.toDouble() }.toFloat(), s.unit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setRange(r: Range) {
        range.update { r }
        flagged.update { false }
    }
    fun toggleFlagged() = flagged.update { !it }
    fun delete(id: Long) = viewModelScope.launch { saved.delete(id) }
}
