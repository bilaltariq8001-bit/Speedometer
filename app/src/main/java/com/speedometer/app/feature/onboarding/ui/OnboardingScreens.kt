package com.speedometer.app.feature.onboarding.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedometer.app.R
import com.speedometer.app.core.data.AppLanguage
import com.speedometer.app.core.data.Vehicle
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.BodyFamily
import com.speedometer.app.core.designsystem.DisplayFamily
import com.speedometer.app.core.designsystem.Line
import com.speedometer.app.core.designsystem.Line3
import com.speedometer.app.core.designsystem.LimitGaugeIllustration
import com.speedometer.app.core.designsystem.Ice
import com.speedometer.app.core.designsystem.Ok
import com.speedometer.app.core.designsystem.PrimaryButton
import com.speedometer.app.core.designsystem.ScoreIllustration
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.Surface2
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.TextSoft
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val ScreenPad = 20.dp

private object SplashMotion {
    val throttle = androidx.compose.animation.core.CubicBezierEasing(0.70f, 0f, 0.84f, 0f)
    val brake = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.30f, 1f)
    val reveal = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    /** Picks up quickly, then eases to a stop at the end of the scale. */
    val run = androidx.compose.animation.core.CubicBezierEasing(0.45f, 0f, 0.2f, 1f)
    val settleSpring = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.49f, stiffness = 70f)
    val textSpring = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.73f, stiffness = 120f)
}

private enum class SplashPhase { PRE, ASSEMBLE, IGNITION, LIVE }

/** A value read at draw time from the splash timeline, so a frame redraws the gauge without recomposing the screen. */
private class SplashValue(private val read: () -> Float) {
    val value: Float get() = read()
}

private const val SPLASH_RUN_MS = 2000
private const val SPLASH_METER_STAGE = 0
private const val SPLASH_PROGRESS_STAGE = 1
private const val SPLASH_WAITING_STAGE = 2
private const val SPLASH_COMPLETE_STAGE = 3
private const val SPLASH_METER_MS = 2200
private const val SPLASH_PROGRESS_MS = 2200
private const val SPLASH_MAX_SPEED = 220f

private val SplashCyan = Color(0xFF38E1FF)
private val SplashAmber = Color(0xFFFFB020)
private val SplashMint = Color(0xFF4BE39A)
private val SplashRedline = Color(0x80C0392B)

private fun speedAngle(value: Float): Float = -135f + value.coerceIn(0f, 220f) / 220f * 270f
private fun loadToSpeed(percent: Float): Float = (percent.coerceIn(0f, 100f) / 100f) * 180f

private fun display(sizeSp: Int, weight: FontWeight, color: Color = TextPrimary, lineHeightEm: Float? = null) =
    TextStyle(
        fontFamily = DisplayFamily, fontWeight = weight, fontSize = sizeSp.sp, color = color,
        lineHeight = lineHeightEm?.let { (sizeSp * it).sp } ?: TextStyle.Default.lineHeight,
    )

private fun body(sizeSp: Int, weight: FontWeight = FontWeight.Normal, color: Color = TextMuted) =
    TextStyle(fontFamily = BodyFamily, fontWeight = weight, fontSize = sizeSp.sp, color = color)

/**
 * Screen frame: optional [top] row, a scrolling middle, and a [bottom] bar pinned outside the scroll area.
 * Width is capped so tablets get a centred column.
 */
@Composable
private fun Frame(
    top: @Composable () -> Unit = {},
    bottom: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = com.speedometer.app.core.designsystem.rememberLayout().contentMax).fillMaxSize()) {
            top()
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), content = content)
            bottom()
        }
    }
}

@Composable
private fun PrimaryBar(text: String, onClick: () -> Unit) {
    Box(Modifier.padding(start = ScreenPad, end = ScreenPad, bottom = 28.dp, top = 8.dp)) {
        PrimaryButton(text, onClick)
    }
}

// ---------------------------------------------------------------- Splash

