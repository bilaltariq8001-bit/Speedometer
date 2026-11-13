package com.speedometer.app.feature.zones.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.speedometer.app.R
import com.speedometer.app.core.database.ZoneEntity
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.DesignSwitch
import com.speedometer.app.core.designsystem.DesignTextField
import com.speedometer.app.core.designsystem.GhostButton
import com.speedometer.app.core.designsystem.Ice
import com.speedometer.app.core.designsystem.PrimaryButton
import com.speedometer.app.core.designsystem.ScrollPage
import com.speedometer.app.core.designsystem.Segmented
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.core.map.MapZone
import com.speedometer.app.core.map.RouteMap
import com.speedometer.app.core.trip.SpeedUnit
import com.speedometer.app.feature.permissions.ui.AppDialog
import org.maplibre.android.geometry.LatLng
import kotlin.math.roundToInt

private fun zoneColor(index: Int): Color = if (index % 2 == 0) Amber else Ice

/** Saved Zones, from design/Zones.dc.html. Zones override the map limit while you are inside one. */
@Composable
fun ZonesScreen(onBack: () -> Unit, vm: ZonesViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val u = ui ?: return
    var adding by rememberSaveable { mutableStateOf(false) }
    val gate = com.speedometer.app.feature.permissions.ui.rememberLocationGate()
    gate.dialogs()

    ScrollPage(bottom = {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp, top = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(Amber)
                    .clickable(role = Role.Button) { gate.require { adding = true } },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StrokeIcon("M12 5v14M5 12h14", Bg, size = 22.dp, box = 24f)
                Text(stringResource(R.string.add_zone_here), style = bodyStyle(17, FontWeight.Bold, Bg))
            }
        }
    }) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                StrokeIcon("M15 5l-7 7 7 7", TextPrimary, size = 24.dp, box = 24f)
            }
            Text(stringResource(R.string.more_zones), Modifier.padding(start = 4.dp), style = displayStyle(22, FontWeight.SemiBold))
        }
        Text(
            stringResource(R.string.zones_intro), Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
            style = bodyStyle(14),
        )

        if (u.zones.isNotEmpty()) {
            RouteMap(
                lines = emptyList(),
                zones = u.zones.mapIndexed { i, z -> MapZone(LatLng(z.lat, z.lon), z.radiusM, zoneColor(i)) },
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp).height(180.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (u.zones.isEmpty()) {
                Text(stringResource(R.string.zones_empty), style = bodyStyle(15), modifier = Modifier.padding(vertical = 24.dp))
            }
            u.zones.forEachIndexed { i, z -> ZoneRow(z, zoneColor(i), u.unit, { vm.setEnabled(z.id, it) }, { vm.delete(z.id) }) }
            if (u.locationFailed) {
                Text(stringResource(R.string.zone_location_failed), style = bodyStyle(14, color = Amber))
            }
        }
    }

    if (adding) {
        AddZoneDialog(
            unit = u.unit,
            onAdd = { name, limitKmh, schedule -> vm.addHere(name, limitKmh, schedule); adding = false },
            onCancel = { adding = false },
        )
    }
}

@Composable
private fun ZoneRow(z: ZoneEntity, color: Color, unit: SpeedUnit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).background(Surface1, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(48.dp).border(4.dp, color, CircleShape), contentAlignment = Alignment.Center) {
            Text(unit.fromKmh(z.limitKmh.toFloat()).roundToInt().toString(), style = displayStyle(16, FontWeight.Bold))
        }
        Column(Modifier.weight(1f)) {
            Text(z.name, style = bodyStyle(16, FontWeight.Bold, TextPrimary))
            Text("${z.radiusM} m · ${z.schedule}", Modifier.padding(top = 2.dp), style = bodyStyle(13))
        }
        DesignSwitch(z.enabled, onToggle)
        Box(Modifier.size(40.dp).clickable(role = Role.Button, onClick = onDelete), contentAlignment = Alignment.Center) {
            StrokeIcon("M6 6l12 12M18 6L6 18", TextMuted, size = 20.dp, box = 24f)
        }
    }
}

/** Add zone dialog with name, limit, and time/day schedule options. */
@Composable
private fun AddZoneDialog(unit: SpeedUnit, onAdd: (String, Int, String) -> Unit, onCancel: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableStateOf("") }
    var scheduleIndex by rememberSaveable { mutableIntStateOf(0) }
    val limitValue = limit.toIntOrNull()
    AppDialog(onCancel) {
        Text(stringResource(R.string.add_zone_title), style = displayStyle(22, FontWeight.SemiBold))
        DesignTextField(name, { name = it }, stringResource(R.string.zone_name), KeyboardType.Text)
        DesignTextField(limit, { limit = it.filter(Char::isDigit).take(3) }, stringResource(R.string.zone_limit, unit.label), KeyboardType.Number)
        Text("Schedule", style = bodyStyle(13, color = TextMuted))
        Segmented(listOf("Always", "7–9 AM", "Weekdays"), scheduleIndex, { scheduleIndex = it })
        PrimaryButton(stringResource(R.string.add_zone), onClick = {
            if (limitValue != null && limitValue > 0) {
                val sched = when (scheduleIndex) {
                    1 -> "weekdays 7–9 AM"
                    2 -> "weekdays"
                    else -> "always"
                }
                onAdd(name.trim(), (limitValue / unit.perKmh).roundToInt(), sched)
            }
        })
        GhostButton(stringResource(R.string.not_now), onCancel)
    }
}
