package com.speedometer.app.feature.drive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import com.speedometer.app.core.designsystem.Line3
import com.speedometer.app.core.designsystem.rememberLayout
import com.speedometer.app.core.designsystem.tabSwipe
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.speedometer.app.R
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.Danger
import androidx.compose.foundation.border
import com.speedometer.app.core.designsystem.Nav
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.TextSoft
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.core.designsystem.tripScoreColor
import com.speedometer.app.core.map.MapDot
import com.speedometer.app.core.map.CameraTarget
import com.speedometer.app.core.designsystem.Ice
import com.speedometer.app.core.map.MapLine
import com.speedometer.app.core.map.RouteMap
import com.speedometer.app.core.location.GeoPoint
import com.speedometer.app.core.location.toCoordinateText
import com.speedometer.app.core.trip.DistanceUnit
import org.maplibre.android.geometry.LatLng
import kotlin.math.roundToInt

/**
 * Live map, from design/Map.dc.html: the route so far and the current position, with speed and trip stats.
 * The next-zone card uses your saved zones. Arrival time is left out: the app has no destination.
 */
@Composable
fun LiveMapScreen(onBack: () -> Unit, onEnd: () -> Unit, vm: LiveDriveViewModel = hiltViewModel()) {
    val trip by vm.trip.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val unit = settings.unit
    val mi = settings.mph
    val line = remember(trip.routeSize) { vm.route().map { LatLng(it.lat, it.lon) } }
    // The position shows even before the first GPS fix has made a route point: the latest position from any source.
    val tripHere = trip.let { vm.here() }?.let { LatLng(it.lat, it.lon) }
    // Before the trip has a position of its own, the map still opens on the phone's last known one, not on the whole world.
    var lastKnown by remember { mutableStateOf<LatLng?>(null) }
    // Offline the trip's first GPS fix can take a while (no assisted GPS), so show the phone's cached position at once,
    // then keep asking for a fresh one until the trip has its own. Neither step needs internet.
    androidx.compose.runtime.LaunchedEffect(tripHere == null) {
        if (tripHere != null) return@LaunchedEffect
        vm.lastKnownPoint()?.let { lastKnown = LatLng(it.lat, it.lon) }
        while (true) {
            vm.freshPoint()?.let { lastKnown = LatLng(it.lat, it.lon) }
            kotlinx.coroutines.delay(FRESH_RETRY_MS)
        }
    }
    val here = tripHere ?: lastKnown
    // The path runs on to the current position, so it never trails behind the dot between two recorded route points.
    val routeLines = remember(line, here?.latitude, here?.longitude) { listOf(MapLine(if (here != null && line.isNotEmpty()) line + here else line, Amber)) }
    val currentDotPoint = here ?: line.lastOrNull()
    val hereKey = here?.let { it.latitude to it.longitude }
    val lastLinePoint = line.lastOrNull()
    val lastLineKey = lastLinePoint?.let { it.latitude to it.longitude }
    val currentDotKey = hereKey ?: lastLineKey
    val routeDots = remember(currentDotKey) { currentDotPoint?.let { listOf(MapDot(it, Amber, 9f, Bg)) } ?: emptyList() }
    var camera by remember { mutableStateOf<CameraTarget?>(null) }
    var cameraId by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // The map follows the position by itself (RouteMap, follow = true): it opens on you, and keeps you and the path in view as you move.
    val locate: () -> Unit = {
        val target = here ?: line.lastOrNull() ?: vm.here()?.let { LatLng(it.lat, it.lon) }
        target?.let {
            cameraId += 1
            camera = CameraTarget(it, cameraId, 15.0)
        }
    }

    if (rememberLayout().isLandscape) {
        // Landscape, from design/MapL.dc.html: the map fills the left, speed and stats sit in a 284dp panel.
        Row(Modifier.fillMaxSize().background(Bg)) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                RouteMap(
                    lines = routeLines,
                    dots = routeDots,
                    follow = true,
                    flyTo = camera,
                    autoFit = false,
                    modifier = Modifier.fillMaxSize(),
                )
                here?.let { LocationCoordinates(it, Modifier.align(Alignment.BottomStart).padding(14.dp)) }
                LocateButton(locate, Modifier.align(Alignment.BottomEnd).padding(14.dp))
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.size(40.dp).background(Surface1, CircleShape).clickable(role = Role.Button, onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) { StrokeIcon("M15 5l-7 7 7 7", TextPrimary, size = 20.dp, box = 24f) }
                                    }
            }
            Column(Modifier.width(284.dp).fillMaxHeight().background(Nav).systemBarsPadding().padding(18.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(unit.fromKmh(trip.speedKmh).roundToInt().toString(), style = displayStyle(76, FontWeight.Bold))
                    Text(unit.label, Modifier.padding(bottom = 8.dp), style = bodyStyle(15))
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Cell(R.string.distance_short, "%.1f".format(DistanceUnit.fromKm(trip.distanceKm, mi)), TextPrimary, Modifier.weight(1f))
                    Cell(R.string.average_short, unit.fromKmh(trip.avgKmh).roundToInt().toString(), TextPrimary, Modifier.weight(1f))
                    Cell(R.string.smooth_score_short, trip.score.toString(), tripScoreColor(trip.score), Modifier.weight(1f))
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).background(Danger)
                        .clickable(role = Role.Button) { vm.end(); onEnd() },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.end_short), style = bodyStyle(15, FontWeight.Bold, Bg)) }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        RouteMap(
            lines = routeLines,
            dots = routeDots,
            follow = true,
            flyTo = camera,
            autoFit = false,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.68f),
        )
        here?.let {
            LocationCoordinates(
                it,
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 70.dp, top = 12.dp),
            )
        }
        LocateButton(locate, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 72.dp, end = 14.dp))
        // The map takes every drag, so there is a swipe strip on its left edge and on the panel below: swipe right to go back to the gauge.
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight(0.68f).width(28.dp).tabSwipe({}, onBack))

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(48.dp).background(Surface1, CircleShape).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { StrokeIcon("M15 5l-7 7 7 7", TextPrimary, size = 24.dp, box = 24f) }
                    }

        val next by vm.nextZone.collectAsStateWithLifecycle()
        next?.let { z ->
            NextZoneCard(
                z, trip.limitKmh, unit,
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 20.dp, top = 84.dp),
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Nav)
                .tabSwipe({}, onBack)
                .padding(start = 20.dp, end = 20.dp, top = 20.dp).navigationBarsPadding().padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(unit.fromKmh(trip.speedKmh).roundToInt().toString(), style = displayStyle(72, FontWeight.Bold))
                Text(unit.label, Modifier.padding(bottom = 10.dp), style = bodyStyle(16))
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Cell(R.string.distance, "%.1f %s".format(DistanceUnit.fromKm(trip.distanceKm, mi), if (mi) "mi" else "km"), TextPrimary, Modifier.weight(1f))
                Cell(R.string.average_label, unit.fromKmh(trip.avgKmh).roundToInt().toString(), TextPrimary, Modifier.weight(1f))
                Cell(R.string.smooth_score_short, trip.score.toString(), tripScoreColor(trip.score), Modifier.weight(1f))
            }
            Box(
                Modifier.padding(top = 16.dp).fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(Danger)
                    .clickable(role = Role.Button) { vm.end(); onEnd() },
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.end_trip), style = bodyStyle(16, FontWeight.Bold, Bg)) }
        }
    }
}

