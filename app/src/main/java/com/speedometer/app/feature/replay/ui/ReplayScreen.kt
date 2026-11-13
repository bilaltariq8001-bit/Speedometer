package com.speedometer.app.feature.replay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.speedometer.app.R
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.Danger
import com.speedometer.app.core.designsystem.Ok
import com.speedometer.app.core.designsystem.ScrollPage
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.TextSoft
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.core.map.MapDot
import com.speedometer.app.core.map.MapLine
import com.speedometer.app.core.map.RouteMap
import com.speedometer.app.core.trip.DistanceUnit
import com.speedometer.app.core.trip.SpeedUnit
import com.speedometer.app.feature.replay.domain.ReplayMath
import org.maplibre.android.geometry.LatLng
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
private fun clock(startMs: Long, plusSec: Int) =
    Instant.ofEpochMilli(startMs + plusSec * 1000L).atZone(ZoneId.systemDefault()).format(clockFormat)

/** Trip Replay, from design/Replay.dc.html: route, speed line to scrub, play controls and Moments. */
@Composable
fun ReplayScreen(onBack: () -> Unit, vm: ReplayViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val missing by vm.tripMissing.collectAsState()
    val u = ui

    ScrollPage {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                StrokeIcon("M15 5l-7 7 7 7", TextPrimary, size = 24.dp, box = 24f)
            }
            Text(stringResource(R.string.trip_replay), Modifier.padding(start = 4.dp), style = displayStyle(22, FontWeight.SemiBold))
        }
        if (u == null || u.points.size < 2) {
            Text(
                stringResource(if (missing) R.string.replay_missing else R.string.replay_no_route),
                Modifier.fillMaxWidth().padding(40.dp), style = bodyStyle(15),
            )
            return@ScrollPage
        }
        val mi = u.unit == SpeedUnit.Mph
        val speed = ReplayMath.speedAt(u.points, u.tSec)
        val pos = ReplayMath.positionAt(u.points, u.tSec)
        val line = u.points.map { LatLng(it.lat, it.lon) }
        val layout = com.speedometer.app.core.designsystem.rememberLayout()
        val mapHeight = when { layout.isLandscape -> 200.dp; layout.isTablet -> 380.dp; else -> 240.dp }

        RouteMap(
            lines = listOf(MapLine(line, Amber)),
            dots = buildList {
                add(MapDot(line.first(), Ok, 6f))
                add(MapDot(line.last(), TextPrimary, 6f))
                u.moments.forEach { m -> ReplayMath.positionAt(u.points, m.tSec.toFloat())?.let { add(MapDot(LatLng(it.first, it.second), Danger, 7f, Bg)) } }
                pos?.let { add(MapDot(LatLng(it.first, it.second), Amber, 8f, Bg)) }
            },
            follow = u.playing,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp).height(mapHeight).clip(RoundedCornerShape(24.dp)),
        )

        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(u.unit.fromKmh(speed).roundToInt().toString(), style = displayStyle(56, FontWeight.Bold))
                Text(" " + u.unit.label, Modifier.padding(bottom = 8.dp), style = bodyStyle(15))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.replay_at), style = bodyStyle(12))
                Text(
                    clock(u.trip.startMs, u.tSec.toInt()) + " · %.1f %s".format(
                        DistanceUnit.fromKm(ReplayMath.distanceKmAt(u.points, u.tSec), mi), if (mi) "mi" else "km",
                    ),
                    style = displayStyle(20, FontWeight.SemiBold),
                )
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)
                .background(Surface1, RoundedCornerShape(20.dp)).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        ) {
            SpeedLine(u, onSeek = vm::seek)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clock(u.trip.startMs, 0), style = bodyStyle(12))
                Text(clock(u.trip.startMs, u.durationSec), style = bodyStyle(12))
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(56, Surface1, stringResource(R.string.back_10), vm::back) {
                StrokeIcon("M11 6l-6 6 6 6M19 6l-6 6 6 6", TextPrimary, size = 24.dp, box = 24f)
            }
            RoundButton(68, Amber, stringResource(if (u.playing) R.string.pause else R.string.play), vm::toggle) {
                if (u.playing) StrokeIcon("M7 5h2v14H7z M15 5h2v14h-2z", Bg, size = 28.dp, box = 24f, filled = true)
                else StrokeIcon("M7 4l13 8-13 8z", Bg, size = 28.dp, box = 24f, filled = true)
            }
            RoundButton(56, Surface1, stringResource(R.string.forward_10), vm::forward) {
                StrokeIcon("M13 6l6 6-6 6M5 6l6 6-6 6", TextPrimary, size = 24.dp, box = 24f)
            }
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)) {
            Text(stringResource(R.string.moments), Modifier.padding(bottom = 8.dp), style = bodyStyle(13))
            if (u.moments.isEmpty()) Text(stringResource(R.string.no_moments), style = bodyStyle(14, color = TextSoft))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                u.moments.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(Surface1)
                            .clickable(role = Role.Button) { vm.seek(m.tSec.toFloat()) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(10.dp).background(Danger, CircleShape))
                        Text(
                            clock(u.trip.startMs, m.tSec) + " · " + stringResource(R.string.hard_brake),
                            Modifier.weight(1f), style = bodyStyle(14, color = TextPrimary),
                        )
                        Text(
                            "${u.unit.fromKmh(m.fromKmh.toFloat()).roundToInt()} " + stringResource(R.string.to_word) +
                                " ${u.unit.fromKmh(m.toKmh.toFloat()).roundToInt()}",
                            style = displayStyle(15, FontWeight.SemiBold, TextSoft),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundButton(size: Int, color: Color, label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(color).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/** Speed over the trip, 318 x 84 in the design. Tap or drag to move the replay position. */
@Composable
private fun SpeedLine(u: ReplayUi, onSeek: (Float) -> Unit) {
    val maxKmh = (u.points.maxOf { it.speedKmh }).coerceAtLeast(1f)
    Canvas(
        Modifier.fillMaxWidth().height(84.dp)
            .pointerInput(u.durationSec) {
                detectTapGestures { o -> onSeek(o.x / size.width * u.durationSec) }
            }
            .pointerInput(u.durationSec) {
                detectHorizontalDragGestures { change, _ -> onSeek(change.position.x / size.width * u.durationSec) }
            },
    ) {
        val dur = u.durationSec.toFloat().coerceAtLeast(1f)
        fun x(t: Float) = t / dur * size.width
        fun y(v: Float) = size.height - (v / maxKmh) * (size.height - 14.dp.toPx()) - 2.dp.toPx()
        val path = Path()
        u.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.tSec.toFloat()), y(p.speedKmh)) else path.lineTo(x(p.tSec.toFloat()), y(p.speedKmh)) }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(fill, Amber.copy(alpha = 0.15f))
        drawPath(path, Amber, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        u.moments.forEach { m ->
            val p = u.points.firstOrNull { it.tSec >= m.tSec } ?: return@forEach
            drawCircle(Danger, 5.dp.toPx(), Offset(x(p.tSec.toFloat()), y(p.speedKmh)))
        }
        val cx = x(u.tSec)
        drawLine(TextPrimary, Offset(cx, 0f), Offset(cx, size.height), 2.dp.toPx())
        drawCircle(TextPrimary, 7.dp.toPx(), Offset(cx, y(ReplayMath.speedAt(u.points, u.tSec))))
    }
}
