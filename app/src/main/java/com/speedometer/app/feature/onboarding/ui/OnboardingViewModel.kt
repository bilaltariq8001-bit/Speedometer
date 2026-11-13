package com.speedometer.app.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedometer.app.core.data.Settings
import com.speedometer.app.core.data.SettingsRepository
import com.speedometer.app.core.data.Vehicle
import com.speedometer.app.core.trip.SpeedUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val autoDetect: com.speedometer.app.core.autodetect.AutoDetect,
    private val repo: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<Settings?> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setLanguage(language: String) = viewModelScope.launch { repo.setLanguage(language) }
    fun setVehicle(vehicle: Vehicle) = viewModelScope.launch { repo.setVehicle(vehicle) }
    fun setMph(mph: Boolean) = viewModelScope.launch { repo.setUnit(if (mph) SpeedUnit.Mph else SpeedUnit.Kmh) }
    fun hasActivityPermission() = autoDetect.hasPermission()
    fun setAutoDetect(on: Boolean) = viewModelScope.launch { repo.setAutoDetect(on) }
    fun finish() = viewModelScope.launch { repo.finishOnboarding() }
}
