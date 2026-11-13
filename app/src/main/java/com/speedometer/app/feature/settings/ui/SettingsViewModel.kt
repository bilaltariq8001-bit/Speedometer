package com.speedometer.app.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedometer.app.core.autodetect.AutoDetect
import com.speedometer.app.core.data.GuardOption
import com.speedometer.app.core.data.Settings
import com.speedometer.app.core.data.SettingsRepository
import com.speedometer.app.core.data.ScreenOrientation
import com.speedometer.app.core.trip.SpeedUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val autoDetect: AutoDetect,
) : ViewModel() {
    val settings: StateFlow<Settings?> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setUnit(unit: SpeedUnit) = viewModelScope.launch { repo.setUnit(unit) }
    fun setScreenOrientation(orientation: ScreenOrientation) = viewModelScope.launch { repo.setScreenOrientation(orientation) }
    fun setAutoDetect(on: Boolean) = viewModelScope.launch { repo.setAutoDetect(on) }
    fun hasActivityPermission() = autoDetect.hasPermission()
    fun setOption(option: GuardOption, on: Boolean) = viewModelScope.launch { repo.setGuardOption(option, on) }
    fun setSpeedLimitOn(on: Boolean) = viewModelScope.launch { repo.setSpeedLimitOn(on) }
    fun setSpeedLimitKmh(kmh: Int) = viewModelScope.launch { repo.setSpeedLimitKmh(kmh) }
    fun setLimitTolerance(kmh: Int) = viewModelScope.launch { repo.setLimitTolerance(kmh) }
}
