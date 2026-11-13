package com.speedometer.app.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speedometer.app.core.navigation.NavTransitions
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlin.math.hypot

private const val FADE_IN_MS = 250
private const val FADE_OUT_MS = 350
private const val PULSE_HALF_MS = 750
private const val PULSE_LOW = 0.5f

/**
 * A red glow around the edges of the whole screen while the speed is over the limit. It is its own layer: it reads one
 * boolean ([isOver], from the same guard signal as the alerts, so it keeps the guard's hysteresis), draws a gradient built
 * once per size, and only the layer's alpha changes while it pulses (about 0.67 Hz, 1.5 s per cycle). It has no touch
 * handling, so everything under it (tabs, maps, buttons, the bottom bar) keeps working. With animations off it is a steady edge.
 * The gradient is symmetric, so it is the same in left-to-right and right-to-left layouts.
 */
@Composable
fun OverLimitGlow(isOver: StateFlow<Boolean>, modifier: Modifier = Modifier) {
    // Only this composable reads the flow, so a change recomposes this layer and not the screen under it.
    val over by isOver.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reduceMotion = remember { NavTransitions.reduceMotion(context) }
    val level = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(over) {
        if (over) {
            level.animateTo(1f, tween(FADE_IN_MS, easing = LinearEasing))
            if (!reduceMotion) {
                while (isActive) {
                    pulse.animateTo(PULSE_LOW, tween(PULSE_HALF_MS, easing = FastOutSlowInEasing))
                    pulse.animateTo(1f, tween(PULSE_HALF_MS, easing = FastOutSlowInEasing))
                }
            }
        } else {
            level.animateTo(0f, tween(FADE_OUT_MS, easing = LinearEasing))
            pulse.snapTo(1f)
        }
    }
    Box(
        modifier.fillMaxSize()
            .graphicsLayer { alpha = level.value * pulse.value }
            .drawWithCache {
                val radius = hypot(size.width, size.height) / 2f
                val brush = Brush.radialGradient(
                    0.55f to Color(0x00FF3B30),
                    0.80f to Color(0x55FF3B30),
                    1.00f to Color(0xCCFF3B30),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = radius,
                )
                onDrawBehind { drawRect(brush) }
            },
    )
}
