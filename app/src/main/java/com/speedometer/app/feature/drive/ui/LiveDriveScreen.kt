package com.speedometer.app.feature.drive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.speedometer.app.core.designsystem.Segmented
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.core.designsystem.tripScoreColor
import com.speedometer.app.core.designsystem.TextSoft
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.speedometer.app.R
import com.speedometer.app.core.data.GuardOption
import com.speedometer.app.core.data.MeterStyle
import com.speedometer.app.core.data.Settings
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.Danger
import com.speedometer.app.core.designsystem.GhostButton
import com.speedometer.app.core.designsystem.Ok
import com.speedometer.app.core.designsystem.Radii
import com.speedometer.app.core.map.MapDot
import com.speedometer.app.core.map.MapLine
import com.speedometer.app.core.map.RouteMap
import com.speedometer.app.core.designsystem.rememberLayout
import com.speedometer.app.core.designsystem.SelectCard
import com.speedometer.app.core.designsystem.tabSwipe
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.trip.DistanceUnit
import com.speedometer.app.core.trip.GpsQuality
import com.speedometer.app.core.trip.GuardLevel
import com.speedometer.app.core.trip.SpeedUnit
import com.speedometer.app.core.trip.TripPhase
import com.speedometer.app.core.trip.TripState
import kotlin.math.roundToInt

private const val G_MS2 = 9.81f

