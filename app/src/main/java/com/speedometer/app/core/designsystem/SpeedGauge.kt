package com.speedometer.app.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

const val GAUGE_START_DEG = 135f
const val GAUGE_SWEEP_DEG = 270f
const val GAUGE_MAX_KMH = 200f

/** A responsive, 270-degree instrument dial shared by the analog meter and live overlays. */
@Composable
fun SpeedGauge(
    speedKmh: Float,
    limitKmh: Float?,
    modifier: Modifier = Modifier,
    maxKmh: Float = GAUGE_MAX_KMH,
    accent: Color = Amber,
    toDisplayUnit: (Float) -> Float = { it },
) {
    val context = LocalContext.current
    val target = (speedKmh / maxKmh).coerceIn(0f, 1f)
    val progress = animateFloatAsState(
        targetValue = target,
        animationSpec = if (com.speedometer.app.core.navigation.NavTransitions.reduceMotion(context)) {
            snap()
        } else {
            spring(dampingRatio = 0.82f, stiffness = 145f)
        },
        label = "gaugeProgress",
    ).value
    val labels = rememberTextMeasurer()

    Box(modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .drawWithCache {
                    val sWidth = size.width
                    val sHeight = size.height
                    val c = Offset(sWidth / 2f, sHeight / 2f)
                    val minDim = size.minDimension
                    val tickCount = 40
                    val faceBrush = Brush.radialGradient(
                        colors = listOf(Color(0xFF202832), Color(0xFF11171E), Color(0xFF090D12)),
                        center = Offset(c.x, c.y - (minDim * 0.48f) * 0.18f),
                        radius = (minDim * 0.48f) * 1.35f,
                    )

                    val tickData = (0..tickCount).map { i ->
                        val outer = minDim * 0.48f
                        val fraction = i / tickCount.toFloat()
                        val angle = Math.toRadians((GAUGE_START_DEG + GAUGE_SWEEP_DEG * fraction).toDouble())
                        val major = i % 4 == 0
                        val innerRadius = outer * if (major) 0.855f else 0.875f
                        val outerRadius = outer * 0.905f
                        val start = Offset(
                            c.x + (cos(angle) * innerRadius).toFloat(),
                            c.y + (sin(angle) * innerRadius).toFloat(),
                        )
                        val end = Offset(
                            c.x + (cos(angle) * outerRadius).toFloat(),
                            c.y + (sin(angle) * outerRadius).toFloat(),
                        )
                        val textLayout = if (major) {
                            labels.measure(
                                toDisplayUnit(maxKmh * fraction).roundToInt().toString(),
                                TextStyle(color = Color(0xFFC0CAD4), fontWeight = FontWeight.Medium, fontSize = (outer * 0.078f).toSp()),
                            )
                        } else null
                        val labelPos = if (major && textLayout != null) {
                            val labelRadius = outer * 0.69f
                            Offset(
                                c.x + (cos(angle) * labelRadius).toFloat() - textLayout.size.width / 2f,
                                c.y + (sin(angle) * labelRadius).toFloat() - textLayout.size.height / 2f,
                            )
                        } else Offset.Zero
                        Triple(major, start to end, textLayout to labelPos)
                    }

                    onDrawBehind {
                        val outer = size.minDimension * 0.48f
                        val trackRadius = outer * 0.78f
                        val trackStroke = outer * 0.065f
                        val trackRect = Offset(c.x - trackRadius, c.y - trackRadius)
                        val trackSize = Size(trackRadius * 2f, trackRadius * 2f)

                        drawCircle(Color(0xFF303A45), outer, c)
                        drawCircle(Color(0xFF090C10), outer * 0.965f, c)
                        drawCircle(faceBrush, outer * 0.94f, c)
                        drawCircle(Color.White.copy(alpha = 0.045f), outer * 0.91f, c, style = Stroke(1.dp.toPx()))
                        drawCircle(Color.Black.copy(alpha = 0.5f), outer * 0.89f, c, style = Stroke(2.dp.toPx()))

                        drawArc(
                            Color(0xFF252E38), GAUGE_START_DEG, GAUGE_SWEEP_DEG, false, trackRect, trackSize,
                            style = Stroke(trackStroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            accent.copy(alpha = 0.13f), GAUGE_START_DEG, GAUGE_SWEEP_DEG, false, trackRect, trackSize,
                            style = Stroke(trackStroke * 1.8f, cap = StrokeCap.Round),
                        )

                        tickData.forEach { (major, line, labelInfo) ->
                            drawLine(
                                if (major) Color(0xFFE0E6EC) else Color(0xFF778390),
                                line.first,
                                line.second,
                                strokeWidth = outer * if (major) 0.014f else 0.007f,
                                cap = StrokeCap.Round,
                            )
                            val (text, pos) = labelInfo
                            if (major && text != null) {
                                drawText(text, topLeft = pos)
                            }
                        }

                        // Dynamic progress arc, limit marker, and needle drawn in frame loop
                        drawArc(
                            Brush.linearGradient(listOf(accent.copy(alpha = 0.72f), accent, accent.copy(alpha = 0.84f))),
                            GAUGE_START_DEG, GAUGE_SWEEP_DEG * progress, false, trackRect, trackSize,
                            style = Stroke(trackStroke, cap = StrokeCap.Round),
                        )

                        if (limitKmh != null) {
                            val fraction = (limitKmh / maxKmh).coerceIn(0f, 1f)
                            val angle = Math.toRadians((GAUGE_START_DEG + GAUGE_SWEEP_DEG * fraction).toDouble())
                            val inner = outer * 0.74f
                            val outside = outer * 0.82f
                            drawLine(
                                Color.White.copy(alpha = 0.9f),
                                Offset(c.x + (cos(angle) * inner).toFloat(), c.y + (sin(angle) * inner).toFloat()),
                                Offset(c.x + (cos(angle) * outside).toFloat(), c.y + (sin(angle) * outside).toFloat()),
                                strokeWidth = outer * 0.025f,
                                cap = StrokeCap.Round,
                            )
                        }

                        val needleAngle = Math.toRadians((GAUGE_START_DEG + GAUGE_SWEEP_DEG * progress).toDouble())
                        val dx = cos(needleAngle).toFloat()
                        val dy = sin(needleAngle).toFloat()
                        val perpendicularX = -dy
                        val perpendicularY = dx
                        val tip = Offset(c.x + dx * outer * 0.72f, c.y + dy * outer * 0.72f)
                        val tail = Offset(c.x - dx * outer * 0.12f, c.y - dy * outer * 0.12f)
                        val halfWidth = outer * 0.025f
                        val needle = Path().apply {
                            moveTo(tip.x, tip.y)
                            lineTo(c.x + perpendicularX * halfWidth, c.y + perpendicularY * halfWidth)
                            lineTo(tail.x, tail.y)
                            lineTo(c.x - perpendicularX * halfWidth, c.y - perpendicularY * halfWidth)
                            close()
                        }
                        drawPath(needle, Color.Black.copy(alpha = 0.52f), style = Stroke(width = 3.dp.toPx()))
                        drawPath(needle, Brush.linearGradient(listOf(Color.White, accent, accent.copy(alpha = 0.84f))))
                        drawCircle(Color(0xFF06090D), outer * 0.075f, c)
                        drawCircle(Color(0xFF56616D), outer * 0.061f, c)
                        drawCircle(
                            Brush.radialGradient(listOf(Color(0xFFE3E8ED), accent, Color(0xFF59636D)), c, outer * 0.06f),
                            outer * 0.05f,
                            c,
                        )
                    }
                },
            onDraw = {},
        )
    }
}
