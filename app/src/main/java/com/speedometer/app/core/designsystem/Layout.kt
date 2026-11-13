package com.speedometer.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Smallest side that counts as a tablet. */
private const val TABLET_MIN_SIDE_DP = 600

/** Portrait width from which content is centred with larger sizes (design: tablet portrait, 700dp and up). */
private const val TABLET_PORTRAIT_DP = 700

/**
 * Window shape from the design spec: landscape windows get a left rail and two panes; tablet portrait keeps the
 * bottom bar with centred, larger content. Driven by the window size, never the device model.
 */
@Immutable
data class WindowLayout(val isLandscape: Boolean, val isTablet: Boolean) {
    /** Widest a single column of content gets. */
    val contentMax: Dp get() = when {
        isLandscape && isTablet -> 1200.dp
        isLandscape -> 960.dp
        isTablet -> 720.dp
        else -> 560.dp
    }
}

@Composable
fun rememberLayout(): WindowLayout {
    val c = LocalConfiguration.current
    val w = c.screenWidthDp
    val h = c.screenHeightDp
    val landscape = w > h
    val tablet = minOf(w, h) >= TABLET_MIN_SIDE_DP || (!landscape && w >= TABLET_PORTRAIT_DP)
    return WindowLayout(isLandscape = landscape, isTablet = tablet)
}

/**
 * Draws [content] at its natural size when it fits the space given, and scaled down evenly (about its centre) when it
 * is taller than that space. Meters have a fixed shape that grows with the width, so on a short screen they would
 * run over the cards below; this keeps them whole and in place instead. With unbounded height nothing is scaled.
 */
@Composable
fun FitInside(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier.clipToBounds()) { measurables, constraints ->
        val placeable = measurables.first().measure(
            androidx.compose.ui.unit.Constraints(maxWidth = constraints.maxWidth),
        )
        val boundedHeight = constraints.maxHeight != androidx.compose.ui.unit.Constraints.Infinity
        val scale = if (boundedHeight && placeable.height > constraints.maxHeight) {
            constraints.maxHeight.toFloat() / placeable.height
        } else 1f
        val w = constraints.maxWidth
        val h = if (boundedHeight) constraints.maxHeight else placeable.height
        layout(w, h) {
            placeable.placeWithLayer((w - placeable.width) / 2, (h - placeable.height) / 2) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
}