internal fun clock(sec: Int) = "%02d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveDriveScreen(
    onEnd: () -> Unit, onMap: () -> Unit, onGpsDetails: () -> Unit, onHud: () -> Unit,
    onShare: () -> Unit,
    initialDigital: Boolean? = null,
    onTabSelected: (Int) -> Unit = {},
    initialSettings: Settings? = null,
    vm: LiveDriveViewModel = hiltViewModel(),
) {
    val trip by vm.trip.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val loadedSettings by vm.loadedSettings.collectAsStateWithLifecycle()
    val activeSettings = loadedSettings ?: initialSettings ?: settings
    val settingsReady = loadedSettings != null || initialSettings != null
    val style = activeSettings.meterStyle
    // The Digital tab only switches the view; it never overwrites the gauge style chosen in Gauge style, so the Gauge tab
    // always returns to that style (rings, bar, graph, a cluster...). LED digital as the chosen style opens on Digital.
    var digital by androidx.compose.runtime.saveable.rememberSaveable(style, initialDigital) {
        mutableStateOf(initialDigital ?: (style == MeterStyle.Led))
    }
    val shownStyle = if (digital) MeterStyle.Led else if (style == MeterStyle.Led) MeterStyle.Dial else style
    val history by vm.history.collectAsStateWithLifecycle()
    val zeroTo100Sec by vm.zeroTo100Sec.collectAsStateWithLifecycle()
    val topToday by vm.topTodayKmh.collectAsStateWithLifecycle()
    val gTrail by vm.gTrail.collectAsStateWithLifecycle()
    var lastKmh by rememberSaveable { mutableFloatStateOf(0f) }
    LaunchedEffect(trip.speedKmh, trip.gps) { if (trip.gps != GpsQuality.Lost && trip.speedKmh > 0f) lastKmh = trip.speedKmh }
    // Signal loss stays on the Live screen as a banner; the full-screen view flickered in and out on a weak signal.
    val signalLost = false
    val lastSeen by androidx.compose.runtime.produceState<String?>(null, signalLost) { val lost = signalLost; value = if (lost) vm.lastSeenPlace() else null }
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(activeSettings.option(GuardOption.KeepScreenOn)) {
        view.keepScreenOn = activeSettings.option(GuardOption.KeepScreenOn)
        onDispose { view.keepScreenOn = false }
    }

    val unit = activeSettings.unit
    val data = MeterData(
        speedKmh = trip.speedKmh,
        speedText = unit.fromKmh(trip.speedKmh).roundToInt().toString(),
        unit = unit.label,
        limitKmh = trip.limitKmh,
        limitText = trip.limitKmh?.let { unit.fromKmh(it).roundToInt().toString() },
        score = trip.score,
        accelMs2 = trip.accelMs2,
        history = history,
        maxKmh = activeSettings.rangeKmh,
        toUnit = { unit.fromKmh(it) },
        zeroTo100Sec = zeroTo100Sec,
        topTodayKmh = topToday,
        gLateral = trip.lateralMs2 / G_MS2,
        gLong = trip.accelMs2 / G_MS2,
        gPeak = trip.peakG,
        gTrail = gTrail,
    )
    val paused = trip.phase == TripPhase.Paused

    val layout = rememberLayout()

    val scrollableContent: @Composable ColumnScope.(Boolean) -> Unit = { meterInline ->
        Segmented(
            listOf(stringResource(com.speedometer.app.feature.gauges.ui.meterNameRes(style)), stringResource(R.string.tab_digital), stringResource(R.string.map_label)),
            if (digital) 1 else 0,
            { i ->
                when (i) {
                    0 -> digital = false
                    1 -> digital = true
                    else -> onMap()
                }
                onTabSelected(i)
            },
        )

        if (meterInline) {
            // Whatever room is left between the tabs and the stats; the meter is scaled down to fit it, so the page never scrolls.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (settingsReady) {
                    com.speedometer.app.core.designsystem.FitInside(Modifier.fillMaxSize()) { Meter(shownStyle, data) }
                }
            }
        }

        if (!meterInline && layout.isTablet) {
            val line = remember(trip.routeSize) { vm.route().map { org.maplibre.android.geometry.LatLng(it.lat, it.lon) } }
            val routeLines = remember(line) { listOf(MapLine(line, Amber)) }
            val currentDotPoint = line.lastOrNull()
            val currentDotKey = currentDotPoint?.let { it.latitude to it.longitude }
            val routeDots = remember(currentDotKey) { currentDotPoint?.let { listOf(MapDot(it, Amber, 8f, Bg)) } ?: emptyList() }
            RouteMap(
                lines = routeLines,
                dots = routeDots,
                follow = true,
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(24.dp)),
            )
        }



        // Four numbers under the gauge styles; the vehicle clusters (Sports, Superbike) carry their own readouts, so none are shown.
        if (shownStyle.generic) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(stringResource(R.string.duration_label), clock(trip.durationSec), TextPrimary, Modifier.weight(1f), StatIcons.Duration)
            Stat(
                stringResource(R.string.distance), "%.1f".format(DistanceUnit.fromKm(trip.distanceKm, activeSettings.mph)),
                TextPrimary, Modifier.weight(1f), StatIcons.Distance, suffix = if (activeSettings.mph) "mi" else "km",
            )
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(stringResource(R.string.avg_speed_label), unit.fromKmh(trip.avgKmh).roundToInt().toString(), TextPrimary, Modifier.weight(1f), StatIcons.Average, suffix = unit.label)
            Stat(stringResource(R.string.max_speed_label), unit.fromKmh(trip.maxKmh).roundToInt().toString(), TextPrimary, Modifier.weight(1f), StatIcons.Max, suffix = unit.label)
        }
        }
    }

    val bottomActions: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundAction("M4 8V5h3M20 8V5h-3M4 16v3h3M20 16v3h-3 M9 12h6", stringResource(R.string.hud_label), onHud)
            if (paused) {
                RoundAction("M7 4l13 8-13 8z", stringResource(R.string.resume), vm::resume, filled = true)
            } else {
                RoundAction("M6 5h4v14H6z M14 5h4v14h-4z", stringResource(R.string.pause), vm::pause, filled = true)
            }
            Row(
                Modifier.weight(1f).heightIn(min = 60.dp).clip(RoundedCornerShape(30.dp)).background(Danger)
                    .clickable(role = Role.Button) { vm.end(); onEnd() },
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
            ) {
                StrokeIcon("M5 5h14v14H5z", Bg, size = 20.dp, box = 24f, filled = true)
                Text(stringResource(R.string.end_trip), color = Bg, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
            }
        }


    }

    val userPaused = trip.phase == TripPhase.Paused && !trip.autoPaused
    var alertDismissed by rememberSaveable { mutableStateOf(false) }
    // Got it hides the full-screen alert until the speed is back under the limit.
    LaunchedEffect(trip.guard) { if (trip.guard == GuardLevel.Off) alertDismissed = false }
    val showAlert = guardAlertActive(trip) && !alertDismissed && !userPaused && !signalLost

    // Swiping the screen moves between the tabs, as on Home: left goes Gauge, Digital, Map; right goes back.
    val swipeNext: () -> Unit = { if (!digital) { digital = true; onTabSelected(1) } else { onTabSelected(2); onMap() } }
    val swipePrevious: () -> Unit = { if (digital) { digital = false; onTabSelected(0) } }

    Box(Modifier.fillMaxSize()) {
        if (signalLost) {
            Column(
                Modifier.fillMaxSize().background(Bg).statusBarsPadding().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = layout.contentMax).fillMaxWidth().padding(20.dp)) {
                    GpsLostView(
                        lastSpeedText = unit.fromKmh(lastKmh).roundToInt().toString(), unitLabel = unit.label, lastSeen = lastSeen,
                        onDetails = onGpsDetails, onEnd = { vm.end(); onEnd() },
                    )
                }
            }
        } else if (userPaused) {
            Column(
                Modifier.fillMaxSize().background(Bg).statusBarsPadding().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = layout.contentMax).fillMaxWidth().padding(20.dp)) {
                    PausedView(trip, unit, activeSettings.mph, vm.pausedAtMs(), onResume = vm::resume, onEnd = { vm.end(); onEnd() })
                }
            }
        } else if (layout.isLandscape) {
            BoxWithConstraints(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
                val meterPaneWidth = minOf(if (layout.isTablet) 560.dp else 420.dp, maxWidth * 0.52f)
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(meterPaneWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (settingsReady) {
                            com.speedometer.app.core.designsystem.FitInside(Modifier.fillMaxSize()) { Meter(shownStyle, data) }
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState())
                                .padding(start = 0.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            scrollableContent(false)
                        }
                        Column(
                            Modifier.fillMaxWidth().background(Bg).navigationBarsPadding()
                                .padding(end = 16.dp, bottom = 8.dp, top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            bottomActions()
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().tabSwipe(swipeNext, swipePrevious)) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    scrollableContent(true)
                }
                Column(
                    Modifier.fillMaxWidth().background(Bg).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    bottomActions()
                }
            }
        }
        // The over-the-limit screen is drawn by the nav host, over every screen, not only this one.
    }
}

