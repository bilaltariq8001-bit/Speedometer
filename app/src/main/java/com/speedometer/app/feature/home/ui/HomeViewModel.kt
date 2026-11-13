package com.speedometer.app.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedometer.app.core.data.Settings
import com.speedometer.app.core.data.SettingsRepository
import com.speedometer.app.core.data.ScreenOrientation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val autoDetect: com.speedometer.app.core.autodetect.AutoDetect,
    private val here: com.speedometer.app.core.location.CurrentLocation,
    private val places: com.speedometer.app.core.location.PlaceNames,
    zonesRepo: com.speedometer.app.core.database.ZonesRepository,
    monitor: com.speedometer.app.core.location.GnssMonitor,
) : ViewModel() {
    /** Auto-detect is on and the phone has given the permission it needs. */
    fun autoDetectActive(on: Boolean) = on && autoDetect.hasPermission()

    /** One-shot current position for the tablet map preview, or null without permission or a fix. */
    suspend fun locate() = here.get()

    /** Street or area name for [point], or null. */
    suspend fun placeAt(point: com.speedometer.app.core.location.GeoPoint) = places.at(point.lat, point.lon)

    /** Saved zones, to show the limit that applies where you stand. */
    val zones: StateFlow<List<com.speedometer.app.core.database.ZoneEntity>> = zonesRepo.zones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null until the first read from DataStore. */
    val settings: StateFlow<Settings?> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMeterStyle(style: com.speedometer.app.core.data.MeterStyle) { viewModelScope.launch { repo.setMeterStyle(style) } }
    fun setScreenOrientation(orientation: ScreenOrientation) { viewModelScope.launch { repo.setScreenOrientation(orientation) } }

    fun setVehicle(vehicle: com.speedometer.app.core.data.Vehicle) { viewModelScope.launch { repo.setVehicle(vehicle) } }

    fun markLocationAsked() = viewModelScope.launch { repo.setLocationAsked() }
    fun markNotificationAsked() = viewModelScope.launch { repo.setNotificationAsked() }
    fun markLocationGranted() = viewModelScope.launch { repo.setLocationGranted() }

    /** Live GPS state for the Home pill, collected only while Home is on screen. */
    val gps: StateFlow<com.speedometer.app.core.location.GnssSnapshot> = monitor.snapshots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.speedometer.app.core.location.GnssSnapshot())
}