@Composable
private fun LocationCoordinates(point: LatLng, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Surface1, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(stringResource(R.string.current_location), style = bodyStyle(11, FontWeight.SemiBold, TextSoft))
        Text(GeoPoint(point.latitude, point.longitude).toCoordinateText(), style = bodyStyle(13, FontWeight.Bold, TextPrimary))
    }
}

@Composable
private fun Cell(label: Int, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight().background(Surface1, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(label), style = bodyStyle(12))
        Text(value, style = displayStyle(20, FontWeight.SemiBold, color))
    }
}

/** "Next: school zone in 400 m / Limit drops to 40", from design/Map.dc.html, for the nearest saved zone ahead. */
@Composable
fun NextZoneCard(zone: NextZone, currentLimitKmh: Float?, unit: com.speedometer.app.core.trip.SpeedUnit, modifier: Modifier = Modifier) {
    val limit = unit.fromKmh(zone.limitKmh.toFloat()).roundToInt()
    val drops = currentLimitKmh != null && zone.limitKmh < currentLimitKmh
    Row(
        modifier.background(Surface1, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(TextPrimary, CircleShape).border(5.dp, Danger, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                (currentLimitKmh?.let { unit.fromKmh(it).roundToInt() } ?: limit).toString(),
                style = displayStyle(15, FontWeight.Bold, Bg),
            )
        }
        Column {
            Text(stringResource(R.string.next_zone, zone.name, zone.distanceM), style = bodyStyle(12))
            Text(
                stringResource(if (drops) R.string.limit_drops_to else R.string.limit_is, limit),
                style = bodyStyle(14, FontWeight.Bold, TextPrimary),
            )
        }
    }
}

private const val FRESH_RETRY_MS = 5_000L

private const val CENTRE_ICON = "M12 9a3 3 0 1 0 0 6a3 3 0 0 0 0-6 M12 4a8 8 0 1 0 0 16a8 8 0 0 0 0-16 M12 2v3M12 19v3M2 12h3M19 12h3"

/** Round button that brings the map back to your position; same look as the one on the Nav screen. */
@Composable
private fun LocateButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(52.dp).clip(CircleShape).background(Nav).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { StrokeIcon(CENTRE_ICON, Ice, size = 26.dp, box = 24f) }
}
