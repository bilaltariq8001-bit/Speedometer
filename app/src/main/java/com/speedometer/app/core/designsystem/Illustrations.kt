package com.speedometer.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Illustrations redrawn from the design's SVGs (Main, Onboard1, Onboard2). Coordinates are the SVG
// viewBox values; each composable scales them to its width.

/** Arc by centre, radius, start angle and sweep (degrees, clockwise from 3 o'clock, as in Canvas). */
private fun DrawScope.arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float, color: Color, w: Float, sc: Float) {
    drawArc(
        color, start, sweep, false,
        Offset((cx - r) * sc, (cy - r) * sc), Size(2 * r * sc, 2 * r * sc),
        style = Stroke(w * sc, cap = StrokeCap.Round),
    )
}

private fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, w: Float, sc: Float) =
    drawLine(color, Offset(x1 * sc, y1 * sc), Offset(x2 * sc, y2 * sc), w * sc, StrokeCap.Round)

private fun DrawScope.centredText(
    m: TextMeasurer, text: String, cx: Float, baseline: Float, sizePx: Float, color: Color,
    weight: FontWeight, sc: Float, family: androidx.compose.ui.text.font.FontFamily,
) {
    val layout = m.measure(text, TextStyle(color = color, fontSize = (sizePx * sc).toSp(), fontWeight = weight, fontFamily = family))
    drawText(layout, topLeft = Offset(cx * sc - layout.size.width / 2f, baseline * sc - layout.firstBaseline))
}

/** Splash logo, 168 box. */
@Composable
/** [sweep] 0 to 1 moves the needle and the amber arc from rest to their drawn position (1 is the still logo); a spring may pass 1 a little. */
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 168.dp, sweep: Float = 1f) {
    Canvas(modifier.size(size)) {
        val sc = this.size.width / 168f
        // Track and amber arc share a centre of about (84, 98.85) and radius 66.
        arc(84f, 98.85f, 66f, 144.1f, 251.8f, Line, 12f, sc)
        arc(84f, 98.85f, 66f, 144.1f, 146.4f * sweep.coerceIn(0.001f, 1f), Amber, 12f, sc)
        if (sweep >= 1f && sweep <= 1.0001f) line(84f, 96f, 118f, 58f, TextPrimary, 7f, sc)
        else {
            val a = Math.toRadians((144.1 + (311.8 - 144.1) * sweep).toDouble())
            line(84f, 96f, 84f + 51f * kotlin.math.cos(a).toFloat(), 96f + 51f * kotlin.math.sin(a).toFloat(), TextPrimary, 7f, sc)
        }
        drawCircle(Amber, 10f * sc, Offset(84f * sc, 96f * sc))
    }
}

/** Onboarding 1: gauge with a 60 limit sign and the needle slightly past it. */
@Composable
fun LimitGaugeIllustration(modifier: Modifier = Modifier, progress: Float = 1f) {
    val m = rememberTextMeasurer()
    Canvas(modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f)) {
        val sc = size.width / 320f
        drawCircle(Surface1, 150f * sc, Offset(160f * sc, 160f * sc))
        arc(160f, 180.7f, 120f, 144.7f, 250.6f, Line, 16f, sc)
        arc(160f, 180.7f, 120f, 144.7f, 145.1f * progress.coerceIn(0.001f, 1f), Amber, 16f, sc)
        if (progress >= 0.9f) arc(160f, 180.7f, 120f, 289.8f, 15.1f * ((progress - 0.9f) / 0.1f).coerceIn(0f, 1f), Danger, 16f, sc)
        if (progress >= 1f && progress <= 1.0001f) line(160f, 170f, 214f, 98f, TextPrimary, 6f, sc)
        else {
            val a = Math.toRadians((144.7 + (306.9 - 144.7) * progress).toDouble())
            line(160f, 170f, 160f + 90f * kotlin.math.cos(a).toFloat(), 170f + 90f * kotlin.math.sin(a).toFloat(), TextPrimary, 6f, sc)
        }
        drawCircle(Amber, 10f * sc, Offset(160f * sc, 170f * sc))
        val sign = progress.coerceIn(0f, 1f)
        drawCircle(TextPrimary.copy(alpha = sign), 30f * sc * (0.6f + 0.4f * sign), Offset(160f * sc, 236f * sc))
        drawCircle(Danger.copy(alpha = sign), 21f * sc * (0.6f + 0.4f * sign), Offset(160f * sc, 236f * sc), style = Stroke(6f * sc))
        if (sign > 0.6f) centredText(m, "60", 160f, 244f, 22f, Bg.copy(alpha = ((sign - 0.6f) / 0.4f).coerceIn(0f, 1f)), FontWeight.Bold, sc, DisplayFamily)
    }
}

/** Onboarding 2: Smooth score ring at 86 with a speed ribbon beneath. */
@Composable
fun ScoreIllustration(modifier: Modifier = Modifier, progress: Float = 1f) {
    val m = rememberTextMeasurer()
    Canvas(modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f)) {
        val sc = size.width / 320f
        drawCircle(Surface1, 150f * sc, Offset(160f * sc, 160f * sc))
        drawCircle(Line, 78f * sc, Offset(160f * sc, 140f * sc), style = Stroke(16f * sc))
        val p = progress.coerceIn(0f, 1f)
        if (p > 0f) arc(160f, 140f, 78f, -90f, 360f * 0.86f * p, Amber, 16f, sc)
        centredText(m, (86 * p).toInt().toString(), 160f, 152f, 48f, TextPrimary, FontWeight.Bold, sc, DisplayFamily)
        centredText(m, "Smooth score", 160f, 176f, 13f, TextMuted, FontWeight.Normal, sc, BodyFamily)
        val ribbon = Path().apply {
            moveTo(50f * sc, 270f * sc)
            listOf(80f to 250f, 110f to 258f, 140f to 236f, 170f to 246f, 200f to 232f, 230f to 252f, 270f to 240f)
                .forEach { (x, y) -> lineTo(x * sc, y * sc) }
        }
        drawPath(ribbon, Ice, style = Stroke(4f * sc, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Amber, 7f * sc, Offset(200f * sc, 232f * sc))
    }
}

/** Stroke icon from an SVG path string in a 32 unit box (vehicle picker icons). */
@Composable
fun StrokeIcon(
    pathData: String, color: Color, modifier: Modifier = Modifier, size: Dp = 32.dp, box: Float = 32f, filled: Boolean = false,
) {
    val path = PathParser().parsePathString(pathData).toPath()
    Canvas(modifier.size(size)) {
        val sc = this.size.width / box
        withTransform({ scale(sc, sc, Offset.Zero) }) {
            if (filled) drawPath(path, color)
            else drawPath(path, color, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
