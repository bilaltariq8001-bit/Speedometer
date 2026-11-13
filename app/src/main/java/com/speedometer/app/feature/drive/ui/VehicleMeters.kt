package com.speedometer.app.feature.drive.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedometer.app.R
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.Danger
import com.speedometer.app.core.designsystem.Ice
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.Surface2
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.TextSoft
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.core.trip.GearEstimator
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val COUPE_MAX_KMH = 260f
private const val COUPE_REDZONE_FROM = 200f
private const val BIKE_MAX_KMH = 300f
private const val BIKE_SEGMENTS = 32
private const val BIKE_RED_FROM = 200f
private val SegmentOff = Color(0xFF232B35)

/** A row of mode chips. The modes are labels for how the rider feels; the app cannot change the vehicle. */
@Composable
private fun ModeChips(options: List<Int>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            // Chips share the row equally and never wrap their label ("Track" used to break into "Trac" and "k" on narrow phones).
            Box(
                Modifier.weight(1f).height(36.dp).background(if (on) Amber else Surface1, RoundedCornerShape(18.dp))
                    .padding(horizontal = 4.dp)
                    .clickable(role = Role.RadioButton) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(label), maxLines = 1, softWrap = false,
                    style = bodyStyle(12, if (on) FontWeight.Bold else FontWeight.Medium, if (on) Bg else TextSoft),
                )
            }
        }
    }
}


@Composable
private fun GearBox(gear: String, width: Int, height: Int, size: Int, radius: Int) {
    Column(
        Modifier.size(width = width.dp, height = height.dp).background(Surface1, RoundedCornerShape(radius.dp)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.gear_est), style = bodyStyle(11))
        Text(gear, style = displayStyle(size, FontWeight.Bold, Amber).copy(lineHeight = size.sp))
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Surface1, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, style = bodyStyle(12))
        Text(value, Modifier.padding(top = 2.dp), style = displayStyle(24, FontWeight.SemiBold))
    }
}

/**
 * Sports coupe cluster, from design/MeterSports.dc.html: a 0 to 260 dial, gear, drive modes, a 0 to 100 timer and
 * top speed today. The design's oil and coolant temperatures need data from the car (OBD), which the app does not
 * have, so they are left out.
 */
