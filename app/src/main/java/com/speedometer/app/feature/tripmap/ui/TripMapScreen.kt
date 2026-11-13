package com.speedometer.app.feature.tripmap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.speedometer.app.R
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.Danger
import com.speedometer.app.core.designsystem.Line
import com.speedometer.app.core.designsystem.Line3
import com.speedometer.app.core.designsystem.Nav
import com.speedometer.app.core.designsystem.Ok
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.TextSoft
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.core.designsystem.tripScoreColor
import com.speedometer.app.core.map.MapLine
import com.speedometer.app.core.map.RouteMap
import com.speedometer.app.core.trip.DistanceUnit
import com.speedometer.app.core.trip.SpeedUnit
import com.speedometer.app.core.trip.TripFormat
import org.maplibre.android.geometry.LatLng
import java.time.LocalDate
import kotlin.math.roundToInt

/** Colours from the Trip map legend: Smooth 80+, 60 to 79, below 60. */
private fun mapScoreColor(score: Int) = when {
    score >= 80 -> Ok
    score >= 60 -> Amber
    else -> Danger
}

private const val DIM_ALPHA = 0.22f

/** [dim]: another trip is selected, so this one fades back. [selected]: drawn thicker and on top. */
private fun tripLines(t: MappedTrip, heatmap: Boolean, dim: Boolean, selected: Boolean): List<MapLine> {
    fun tint(c: androidx.compose.ui.graphics.Color) = if (dim) c.copy(alpha = DIM_ALPHA) else c
    val width = if (selected) 8f else 5f
    if (!heatmap) {
        return listOf(MapLine(t.points.map { LatLng(it.lat, it.lon) }, tint(mapScoreColor(t.trip.score)), width))
    }
    // Consecutive points of the same colour become one line; each run starts on the last point of the one before,
    // so the route is drawn without breaks.
    fun colorAt(speed: Float) = when {
        speed < 50f -> Ok
        speed <= 80f -> Amber
        else -> Danger
    }
    val pts = t.points
    val lines = mutableListOf<MapLine>()
    var run = mutableListOf(LatLng(pts[0].lat, pts[0].lon))
    var runColor = colorAt(pts[0].speedKmh)
    for (i in 1 until pts.size) {
        val p = LatLng(pts[i].lat, pts[i].lon)
        val c = colorAt(pts[i - 1].speedKmh)
        if (c != runColor) {
            lines += MapLine(run, tint(runColor), width)
            run = mutableListOf(run.last())
            runColor = c
        }
        run.add(p)
    }
    lines += MapLine(run, tint(runColor), width)
    return lines
}