@Composable
internal fun Stat(label: String, value: String, color: Color, modifier: Modifier, icon: String, suffix: String? = null) {
    Column(
        modifier.fillMaxHeight().background(Surface1, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StrokeIcon(icon, Amber, size = 18.dp, box = 24f)
            Text(
                label,
                Modifier.weight(1f),
                style = bodyStyle(12),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Bottom) {
            Text(value, style = displayStyle(22, FontWeight.SemiBold, color))
            if (suffix != null) Text(" $suffix", Modifier.padding(bottom = 4.dp), style = bodyStyle(12))
        }
    }
}

@Composable
private fun LivePill(onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .widthIn(min = 118.dp, max = 118.dp)
            .heightIn(min = 36.dp)
            .background(Surface1, RoundedCornerShape(999.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** 48dp round icon button on Surface1, as the HUD and Share buttons in the design. */
@Composable
private fun RoundAction(icon: String, label: String, onClick: () -> Unit, filled: Boolean = false) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Surface1).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { StrokeIcon(icon, TextSoft, size = 24.dp, box = 24f, filled = filled) }
}

/** Small stroke icons for the four stats under the gauge. */
internal object StatIcons {
    const val Duration = "M10 2h4 M12 14l3-3 M12 6a8 8 0 1 0 0 16a8 8 0 0 0 0-16"
    const val Distance = "M6 20a2 2 0 1 0 0-4a2 2 0 0 0 0 4z M18 8a2 2 0 1 0 0-4a2 2 0 0 0 0 4z M8 18h7a3 3 0 0 0 0-6H9a3 3 0 0 1 0-6h7"
    const val Average = "M3 15c3-8 5 6 9-2s6 4 9-4"
    const val Max = "M4.9 19a9 9 0 1 1 14.2 0 M12 13l4-4"
}
