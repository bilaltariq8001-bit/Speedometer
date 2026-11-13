package com.speedometer.app.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.speedometer.app.core.data.MeterStyle
import com.speedometer.app.core.data.Vehicle
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.SpeedometerTheme
import com.speedometer.app.core.trip.GuardLevel
import com.speedometer.app.core.trip.SpeedUnit
import com.speedometer.app.core.trip.TripState
import com.speedometer.app.feature.drive.ui.GpsLostView
import com.speedometer.app.feature.drive.ui.GuardAlertView
import com.speedometer.app.feature.drive.ui.Meter
import com.speedometer.app.feature.drive.ui.MeterData
import com.speedometer.app.feature.drive.ui.PausedView
import com.speedometer.app.feature.drive.ui.WeakGpsNotice
import com.speedometer.app.feature.onboarding.ui.LanguageScreen
import com.speedometer.app.feature.onboarding.ui.OnboardingScreen
import com.speedometer.app.feature.onboarding.ui.VehicleScreen

/**
 * Previews at the sizes the design is checked at: small phone, phone, foldable cover, landscape phone, tablet,
 * 200% font scale and a right-to-left language. Android Studio shows them side by side.
 */
@Preview(name = "Small 320x568", widthDp = 320, heightDp = 568, showBackground = true, backgroundColor = 0xFF0B0E11)
@Preview(name = "Phone 360x800", widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFF0B0E11)
@Preview(name = "Large 412x915", widthDp = 412, heightDp = 915, showBackground = true, backgroundColor = 0xFF0B0E11)
@Preview(name = "Foldable cover 280x653", widthDp = 280, heightDp = 653, showBackground = true, backgroundColor = 0xFF0B0E11)
@Preview(name = "Landscape 800x360", widthDp = 800, heightDp = 360, showBackground = true, backgroundColor = 0xFF0B0E11)
@Preview(name = "Tablet 820x1180", widthDp = 820, heightDp = 1180, showBackground = true, backgroundColor = 0xFF0B0E11)
@Preview(name = "Font 200%", widthDp = 360, heightDp = 800, fontScale = 2f, showBackground = true, backgroundColor = 0xFF0B0E11)
@Preview(name = "RTL Arabic", widthDp = 360, heightDp = 800, locale = "ar", showBackground = true, backgroundColor = 0xFF0B0E11)
annotation class DesignSizes

private val sampleTrip = TripState(
    speedKmh = 74f, distanceKm = 12.4f, durationSec = 1458, maxKmh = 84f, avgKmh = 47f, score = 91,
    limitKmh = 60f, guard = GuardLevel.Firm, searching = false,
)

private fun sampleMeter(unit: SpeedUnit = SpeedUnit.Kmh) = MeterData(
    speedKmh = 74f, speedText = unit.fromKmh(74f).toInt().toString(), unit = unit.label,
    limitKmh = 60f, limitText = "60", score = 91, accelMs2 = 0.8f,
    history = List(60) { 50f + 20f * kotlin.math.sin(it / 6f) },
    toUnit = { unit.fromKmh(it) }, zeroTo100Sec = 5, topTodayKmh = 118f,
    gLateral = 0.2f, gLong = 0.15f, gPeak = 0.4f, gTrail = List(8) { 0.03f * it to 0.02f * it },
)

@Composable
private fun Frame(content: @Composable () -> Unit) {
    SpeedometerTheme { Box(Modifier.fillMaxSize().background(Bg).padding(16.dp)) { content() } }
}

@DesignSizes @Composable private fun LanguagePreview() = SpeedometerTheme { LanguageScreen("English") {} }

@DesignSizes @Composable private fun Onboarding1Preview() = SpeedometerTheme { OnboardingScreen(0, {}, {}, {}) }

@DesignSizes @Composable private fun Onboarding2Preview() = SpeedometerTheme { OnboardingScreen(1, {}, {}, {}) }

@DesignSizes @Composable
private fun VehiclePreview() = SpeedometerTheme { VehicleScreen(Vehicle.Car, false, true, {}, {}, {}, {}) }

@DesignSizes @Composable private fun DialPreview() = Frame { Meter(MeterStyle.Dial, sampleMeter()) }

@DesignSizes @Composable private fun LedPreview() = Frame { Meter(MeterStyle.Led, sampleMeter()) }

@DesignSizes @Composable private fun BarPreview() = Frame { Meter(MeterStyle.Bar, sampleMeter()) }

@DesignSizes @Composable private fun RingsPreview() = Frame { Meter(MeterStyle.Rings, sampleMeter()) }

@DesignSizes @Composable private fun GraphPreview() = Frame { Meter(MeterStyle.Graph, sampleMeter()) }

@DesignSizes @Composable private fun GForcePreview() = Frame { Meter(MeterStyle.GForce, sampleMeter()) }

@DesignSizes @Composable private fun SportsPreview() = Frame { Meter(MeterStyle.Sports, sampleMeter()) }

@DesignSizes @Composable private fun SuperbikePreview() = Frame { Meter(MeterStyle.Superbike, sampleMeter()) }

@DesignSizes @Composable private fun WeakGpsPreview() = Frame { WeakGpsNotice(accuracyM = 38f) }

@DesignSizes @Composable
private fun GpsLostPreview() = Frame { GpsLostView("48", "km/h", lastSeen = "Margalla Road", onDetails = {}, onEnd = {}) }

@DesignSizes @Composable
private fun PausedPreview() = Frame {
    PausedView(sampleTrip.copy(guard = GuardLevel.Off, speedKmh = 0f), SpeedUnit.Kmh, false, System.currentTimeMillis() - 130_000, {}, {})
}

@DesignSizes @Composable
private fun GuardAlertPreview() = SpeedometerTheme { GuardAlertView(sampleTrip, SpeedUnit.Kmh, onMute = {}, onDismiss = {}) }