@Composable
fun SportsMeter(d: MeterData, modifier: Modifier = Modifier) {
    var mode by rememberSaveable { mutableIntStateOf(1) }
    val progress = animatedMeterFraction(d.speedKmh / COUPE_MAX_KMH)
    val labels = rememberTextMeasurer()
    Column(modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(340f / 300f), contentAlignment = Alignment.TopCenter) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(340f / 300f)) {
                val sc = size.width / 340f
                val r = 130f * sc
                val c = Offset(170f * sc, 170f * sc)
                val tl = Offset(c.x - r, c.y - r)
                val sz = Size(2 * r, 2 * r)
                drawCircle(Color(0xFF2B3540), r, c)
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFF1A222B), Color(0xFF10161D), Color(0xFF090D12)), c, r),
                    r * 0.96f,
                    c,
                )
                drawCircle(Color.White.copy(alpha = 0.045f), r * 0.9f, c, style = Stroke(1.dp.toPx()))
                val stroke = Stroke(14f * sc, cap = StrokeCap.Round)
                drawArc(Surface2, 135f, 270f, false, tl, sz, style = stroke)
                drawArc(Danger.copy(alpha = 0.35f), 135f + 270f * COUPE_REDZONE_FROM / COUPE_MAX_KMH, 270f * (1f - COUPE_REDZONE_FROM / COUPE_MAX_KMH), false, tl, sz, style = stroke)
                drawArc(
                    if (d.speedKmh > COUPE_REDZONE_FROM) Danger else Amber,
                    135f, 270f * progress, false, tl, sz, style = stroke,
                )
                for (i in 0..40) {
                    val fraction = i / 40f
                    val angle = Math.toRadians((135f + 270f * fraction).toDouble())
                    val major = i % 4 == 0
                    val inner = r * if (major) 0.79f else 0.83f
                    val outer = r * 0.91f
                    drawLine(
                        if (major) TextPrimary else TextMuted,
                        Offset(c.x + (cos(angle) * inner).toFloat(), c.y + (sin(angle) * inner).toFloat()),
                        Offset(c.x + (cos(angle) * outer).toFloat(), c.y + (sin(angle) * outer).toFloat()),
                        if (major) 2.4f * sc else 1.2f * sc,
                        StrokeCap.Round,
                    )
                    if (major) {
                        val value = d.toUnit(COUPE_MAX_KMH * fraction).roundToInt().toString()
                        val text = labels.measure(
                            value,
                            TextStyle(color = TextSoft, fontSize = (r * 0.09f).toSp(), fontWeight = FontWeight.Medium),
                        )
                        val labelRadius = r * 0.68f
                        drawText(
                            text,
                            topLeft = Offset(
                                c.x + (cos(angle) * labelRadius).toFloat() - text.size.width / 2f,
                                c.y + (sin(angle) * labelRadius).toFloat() - text.size.height / 2f,
                            ),
                        )
                    }
                }
                val a = Math.toRadians((135f + 270f * progress).toDouble())
                val dx = cos(a).toFloat()
                val dy = sin(a).toFloat()
                val hub = c
                val tip = Offset(hub.x + dx * 108f * sc, hub.y + dy * 108f * sc)
                val tail = Offset(hub.x - dx * 18f * sc, hub.y - dy * 18f * sc)
                val px = -dy
                val py = dx
                val needle = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(hub.x + px * 4f * sc, hub.y + py * 4f * sc)
                    lineTo(tail.x, tail.y)
                    lineTo(hub.x - px * 4f * sc, hub.y - py * 4f * sc)
                    close()
                }
                drawPath(needle, Color.Black.copy(alpha = 0.6f), style = Stroke(2.dp.toPx()))
                drawPath(needle, Brush.linearGradient(listOf(Color.White, Amber, Color(0xFFCF7200))))
                drawCircle(TextPrimary, 18f * sc, hub)
                drawCircle(Color(0xFF25303A), 12f * sc, hub)
                drawCircle(Amber, 7f * sc, hub)
            }
            // Sized and placed from the dial's own width, so the digits always sit below the hub, whatever the screen width.
            val digitSize = with(androidx.compose.ui.platform.LocalDensity.current) { (maxWidth * 0.17f).toSp() }
            Column(Modifier.padding(top = maxWidth * 0.60f), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedSpeedText(
                    d.speedText,
                    displayStyle(64, FontWeight.Bold, if (d.over) Danger else TextPrimary)
                        .copy(letterSpacing = (-1).sp, fontSize = digitSize, lineHeight = digitSize),
                )
                Text(d.unit, style = bodyStyle(13))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            GearBox(GearEstimator.coupe(d.speedKmh), 88, 88, 48, 24)
            ModeChips(listOf(R.string.mode_comfort, R.string.mode_sport, R.string.mode_track), mode, { mode = it }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBox(stringResource(R.string.zero_to_100), d.zeroTo100Sec?.let { "$it s" } ?: "--", Modifier.weight(1f))
            StatBox(stringResource(R.string.top_today), d.toUnit(d.topTodayKmh).roundToInt().toString(), Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().background(Surface1, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            StrokeIcon("M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3z", Ice, size = 22.dp, box = 24f)
            Text(stringResource(R.string.track_note), style = bodyStyle(13, color = TextSoft))
        }
    }
}


@Composable
fun BikeMeter(d: MeterData, modifier: Modifier = Modifier) {
    var mode by rememberSaveable { mutableIntStateOf(1) }
    val progress = animatedMeterFraction(d.speedKmh / BIKE_MAX_KMH)
    Column(modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Canvas(Modifier.fillMaxWidth().height(44.dp)) {
                val gap = 3.dp.toPx()
                val w = (size.width - gap * (BIKE_SEGMENTS - 1)) / BIKE_SEGMENTS
                for (i in 0 until BIKE_SEGMENTS) {
                    val top = (i + 1) * (BIKE_MAX_KMH / BIKE_SEGMENTS)
                    val lit = top <= progress * BIKE_MAX_KMH + 4f
                    val h = (16f + i * 26f / BIKE_SEGMENTS).dp.toPx()
                    val topLeft = Offset(i * (w + gap), size.height - h)
                    val segmentSize = Size(w, h)
                    if (lit) {
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(
                                    if (top > BIKE_RED_FROM) Color(0xFFFF8B72) else Color(0xFFFFD477),
                                    if (top > BIKE_RED_FROM) Danger else Amber,
                                ),
                                startY = size.height - h,
                                endY = size.height,
                            ),
                            topLeft = topLeft,
                            size = segmentSize,
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    } else {
                        drawRoundRect(SegmentOff, topLeft, segmentSize, CornerRadius(2.dp.toPx()))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0", style = bodyStyle(12))
                Text(stringResource(R.string.speed_word), style = bodyStyle(12))
                Text("${d.toUnit(BIKE_MAX_KMH).roundToInt()} ${d.unit}", style = bodyStyle(12))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedSpeedText(
                    d.speedText,
                    displayStyle(112, FontWeight.Bold, if (d.over) Danger else TextPrimary)
                        .copy(lineHeight = 106.sp, letterSpacing = (-3).sp),
                )
                Text(d.unit.uppercase(), style = bodyStyle(16).copy(letterSpacing = 2.sp))
            }
            GearBox(GearEstimator.bike(d.speedKmh), 84, 104, 56, 22)
        }
        LeanAngle(d.gLateral)
        ModeChips(listOf(R.string.mode_rain, R.string.mode_road, R.string.mode_track), mode, { mode = it }, Modifier.fillMaxWidth())
    }
}

private const val LEAN_SCALE_DEG = 55f

/**
 * Lean angle from design/MeterBike.dc.html. It is estimated from the sideways force GPS gives (a bike leans at
 * about atan(lateral force in g)), so it lags quick changes and reads 0 while going straight or stopped.
 */
@Composable
private fun LeanAngle(gLateral: Float) {
    val lean = Math.toDegrees(kotlin.math.atan(gLateral.toDouble())).toFloat().coerceIn(-LEAN_SCALE_DEG, LEAN_SCALE_DEG)
    val side = if (lean < -1f) stringResource(R.string.lean_left_prefix) + " " else if (lean > 1f) stringResource(R.string.lean_right_prefix) + " " else ""
    Column(Modifier.fillMaxWidth().background(Surface1, RoundedCornerShape(24.dp)).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.lean_angle), style = bodyStyle(13))
            Text("$side${kotlin.math.abs(lean).roundToInt()}°", style = displayStyle(26, FontWeight.Bold))
        }
        Canvas(Modifier.fillMaxWidth().padding(top = 6.dp).aspectRatio(318f / 104f)) {
            val sc = size.width / 318f
            val c = Offset(159f * sc, 116.1f * sc)
            val r = 130f * sc
            drawArc(SegmentOff, 187.1f, 165.8f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(8f * sc, cap = StrokeCap.Round))
            val theta = Math.toRadians((lean / LEAN_SCALE_DEG * 74.25f).toDouble())
            val pivot = Offset(159f * sc, 99f * sc)
            val len = 94f * sc
            fun at(f: Float) = Offset(pivot.x + (sin(theta) * len * f).toFloat(), pivot.y - (cos(theta) * len * f).toFloat())
            drawLine(TextPrimary, pivot, at(1f), 6f * sc, StrokeCap.Round)
            drawLine(Amber, at(0.65f), at(1f), 6f * sc, StrokeCap.Round)
            drawCircle(TextPrimary, 11f * sc, pivot)
            drawCircle(Bg, 7f * sc, pivot)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.lean_scale_left), style = bodyStyle(12))
            Text("0", style = bodyStyle(12))
            Text(stringResource(R.string.lean_scale_right), style = bodyStyle(12))
        }
    }
}