@Composable
fun SplashScreen(onDone: () -> Unit) {
    var meterProgress by rememberSaveable { mutableFloatStateOf(0f) }
    var barProgress by rememberSaveable { mutableFloatStateOf(0f) }
    var stage by rememberSaveable { mutableIntStateOf(SPLASH_METER_STAGE) }
    var navigationSent by rememberSaveable { mutableStateOf(false) }
    val meterAnimation = remember { Animatable(meterProgress) }
    val progressAnimation = remember { Animatable(barProgress) }
    fun reveal(from: Float, length: Float) =
        SplashMotion.reveal.transform(((meterProgress - from) / length).coerceIn(0f, 1f))
    fun run() = SplashMotion.run.transform(((meterProgress - 0.06f) / 0.94f).coerceIn(0f, 1f))
    val dialIn = SplashValue { reveal(0f, 0.12f) }
    val ticksIn = SplashValue { reveal(0f, 0.2f) }
    val titleIn = SplashValue { reveal(0.06f, 0.2f) }
    val taglineIn = SplashValue { reveal(0.12f, 0.2f) }
    val hintIn = SplashValue { reveal(0.25f, 0.2f) }
    val needleProgress = SplashValue { run() }
    val percent = (barProgress * 100f).toInt().coerceIn(0, 100)
    val currentOnDone by rememberUpdatedState(onDone)
    val phase = {
        when {
            meterProgress < 0.02f -> SplashPhase.PRE
            meterProgress < 0.995f -> SplashPhase.IGNITION
            else -> SplashPhase.LIVE
        }
    }

    LaunchedEffect(Unit) {
        if (stage == SPLASH_METER_STAGE) {
            coroutineScope {
                launch {
                    val remainingDuration = (SPLASH_RUN_MS * (1f - meterAnimation.value)).toInt().coerceAtLeast(1)
                    meterAnimation.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(remainingDuration, easing = SplashMotion.run),
                    ) {
                        meterProgress = value
                    }
                    meterProgress = 1f
                }
                launch {
                    val remainingDuration = (SPLASH_RUN_MS * (1f - progressAnimation.value)).toInt().coerceAtLeast(1)
                    progressAnimation.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(remainingDuration, easing = SplashMotion.run),
                    ) {
                        barProgress = value
                    }
                    barProgress = 1f
                }
            }
            stage = SPLASH_WAITING_STAGE
        }
        if (stage == SPLASH_WAITING_STAGE && !navigationSent) {
            navigationSent = true
            stage = SPLASH_COMPLETE_STAGE
            currentOnDone()
        }
    }

    val landscape = com.speedometer.app.core.designsystem.rememberLayout().isLandscape
    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF14232A), Bg, Color(0xFF05090C))))
            .systemBarsPadding(),
    ) {
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    SplashNeonGauge(
                        dialIn = { dialIn.value },
                        ticksIn = { ticksIn.value },
                        needleValue = { needleProgress.value * SPLASH_MAX_SPEED },
                        phase = phase,
                        modifier = Modifier.fillMaxHeight().widthIn(max = 320.dp).aspectRatio(1f),
                    )
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, end = 12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Speedometer",
                        Modifier.graphicsLayer { alpha = titleIn.value; translationY = (1f - titleIn.value) * 12.dp.toPx() },
                        style = display(36, FontWeight.Bold, Amber).copy(letterSpacing = 1.sp),
                    )
                    Text(
                        stringResource(R.string.tagline),
                        Modifier.padding(top = 8.dp).graphicsLayer {
                            alpha = taglineIn.value
                            translationY = (1f - taglineIn.value) * 8.dp.toPx()
                        },
                        style = body(16),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Text("$percent%", Modifier.padding(bottom = 10.dp), style = body(16, FontWeight.SemiBold, TextPrimary))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Line3)) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight()
                                .graphicsLayer {
                                    scaleX = barProgress
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                }
                                .clip(RoundedCornerShape(3.dp))
                                .background(Brush.horizontalGradient(listOf(SplashCyan, Color.White, SplashCyan, SplashAmber, SplashMint))),
                        )
                    }
                    Text(
                        stringResource(R.string.splash_hint),
                        Modifier.padding(top = 20.dp).graphicsLayer { alpha = hintIn.value },
                        style = body(14),
                    )
                }
            }
        } else {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // The gauge reads the animated values only while drawing, so a frame redraws it without recomposing the screen.
                SplashNeonGauge(
                    dialIn = { dialIn.value },
                    ticksIn = { ticksIn.value },
                    needleValue = { needleProgress.value * SPLASH_MAX_SPEED },
                    phase = phase,
                )
                Text(
                    "Speedometer",
                    Modifier.padding(top = 28.dp)
                        .graphicsLayer { alpha = titleIn.value; translationY = (1f - titleIn.value) * 12.dp.toPx() },
                    style = display(36, FontWeight.Bold, Amber).copy(letterSpacing = 1.sp))
                Text(
                    stringResource(R.string.tagline),
                    Modifier.padding(top = 8.dp)
                        .graphicsLayer { alpha = taglineIn.value; translationY = (1f - taglineIn.value) * 8.dp.toPx() },
                    style = body(16),
                )
            }
            Text(
                "$percent%",
                Modifier.padding(bottom = 10.dp),
                style = body(16, FontWeight.SemiBold, TextPrimary).copy(fontFeatureSettings = "tnum"),
            )
            Box(Modifier.fillMaxWidth(0.62f).widthIn(max = 280.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Line3)) {
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight()
                        .graphicsLayer { scaleX = barProgress; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(SplashCyan, Color.White, SplashCyan, SplashAmber, SplashMint))),
                )
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 56.dp).heightIn(min = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.splash_hint),
                    Modifier.graphicsLayer { alpha = hintIn.value },
                    style = body(14),
                )
            }
        }
        }
    }
}

