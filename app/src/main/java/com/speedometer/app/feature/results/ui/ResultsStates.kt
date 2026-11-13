package com.speedometer.app.feature.results.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedometer.app.R
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.Line
import com.speedometer.app.core.designsystem.Line3
import com.speedometer.app.core.designsystem.PrimaryButton
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.Surface2
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.core.trip.DistanceUnit
import com.speedometer.app.core.trip.SpeedUnit
import com.speedometer.app.core.trip.TripFormat

private val Skeleton = Color(0xFF232B35)
private const val PULSE_MS = 900
private const val PULSE_LOW = 0.45f

@Composable
private fun pulse(): Float {
    val t = rememberInfiniteTransition(label = "pulse")
    val v by t.animateFloat(
        initialValue = PULSE_LOW, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse), label = "pulse",
    )
    return v
}

/**
 * Saving state, from design/Saving.dc.html. Instead of grey placeholders only, it shows the real trip while it is
 * written: score ring, title, distance, average and max speed. The percentage follows time, not the database, because the real
 * write has no percentage to report.
 */
@Composable
fun SavingView(r: ResultsUi) {
    val p = pulse()
    val mi = r.unit == SpeedUnit.Mph
    val t = r.trip
    // The database write has no percentage to report; animate to completion over the save screen's minimum dwell.
    val progress = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1500, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
    }
    Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = com.speedometer.app.core.designsystem.rememberLayout().contentMax).fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.saving_title), Modifier.padding(top = 24.dp), style = displayStyle(22, FontWeight.SemiBold))
            Text(stringResource(R.string.saving_sub), Modifier.padding(top = 4.dp), style = bodyStyle(14))
            Box(Modifier.padding(top = 28.dp).size(200.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(200.dp)) {
                    val stroke = 16.dp.toPx()
                    val tl = Offset(stroke / 2, stroke / 2)
                    val sz = Size(size.width - stroke, size.height - stroke)
                    drawArc(Surface2, -90f, 360f, false, tl, sz, style = Stroke(stroke))
                    drawArc(Amber.copy(alpha = p), -90f, 360f * t.score / 100f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t.score.toString(), style = displayStyle(64, FontWeight.Bold))
                    Text(stringResource(R.string.smooth_score), Modifier.padding(top = 4.dp), style = bodyStyle(13))
                }
            }
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.fillMaxWidth().background(Surface1, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.title, style = bodyStyle(16, FontWeight.Bold, TextPrimary))
                    Text(
                        TripFormat.duration(t.durationSec) + " · " + stringResource(R.string.saving_tip_wait),
                        style = bodyStyle(13, color = TextMuted),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SavingStat(
                        stringResource(R.string.distance),
                        "%.1f %s".format(DistanceUnit.fromKm(t.distanceKm, mi), if (mi) "mi" else "km"), Modifier.weight(1f),
                    )
                    SavingStat(
                        stringResource(R.string.average_label),
                        r.unit.fromKmh(t.avgKmh).let { "%d".format(it.toInt()) }, Modifier.weight(1f),
                    )
                    SavingStat(
                        stringResource(R.string.max_label),
                        r.unit.fromKmh(t.maxKmh).let { "%d".format(it.toInt()) }, Modifier.weight(1f),
                    )
                }
            }
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                Box(Modifier.fillMaxWidth().height(6.dp).background(Line, RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(progress.value).height(6.dp).background(Amber, RoundedCornerShape(3.dp)))
                }
                Text(
                    "%d%% · %.1f %s, %s".format((progress.value * 100).toInt(), DistanceUnit.fromKm(t.distanceKm, mi), if (mi) "mi" else "km", TripFormat.duration(t.durationSec)),
                    Modifier.fillMaxWidth().padding(top = 8.dp), style = bodyStyle(13), textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SavingStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Surface1, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, style = bodyStyle(12))
        Text(value, Modifier.padding(top = 2.dp), style = displayStyle(22, FontWeight.SemiBold))
    }
}

/**
 * Save error, from design/SaveError.dc.html. The trip stays in memory while this shows, so it can be retried,
 * exported as a CSV file or discarded.
 */
@Composable
fun SaveErrorView(r: ResultsUi, storageFull: Boolean, onRetry: () -> Unit, onExport: () -> Unit, onDiscard: () -> Unit) {
    val mi = r.unit == SpeedUnit.Mph
    Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = com.speedometer.app.core.designsystem.rememberLayout().contentMax).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f))
            Canvas(Modifier.size(width = 180.dp, height = 160.dp)) {
                val sc = size.width / 180f
                drawRoundRect(Surface2, Offset(56f * sc, 36f * sc), Size(68f * sc, 88f * sc), CornerRadius(10f * sc))
                val tri = Path().apply {
                    moveTo(90f * sc, 52f * sc); lineTo(122f * sc, 108f * sc); lineTo(58f * sc, 108f * sc); close()
                }
                drawPath(tri, Amber, style = Stroke(6f * sc, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(Amber, Offset(90f * sc, 70f * sc), Offset(90f * sc, 88f * sc), 6f * sc, StrokeCap.Round)
                drawCircle(Amber, 3.5f * sc, Offset(90f * sc, 98f * sc))
            }
            Column(Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.save_error_title), style = displayStyle(28, FontWeight.Bold).copy(lineHeight = 32.sp),
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(if (storageFull) R.string.save_error_storage else R.string.save_error_other),
                    Modifier.padding(top = 10.dp), style = bodyStyle(15).copy(lineHeight = 22.sp), textAlign = TextAlign.Center,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp)
                    .background(Surface1, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(44.dp).background(Surface2, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    StrokeIcon("M4 7h16M4 12h16M4 17h10", Amber, size = 24.dp, box = 24f)
                }
                Column {
                    Text(r.title, style = bodyStyle(15, FontWeight.Bold, TextPrimary))
                    Text(
                        "%.1f %s · %s".format(DistanceUnit.fromKm(r.trip.distanceKm, mi), if (mi) "mi" else "km", TripFormat.duration(r.trip.durationSec)),
                        Modifier.padding(top = 2.dp), style = bodyStyle(13),
                    )
                }
            }
            Box(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(stringResource(R.string.try_again), onRetry)
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).border(1.dp, Line3, RoundedCornerShape(26.dp))
                        .clickable(role = Role.Button, onClick = onExport),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.export_file), style = bodyStyle(16, FontWeight.Bold, TextPrimary)) }
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onDiscard),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.discard_trip), style = bodyStyle(15, color = TextMuted)) }
            }
        }
    }
}
