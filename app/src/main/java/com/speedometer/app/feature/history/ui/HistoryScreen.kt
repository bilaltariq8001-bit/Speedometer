package com.speedometer.app.feature.history.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import com.speedometer.app.core.designsystem.rememberLayout
import com.speedometer.app.core.map.MapDot
import com.speedometer.app.core.map.MapLine
import com.speedometer.app.core.map.RouteMap
import org.maplibre.android.geometry.LatLng
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.speedometer.app.R
import com.speedometer.app.core.database.TripEntity
import com.speedometer.app.core.ads.AdsViewModel
import com.speedometer.app.core.ads.HistoryNativeAd
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.Danger
import com.speedometer.app.core.designsystem.Line2
import com.speedometer.app.core.designsystem.Line3
import com.speedometer.app.core.designsystem.Ok
import com.speedometer.app.core.designsystem.PrimaryButton
import com.speedometer.app.core.designsystem.ScrollPage
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.Surface3
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.TextSoft
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.core.designsystem.tripScoreColor
import com.speedometer.app.core.trip.DistanceUnit
import com.speedometer.app.core.trip.SpeedUnit
import com.speedometer.app.core.trip.TripFormat
import java.time.LocalDate
import kotlin.math.roundToInt

/** Trips, from design/History.dc.html and EmptyHistory.dc.html. Tap a trip for the Delete and Close sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    onStartTrip: () -> Unit,
    onViewMap: (Range) -> Unit,
    onViewTripMap: (Long) -> Unit,
    onReplay: (Long) -> Unit,
    vm: HistoryViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val u = ui ?: return
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    val mi = u.unit == SpeedUnit.Mph
    val ads = hiltViewModel<AdsViewModel>().ads
    val nativeAd = ads.config.native("history")
    val showNativeAd = nativeAd.isAdEnabled && nativeAd.adNativeId.isNotBlank() && ads.config.canRequestAds()

    if (!u.hasAnyTrips) {
        HistoryEmptyState(onStartTrip) {
            if (showNativeAd) HistoryNativeAd(nativeAd, ads.analytics, Modifier.padding(top = 24.dp))
        }
        return
    }

    val landscape = rememberLayout().isLandscape
    if (landscape && u.trips.isNotEmpty()) {
        HistoryLandscape(u, onViewMap, onReplay, selected, showNativeAd, nativeAd, ads.analytics, loadRoute = vm::routeOf, onSelect = { selected = it })
    } else {
        // A lazy list: only the rows on screen are composed, so a long "All" history opens and scrolls without jank.
        val contentMax = rememberLayout().contentMax
        val today = remember { LocalDate.now() }
        val onSelect: (Long) -> Unit = { selected = it }
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxSize().background(Bg).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Title, totals, View on map and the Week / Month / All / Flagged filter stay fixed; only the trips scroll.
            androidx.compose.foundation.layout.Column(Modifier.widthIn(max = contentMax).fillMaxWidth()) {
                HistoryTopBar(u, mi, onViewMap)
                HistoryFilterSection(u, vm)
            }
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.weight(1f).fillMaxWidth()
                    // Trips slide under the fixed filter with a clean edge instead of overlapping it.
                    .clipToBounds(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (u.trips.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        Box(
                            Modifier.widthIn(max = contentMax).fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.no_trips_yet), style = bodyStyle(15, color = TextSoft), textAlign = TextAlign.Center)
                        }
                    }
                    if (showNativeAd) item(key = "native_ad", contentType = "ad") {
                        Box(Modifier.widthIn(max = contentMax).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                            HistoryNativeAd(nativeAd, ads.analytics)
                        }
                    }
                } else {
                    val last = u.trips.lastIndex
                    u.trips.forEachIndexed { index, t ->
                        item(key = t.id, contentType = "trip") {
                            Box(
                                Modifier.widthIn(max = contentMax).fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = if (index == 0) 16.dp else 12.dp, bottom = if (index == last && !(showNativeAd && last <= 1)) 16.dp else 0.dp),
                            ) { TripRow(t, u.unit, today) { onSelect(t.id) } }
                        }
                        if (showNativeAd && (index == 1 || (u.trips.size == 1 && index == 0))) {
                            item(key = "native_ad", contentType = "ad") {
                                Box(
                                    Modifier.widthIn(max = contentMax).fillMaxWidth()
                                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = if (index == last) 16.dp else 0.dp),
                                ) { HistoryNativeAd(nativeAd, ads.analytics) }
                            }
                        }
                    }
                }
            }
        }
    }

    // In landscape a tap only selects the trip for the map pane; the sheet would cover the whole list there.
    val trip = u.trips.firstOrNull { it.id == selected }
    if (trip != null && !(landscape && u.trips.isNotEmpty())) {
        TripSheet(
            trip, u.unit,
            onViewMap = { selected = null; onViewTripMap(trip.id) },
            onReplay = { selected = null; onReplay(trip.id) },
            onDelete = { vm.delete(trip.id); selected = null }, onClose = { selected = null },
        )
    }
}

@Composable
private fun HistoryTopBar(u: HistoryUi, mi: Boolean, onViewMap: (Range) -> Unit) {
    // Landscape (a filter with no trips): the back button is beside the title, so the title sits level with it.
    val top = if (rememberLayout().isLandscape) com.speedometer.app.core.designsystem.SECONDARY_TITLE_TOP_LANDSCAPE else 20.dp
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = top)) {
        Text(stringResource(R.string.tab_trips), style = displayStyle(26, FontWeight.SemiBold))
        val range = stringResource(
            when (u.range) {
                Range.Week -> R.string.range_week
                Range.Month -> R.string.range_month
                Range.All -> R.string.range_all
            },
        )
        Text(
            "$range · %.1f %s · ".format(DistanceUnit.fromKm(u.totalKm, mi), if (mi) "mi" else "km") +
                pluralStringResource(R.plurals.trip_count, u.trips.size, u.trips.size),
            Modifier.padding(top = 4.dp), style = bodyStyle(14),
        )
        Row(
            Modifier.padding(top = 12.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
                .border(1.dp, Amber, RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = { onViewMap(u.range) })
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StrokeIcon("M9 4L3 6v14l6-2 6 2 6-2V4l-6 2-6-2z M9 4v14 M15 6v14", Amber, size = 18.dp, box = 24f)
            Text(stringResource(R.string.view_on_map), style = bodyStyle(14, FontWeight.Bold, Amber))
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HistoryFilterSection(u: HistoryUi, vm: HistoryViewModel) {
    // One choice at a time: Flagged is its own choice, so picking it no longer leaves a period lit as well. The highlight slides.
    val selected = if (u.flaggedOnly) 3 else when (u.range) { Range.Week -> 0; Range.Month -> 1; Range.All -> 2 }
    com.speedometer.app.core.designsystem.Segmented(
        listOf(
            stringResource(R.string.chip_week), stringResource(R.string.chip_month),
            stringResource(R.string.chip_all), stringResource(R.string.chip_flagged),
        ),
        selected,
        { i ->
            when (i) {
                0 -> vm.setRange(Range.Week)
                1 -> vm.setRange(Range.Month)
                2 -> vm.setRange(Range.All)
                else -> if (!u.flaggedOnly) vm.toggleFlagged()
            }
        },
        Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
    )
}

@Composable
private fun HistoryTripList(
    u: HistoryUi,
    onSelect: (Long) -> Unit,
    showNativeAd: Boolean,
    nativeAd: com.speedometer.app.core.ads.NativeAdModel,
    analytics: com.speedometer.app.core.analytics.AdAnalytics,
) {
    if (u.trips.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().padding(vertical = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.no_trips_yet), style = bodyStyle(15, color = TextSoft), textAlign = TextAlign.Center)
        }
    } else {
        val today = LocalDate.now()
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            u.trips.forEachIndexed { index, t ->
                TripRow(t, u.unit, today) { onSelect(t.id) }
                if (showNativeAd && index == 1) HistoryNativeAd(nativeAd, analytics)
            }
            if (showNativeAd && u.trips.size == 1) HistoryNativeAd(nativeAd, analytics)
        }
    }
}

@Composable
private fun Chip(label: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (selected) Amber else Surface1)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(label), style = bodyStyle(14, if (selected) FontWeight.Bold else FontWeight.Medium, if (selected) Bg else TextSoft))
    }
}

@Composable
private fun TripRow(t: TripEntity, unit: SpeedUnit, today: LocalDate, onClick: () -> Unit) {
    val mi = unit == SpeedUnit.Mph
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface1)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(48.dp).border(3.dp, tripScoreColor(t.score), CircleShape), contentAlignment = Alignment.Center) {
            Text(t.score.toString(), style = displayStyle(16, FontWeight.Bold))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.speedometer.app.core.designsystem.StrokeIcon(
                    com.speedometer.app.core.designsystem.vehicleIconPath(com.speedometer.app.core.designsystem.vehicleFromName(t.vehicle)),
                    Amber, size = 20.dp, box = 32f,
                )
                Text(t.title, style = bodyStyle(16, FontWeight.Bold, TextPrimary))
            }
            Text(TripFormat.whenLabel(t.startMs, today), Modifier.padding(top = 2.dp), style = bodyStyle(13))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("%.1f %s".format(DistanceUnit.fromKm(t.distanceKm, mi), if (mi) "mi" else "km"), style = displayStyle(19, FontWeight.SemiBold))
            Text(
                stringResource(R.string.max_value, unit.fromKmh(t.maxKmh).roundToInt()),
                Modifier.padding(top = 2.dp), style = bodyStyle(13),
            )
        }
    }
}

@Composable
private fun HistoryEmptyState(onStartTrip: () -> Unit, footer: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 380.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(width = 200.dp, height = 160.dp)) {
                val sc = size.width / 200f
                val route = Path().apply {
                    moveTo(30f * sc, 130f * sc)
                    cubicTo(70f * sc, 120f * sc, 90f * sc, 60f * sc, 130f * sc, 50f * sc)
                    cubicTo(150f * sc, 45f * sc, 170f * sc, 60f * sc, 170f * sc, 80f * sc)
                }
                drawPath(route, Line3, style = Stroke(5f * sc, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * sc, 12f * sc))))
                drawCircle(Ok, 8f * sc, Offset(30f * sc, 130f * sc))
                drawCircle(Line2, 8f * sc, Offset(172f * sc, 36f * sc))
                drawCircle(Color(0xFF5A6776), 8f * sc, Offset(172f * sc, 36f * sc), style = Stroke(3f * sc))
            }
            Text(
                stringResource(R.string.empty_title), Modifier.padding(top = 12.dp),
                style = displayStyle(26, FontWeight.Bold).copy(lineHeight = 30.sp), textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.empty_body), Modifier.padding(top = 10.dp),
                style = bodyStyle(15).copy(lineHeight = 22.sp), textAlign = TextAlign.Center,
            )
            Box(Modifier.padding(top = 24.dp)) { PrimaryButton(stringResource(R.string.start_a_trip), onStartTrip, Modifier.padding(horizontal = 0.dp)) }
            footer()
        }
    }
}

@Composable
private fun HistoryLandscape(
    u: HistoryUi,
    onViewMap: (Range) -> Unit,
    onReplay: (Long) -> Unit,
    selectedId: Long?,
    showNativeAd: Boolean,
    nativeAd: com.speedometer.app.core.ads.NativeAdModel,
    analytics: com.speedometer.app.core.analytics.AdAnalytics,
    loadRoute: suspend (Long) -> List<com.speedometer.app.core.trip.RoutePoint>,
    onSelect: (Long) -> Unit,
) {
    val current = u.trips.firstOrNull { it.id == selectedId } ?: u.trips.first()
    val today = LocalDate.now()
    // The route is read for the selected trip only, off the main thread. Null while it loads, so "no route" never flashes.
    val line by androidx.compose.runtime.produceState<List<LatLng>?>(null, current.id) {
        value = null
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { loadRoute(current.id).map { LatLng(it.lat, it.lon) } }
    }

    Row(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.width(380.dp).fillMaxHeight().padding(start = 20.dp, top = com.speedometer.app.core.designsystem.SECONDARY_TITLE_TOP_LANDSCAPE)) {
            Text(stringResource(R.string.tab_trips), style = displayStyle(26, FontWeight.SemiBold))
            val range = stringResource(
                when (u.range) {
                    Range.Week -> R.string.range_week
                    Range.Month -> R.string.range_month
                    Range.All -> R.string.range_all
                },
            )
            Text(
                "$range · %.1f %s · ".format(DistanceUnit.fromKm(u.totalKm, u.unit == SpeedUnit.Mph), if (u.unit == SpeedUnit.Mph) "mi" else "km") +
                    pluralStringResource(R.plurals.trip_count, u.trips.size, u.trips.size),
                Modifier.padding(top = 2.dp), style = bodyStyle(13),
            )
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.padding(top = 12.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(u.trips.size, key = { u.trips[it].id }, contentType = { "trip" }) { i ->
                    val t = u.trips[i]
                    LandscapeTripRow(t, current, t.id == selectedId, u.unit, today) { onSelect(t.id) }
                }
                if (showNativeAd) item(key = "native_ad", contentType = "ad") { HistoryNativeAd(nativeAd, analytics) }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1)) {
                val l = line
                if (l == null || !com.speedometer.app.core.designsystem.LocalScreenSettled.current.value) {
                    Unit
                } else if (l.size >= 2) {
                    RouteMap(
                        lines = remember(l) { listOf(MapLine(l, Amber)) },
                        dots = remember(l) { listOf(MapDot(l.first(), Ok, 6f), MapDot(l.last(), TextPrimary, 6f)) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(stringResource(R.string.replay_no_route), Modifier.align(Alignment.Center).padding(24.dp), style = bodyStyle(14))
                }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if ((line?.size ?: 0) >= 2) {
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(Amber)
                            .clickable(role = Role.Button) { onReplay(current.id) },
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.replay), style = bodyStyle(15, FontWeight.Bold, Bg)) }
                }
                Box(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).border(1.dp, Line3, RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button, onClick = { onViewMap(u.range) }),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.all_on_map), style = bodyStyle(15, FontWeight.Bold, TextPrimary)) }
            }
        }
    }
}

@Composable
private fun LandscapeTripRow(t: TripEntity, current: TripEntity, isSelected: Boolean, unit: SpeedUnit, today: LocalDate, onClick: () -> Unit) {
    val mi = unit == SpeedUnit.Mph
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1)
            .border(2.dp, if (isSelected) Amber else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).border(3.dp, tripScoreColor(t.score), CircleShape), contentAlignment = Alignment.Center) {
            Text(t.score.toString(), style = displayStyle(14, FontWeight.Bold))
        }
        Column(Modifier.weight(1f)) {
            Text(t.title, style = bodyStyle(14, FontWeight.Bold, TextPrimary))
            Text(TripFormat.whenLabel(t.startMs, today), Modifier.padding(top = 1.dp), style = bodyStyle(12))
        }
        Text("%.1f %s".format(DistanceUnit.fromKm(t.distanceKm, mi), if (mi) "mi" else "km"), style = displayStyle(16, FontWeight.SemiBold))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripSheet(t: TripEntity, unit: SpeedUnit, onViewMap: () -> Unit, onReplay: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, containerColor = Surface3) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(t.title, style = displayStyle(20, FontWeight.Bold))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(Surface1).clickable(role = Role.Button, onClick = onViewMap), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.view_on_map), style = bodyStyle(14, FontWeight.Bold, TextPrimary))
                }
                Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(Amber).clickable(role = Role.Button, onClick = onReplay), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.replay), style = bodyStyle(14, FontWeight.Bold, Bg))
                }
            }
            Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Surface1).clickable(role = Role.Button, onClick = onDelete), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.delete), style = bodyStyle(14, FontWeight.Bold, Danger))
            }
        }
    }
}
