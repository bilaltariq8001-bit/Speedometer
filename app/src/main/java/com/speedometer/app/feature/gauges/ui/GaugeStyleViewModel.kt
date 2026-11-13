package com.speedometer.app.feature.gauges.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.speedometer.app.core.data.MeterStyle
import com.speedometer.app.core.data.SettingsRepository
import com.speedometer.app.core.trip.SpeedUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The user-made vehicle model: its name, top speed in km/h and whether the meters use it. */
data class CustomModel(val name: String, val maxKmh: Int, val active: Boolean)

@HiltViewModel
class GaugeStyleViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {
    val unit: StateFlow<SpeedUnit> = repo.settings.map { it.unit }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpeedUnit.Kmh)

    val style: StateFlow<MeterStyle> = repo.settings.map { it.meterStyle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeterStyle.Dial)

    val custom: StateFlow<CustomModel?> = repo.settings.map { s ->
        val name = s.customName
        val max = s.customMaxKmh
        if (name != null && max != null) CustomModel(name, max, s.customActive) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Saves a generic meter choice; the standard 0 to 200 scale applies again. */
    fun save(style: MeterStyle, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            repo.useCustomModel(false)
            repo.setMeterStyle(style)
            onSaved()
        }
    }

    fun saveCustomModel(name: String, maxKmh: Int) { viewModelScope.launch { repo.saveCustomModel(name, maxKmh) } }

    /** Uses the custom model: the classic dial with the model's own top speed. */
    fun useCustomModel() {
        viewModelScope.launch {
            repo.useCustomModel(true)
            repo.setMeterStyle(MeterStyle.Dial)
        }
    }
}