@Composable
private fun SplashNeonGauge(
    dialIn: () -> Float,
    ticksIn: () -> Float,
    needleValue: () -> Float,
    phase: () -> SplashPhase,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val labelSizeSp = minOf(configuration.screenWidthDp, 320) * 0.46f * 0.071f
    val labelLayouts = remember(textMeasurer, labelSizeSp) {
        (0..11).map { index ->
            textMeasurer.measure(
                (index * 20).toString(),
                TextStyle(
                    color = Color(0xFFAAB9C0),
                    fontFamily = BodyFamily,
                    fontSize = labelSizeSp.sp,
                    fontFeatureSettings = "tnum",
                ),
            )
        }
    }
    Canvas(modifier.fillMaxWidth().widthIn(max = 320.dp).aspectRatio(1f)) {
        val dialIn = dialIn()
        val ticksIn = ticksIn()
        val needleValue = needleValue()
        val needleAlpha = 1f
        val tickCascade = 0f
        val centerPulse = 0f
        val scale = size.minDimension / 320f
        val c = center
        val outer = size.minDimension * 0.46f
        val start = 135f
        val sweep = 270f
        val faceRadius = outer * 0.94f
        val trackRadius = outer * 0.77f
        val trackBounds = Offset(c.x - trackRadius, c.y - trackRadius)
        val trackSize = Size(trackRadius * 2f, trackRadius * 2f)
        val dim = dialIn.coerceIn(0f, 1f)

        drawCircle(Color(0xFF202B32).copy(alpha = 0.65f * dim), outer, c)
        drawCircle(Color(0xFF05090D), outer * 0.965f, c)
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF15252A), Color(0xFF0B1217), Color(0xFF070B0F)),
                center = Offset(c.x, c.y - outer * 0.24f),
                radius = outer * 1.45f,
            ),
            faceRadius,
            c,
        )
        drawCircle(Color(0xFF596773).copy(alpha = 0.68f * dim), outer * 0.98f, c, style = Stroke(outer * 0.035f))
        drawCircle(Color.White.copy(alpha = 0.06f * dim), outer * 0.91f, c, style = Stroke(scale))
        drawArc(
            Color(0xFF26343C).copy(alpha = dim),
            start,
            sweep,
            false,
            trackBounds,
            trackSize,
            style = Stroke(outer * 0.07f, cap = StrokeCap.Round),
        )
        val redlineStart = start + sweep * (185f / 220f)
        val redlineSweep = sweep * (35f / 220f)
        drawArc(
            SplashRedline.copy(alpha = SplashRedline.alpha * dim),
            redlineStart,
            redlineSweep,
            false,
            trackBounds,
            trackSize,
            style = Stroke(outer * 0.052f, cap = StrokeCap.Round),
        )
        val speedFraction = (needleValue / 220f).coerceIn(0f, 1f)
        val arcSweep = sweep * speedFraction
        if (arcSweep > 0f) {
            // One soft glow and the bright core: enough to look lit, with half the drawing work per frame.
            drawArc(SplashCyan.copy(alpha = 0.22f * dim), start, arcSweep, false, trackBounds, trackSize,
                style = Stroke(outer * 0.11f, cap = StrokeCap.Round), blendMode = BlendMode.Screen)
            drawArc(SplashCyan.copy(alpha = dim), start, arcSweep, false, trackBounds, trackSize,
                style = Stroke(outer * 0.045f, cap = StrokeCap.Round), blendMode = BlendMode.Screen)
        }
        for (i in 0..44) {
            val fraction = i / 44f
            val angle = Math.toRadians((start + sweep * fraction).toDouble())
            val major = i % 4 == 0
            val inner = outer * if (major) 0.64f else 0.685f
            val outerTick = outer * 0.715f
            val dx = kotlin.math.cos(angle).toFloat()
            val dy = kotlin.math.sin(angle).toFloat()
            val tickReveal = (((ticksIn.coerceIn(0f, 1f) - fraction * 0.78f) / 0.22f).coerceIn(0f, 1f))
            val tickValue = fraction * 220f
            val active = tickValue <= needleValue || (tickCascade > 0f && fraction <= tickCascade)
            val alpha = tickReveal * (if (active) 1f else 0.45f)
            if (active) {
                drawLine(SplashCyan.copy(alpha = 0.24f * tickReveal), Offset(c.x + inner * dx, c.y + inner * dy),
                    Offset(c.x + outerTick * dx, c.y + outerTick * dy), outer * 0.04f, StrokeCap.Round, blendMode = BlendMode.Screen)
            }
            drawLine(
                (if (active) Color.White else if (major) Color(0xFFE7F0F2) else Color(0xFF87959D)).copy(alpha = alpha),
                Offset(c.x + inner * dx, c.y + inner * dy),
                Offset(c.x + outerTick * dx, c.y + outerTick * dy),
                outer * if (major) 0.012f else 0.006f,
                StrokeCap.Round,
            )
            if (major) {
                val layout = labelLayouts[i / 4]
                val r = outer * 0.545f
                drawText(
                    layout,
                    topLeft = Offset(
                        c.x + r * dx - layout.size.width / 2f,
                        c.y + r * dy - layout.size.height / 2f,
                    ),
                    alpha = tickReveal,
                )
            }
        }

        val needleRotation = speedAngle(needleValue)
        val needleRadians = Math.toRadians((needleRotation - 90f).toDouble())
        val tip = Offset(c.x + kotlin.math.cos(needleRadians).toFloat() * outer * 0.72f,
            c.y + kotlin.math.sin(needleRadians).toFloat() * outer * 0.72f)
        rotate(needleRotation, c) {
            drawLine(Color.Black.copy(alpha = 0.7f * needleAlpha), c + Offset(0f, scale * 2f),
                c + Offset(0f, -outer * 0.72f + scale * 2f), outer * 0.035f, StrokeCap.Round)
            drawLine(SplashCyan.copy(alpha = 0.22f * needleAlpha), c, c + Offset(0f, -outer * 0.72f),
                outer * 0.05f, StrokeCap.Round, blendMode = BlendMode.Screen)
            drawLine(Color(0xFFE5FBFF).copy(alpha = needleAlpha), c, c + Offset(0f, -outer * 0.72f),
                outer * 0.012f, StrokeCap.Round)
            drawCircle(Color.White.copy(alpha = needleAlpha), outer * 0.016f, c + Offset(0f, -outer * 0.72f))
        }
        if (phase() == SplashPhase.IGNITION && needleValue > 40f) {
            for (i in 0 until 8) {
                val angle = Math.toRadians(i * 45.0 + needleValue * 0.8)
                val dx = kotlin.math.cos(angle).toFloat()
                val dy = kotlin.math.sin(angle).toFloat()
                drawCircle(Color.White.copy(alpha = 0.7f * needleAlpha), outer * 0.008f,
                    tip + Offset(dx * outer * 0.035f, dy * outer * 0.035f))
            }
        }
        drawCircle(SplashMint.copy(alpha = centerPulse * 0.18f), outer * 0.22f, c, blendMode = BlendMode.Screen)
        drawCircle(Color(0xFF0A1116), outer * 0.105f, c)
        drawCircle(Color(0xFF566771).copy(alpha = dim), outer * 0.082f, c, style = Stroke(outer * 0.012f))
        drawCircle(SplashCyan.copy(alpha = needleAlpha), outer * 0.036f, c)

    }
}

