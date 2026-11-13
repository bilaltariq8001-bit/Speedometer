package com.speedometer.app.feature.results.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.speedometer.app.core.common.findActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.speedometer.app.core.designsystem.rememberLayout
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.speedometer.app.R
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.Danger
import com.speedometer.app.core.designsystem.GhostButton
import com.speedometer.app.core.designsystem.Ice
import com.speedometer.app.core.designsystem.Line
import com.speedometer.app.core.designsystem.Line3
import com.speedometer.app.core.designsystem.Ok
import com.speedometer.app.core.designsystem.PrimaryButton
import com.speedometer.app.core.designsystem.ScrollPage
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.Surface2
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.TextSoft
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.core.designsystem.partScoreColor
import com.speedometer.app.core.map.MapDot
import com.speedometer.app.core.map.MapLine
import com.speedometer.app.core.map.RouteMap
import com.speedometer.app.core.trip.DistanceUnit
import com.speedometer.app.core.trip.ScoreParts
import com.speedometer.app.core.trip.SpeedUnit
import com.speedometer.app.core.trip.TripFormat
import org.maplibre.android.geometry.LatLng
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Results, from design/Results.dc.html. Done saves the trip; a trip under 8 s can only be discarded. */
@Composable
fun ResultsScreen(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    onSavedDone: () -> Unit,
    vm: ResultsViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsState()
    val r = ui ?: return
    val saveState by vm.saveState.collectAsState()
    val context = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val csv = vm.exportCsv()
        if (uri != null && csv != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } }
        }
    }
    (saveState as? SaveState.Failed)?.let { failed ->
        SaveErrorView(r, failed.storageFull, onRetry = vm::retry, onExport = { exporter.launch("${r.title}.csv") }, onDiscard = { vm.discard(onClose) })
        return
    }
    if (saveState is SaveState.Saving) {
        SavingView(r)
        return
    }
    val alreadySaved = saveState == SaveState.Saved
    if (alreadySaved) {
        androidx.activity.compose.BackHandler {
            vm.finishSaved()
            onSavedDone()
        }
    }
    val t = r.trip
    val mi = r.unit == SpeedUnit.Mph
    val distance = DistanceUnit.fromKm(t.distanceKm, mi)
    val unit = r.unit
    val startTime = DateTimeFormatter.ofPattern("h:mm a").format(Instant.ofEpochMilli(r.startedAtMs).atZone(ZoneId.systemDefault()))
    val endTime = DateTimeFormatter.ofPattern("h:mm a").format(Instant.ofEpochMilli(r.startedAtMs + t.durationSec * 1000L).atZone(ZoneId.systemDefault()))
    val line = remember(r.route) { r.route.map { LatLng(it.lat, it.lon) } }

    var showSaveDiscardDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    val bottom: @Composable () -> Unit = {
        if (alreadySaved) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                PrimaryButton(stringResource(R.string.done), {
                    vm.finishSaved()
                    onSavedDone()
                })
            }
        } else if (t.tooShortToSave) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.trip_too_short), style = bodyStyle(14, color = TextSoft), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
                PrimaryButton(stringResource(R.string.discard), { vm.discard(onClose) })
            }
        } else {
            // Save on the left (wider), Discard on the right: same height, Discard outlined in red as the destructive choice.
            // Save and Discard: the same width and the same height, whatever the label or the font size.
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp).height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrimaryButton(stringResource(R.string.save), { vm.save { onSaved() } }, Modifier.weight(1f).fillMaxHeight())
                DiscardButton(Modifier.weight(1f).fillMaxHeight()) { showSaveDiscardDialog = true }
            }
        }
    }

    // Asked before a trip is thrown away, since a discard cannot be undone.
    if (showSaveDiscardDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showSaveDiscardDialog = false }) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Surface2, RoundedCornerShape(28.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.discard_trip_question), style = displayStyle(20, FontWeight.SemiBold))
                Text(stringResource(R.string.discard_trip_body), style = bodyStyle(14, color = TextSoft))
                PrimaryButton(stringResource(R.string.discard_keep), { showSaveDiscardDialog = false })
                DiscardButton(Modifier.fillMaxWidth()) { showSaveDiscardDialog = false; vm.discard(onClose) }
            }
        }
    }

    val summary: @Composable ColumnScope.() -> Unit = {
        Text(
            "${r.title} · ${TripFormat.duration(t.durationSec)} · %.1f %s".format(distance, if (mi) "mi" else "km"),
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp),
            style = bodyStyle(14), textAlign = TextAlign.Center,
        )

        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(200.dp)) {
                val stroke = 16.dp.toPx()
                val tl = Offset(stroke / 2, stroke / 2)
                val sz = Size(size.width - stroke, size.height - stroke)
                drawArc(Surface2, -90f, 360f, false, tl, sz, style = Stroke(stroke))
                drawArc(Amber, -90f, 360f * t.score / 100f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t.score.toString(), style = displayStyle(64, FontWeight.Bold))
                Text(stringResource(R.string.smooth_score), Modifier.padding(top = 4.dp), style = bodyStyle(13))
            }
        }

        r.sevenDayAvg?.let { avg ->
            val delta = t.score - avg
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                Text(
                    (if (delta >= 0) "+" else "") + delta,
                    style = bodyStyle(15, FontWeight.Bold, if (delta >= 0) Ok else Danger),
                )
                Text(" " + stringResource(R.string.vs_seven_day), style = bodyStyle(15))
            }
        }

        // Score parts card placed right below the score ring as requested
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)
                .background(Surface1, RoundedCornerShape(20.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Part(R.string.part_braking, ScoreParts.braking(t.hardBrakes))
            Part(R.string.part_acceleration, null)
            Part(R.string.part_cornering, null)
            Part(R.string.part_limit, ScoreParts.limitCompliance(t.secondsOver))
        }
    }

    val details: @Composable ColumnScope.() -> Unit = {
        // Stats row: Distance, Duration, Avg speed, Max speed
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Distance", "%.1f %s".format(distance, if (mi) "mi" else "km"), Modifier.weight(1f))
                StatCard("Duration", TripFormat.duration(t.durationSec), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Avg speed", "${unit.fromKmh(t.avgKmh).roundToInt()} ${unit.label}", Modifier.weight(1f))
                StatCard("Max speed", "${unit.fromKmh(t.maxKmh).roundToInt()} ${unit.label}", Modifier.weight(1f))
            }
        }

        FromToCard(startTime, endTime, r.startPoint, r.endPoint, r.startPlace, r.endPlace, r.placesLoading)

        // Route Map (Trip line from start to finish/destination)
        if (line.size >= 2) {
            Box(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)
                    .height(220.dp).clip(RoundedCornerShape(20.dp)).background(Surface1),
            ) {
                RouteMap(
                    lines = listOf(MapLine(line, Amber)),
                    // Start is red and end is green, as on the From and To card. The end is smaller and drawn last, so when both are in the
                    // same place (a very short trip) the green end still shows inside the red start.
                    dots = listOf(MapDot(line.first(), Danger, 12f, Bg), MapDot(line.last(), Ok, 7f, Bg)),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)
                .background(Surface1, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StrokeIcon(
                "M9 18h6M10 21h4M12 3a6 6 0 0 0-4 10.5c.7.7 1 1.5 1 2.5h6c0-1 .3-1.8 1-2.5A6 6 0 0 0 12 3z",
                Ice, size = 24.dp, box = 24f,
            )
            Text(
                if (t.hardBrakes > 0) stringResource(R.string.tip_hard_brakes, t.hardBrakes)
                else stringResource(R.string.tip_smooth),
                style = bodyStyle(14, color = TextSoft).copy(lineHeight = 20.sp),
            )
        }
        Box(Modifier.height(16.dp))
    }

    val layout = rememberLayout()
    if (layout.isLandscape) {
        Row(Modifier.fillMaxSize().background(Bg)) {
            Column(
                Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) { summary() }
            Column(Modifier.weight(1f).fillMaxHeight().padding(end = 4.dp, top = 20.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { details() }
                bottom()
            }
        }
    } else {
        ScrollPage(bottom = bottom) {
            summary()
            details()
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight().background(Surface1, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = bodyStyle(12))
        Text(value, Modifier.padding(top = 2.dp), style = displayStyle(18, FontWeight.SemiBold, TextPrimary))
    }
}

/** From and To with the street address and time of each end of the trip, like a ride receipt. */
@Composable
private fun FromToCard(
    startTime: String, endTime: String,
    startPoint: com.speedometer.app.core.trip.RoutePoint?, endPoint: com.speedometer.app.core.trip.RoutePoint?,
    startPlace: String?, endPlace: String?, loading: Boolean,
) {
    fun coords(p: com.speedometer.app.core.trip.RoutePoint?) = p?.let { "%.5f, %.5f".format(java.util.Locale.ROOT, it.lat, it.lon) }
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)
            .background(androidx.compose.ui.graphics.Color(0xFF2C3036), RoundedCornerShape(24.dp)).padding(horizontal = 22.dp, vertical = 26.dp),
    ) {
        FromToRow(Danger, stringResource(R.string.from_label), startTime, startPlace, coords(startPoint), loading, connector = true)
        FromToRow(Ok, stringResource(R.string.to_label), endTime, endPlace, coords(endPoint), loading, connector = false)
    }
}

