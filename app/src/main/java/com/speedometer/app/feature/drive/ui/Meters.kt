package com.speedometer.app.feature.drive.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.drawText
import kotlin.math.roundToInt
import com.speedometer.app.R
import com.speedometer.app.core.data.MeterStyle
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.Danger
import com.speedometer.app.core.designsystem.GAUGE_MAX_KMH
import com.speedometer.app.core.designsystem.Ice
import com.speedometer.app.core.designsystem.Ok
import com.speedometer.app.core.designsystem.SpeedGauge
import com.speedometer.app.core.designsystem.Surface2
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.displayStyle


/** Everything a meter draws. Speeds are km/h; [speedText] and [unit] are already in the user's unit. */
data class MeterData(
    val speedKmh: Float,
    val speedText: String,
    val unit: String,
    val limitKmh: Float?,
    val limitText: String?,
    val score: Int,
    val accelMs2: Float,
    /** Recent speeds in km/h, oldest first, one per second (the last 60 s). */
    val history: List<Float> = emptyList(),
    /** Converts a km/h value to the display unit, for labels drawn from [history]. */
    val toUnit: (Float) -> Float = { it },
    /** Seconds the last standing-start run took to reach 100 km/h, or null if none yet. */
    val zeroTo100Sec: Int? = null,
    /** Highest speed today across saved trips and the current one, km/h. */
    val topTodayKmh: Float = 0f,
    /** Sideways and fore-aft force in g (from GPS speed and heading), the strongest so far, and the last few points. */
    val gLateral: Float = 0f,
    val gLong: Float = 0f,
    val gPeak: Float = 0f,
    val gTrail: List<Pair<Float, Float>> = emptyList(),
    /** Top of the scale in km/h (200 unless a custom vehicle model sets its own top speed). */
    val maxKmh: Float = GAUGE_MAX_KMH,
) {
    val over: Boolean get() = limitKmh != null && speedKmh > limitKmh + 5f
}

private const val LED_SEGMENTS = 30
/** The LED bar spans three quarters of the scale (150 of 200 km/h in the design). */
private const val LED_RANGE_SHARE = 0.75f
private const val ACCEL_RANGE = 4f

@Composable
internal fun animatedMeterFraction(value: Float): Float {
    val context = LocalContext.current
    return animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = if (com.speedometer.app.core.navigation.NavTransitions.reduceMotion(context)) {
            snap()
        } else {
            spring(dampingRatio = 0.88f, stiffness = 220f)
        },
        label = "meterFill",
    ).value
}

@Composable
fun Meter(style: MeterStyle, d: MeterData, modifier: Modifier = Modifier) = when (style) {
    MeterStyle.Dial -> DialMeter(d, modifier)
    MeterStyle.Neon -> NeonDialMeter(d, modifier)
    MeterStyle.Led -> LedMeter(d, modifier)
    MeterStyle.Bar -> BarMeter(d, modifier)
    MeterStyle.Rings -> RingsMeter(d, modifier)
    MeterStyle.Graph -> GraphMeter(d, modifier)
    MeterStyle.GForce -> GForceMeter(d, modifier)
    MeterStyle.Sports -> SportsMeter(d, modifier)
    MeterStyle.Superbike -> BikeMeter(d, modifier)
}

@Composable
internal fun AnimatedSpeedText(speedText: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val numericSpeed = speedText.toFloatOrNull()
    val animatedSpeed = animateFloatAsState(
        targetValue = numericSpeed ?: 0f,
        animationSpec = if (com.speedometer.app.core.navigation.NavTransitions.reduceMotion(context)) {
            snap()
        } else {
            spring(dampingRatio = 0.9f, stiffness = 360f)
        },
        label = "speedReadout",
    ).value
    Text(
        numericSpeed?.let { animatedSpeed.roundToInt().toString() } ?: speedText,
        modifier,
        style = style.copy(fontFeatureSettings = "tnum"),
    )
}

