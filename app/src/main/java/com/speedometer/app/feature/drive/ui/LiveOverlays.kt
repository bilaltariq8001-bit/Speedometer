package com.speedometer.app.feature.drive.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedometer.app.R
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.Danger
import com.speedometer.app.core.designsystem.Ice
import com.speedometer.app.core.designsystem.Ok
import com.speedometer.app.core.designsystem.SpeedGauge
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.TextSoft
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.core.designsystem.rememberLayout
import com.speedometer.app.core.designsystem.tripScoreColor
import com.speedometer.app.core.trip.DistanceUnit
import com.speedometer.app.core.trip.GpsQuality
import com.speedometer.app.core.trip.GuardLevel
import com.speedometer.app.core.trip.SpeedUnit
import com.speedometer.app.core.trip.TripState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val PausedGrey = Color(0xFF5A6776)
private val AlertBg = Color(0xFF150B0B)
private val AlertCard = Color(0xFF1F1213)
private val AlertMuted = Color(0xFFC9A9A6)
private val AlertLine = Color(0xFF6B4C4E)
private val AlertSegmentOff = Color(0xFF3A2527)

private fun mmss(sec: Long) = "%02d:%02d".format(sec / 60, sec % 60)

@Composable
private fun StatusPill(content: @Composable () -> Unit) {
    Row(
        Modifier.background(Surface1, RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/**
 * Trip paused by the user, from design/Paused.dc.html. The trip resumes by itself when the vehicle starts moving
 * and ends by itself after 30 minutes paused (both handled by the trip service).
 */
@Composable
fun PausedView(trip: TripState, unit: SpeedUnit, mi: Boolean, pausedAtMs: Long, onResume: () -> Unit, onEnd: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val pausedSec = ((now - pausedAtMs) / 1000).coerceAtLeast(0)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StatusPill {
                StrokeIcon("M6 5h4v14H6z M14 5h4v14h-4z", Amber, size = 14.dp, box = 24f, filled = true)
                Text(stringResource(R.string.paused_pill, clock(trip.durationSec)), style = bodyStyle(13, color = TextSoft))
            }
            StatusPill {
                Box(Modifier.size(8.dp).background(if (trip.gps == GpsQuality.Strong) Ok else Amber, CircleShape))
                Text(stringResource(if (trip.gps == GpsQuality.Strong) R.string.gps_strong_pill else R.string.gps_weak_pill), style = bodyStyle(13, color = TextSoft))
            }
        }
        Box(Modifier.widthIn(max = 300.dp).fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
            SpeedGauge(
                speedKmh = 0f,
                limitKmh = null,
                toDisplayUnit = { unit.fromKmh(it) },
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("0", style = displayStyle(80, FontWeight.Bold, PausedGrey).copy(lineHeight = 80.sp))
                Text(stringResource(R.string.trip_paused), Modifier.padding(top = 4.dp), style = bodyStyle(15))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PausedStat(R.string.distance, "%.1f %s".format(DistanceUnit.fromKm(trip.distanceKm, mi), if (mi) "mi" else "km"), TextPrimary, Modifier.weight(1f))
            PausedStat(R.string.paused_for, mmss(pausedSec), TextPrimary, Modifier.weight(1f))
            PausedStat(R.string.smooth_score_short, trip.score.toString(), tripScoreColor(trip.score), Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).background(Surface1, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            StrokeIcon("M12 3a9 9 0 1 0 0 18a9 9 0 0 0 0-18 M12 7v5l3 2", Ice, size = 22.dp, box = 24f)
            Text(stringResource(R.string.auto_resume_note), style = bodyStyle(13, color = TextSoft).copy(lineHeight = 19.sp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.weight(1f).heightIn(min = 60.dp).clip(RoundedCornerShape(30.dp)).border(1.dp, Danger, RoundedCornerShape(30.dp))
                    .clickable(role = Role.Button, onClick = onEnd),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.end_trip), style = bodyStyle(17, FontWeight.Bold, Danger)) }
            Row(
                Modifier.weight(1.4f).heightIn(min = 60.dp).clip(RoundedCornerShape(30.dp)).background(Amber).clickable(role = Role.Button, onClick = onResume),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
            ) {
                StrokeIcon("M7 4l13 8-13 8z", Bg, size = 20.dp, box = 24f, filled = true)
                Text(stringResource(R.string.resume), style = bodyStyle(17, FontWeight.Bold, Bg))
            }
        }
    }
}

@Composable
private fun PausedStat(label: Int, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight().background(Surface1, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(label), style = bodyStyle(12))
        Text(value, Modifier.padding(top = 2.dp), style = displayStyle(22, FontWeight.SemiBold, color))
    }
}

/**
 * Full-screen Limit Guard alert, from design/Alert.dc.html. Shown when Guard reaches level 2 or 3; Got it hides it
 * until the speed drops back under the limit, and Mute for 5 min also silences the tones.
 */
@Composable
fun GuardAlertView(trip: TripState, unit: SpeedUnit, onMute: () -> Unit, onDismiss: () -> Unit) {
    val limit = trip.limitKmh ?: return
    val over = unit.fromKmh(trip.speedKmh - limit).roundToInt().coerceAtLeast(0)
    val level = trip.guard.ordinal
    val pulse by rememberInfiniteTransition(label = "alertPulse").animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alertPulse",
    )
    val landscape = rememberLayout().isLandscape
    Box(
        Modifier.fillMaxSize().background(AlertBg.copy(alpha = 0.94f * pulse)).systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        if (landscape) {
            Row(
                Modifier.widthIn(max = 1000.dp).fillMaxSize().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                ) {
                    AlertHeader(over, unit, limit)
                    AlertGauge(trip, unit, limit, maxSize = 240.dp)
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                ) {
                    AlertLevel(level)
                    AlertActions(onMute, onDismiss, landscape = true)
                }
            }
        } else {
            Column(Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
                AlertHeader(over, unit, limit)
                AlertGauge(trip, unit, limit, maxSize = 320.dp)
                AlertLevel(level)
                AlertActions(onMute, onDismiss, landscape = false)
            }
        }
    }
}

