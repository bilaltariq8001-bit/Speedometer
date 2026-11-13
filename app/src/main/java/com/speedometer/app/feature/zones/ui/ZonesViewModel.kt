package com.speedometer.app.feature.zones.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedometer.app.core.data.SettingsRepository
import com.speedometer.app.core.database.ZoneEntity
import com.speedometer.app.core.database.ZonesRepository
import com.speedometer.app.core.location.CurrentLocation
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

/** Radius given to a new zone; the design does not specify how it is chosen. */
const val DEFAULT_ZONE_RADIUS_M = 100

data class ZonesUi(val zones: List<ZoneEntity>, val unit: SpeedUnit, val locationFailed: Boolean)

@HiltViewModel
class ZonesViewModel @Inject constructor(
    private val repo: ZonesRepository,
    private val here: CurrentLocation,
    settings: SettingsRepository,
) : ViewModel() {
    private val failed = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            repo.removeLegacyDemoZones()
        }
    }

    val ui: StateFlow<ZonesUi?> = combine(repo.zones, settings.settings, failed) { z, s, f -> ZonesUi(z, s.unit, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(id: Long, on: Boolean) = viewModelScope.launch { repo.setEnabled(id, on) }
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun dismissFailure() = failed.update { false }

    /** Adds a zone at the current location. [limitKmh] is entered in km/h by the caller's unit conversion. */
    fun addHere(name: String, limitKmh: Int, schedule: String = "always") = viewModelScope.launch {
        val p = here.get()
        if (p == null) failed.update { true }
        else repo.add(name.ifBlank { "Zone" }, limitKmh, p.lat, p.lon, DEFAULT_ZONE_RADIUS_M, schedule)
    }
}
