package com.speedometer.app.feature.drive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.speedometer.app.R
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.Danger
import com.speedometer.app.core.designsystem.Line3
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.TextSoft
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.core.designsystem.rememberLayout
import com.speedometer.app.core.trip.SpeedUnit
import kotlin.math.roundToInt

private const val DIM_ALPHA = 0.45f

/**
 * HUD mode, from design/Hud.dc.html: a huge mirrored speed for reflecting off a windscreen. Mirror flips the
 * readout, Dim lowers its brightness, Units cycles km/h, mph and m/s. The design's "Next: Zone 40 · 400 m" line
 * needs map data the app does not have, so only the current limit is shown.
 */
@Composable
fun HudScreen(onExit: () -> Unit, vm: LiveDriveViewModel = hiltViewModel()) {
    val trip by vm.trip.collectAsState()
    val settings by vm.settings.collectAsState()
    val nextZone by vm.nextZone.collectAsState()
    var mirror by rememberSaveable { mutableStateOf(true) }
    var dim by rememberSaveable { mutableStateOf(false) }
    val unit = settings.unit
    val over = trip.limitKmh != null && trip.speedKmh > trip.limitKmh!! + 5f
    val landscape = rememberLayout().isLandscape

    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = if (landscape) 8.dp else 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(Surface1)
                        .clickable(role = Role.Button, onClick = onExit).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StrokeIcon("M15 5l-7 7 7 7", TextSoft, size = 18.dp, box = 24f)
                    Text(stringResource(R.string.exit_hud), style = bodyStyle(14, FontWeight.Medium, TextSoft))
                }
                Text(
                    stringResource(if (mirror) R.string.hud_mirror_on else R.string.hud_mirror_off) + " · " +
                        stringResource(if (dim) R.string.hud_brightness_dim else R.string.hud_brightness_auto),
                    Modifier.background(Surface1, RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                    style = bodyStyle(13),
                )
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val centerWidth = maxWidth
                val centerHeight = maxHeight
                if (landscape) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        val speedSize = with(LocalDensity.current) { min(160.dp, min(centerWidth * 0.4f, centerHeight * 0.68f)).toSp() }
                        Column(
                            Modifier.weight(1f).scale(scaleX = if (mirror) -1f else 1f, scaleY = 1f)
                                .alpha(if (dim) DIM_ALPHA else 1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                unit.fromKmh(trip.speedKmh).roundToInt().toString(),
                                style = displayStyle(160, FontWeight.Bold, if (over) Danger else Amber)
                                    .copy(fontSize = speedSize, lineHeight = speedSize * 0.9f, letterSpacing = (-4).sp),
                            )
                            Text(unit.label.uppercase(), Modifier.padding(top = 4.dp), style = bodyStyle(22, color = TextSoft).copy(letterSpacing = 2.sp))
                        }
                        trip.limitKmh?.let { limit ->
                            Row(
                                Modifier.weight(1f).alpha(if (dim) DIM_ALPHA else 1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Box(
                                    Modifier.size(72.dp).background(TextPrimary, CircleShape).border(9.dp, Danger, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { Text(unit.fromKmh(limit).roundToInt().toString(), style = displayStyle(28, FontWeight.Bold, Bg)) }
                                Column {
                                    val ahead = nextZone
                                    Text(stringResource(if (ahead != null) R.string.hud_next else R.string.hud_limit), style = bodyStyle(14))
                                    Text(
                                        if (ahead != null) ahead.name + " · " + ahead.distanceM + " m"
                                        else unit.fromKmh(limit).roundToInt().toString() + " " + unit.label,
                                        style = bodyStyle(22, FontWeight.SemiBold, TextPrimary),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val speedSize = with(LocalDensity.current) { min(240.dp, min(maxWidth * 0.62f, maxHeight * 0.42f)).toSp() }
                    Column(
                        Modifier.scale(scaleX = if (mirror) -1f else 1f, scaleY = 1f).alpha(if (dim) DIM_ALPHA else 1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            unit.fromKmh(trip.speedKmh).roundToInt().toString(),
                            style = displayStyle(240, FontWeight.Bold, if (over) Danger else Amber)
                                .copy(fontSize = speedSize, lineHeight = speedSize * 0.9f, letterSpacing = (-6).sp),
                        )
                        Text(unit.label.uppercase(), Modifier.padding(top = 8.dp), style = bodyStyle(28, color = TextSoft).copy(letterSpacing = 2.sp))
                        trip.limitKmh?.let { limit ->
                        Row(Modifier.padding(top = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Box(
                                Modifier.size(84.dp).background(TextPrimary, CircleShape).border(10.dp, Danger, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text(unit.fromKmh(limit).roundToInt().toString(), style = displayStyle(34, FontWeight.Bold, Bg)) }
                            Column {
                                // A saved zone coming up replaces the plain limit line, as "Next" in the design.
                                val ahead = nextZone
                                Text(stringResource(if (ahead != null) R.string.hud_next else R.string.hud_limit), style = bodyStyle(15))
                                Text(
                                    if (ahead != null) {
                                        ahead.name + " · " + ahead.distanceM + " m"
                                    } else {
                                        unit.fromKmh(limit).roundToInt().toString() + " " + unit.label
                                    },
                                    style = bodyStyle(30, FontWeight.SemiBold, TextPrimary),
                                )
                            }
                        }
                    }
                }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = if (landscape) 8.dp else 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                HudButton(R.string.hud_mirror) { mirror = !mirror }
                HudButton(R.string.hud_dim) { dim = !dim }
                HudButton(R.string.hud_units) {
                    val all = SpeedUnit.entries
                    vm.setUnit(all[(all.indexOf(unit) + 1) % all.size])
                }
            }
        }
    }
}

@Composable
private fun HudButton(label: Int, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).border(1.dp, Line3, RoundedCornerShape(26.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Text(stringResource(label), style = bodyStyle(15, FontWeight.Bold, TextPrimary)) }
}