@Composable
private fun AlertHeader(over: Int, unit: SpeedUnit, limit: Float) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp)
            .background(Danger, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StrokeIcon("M12 3l10 18H2L12 3z M12 10v5M12 18v.5", Bg, size = 28.dp, box = 24f)
        Column {
            Text(stringResource(R.string.over_limit_title), style = bodyStyle(17, FontWeight.Bold, Bg))
            Text(
                stringResource(R.string.over_limit_body, over, unit.fromKmh(limit).roundToInt()),
                Modifier.padding(top = 2.dp), style = bodyStyle(14, color = Bg),
            )
        }
    }
}

@Composable
private fun AlertGauge(trip: TripState, unit: SpeedUnit, limit: Float, maxSize: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.widthIn(max = maxSize).fillMaxWidth(), contentAlignment = Alignment.Center) {
            SpeedGauge(
                trip.speedKmh,
                limit,
                accent = Danger,
                toDisplayUnit = { unit.fromKmh(it) },
            )
            Box(
                Modifier.fillMaxWidth(0.50f).aspectRatio(1f).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(androidx.compose.ui.graphics.Color(0xFF14090A))
                    .border(1.5.dp, androidx.compose.ui.graphics.Color(0xFF4A2A2C), androidx.compose.foundation.shape.CircleShape),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    unit.fromKmh(trip.speedKmh).roundToInt().toString(),
                    style = displayStyle(64, FontWeight.Bold, Danger).copy(letterSpacing = (-2).sp, lineHeight = 64.sp),
                )
                Text(
                    unit.label + " · " + stringResource(R.string.limit_label, unit.fromKmh(limit).roundToInt().toString()),
                    Modifier.padding(top = 4.dp), style = bodyStyle(16, color = AlertMuted),
                )
            }
        }
    }
}

@Composable
private fun AlertLevel(level: Int) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(AlertCard, RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.guard_level_label), style = bodyStyle(13, color = AlertMuted))
            Text(stringResource(R.string.guard_level_of, level), style = bodyStyle(13, color = AlertMuted))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Amber, Danger, Danger).forEachIndexed { i, c ->
                Box(Modifier.weight(1f).height(8.dp).background(if (i < level) c else AlertSegmentOff, RoundedCornerShape(4.dp)))
            }
        }
        Text(stringResource(R.string.guard_levels_desc), Modifier.padding(top = 10.dp), style = bodyStyle(13, color = AlertMuted).copy(lineHeight = 19.sp))
    }
}

@Composable
private fun AlertActions(onMute: () -> Unit, onDismiss: () -> Unit, landscape: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(
            start = 20.dp,
            end = 20.dp,
            bottom = if (landscape) 8.dp else 32.dp,
            top = if (landscape) 12.dp else 32.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).border(1.dp, AlertLine, RoundedCornerShape(28.dp))
                .clickable(role = Role.Button) { onMute(); onDismiss() },
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.mute_5_min), style = bodyStyle(16, FontWeight.Bold, TextPrimary), textAlign = TextAlign.Center) }
        Box(
            Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(Amber).clickable(role = Role.Button, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.got_it), style = bodyStyle(16, FontWeight.Bold, Bg)) }
    }
}


fun guardAlertActive(trip: TripState): Boolean = trip.guard == GuardLevel.Firm || trip.guard == GuardLevel.Spoken