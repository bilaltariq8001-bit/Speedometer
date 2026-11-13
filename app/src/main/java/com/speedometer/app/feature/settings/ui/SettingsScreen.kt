package com.speedometer.app.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.feature.permissions.ui.rememberAutoDetectSwitch
import com.speedometer.app.core.designsystem.rememberLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.speedometer.app.R
import com.speedometer.app.core.data.AppLanguage
import com.speedometer.app.core.data.GuardOption
import com.speedometer.app.core.designsystem.DesignSwitch
import com.speedometer.app.core.designsystem.Line2
import com.speedometer.app.core.designsystem.ScrollPage
import com.speedometer.app.core.designsystem.Segmented
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.core.trip.SpeedUnit
import com.speedometer.app.feature.exit.ui.openPlayStore
import com.speedometer.app.feature.gauges.ui.meterNameRes

private val optionRows = listOf(
    Triple(GuardOption.AlertTones, R.string.opt_alert_tones, R.string.opt_alert_tones_sub),
    Triple(GuardOption.SpokenZoneWarnings, R.string.opt_spoken, R.string.opt_spoken_sub),
    Triple(GuardOption.VibrateOnAlert, R.string.opt_vibrate, R.string.opt_vibrate_sub),
    Triple(GuardOption.KeepScreenOn, R.string.opt_screen_on, R.string.opt_screen_on_sub),
)

private val units = listOf(SpeedUnit.Kmh, SpeedUnit.Mph, SpeedUnit.Ms)

/**
 * Settings, from design/Settings.dc.html. Speed unit and the four Limit Guard switches are saved to DataStore.
 * The More list shows the language; Gauge style, Saved Zones and Rate the app lead to screens not built yet.
 */
@Composable
fun SettingsScreen(onZones: () -> Unit, onGaugeStyle: () -> Unit, onLanguage: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsState()
    val settings = s ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val setAutoDetect = rememberAutoDetectSwitch(vm::hasActivityPermission) { vm.setAutoDetect(it) }

    if (rememberLayout().isLandscape) {
        Row(Modifier.fillMaxSize().background(Bg).padding(top = 16.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SettingsTopBar()
                SpeedUnitSection(settings, vm)
                SpeedLimitSection(settings, vm)
                AutoDetectSection(settings, setAutoDetect)
                LimitGuardSection(settings, vm)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                MoreSection(settings, context, onZones, onGaugeStyle, onLanguage)
            }
        }
    } else {
        ScrollPage {
            SettingsTopBar()
            SpeedUnitSection(settings, vm)
            SpeedLimitSection(settings, vm)
            AutoDetectSection(settings, setAutoDetect)
            LimitGuardSection(settings, vm)
            MoreSection(settings, context, onZones, onGaugeStyle, onLanguage)
        }
    }
}

@Composable
private fun SettingsTopBar() {
    Text(stringResource(R.string.tab_settings), Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), style = displayStyle(26, FontWeight.SemiBold))
}

@Composable
private fun SpeedUnitSection(settings: com.speedometer.app.core.data.Settings, vm: SettingsViewModel) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
        Text(stringResource(R.string.speed_unit), Modifier.padding(bottom = 8.dp), style = bodyStyle(13))
        Segmented(units.map { it.label }, units.indexOf(settings.unit), { vm.setUnit(units[it]) })
    }
}

@Composable
private fun SpeedLimitSection(settings: com.speedometer.app.core.data.Settings, vm: SettingsViewModel) {
    SpeedLimitBlock(
        settings,
        onOn = { vm.setSpeedLimitOn(it) },
        onLimitKmh = { vm.setSpeedLimitKmh(it) },
    )
}

@Composable
private fun AutoDetectSection(settings: com.speedometer.app.core.data.Settings, setAutoDetect: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp)
            .background(Surface1, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 6.dp)
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.auto_detect_title), style = bodyStyle(16, FontWeight.Bold, TextPrimary))
            Text(stringResource(R.string.auto_detect_body), Modifier.padding(top = 2.dp), style = bodyStyle(13))
        }
        DesignSwitch(settings.autoDetect, setAutoDetect)
    }
}

@Composable
private fun LimitGuardSection(settings: com.speedometer.app.core.data.Settings, vm: SettingsViewModel) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        Text(stringResource(R.string.limit_guard), Modifier.padding(bottom = 8.dp), style = bodyStyle(13))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1)) {
            optionRows.forEachIndexed { i, (option, name, sub) ->
                if (i > 0) Divider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(name), style = bodyStyle(16, FontWeight.Bold, TextPrimary))
                        Text(stringResource(sub), Modifier.padding(top = 2.dp), style = bodyStyle(13))
                    }
                    DesignSwitch(settings.option(option)) { vm.setOption(option, it) }
                }
            }
        }
    }
}

@Composable
private fun MoreSection(settings: com.speedometer.app.core.data.Settings, context: android.content.Context, onZones: () -> Unit, onGaugeStyle: () -> Unit, onLanguage: () -> Unit) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)) {
        Text(stringResource(R.string.more), Modifier.padding(bottom = 8.dp), style = bodyStyle(13))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1)) {
            MoreRow(R.string.more_gauge_style, stringResource(meterNameRes(settings.meterStyle)), first = true, onClick = onGaugeStyle)
            MoreRow(R.string.more_zones, null, onClick = onZones)
            MoreRow(R.string.more_language, AppLanguage.native[settings.language] ?: settings.language, onClick = onLanguage)
            MoreRow(R.string.more_rate, null, onClick = { openPlayStore(context) })
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line2.copy(alpha = 0.6f)))
}

@Composable
private fun MoreRow(label: Int, value: String?, first: Boolean = false, onClick: (() -> Unit)? = null) {
    if (!first) Divider()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), Modifier.weight(1f), style = bodyStyle(16, FontWeight.Medium, TextPrimary))
        if (value != null) Text(value, style = bodyStyle(14, color = TextMuted))
    }
}