// ---------------------------------------------------------------- Language

private data class Language(val code: String, val name: String, val native: String)

private val languages = AppLanguage.tags.keys.map { Language(AppLanguage.tags.getValue(it).uppercase(), it, AppLanguage.native.getValue(it)) }

@Composable
fun LanguageScreen(selected: String, onContinue: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var pendingLanguage by rememberSaveable(selected) { mutableStateOf(selected) }
    val shown = languages.filter {
        query.isBlank() || it.name.contains(query, true) || it.native.contains(query, true)
    }
    Frame(bottom = { PrimaryBar(stringResource(R.string.continue_label)) { onContinue(pendingLanguage) } }) {
        Column(Modifier.padding(start = ScreenPad, end = ScreenPad, top = 28.dp)) {
            Text(stringResource(R.string.language_title), style = display(28, FontWeight.SemiBold))
            Text(stringResource(R.string.language_hint), Modifier.padding(top = 6.dp), style = body(15))
        }
        Row(
            Modifier.padding(start = ScreenPad, end = ScreenPad, top = 20.dp).fillMaxWidth().height(52.dp)
                .background(Surface1, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StrokeIcon("M11 4a7 7 0 1 0 0 14a7 7 0 0 0 0 -14 M20 20l-4 -4", TextMuted, size = 20.dp)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(stringResource(R.string.search_languages), style = body(16))
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = body(16, color = TextPrimary), cursorBrush = SolidColor(Amber),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Column(
            Modifier.padding(start = ScreenPad, end = ScreenPad, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            shown.forEach { l ->
                val on = l.name == pendingLanguage
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .background(Surface1, RoundedCornerShape(16.dp))
                        .border(2.dp, if (on) Amber else Color.Transparent, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.RadioButton) { pendingLanguage = l.name }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(width = 36.dp, height = 28.dp).background(Surface2, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center) {
                        Text(l.code, style = body(12, FontWeight.Bold, TextSoft))
                    }
                    Text(l.name, Modifier.weight(1f), style = body(16, FontWeight.Medium, TextPrimary))
                    Text(l.native, style = body(14))
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).then(
                            if (on) Modifier.background(Bg).border(7.dp, Amber, CircleShape)
                            else Modifier.border(2.dp, Line3, CircleShape),
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------- Onboarding

private const val ONBOARDING_PAGES = 5

private val onboardingTitles = intArrayOf(R.string.ob_p1_title, R.string.ob_p2_title, R.string.ob_p3_title, R.string.ob_p4_title, R.string.ob_p5_title)
private val onboardingBodies = intArrayOf(R.string.ob_p1_body, R.string.ob_p2_body, R.string.ob_p3_body, R.string.ob_p4_body, R.string.ob_p5_body)

/**
 * Five pages, each showing something the app really does: the live speed dial, the collection of meters (the real meters,
 * cycling), the speed limit guard, trip scoring, and the vehicle choice. Pages slide sideways; the dots stretch to the page.
 */
@Composable
fun OnboardingScreen(page: Int, onPage: (Int) -> Unit, onBackHome: () -> Unit, onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduceMotion = remember { com.speedometer.app.core.navigation.NavTransitions.reduceMotion(context) }
    val landscape = com.speedometer.app.core.designsystem.rememberLayout().isLandscape
    val last = page == ONBOARDING_PAGES - 1
    // One tap finishes the flow once, however fast it is pressed.
    var finished by rememberSaveable { mutableStateOf(false) }
    val finish = { if (!finished) { finished = true; onDone() } }
    androidx.activity.compose.BackHandler {
        if (page > 0) onPage(page - 1) else onBackHome()
    }

    if (landscape) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Bg).systemBarsPadding().onboardingSwipe(page, onPage),
        ) {
            Column(Modifier.fillMaxSize().widthIn(max = 1200.dp).align(Alignment.TopCenter)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.heightIn(min = 48.dp).widthIn(min = 64.dp)
                            .then(if (page > 0) Modifier.clickable(role = Role.Button) { onPage(page - 1) } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { if (page > 0) Text(stringResource(R.string.ob_back), style = body(15)) }
                    Spacer(Modifier.weight(1f))
                    if (!last) {
                        Box(
                            Modifier.heightIn(min = 48.dp).widthIn(min = 64.dp)
                                .clickable(role = Role.Button, onClick = finish),
                            contentAlignment = Alignment.Center,
                        ) { Text(stringResource(R.string.skip), style = body(15)) }
                    }
                }
                OnboardingPageContent(
                    page,
                    reduceMotion,
                    landscape = true,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(ONBOARDING_PAGES) { i ->
                            val width by androidx.compose.animation.core.animateDpAsState(
                                if (i == page) 28.dp else 8.dp,
                                androidx.compose.animation.core.tween(260),
                                label = "dot",
                            )
                            val color by androidx.compose.animation.animateColorAsState(
                                if (i == page) Amber else Line3,
                                androidx.compose.animation.core.tween(260),
                                label = "dotColor",
                            )
                            Box(Modifier.size(width = width, height = 8.dp).background(color, RoundedCornerShape(4.dp)))
                        }
                    }
                    PrimaryButton(
                        stringResource(if (last) R.string.get_started else R.string.next),
                        onClick = { if (last) finish() else onPage((page + 1).coerceAtMost(ONBOARDING_PAGES - 1)) },
                        modifier = Modifier.widthIn(min = 180.dp, max = 280.dp),
                    )
                }
            }
        }
    } else Box(Modifier.fillMaxSize().onboardingSwipe(page, onPage)) {
        Frame(
            top = {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.heightIn(min = 48.dp).widthIn(min = 64.dp).then(if (page > 0) Modifier.clickable(role = Role.Button) { onPage(page - 1) } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { if (page > 0) Text(stringResource(R.string.ob_back), style = body(15)) }
                    Spacer(Modifier.weight(1f))
                    if (!last) {
                        Box(
                            Modifier.heightIn(min = 48.dp).widthIn(min = 64.dp).clickable(role = Role.Button, onClick = finish),
                            contentAlignment = Alignment.Center,
                        ) { Text(stringResource(R.string.skip), style = body(15)) }
                    }
                }
            },
            bottom = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)) {
                        repeat(ONBOARDING_PAGES) { i ->
                            val width by androidx.compose.animation.core.animateDpAsState(if (i == page) 28.dp else 8.dp, androidx.compose.animation.core.tween(260), label = "dot")
                            val color by androidx.compose.animation.animateColorAsState(if (i == page) Amber else Line3, androidx.compose.animation.core.tween(260), label = "dotColor")
                            Box(Modifier.size(width = width, height = 8.dp).background(color, RoundedCornerShape(4.dp)))
                        }
                    }
                    PrimaryBar(stringResource(if (last) R.string.get_started else R.string.next)) {
                        if (last) finish() else onPage((page + 1).coerceAtMost(ONBOARDING_PAGES - 1))
                    }
                }
            },
        ) {
            OnboardingPageContent(page, reduceMotion, landscape = false)
        }
    }
}