@Composable
private fun FromToRow(
    color: Color, label: String, time: String, address: String?, coordinates: String?, loading: Boolean, connector: Boolean,
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(width = 36.dp, height = 44.dp)) {
                val sc = size.width / 24f
                val pin = androidx.compose.ui.graphics.vector.PathParser()
                    .parsePathString("M12 23s8-7.2 8-13.2A8 8 0 0 0 4 9.8C4 15.8 12 23 12 23z").toPath()
                drawContext.canvas.save()
                drawContext.canvas.scale(sc, sc)
                drawPath(pin, color)
                drawCircle(Bg, 3.2f, Offset(12f, 9.8f))
                drawCircle(color, 1.4f, Offset(12f, 9.8f))
                drawContext.canvas.restore()
            }
            if (connector) {
                Canvas(Modifier.width(2.dp).weight(1f).padding(vertical = 4.dp)) {
                    drawLine(
                        TextMuted, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 2f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    )
                }
            }
        }
        Column(Modifier.weight(1f).padding(bottom = if (connector) 30.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = bodyStyle(18, FontWeight.Bold, TextPrimary))
                Text(time, style = bodyStyle(16, color = TextMuted))
            }
            when {
                address != null -> Text(address, style = bodyStyle(17, FontWeight.Normal, TextPrimary).copy(lineHeight = 25.sp))
                loading -> Text(stringResource(R.string.finding_address), style = bodyStyle(16, color = TextMuted))
                else -> Text(coordinates ?: "--", style = bodyStyle(17, FontWeight.Normal, TextPrimary))
            }
        }
    }
}

@Composable
private fun Part(name: Int, value: Int?) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(name), style = bodyStyle(14, color = TextPrimary))
            Text(value?.toString() ?: "--", style = displayStyle(14, FontWeight.SemiBold))
        }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Line)) {
            if (value != null) {
                Box(Modifier.fillMaxWidth(value / 100f).height(8.dp).background(partScoreColor(value), RoundedCornerShape(4.dp)))
            }
        }
    }
}

/** Discard: same size and shape as the primary button, outlined in red. */
@Composable
private fun DiscardButton(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp))
            .border(1.dp, com.speedometer.app.core.designsystem.Danger, RoundedCornerShape(28.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.discard), style = bodyStyle(17, FontWeight.Bold, com.speedometer.app.core.designsystem.Danger))
    }
}