@Composable
private fun SpeedReadout(d: MeterData, size: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedSpeedText(
            d.speedText,
            displayStyle(size, androidx.compose.ui.text.font.FontWeight.Bold, color)
                .copy(letterSpacing = (-1.5).sp),
        )
        Text(
            d.unit,
            color = TextMuted, style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun DialMeter(d: MeterData, modifier: Modifier = Modifier) {
    Box(modifier.widthIn(max = 420.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        SpeedGauge(
            d.speedKmh, d.limitKmh, maxKmh = d.maxKmh,
            accent = if (d.over) Danger else Amber,
            toDisplayUnit = d.toUnit,
        )
        // A dark disc over the hub, like the Neon dial: the needle never runs through the digits, which sit centred on it.
        Box(
            Modifier.fillMaxWidth(0.30f).aspectRatio(1f).clip(CircleShape)
                .background(Color(0xFF0B1118)).border(1.5.dp, Color(0xFF35414D), CircleShape),
        )
        SpeedReadout(d, 48, if (d.over) Danger else TextPrimary)
    }
}

@Composable
fun LedMeter(d: MeterData, modifier: Modifier = Modifier) {
    val progress = animatedMeterFraction(d.speedKmh / (d.maxKmh * LED_RANGE_SHARE))
    Column(
        modifier.widthIn(max = 560.dp).fillMaxWidth()
            .background(com.speedometer.app.core.designsystem.Surface1, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SpeedReadout(d, 72, if (d.over) Danger else Amber)
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val gap = size.width * 0.008f
            val w = (size.width - gap * (LED_SEGMENTS - 1)) / LED_SEGMENTS
            for (i in 0 until LED_SEGMENTS) {
                val ledRange = d.maxKmh * LED_RANGE_SHARE
                val top = (i + 1) * (ledRange / LED_SEGMENTS)
                val lit = top <= progress * ledRange + ledRange / LED_SEGMENTS / 2
                val zone = d.limitKmh != null && top > d.limitKmh
                val h = size.height * (20f + i * 28f / LED_SEGMENTS) / 48f
                drawRoundRect(
                    brush = if (lit) {
                        Brush.verticalGradient(
                            listOf(
                                if (zone) Color(0xFFFF9880) else Color(0xFFFFD477),
                                if (zone) Danger else Amber,
                            ),
                            startY = size.height - h,
                            endY = size.height,
                        )
                    } else {
                        Brush.verticalGradient(listOf(Color(0xFF303A45), Color(0xFF1A2027)))
                    },
                    topLeft = Offset(i * (w + gap), size.height - h),
                    size = Size(w, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 3),
                )
            }
        }
    }
}

@Composable
fun BarMeter(d: MeterData, modifier: Modifier = Modifier) {
    val frac = animatedMeterFraction(d.speedKmh / d.maxKmh)
    val accel = d.accelMs2.coerceIn(-ACCEL_RANGE, ACCEL_RANGE)
    val mag = kotlin.math.abs(accel) / ACCEL_RANGE * 0.5f
    Column(
        modifier.widthIn(max = 560.dp).fillMaxWidth()
            .background(com.speedometer.app.core.designsystem.Surface1, RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            AnimatedSpeedText(
                d.speedText,
                displayStyle(56, androidx.compose.ui.text.font.FontWeight.Bold, if (d.over) Danger else TextPrimary)
                    .copy(letterSpacing = (-1.5).sp),
            )
            Text(d.unit, Modifier.padding(bottom = 8.dp), color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(28.dp)
                .background(Color(0xFF10151A), RoundedCornerShape(14.dp)),
        ) {
            Box(
                Modifier.fillMaxWidth(frac).fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(if (d.over) Danger.copy(alpha = 0.72f) else Amber.copy(alpha = 0.72f), if (d.over) Danger else Amber),
                        ),
                        RoundedCornerShape(14.dp),
                    ),
            )
            d.limitKmh?.let { limit ->
                Box(Modifier.offset(x = maxWidth * (limit / d.maxKmh).coerceIn(0f, 1f)).fillMaxHeight().widthIn(max = 3.dp)
                    .background(Color.White))
            }
        }
        d.limitText?.let { Text(stringResource(R.string.limit_label, it), color = TextMuted) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.braking), color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.accelerating), color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp).background(Surface2, RoundedCornerShape(8.dp))) {
            val left = if (accel >= 0) 0.5f else 0.5f - mag
            Box(Modifier.offset(x = maxWidth * left).width(maxWidth * mag).fillMaxHeight()
                .background(if (accel >= 0) Ok else Danger, RoundedCornerShape(8.dp)))
        }
        Text("%+.1f m/s²".format(accel), color = TextMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun RingsMeter(d: MeterData, modifier: Modifier = Modifier) {
    val progress = animatedMeterFraction(d.speedKmh / d.maxKmh)
    Box(modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            // Radii 140 / 112 / 84 in the design's 340 box.
            val box = 340f
            val stroke = size.minDimension * 20f / box
            fun ring(r: Float, color: Color, frac: Float) {
                val radius = size.minDimension * r / box
                val tl = Offset(center.x - radius, center.y - radius)
                val sz = Size(radius * 2, radius * 2)
                drawCircle(Color.Black.copy(alpha = 0.34f), radius, center, style = Stroke(stroke * 1.65f))
                drawArc(Color(0xFF28323D), -90f, 360f, false, tl, sz, style = Stroke(stroke))
                drawArc(color.copy(alpha = 0.14f), -90f, 360f, false, tl, sz, style = Stroke(stroke * 1.7f))
                drawArc(
                    Brush.linearGradient(listOf(color.copy(alpha = 0.72f), color, color.copy(alpha = 0.82f))),
                    -90f, 360f * frac.coerceIn(0f, 1f), false, tl, sz,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            ring(140f, if (d.over) Danger else Amber, progress)
            ring(112f, Ice, d.score / 100f)
            ring(84f, Ok, 0f) // Trip progress: no destination or plan to measure against yet (stub).
        }
        SpeedReadout(d, 48, if (d.over) Danger else Amber)
    }
}

private const val GRAPH_SECONDS = 60

/** Live graph, from design/MeterGraph.dc.html: the last 60 seconds of speed with the limit as a dashed line. */
@Composable
fun GraphMeter(d: MeterData, modifier: Modifier = Modifier) {
    Column(modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            AnimatedSpeedText(
                d.speedText,
                MaterialTheme.typography.displayLarge.copy(
                    color = if (d.over) Danger else TextPrimary,
                    letterSpacing = (-2).sp,
                    fontSize = 96.sp,
                ),
            )
            Text(d.unit, Modifier.padding(bottom = 14.dp), color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Column(Modifier.fillMaxWidth().background(com.speedometer.app.core.designsystem.Surface1, RoundedCornerShape(24.dp)).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.last_60_seconds), color = TextMuted, style = MaterialTheme.typography.labelSmall)
                d.limitText?.let { Text(stringResource(R.string.limit_label, it).replaceFirstChar { c -> c.uppercase() }, color = TextMuted, style = MaterialTheme.typography.labelSmall) }
            }
            Canvas(Modifier.fillMaxWidth().padding(top = 10.dp).height(200.dp)) {
                listOf(140f, 80f, 20f).forEach { y ->
                    drawLine(Color(0xFF2A3644), Offset(0f, y / 200f * size.height), Offset(size.width, y / 200f * size.height))
                }
                fun x(i: Int) = size.width * (i + (GRAPH_SECONDS - d.history.size)) / (GRAPH_SECONDS - 1).toFloat()
                fun y(v: Float) = size.height - (v / d.maxKmh).coerceIn(0f, 1f) * size.height
                d.limitKmh?.let { l ->
                    drawLine(Danger.copy(alpha = 0.7f), Offset(0f, y(l)), Offset(size.width, y(l)), 2.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                }
                if (d.history.size >= 2) {
                    val line = androidx.compose.ui.graphics.Path()
                    d.history.forEachIndexed { i, v -> if (i == 0) line.moveTo(x(i), y(v)) else line.lineTo(x(i), y(v)) }
                    val area = androidx.compose.ui.graphics.Path().apply {
                        addPath(line); lineTo(x(d.history.size - 1), size.height); lineTo(x(0), size.height); close()
                    }
                    drawPath(
                        area,
                        Brush.verticalGradient(
                            listOf(Amber.copy(alpha = 0.28f), Amber.copy(alpha = 0.015f)),
                            startY = 0f,
                            endY = size.height,
                        ),
                    )
                    drawPath(line, Amber, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(R.string.minus_60, R.string.minus_30, R.string.now_label).forEach {
                    Text(stringResource(it), color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private const val G_PAD_RANGE = 0.7f
private const val G_STRONG = 0.45f
private val PadBg = Color(0xFF10151A)
private val PadLine = Color(0xFF232B35)
private val PadRim = Color(0xFF2A3644)

/**
 * G-force pad, from design/MeterG.dc.html. The forces come from GPS speed and heading once a second, not from a
 * motion sensor, so they are smoothed and lag quick movements. Right is a right turn, up is acceleration.
 */
@Composable
fun GForceMeter(d: MeterData, modifier: Modifier = Modifier) {
    Column(modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedSpeedText(d.speedText, displayStyle(56, androidx.compose.ui.text.font.FontWeight.Bold, TextPrimary))
            Text(d.unit, Modifier.padding(bottom = 8.dp), color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f)
                .background(com.speedometer.app.core.designsystem.Surface1, RoundedCornerShape(28.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val mag = kotlin.math.hypot(d.gLateral, d.gLong)
            val dot = if (mag > G_STRONG) Danger else Amber
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                val sc = size.width / 300f
                val c = Offset(150f * sc, 150f * sc)
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFF1C252E), PadBg, Color(0xFF090D12)), c, 150f * sc),
                    150f * sc,
                    c,
                )
                drawCircle(Color.White.copy(alpha = 0.06f), 147f * sc, c, style = Stroke(1.5f * sc))
                drawCircle(PadRim, 149f * sc, c, style = Stroke(2f * sc))
                drawCircle(PadLine, 99f * sc, c, style = Stroke(2f * sc))
                drawCircle(PadLine, 49f * sc, c, style = Stroke(2f * sc))
                drawLine(PadLine, Offset(150f * sc, 6f * sc), Offset(150f * sc, 294f * sc), 2f * sc)
                drawLine(PadLine, Offset(6f * sc, 150f * sc), Offset(294f * sc, 150f * sc), 2f * sc)
                fun at(lat: Float, lon: Float) = Offset(
                    (150f + lat.coerceIn(-G_PAD_RANGE, G_PAD_RANGE) / G_PAD_RANGE * 140f) * sc,
                    (150f - lon.coerceIn(-G_PAD_RANGE, G_PAD_RANGE) / G_PAD_RANGE * 140f) * sc,
                )
                d.gTrail.forEachIndexed { i, (lat, lon) ->
                    val f = (i + 1f) / (d.gTrail.size + 1f)
                    drawCircle(Amber.copy(alpha = 0.15f + 0.4f * f), (4f + 6f * f) * sc, at(lat, lon))
                }
                val p = at(d.gLateral, d.gLong)
                drawCircle(dot.copy(alpha = 0.3f), 22f * sc, p)
                drawCircle(Bg, 14f * sc, p)
                drawCircle(dot, 10f * sc, p)
            }
            Text(stringResource(R.string.g_accel), Modifier.align(Alignment.TopCenter).padding(top = 12.dp), color = TextMuted, style = MaterialTheme.typography.labelSmall)
            Text(stringResource(R.string.g_brake), Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private const val NEON_START_DEG = 135f
private const val NEON_SWEEP_DEG = 270f

/**
 * A dark analog dial with a numbered scale, a green arc that grows with the speed, a red zone at the top of the scale,
 * a needle, and the speed in big digits in the middle. The needle moves on a spring, like a real needle with a little
 * weight.
 */
@Composable
fun NeonDialMeter(d: MeterData, modifier: Modifier = Modifier) {
    val frac = (d.speedKmh / d.maxKmh).coerceIn(0f, 1f)
    val shown = androidx.compose.animation.core.animateFloatAsState(
        frac, androidx.compose.animation.core.spring(dampingRatio = 0.88f, stiffness = 220f), label = "needle",
    ).value
    val pos = shown
    val labels = androidx.compose.ui.text.rememberTextMeasurer()
    val readout = labels.measure(
        d.speedText,
        androidx.compose.ui.text.TextStyle(
            color = if (d.over) Danger else Ok,
            fontFamily = com.speedometer.app.core.designsystem.DisplayFamily,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontSize = 52.sp,
            letterSpacing = (-1.5).sp,
            fontFeatureSettings = "tnum",
        ),
    )
    val unitReadout = labels.measure(
        d.unit,
        androidx.compose.ui.text.TextStyle(color = TextMuted, fontSize = 14.sp),
    )
    val needleColor = Ice
    Box(modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val outer = size.minDimension / 2f
            // Bezel and face.
            drawCircle(Color(0xFF35414D), outer)
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFF19232C), Color(0xFF0B1118), Color(0xFF05070A)), c, outer), outer * 0.95f)
            drawCircle(Color.White.copy(alpha = 0.05f), outer * 0.91f, c, style = Stroke(1.dp.toPx()))
            val track = outer * 0.80f
            val stroke = outer * 0.055f
            val tl = Offset(c.x - track, c.y - track)
            val sz = Size(track * 2, track * 2)
            drawArc(Color(0xFF26323D), NEON_START_DEG, NEON_SWEEP_DEG, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Ok.copy(alpha = 0.12f), NEON_START_DEG, NEON_SWEEP_DEG, false, tl, sz, style = Stroke(stroke * 1.8f, cap = StrokeCap.Round))
            drawArc(Ok, NEON_START_DEG, NEON_SWEEP_DEG * pos, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            val redFrom = 0.72f
            drawArc(Danger.copy(alpha = 0.85f), NEON_START_DEG + NEON_SWEEP_DEG * redFrom, NEON_SWEEP_DEG * (1f - redFrom), false, tl, sz, style = Stroke(stroke * 0.45f, cap = StrokeCap.Butt))
            // Scale: a tick every 10, a number every 20 (every 10 on a short scale), in the user's unit.
            val maxU = d.toUnit(d.maxKmh)
            val unitToKmh = if (d.toUnit(1f) > 0f) 1f / d.toUnit(1f) else 1f
            val step = if (maxU > 150f) 10 else 5
            var v = 0
            while (v <= maxU + 0.01f) {
                val f = (v * unitToKmh / d.maxKmh).coerceIn(0f, 1f)
                val a = Math.toRadians((NEON_START_DEG + NEON_SWEEP_DEG * f).toDouble())
                val major = v % (step * 2) == 0
                val r1 = outer * (if (major) 0.66f else 0.69f)
                val r2 = outer * 0.725f
                val ca = kotlin.math.cos(a).toFloat(); val sa = kotlin.math.sin(a).toFloat()
                drawLine(if (major) TextPrimary else TextMuted, Offset(c.x + r1 * ca, c.y + r1 * sa), Offset(c.x + r2 * ca, c.y + r2 * sa), if (major) 3f else 2f)
                if (major) {
                    val t = labels.measure(v.toString(), androidx.compose.ui.text.TextStyle(color = TextMuted, fontSize = (outer * 0.07f).toSp()))
                    val rl = outer * 0.54f
                    drawText(t, topLeft = Offset(c.x + rl * ca - t.size.width / 2f, c.y + rl * sa - t.size.height / 2f))
                }
                v += step
            }
            // Needle.
            val na = Math.toRadians((NEON_START_DEG + NEON_SWEEP_DEG * pos).toDouble())
            val tip = Offset(c.x + outer * 0.74f * kotlin.math.cos(na).toFloat(), c.y + outer * 0.74f * kotlin.math.sin(na).toFloat())
            val tail = Offset(c.x - outer * 0.10f * kotlin.math.cos(na).toFloat(), c.y - outer * 0.10f * kotlin.math.sin(na).toFloat())
            drawLine(Color.Black.copy(alpha = 0.65f), tail + Offset(0f, 2.dp.toPx()), tip + Offset(0f, 2.dp.toPx()), outer * 0.04f, StrokeCap.Round)
            drawLine(needleColor.copy(alpha = 0.32f), tail, tip, outer * 0.04f, StrokeCap.Round)
            drawLine(needleColor, tail, tip, outer * 0.015f, StrokeCap.Round)
            // Mask the needle at the readout, then center the measured text independent of digit count.
            drawCircle(Color(0xFF0B1118), outer * 0.26f, c)
            drawCircle(Color(0xFF35414D), outer * 0.26f, c, style = Stroke(outer * 0.012f))
            drawText(readout, topLeft = Offset(c.x - readout.size.width / 2f, c.y - readout.size.height / 2f))
            drawText(
                unitReadout,
                topLeft = Offset(
                    c.x - unitReadout.size.width / 2f,
                    c.y + readout.size.height / 2f + outer * 0.035f,
                ),
            )
        }
    }
}