private fun Modifier.onboardingSwipe(page: Int, onPage: (Int) -> Unit): Modifier = pointerInput(page) {
    var dragX = 0f
    detectHorizontalDragGestures(
        onHorizontalDrag = { _, amount -> dragX += amount },
        onDragEnd = {
            when {
                dragX > 48.dp.toPx() && page > 0 -> onPage(page - 1)
                dragX < -48.dp.toPx() && page < ONBOARDING_PAGES - 1 -> onPage(page + 1)
            }
        },
    )
}

@Composable
private fun OnboardingPageContent(
    page: Int,
    reduceMotion: Boolean,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    androidx.compose.animation.AnimatedContent(
        modifier = modifier,
        targetState = page,
        transitionSpec = {
            if (reduceMotion) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
            else {
                val dir = if (targetState > initialState) 1 else -1
                (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { dir * it / 3 } +
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(320))) togetherWith
                    (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -dir * it / 3 } +
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)))
            }
        },
        label = "onboardingPage",
    ) { p ->
        if (landscape) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1.1f).fillMaxHeight(), contentAlignment = Alignment.Center) { OnboardingArt(p, reduceMotion) }
                Column(
                    Modifier.weight(0.9f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(onboardingTitles[p]), style = display(30, FontWeight.Bold, lineHeightEm = 1.15f), textAlign = TextAlign.Center)
                    Text(stringResource(onboardingBodies[p]), Modifier.padding(top = 12.dp), style = body(16).copy(lineHeight = 24.sp), textAlign = TextAlign.Center)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(300.dp), contentAlignment = Alignment.Center) { OnboardingArt(p, reduceMotion) }
                Column(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(onboardingTitles[p]), style = display(30, FontWeight.Bold, lineHeightEm = 1.15f), textAlign = TextAlign.Center)
                    Text(stringResource(onboardingBodies[p]), Modifier.padding(top = 12.dp), style = body(16).copy(lineHeight = 24.sp), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private val onboardingMeterStyles = listOf(
    com.speedometer.app.core.data.MeterStyle.Dial, com.speedometer.app.core.data.MeterStyle.Neon,
    com.speedometer.app.core.data.MeterStyle.Rings, com.speedometer.app.core.data.MeterStyle.Bar,
    com.speedometer.app.core.data.MeterStyle.Sports, com.speedometer.app.core.data.MeterStyle.Superbike,
)

private fun demoMeter(speed: Float) = com.speedometer.app.feature.drive.ui.MeterData(
    speed, speed.toInt().toString(), "km/h", null, null, 100, 0f, maxKmh = 200f,
)

/** The picture of each page. It draws itself in when the page appears and then loops gently; with animations off it is still. */
@Composable
private fun OnboardingArt(page: Int, reduceMotion: Boolean) {
    when (page) {
        0 -> {
            val speed = remember { androidx.compose.animation.core.Animatable(if (reduceMotion) 86f else 0f) }
            LaunchedEffect(Unit) {
                if (!reduceMotion) {
                    speed.animateTo(86f, androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                    while (true) {
                        delay(900)
                        speed.animateTo(58f, androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                        delay(700)
                        speed.animateTo(86f, androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                    }
                }
            }
            com.speedometer.app.core.designsystem.FitInside(Modifier.fillMaxSize()) {
                com.speedometer.app.feature.drive.ui.Meter(com.speedometer.app.core.data.MeterStyle.Dial, demoMeter(speed.value))
            }
        }
        1 -> {
            var index by rememberSaveable { mutableStateOf(0) }
            LaunchedEffect(Unit) { if (!reduceMotion) while (true) { delay(2200); index = (index + 1) % onboardingMeterStyles.size } }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.animation.Crossfade(index, Modifier.weight(1f).fillMaxWidth(), animationSpec = androidx.compose.animation.core.tween(450), label = "meters") { i ->
                    com.speedometer.app.core.designsystem.FitInside(Modifier.fillMaxSize()) {
                        com.speedometer.app.feature.drive.ui.Meter(onboardingMeterStyles[i], demoMeter(72f))
                    }
                }
                Text(
                    stringResource(com.speedometer.app.feature.gauges.ui.meterNameRes(onboardingMeterStyles[index])),
                    Modifier.padding(top = 6.dp), style = body(14, FontWeight.Bold, Amber),
                )
            }
        }
        2 -> {
            val drawIn = remember { androidx.compose.animation.core.Animatable(if (reduceMotion) 1f else 0f) }
            LaunchedEffect(Unit) { if (!reduceMotion) drawIn.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 120f)) }
            LimitGaugeIllustration(progress = drawIn.value)
        }
        3 -> {
            val drawIn = remember { androidx.compose.animation.core.Animatable(if (reduceMotion) 1f else 0f) }
            LaunchedEffect(Unit) { if (!reduceMotion) drawIn.animateTo(1f, androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
            ScoreIllustration(progress = drawIn.value)
        }
        else -> {
            val choices = listOf(Vehicle.Walking, Vehicle.Bicycle, Vehicle.Motorbike, Vehicle.Car)
            var on by rememberSaveable { mutableStateOf(3) }
            LaunchedEffect(Unit) { if (!reduceMotion) while (true) { delay(1500); on = (on + 1) % choices.size } }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    choices.forEachIndexed { i, v ->
                        val selected = i == on
                        val border by androidx.compose.animation.animateColorAsState(if (selected) Amber else Color.Transparent, androidx.compose.animation.core.tween(300), label = "vehicleBorder")
                        val tint by androidx.compose.animation.animateColorAsState(if (selected) Amber else TextSoft, androidx.compose.animation.core.tween(300), label = "vehicleTint")
                        Box(
                            Modifier.size(72.dp).background(Surface1, RoundedCornerShape(20.dp)).border(2.dp, border, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center,
                        ) { StrokeIcon(vehicleIcons.getValue(v), tint, size = 36.dp) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(0.8f).height(60.dp).clip(RoundedCornerShape(34.dp)).background(Amber),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(22.dp)) {
                        val sc = size.width / 24f
                        val tri = androidx.compose.ui.graphics.Path().apply { moveTo(7f * sc, 4f * sc); lineTo(20f * sc, 12f * sc); lineTo(7f * sc, 20f * sc); close() }
                        drawPath(tri, Bg)
                    }
                    Text(stringResource(R.string.start_trip), style = body(18, FontWeight.Bold, Bg))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Vehicle setup

private val vehicleIcons = mapOf(
    Vehicle.Car to "M5 20h22v-5l-3-6H8l-3 6v5z M5 15h22 M9 20v3 M23 20v3",
    Vehicle.Motorbike to "M6 22a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M26 22a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M10 22h8l4-8h-6 M16 10h5",
    Vehicle.Bicycle to "M7 23a5 5 0 1 0 0-10 5 5 0 0 0 0 10z M25 23a5 5 0 1 0 0-10 5 5 0 0 0 0 10z M7 18l5-8h8l5 8 M12 10l5 8",
    Vehicle.Walking to "M16 8a2 2 0 1 0 0-4 2 2 0 0 0 0 4z M16 10l-3 8 M16 10l4 6 M13 18l-2 8 M16 10v8l4 8",
    Vehicle.Train to "M8 6h16v14a3 3 0 0 1-3 3H11a3 3 0 0 1-3-3V6z M8 14h16 M12 27l3-4 M20 27l-3-4",
    Vehicle.Boat to "M4 20h24l-4 6H8l-4-6z M16 4v16 M16 6l8 10H16",
)

@Composable
fun VehicleScreen(
    vehicle: Vehicle, mph: Boolean, autoDetect: Boolean,
    onVehicle: (Vehicle) -> Unit, onMph: (Boolean) -> Unit, onAutoDetect: (Boolean) -> Unit,
    onFinish: () -> Unit,
) {
    val landscape = com.speedometer.app.core.designsystem.rememberLayout().isLandscape
    val title: @Composable () -> Unit = {
        Column(Modifier.padding(start = ScreenPad, end = ScreenPad, top = if (landscape) 12.dp else 28.dp)) {
            Text(stringResource(R.string.vehicle_title), style = display(28, FontWeight.SemiBold, lineHeightEm = 1.15f))
            Text(stringResource(R.string.vehicle_hint), Modifier.padding(top = 8.dp), style = body(15).copy(lineHeight = 22.sp))
        }
    }
    val choices: @Composable () -> Unit = {
        Column(
            Modifier.padding(start = ScreenPad, end = ScreenPad, top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Vehicle.entries.chunked(3).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems.forEach { v ->
                        val on = v == vehicle
                        Column(
                            Modifier.weight(1f).height(104.dp)
                                .background(Surface1, RoundedCornerShape(20.dp))
                                .border(2.dp, if (on) Amber else Color.Transparent, RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .clickable(role = Role.RadioButton) { onVehicle(v) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                        ) {
                            StrokeIcon(vehicleIcons.getValue(v), if (on) Amber else TextSoft)
                            Text(v.name, style = body(14, FontWeight.Medium, TextPrimary))
                        }
                    }
                }
            }
        }
    }
    val unitSection: @Composable () -> Unit = {
        Column(Modifier.padding(start = ScreenPad, end = ScreenPad, top = 24.dp)) {
            Text(stringResource(R.string.speed_unit), Modifier.padding(bottom = 8.dp), style = body(13))
            Row(Modifier.fillMaxWidth().background(Surface1, RoundedCornerShape(24.dp)).padding(4.dp)) {
                UnitSegment(stringResource(R.string.unit_kmh), !mph, Modifier.weight(1f)) { onMph(false) }
                UnitSegment(stringResource(R.string.unit_mph), mph, Modifier.weight(1f)) { onMph(true) }
            }
        }
    }
    val autoDetectSection: @Composable () -> Unit = {
        Row(
            Modifier.padding(start = ScreenPad, end = ScreenPad, top = 16.dp).fillMaxWidth().heightIn(min = 64.dp)
                .background(Surface1, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.auto_detect_title), style = body(16, FontWeight.Bold, TextPrimary))
                Text(stringResource(R.string.auto_detect_body), Modifier.padding(top = 2.dp), style = body(13))
            }
            DesignSwitch(autoDetect, onAutoDetect)
        }
    }

    Frame(bottom = { PrimaryBar(stringResource(R.string.finish_setup), onFinish) }) {
        if (landscape) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1.1f)) {
                    title()
                    choices()
                }
                Column(Modifier.weight(0.9f)) {
                    unitSection()
                    autoDetectSection()
                }
            }
        } else {
            title()
            choices()
            unitSection()
            autoDetectSection()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UnitSegment(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(20.dp))
            .background(if (selected) Amber else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = body(15, if (selected) FontWeight.Bold else FontWeight.Medium, if (selected) Bg else TextSoft))
    }
}

/** 52 x 32 switch with a 24 thumb, 48dp tall touch area, as in the design. */
@Composable
private fun DesignSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.size(width = 56.dp, height = 48.dp)
            .clickable(role = Role.Switch) { onChange(!checked) },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            Modifier.size(width = 52.dp, height = 32.dp)
                .background(if (checked) Amber else Line3, RoundedCornerShape(16.dp)).padding(horizontal = 4.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) { Box(Modifier.size(24.dp).background(if (checked) Bg else TextSoft, CircleShape)) }
    }
}