/** Trip map, from design/TrackerMap.dc.html: saved trip(s) on map, coloured by score or speed. */
@Composable
fun TripMapScreen(onBack: () -> Unit, onOpenTrip: (Long) -> Unit, vm: TripMapViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val u = ui ?: return
    val mi = u.unit == SpeedUnit.Mph

    var sheetHeight by androidx.compose.runtime.saveable.rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.Saver(
            save = { height -> height.value },
            restore = { value -> value.dp },
        ),
    ) { mutableStateOf(SHEET_MID) }
    // The trip tapped in the list: highlighted on the map until it is tapped again.
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedTrip = u.trips.firstOrNull { it.trip.id == selectedId }
    // A trip saved without a usable route (older trips, or no position the whole time) says so, instead of an empty map.
    val noRouteMessage = androidx.compose.ui.res.stringResource(R.string.trip_no_route)
    androidx.compose.runtime.LaunchedEffect(u.singleTrip, u.trips.isEmpty()) {
        if (u.singleTrip && u.trips.isEmpty()) com.speedometer.app.core.designsystem.AppMessages.show(noRouteMessage, com.speedometer.app.core.designsystem.MessageKind.Info)
    }
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val layout = com.speedometer.app.core.designsystem.rememberLayout()
        val landscape = layout.isLandscape
        val maxSheet = minOf(SHEET_MAX, maxHeight * 0.8f)
        // The gesture or navigation bar takes room at the bottom of the sheet, so the sheet is that much taller.
        // When the app banner sits below this screen it already clears the gesture bar.
        val bannerBelow = com.speedometer.app.core.ads.LocalBannerBelow.current
        val navInset = if (bannerBelow) 0.dp else with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(this).toDp() }
        RouteMap(
            lines = u.trips.sortedBy { it.trip.id == selectedId }
                .flatMap { t -> tripLines(t, u.heatmap, dim = selectedTrip != null && t !== selectedTrip, selected = t === selectedTrip) },
            focus = selectedTrip?.points?.map { LatLng(it.lat, it.lon) },
            zoomOutBy = 0.8, maxFitZoom = if (u.singleTrip) 16.0 else 12.0,
            // Keep the routes clear of the header and of the list sheet (or the side panel in landscape).
            fitTopPaddingDp = 100, fitBottomPaddingDp = if (landscape) 0 else 340, fitLeftPaddingDp = if (landscape) 400 else 0,
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            // Back on the left, Heatmap on the right, and the title in a snug pill centred on the screen.
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                Box(
                    Modifier.align(Alignment.CenterStart).size(48.dp).clip(CircleShape).background(Surface1)
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { StrokeIcon("M15 5l-7 7 7 7", TextPrimary, size = 22.dp, box = 24f) }
                Box(
                    Modifier.align(Alignment.Center).heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(Surface1).padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.trip_map), style = bodyStyle(15, FontWeight.Bold, TextPrimary)) }
                // One trip: Heatmap sits in the trip sheet, on the same line as the trip name. Several trips: it stays up here.
                if (!u.singleTrip) Box(Modifier.align(Alignment.CenterEnd)) { RangeChip(R.string.map_heatmap, u.heatmap) { vm.toggleHeatmap() } }
            }
            if (u.heatmap) Legend(Modifier.padding(top = 12.dp))
        }

        Column(
            (
                // Landscape: the list becomes a side panel next to the map. Portrait: a bottom sheet, capped in width on tablets.
                if (landscape) Modifier.align(Alignment.CenterStart).width(380.dp).fillMaxHeight().statusBarsPadding().padding(top = 72.dp, start = 16.dp, bottom = 16.dp)
                else Modifier.align(Alignment.BottomCenter).widthIn(max = layout.contentMax).fillMaxWidth().heightIn(max = minOf(sheetHeight, maxSheet) + navInset)
            )
                .clip(if (landscape) RoundedCornerShape(24.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Nav)
                // The sheet's colour runs to the bottom edge, but its content stays above the gesture and navigation bar.
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp),
        ) {
            // Drag the handle down to tuck the sheet away (only the handle stays), and up to bring it back.
            Box(
                Modifier.fillMaxWidth().height(36.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                val h = sheetHeight
                                sheetHeight = listOf(SHEET_MIN, SHEET_MID, SHEET_MAX).minByOrNull { kotlin.math.abs(it.value - h.value) }!!
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            sheetHeight = with(density) { (sheetHeight.toPx() - dragAmount).toDp().coerceIn(SHEET_MIN, SHEET_MAX) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 44.dp, height = 5.dp).background(Line3, RoundedCornerShape(3.dp)))
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            val totalKm = u.trips.sumOf { it.trip.distanceKm.toDouble() }.toFloat()
            val totalSec = u.trips.sumOf { it.trip.durationSec }
            val trip = u.trips.firstOrNull()
            if (u.singleTrip && trip != null) {
                val today = LocalDate.now()
                Column(Modifier.padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(trip.trip.title, Modifier.weight(1f), style = displayStyle(22, FontWeight.SemiBold), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        RangeChip(R.string.map_heatmap, u.heatmap) { vm.toggleHeatmap() }
                    }
                    Text(
                        TripFormat.whenLabel(trip.trip.startMs, today) + " · " + TripFormat.duration(trip.trip.durationSec) + " · " +
                            "%.1f %s".format(DistanceUnit.fromKm(trip.trip.distanceKm, mi), if (mi) "mi" else "km"),
                        style = bodyStyle(14),
                    )
                    Text(
                        stringResource(R.string.sheet_stats, trip.trip.score, u.unit.fromKmh(trip.trip.maxKmh).roundToInt(), u.unit.label),
                        style = bodyStyle(14, color = TextSoft),
                    )
                    if (trip.trip.route.isNotEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(Amber)
                                .clickable(role = Role.Button, onClick = { onOpenTrip(trip.trip.id) }),
                            contentAlignment = Alignment.Center,
                        ) { Text(stringResource(R.string.replay_trip), style = bodyStyle(17, FontWeight.Bold, Bg)) }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(
                        pluralStringResource(R.plurals.trip_count, u.trips.size, u.trips.size),
                        style = displayStyle(20, FontWeight.SemiBold),
                    )
                    Text(
                        "%.1f %s · %s".format(DistanceUnit.fromKm(totalKm, mi), if (mi) "mi" else "km", hoursMinutes(totalSec)),
                        style = bodyStyle(14),
                    )
                }
                if (u.trips.isEmpty()) Text(stringResource(R.string.map_no_trips), Modifier.padding(vertical = 24.dp), style = bodyStyle(15))
                val today = LocalDate.now()
                Column(Modifier.padding(top = 10.dp, bottom = 16.dp)) {
                    u.trips.forEach { t ->
                        val c = mapScoreColor(t.trip.score)
                        val isSelected = t.trip.id == selectedId
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) Surface1 else androidx.compose.ui.graphics.Color.Transparent)
                                .then(if (isSelected) Modifier.border(1.dp, Amber, RoundedCornerShape(16.dp)) else Modifier)
                                .clickable(role = Role.Button) { selectedId = if (isSelected) null else t.trip.id }
                                .padding(horizontal = if (isSelected) 10.dp else 0.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Column(Modifier.width(12.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(12.dp).background(c, CircleShape))
                                Box(Modifier.weight(1f).width(2.dp).background(Line))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(t.trip.title, style = bodyStyle(15, FontWeight.Bold, TextPrimary))
                                Text(
                                    TripFormat.whenLabel(t.trip.startMs, today) + " · %.1f %s".format(DistanceUnit.fromKm(t.trip.distanceKm, mi), if (mi) "mi" else "km"),
                                    Modifier.padding(top = 2.dp), style = bodyStyle(13),
                                )
                            }
                            Box(Modifier.size(44.dp).border(3.dp, c, CircleShape), contentAlignment = Alignment.Center) {
                                Text(t.trip.score.toString(), style = displayStyle(15, FontWeight.Bold))
                            }
                        }
                        if (isSelected) {
                            Box(
                                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
                                    .background(Amber).clickable(role = Role.Button) { onOpenTrip(t.trip.id) },
                                contentAlignment = Alignment.Center,
                            ) { Text(stringResource(R.string.replay_trip), style = bodyStyle(16, FontWeight.Bold, Bg)) }
                        }
                    }
                }
            }
            }
        }
    }
}

private fun hoursMinutes(sec: Int): String = if (sec >= 3600) "${sec / 3600} h ${sec % 3600 / 60} m" else "${sec / 60} m"

@Composable
private fun RangeChip(label: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(Amber)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(stringResource(label), style = bodyStyle(14, FontWeight.Bold, Bg)) }
}

@Composable
private fun Legend(modifier: Modifier) {
    Column(
        modifier.background(Surface1.copy(alpha = 0.92f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(Ok to R.string.legend_smooth, Amber to R.string.legend_mid, Danger to R.string.legend_low).forEach { (c, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(width = 16.dp, height = 4.dp).background(c, RoundedCornerShape(2.dp)))
                Text(stringResource(label), style = bodyStyle(12, color = TextSoft))
            }
        }
    }
}

private val SHEET_MIN = 36.dp
private val SHEET_MID = 300.dp
private val SHEET_MAX = 560.dp
