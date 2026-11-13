package com.speedometer.app.feature.drive.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.speedometer.app.R
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.Danger
import com.speedometer.app.core.designsystem.GhostButton
import com.speedometer.app.core.designsystem.Ice
import com.speedometer.app.core.designsystem.Line3
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.TextSoft
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.core.trip.GpsQualityRules
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val DOT_STEP_MS = 400L

/** Weak GPS card and "Try this" tips, from design/GpsWeak.dc.html. */
@Composable
fun WeakGpsNotice(accuracyM: Float?) {
    Row(
        Modifier.fillMaxWidth().background(Surface1, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StrokeIcon("M12 3l10 18H2L12 3z M12 10v5M12 18v.5", Amber, size = 24.dp, box = 24f)
        Column {
            Text(stringResource(R.string.weak_gps_title), style = bodyStyle(15, FontWeight.Bold, TextPrimary))
            Text(
                accuracyM?.let { stringResource(R.string.weak_gps_body, it.roundToInt()) } ?: stringResource(R.string.gps_weak),
                Modifier.padding(top = 2.dp), style = bodyStyle(13, color = TextSoft),
            )
        }
    }
    Column(
        Modifier.fillMaxWidth().background(Surface1, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.try_this), style = bodyStyle(13))
        listOf(R.string.tip_window, R.string.tip_battery, R.string.tip_metal).forEach {
            Text("•  " + stringResource(it), style = bodyStyle(14, color = TextSoft))
        }
    }
}

/**
 * Trip paused because the fix was lost, from design/GpsLost.dc.html. [lastSpeedText] is the speed just before
 * the signal dropped.
 */
@Composable
fun GpsLostView(lastSpeedText: String, unitLabel: String, lastSeen: String?, onDetails: () -> Unit, onEnd: () -> Unit) {
    // The engine pauses itself at the lost threshold, so the signal has been gone at least that long.
    var seconds by remember { mutableIntStateOf(GpsQualityRules.LOST_AFTER_SECONDS) }
    var dots by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }
    LaunchedEffect(Unit) { while (true) { delay(DOT_STEP_MS); dots = (dots + 1) % 4 } }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(Amber, CircleShape))
                Text(stringResource(R.string.paused_signal_loss), style = bodyStyle(14, FontWeight.Bold, TextPrimary))
            }
            Text(stringResource(R.string.no_gps), style = bodyStyle(13, color = Danger))
        }
        Canvas(Modifier.size(200.dp)) {
            val sc = size.width / 200f
            val c = Offset(100f * sc, 100f * sc)
            listOf(92f, 62f, 32f).forEach { drawCircle(Color232B35, it * sc, c, style = Stroke(3f * sc)) }
            drawCircle(TextMuted, 8f * sc, c)
        }
        Text(stringResource(R.string.searching_gps) + ".".repeat(dots), style = displayStyle(22, FontWeight.SemiBold))
        Text(stringResource(R.string.signal_dropped, seconds), style = bodyStyle(14), modifier = Modifier.padding(horizontal = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).background(Surface1, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.last_speed), style = bodyStyle(12))
                Text("$lastSpeedText $unitLabel", Modifier.padding(top = 2.dp), style = displayStyle(22, FontWeight.SemiBold))
            }
            if (lastSeen != null) {
                Column(Modifier.weight(1f).background(Surface1, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.last_seen), style = bodyStyle(12))
                    Text(lastSeen, Modifier.padding(top = 2.dp), style = displayStyle(16, FontWeight.SemiBold), maxLines = 2)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Surface1, RoundedCornerShape(16.dp)).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            StrokeIcon("M12 3a9 9 0 1 0 0 18a9 9 0 0 0 0-18 M12 8v4M12 16v.5", Ice, size = 22.dp, box = 24f)
            Text(stringResource(R.string.tunnel_tip), style = bodyStyle(13, color = TextSoft))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).border(1.dp, Line3, RoundedCornerShape(26.dp))
                    .clickable(role = Role.Button, onClick = onDetails),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.gps_details), style = bodyStyle(16, FontWeight.Bold, TextPrimary)) }
            Box(
                Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(Danger)
                    .clickable(role = Role.Button, onClick = onEnd),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.end_trip), style = bodyStyle(16, FontWeight.Bold, Bg)) }
        }
    }
}

private val Color232B35 = androidx.compose.ui.graphics.Color(0xFF232B35)

private const val TIPS_AFTER_SECONDS = 20

/**
 * Shown from Start trip until the first GPS fix arrives. Shows what the phone sees (satellites, accuracy), says
 * plainly when location is off or no satellites are visible, and after a while lists what usually helps.
 */
@Composable
fun SearchingForGpsCard(gnss: com.speedometer.app.core.location.GnssSnapshot, deviceLocationOn: Boolean, onDetails: () -> Unit) {
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }
    val message = when {
        !gnss.permissionGranted -> R.string.search_no_permission
        !deviceLocationOn -> R.string.search_location_off
        gnss.satellites.isEmpty() && seconds >= TIPS_AFTER_SECONDS -> R.string.search_no_satellites
        else -> R.string.searching_first_fix
    }
    // Normal case: the Live screen looks like the design, with one slim line. The full card only appears
    // when something is actually wrong, or the fix is taking long.
    if (message == R.string.searching_first_fix && seconds < TIPS_AFTER_SECONDS) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1).clickable(role = Role.Button, onClick = onDetails)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(Amber, CircleShape))
            Text(stringResource(R.string.searching_gps) + "…", style = bodyStyle(14, FontWeight.Bold, Amber))
            if (gnss.permissionGranted && deviceLocationOn) {
                Text(stringResource(R.string.search_status, gnss.used, gnss.satellites.size, seconds), style = bodyStyle(12, color = TextMuted))
            }
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().background(Surface1, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.searching_gps) + "…", style = bodyStyle(16, FontWeight.Bold, Amber))
        Text(stringResource(message), style = bodyStyle(14, color = TextSoft))
        if (gnss.permissionGranted && deviceLocationOn) {
            Text(
                stringResource(R.string.search_status, gnss.used, gnss.satellites.size, seconds),
                style = bodyStyle(13, color = TextMuted),
            )
        }
        if (seconds >= TIPS_AFTER_SECONDS) {
            listOf(R.string.search_tip_open_sky, R.string.search_tip_mode, R.string.tip_battery).forEach {
                Text("•  " + stringResource(it), style = bodyStyle(13, color = TextSoft))
            }
        }
        GhostButton(stringResource(R.string.gps_details), onDetails)
    }
}

/** Slim banner for a dropped signal (tunnel, garage): the trip pauses itself and resumes on the next fix. */
@Composable
fun SignalDroppedBanner(onDetails: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1).clickable(role = Role.Button, onClick = onDetails)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(Amber, CircleShape))
        Text(stringResource(R.string.paused_signal_loss), Modifier.weight(1f), style = bodyStyle(14, FontWeight.Bold, TextPrimary))
        Text(stringResource(R.string.no_gps), style = bodyStyle(13, color = Danger))
    }
}
