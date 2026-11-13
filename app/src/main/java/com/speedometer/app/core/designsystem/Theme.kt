package com.speedometer.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

object Radii {
    val chip = 16.dp
    val card = 20.dp
    val segmented = 24.dp
    val sheet = 28.dp
    val pill = 999.dp
}

val SpeedometerShapes = Shapes(
    small = RoundedCornerShape(Radii.chip),
    medium = RoundedCornerShape(Radii.card),
    large = RoundedCornerShape(Radii.sheet),
)

private val Scheme = darkColorScheme(
    background = Bg,
    surface = Surface1,
    surfaceVariant = Surface2,
    surfaceContainer = Surface3,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextMuted,
    primary = Amber,
    onPrimary = Bg,
    secondary = Ice,
    error = Danger,
    outline = Line2,
    outlineVariant = Line,
)

/**
 * The app's look. The phone's font size setting is kept within a narrow range (0.9x to 1.15x): the buttons, tiles and
 * bars here are sized in dp for the design, so a very large or small system font would stretch some of them and not
 * others. Within this range text still follows the user's setting.
 */
@Composable
fun SpeedometerTheme(content: @Composable () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
            density.density, density.fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE),
        ),
    ) {
        MaterialTheme(
            colorScheme = Scheme,
            typography = SpeedometerTypography,
            shapes = SpeedometerShapes,
            content = content,
        )
    }
}

private const val MIN_FONT_SCALE = 0.9f
private const val MAX_FONT_SCALE = 1.15f
