package com.speedometer.app.feature.gps.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.speedometer.app.R
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Danger
import com.speedometer.app.core.designsystem.Line3
import com.speedometer.app.core.designsystem.Ok
import com.speedometer.app.core.designsystem.ScrollPage
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.core.location.Constellation
import com.speedometer.app.core.location.GnssSnapshot
import com.speedometer.app.core.location.Satellite
import com.speedometer.app.core.trip.GpsQuality
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Quiet = Color(0xFF232B35)
private val Weak = Color(0xFF5A6776)
private const val STRONG_CN0 = 30f
private const val OK_CN0 = 24f
private const val TOP_SATELLITES = 12

private fun cn0Color(cn0: Float) = if (cn0 >= STRONG_CN0) Ok else if (cn0 >= OK_CN0) Amber else Weak

/** GPS status, from design/GpsStatus.dc.html, read live from the platform's GNSS status. */
@Composable
fun GpsStatusScreen(onBack: () -> Unit, vm: GpsStatusViewModel = hiltViewModel()) {
    val s by vm.snapshot.collectAsState()
    val gate = com.speedometer.app.feature.permissions.ui.rememberLocationGate()
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!com.speedometer.app.feature.permissions.ui.hasPreciseLocation(context) || !com.speedometer.app.feature.permissions.ui.isDeviceLocationOn(context)) gate.require { vm.refresh() }
    }
    gate.dialogs()
    val q = s.quality
    val color = when (q) { GpsQuality.Strong -> Ok; GpsQuality.Weak -> Amber; GpsQuality.Lost -> Danger }

    ScrollPage(bottom = {
        Box(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp, top = 8.dp).fillMaxWidth().height(52.dp)
                .border(1.dp, Line3, RoundedCornerShape(26.dp)).clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.done), style = bodyStyle(16, FontWeight.Bold, TextPrimary)) }
    }) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                StrokeIcon("M15 5l-7 7 7 7", TextPrimary, size = 24.dp, box = 24f)
            }
            Text(stringResource(R.string.gps_status), Modifier.padding(start = 4.dp), style = displayStyle(22, FontWeight.SemiBold))
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp)
                .background(Surface1, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val lit = when (q) { GpsQuality.Strong -> 4; GpsQuality.Weak -> 2; GpsQuality.Lost -> 0 }
            Row(Modifier.height(44.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(12, 22, 32, 44).forEachIndexed { i, h ->
                    Box(Modifier.width(8.dp).height(h.dp).background(if (i < lit) color else Quiet, RoundedCornerShape(2.dp)))
                }
            }
            Column(Modifier.weight(1f)) {
                // With no satellites but a rough Wi-Fi or mobile-network position, say so (amber) rather than "no signal".
                val approx = s.approximate
                Text(
                    stringResource(if (approx) R.string.gps_approx_title else when (q) { GpsQuality.Strong -> R.string.gps_strong_signal; GpsQuality.Weak -> R.string.gps_weak_signal; GpsQuality.Lost -> R.string.gps_no_signal }),
                    style = bodyStyle(20, FontWeight.Bold, if (approx) Amber else color),
                )
                Text(
                    stringResource(if (approx) R.string.gps_approx_sub else when (q) { GpsQuality.Strong -> R.string.gps_strong_sub; GpsQuality.Weak -> R.string.gps_weak_sub; GpsQuality.Lost -> if (s.secondsSinceFix >= 40) R.string.gps_lost_long_sub else R.string.gps_lost_sub }),
                    Modifier.padding(top = 2.dp), style = bodyStyle(13),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text((if (s.approximate) s.approxAccuracyM else s.accuracyM)?.let { "±${it.roundToInt()} m" } ?: "--", style = displayStyle(26, FontWeight.Bold))
                Text(stringResource(R.string.accuracy), style = bodyStyle(12))
            }
        }

        Text(
            if (!s.deviceLocationOn) stringResource(R.string.status_location_off) else stringResource(R.string.status_updated_ago, s.secondsSinceFix),
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, top = 8.dp),
            style = bodyStyle(12, color = if (!s.deviceLocationOn || s.secondsSinceFix >= 5) Amber else TextMuted),
        )
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCell(stringResource(R.string.satellites), s.used.toString(), " / ${s.satellites.size}", Modifier.weight(1f))
            // Android does not expose HDOP, so it cannot be shown.
            StatCell(stringResource(R.string.hdop), "--", "", Modifier.weight(1f))
            StatCell(stringResource(R.string.altitude), s.altitudeM?.let { "${it.roundToInt()} m" } ?: "--", "", Modifier.weight(1f))
        }

        if (!s.permissionGranted) {
            Text(
                stringResource(R.string.gps_needs_precise),
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp),
                style = bodyStyle(14, FontWeight.Medium, Amber),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)
                .background(Surface1, RoundedCornerShape(24.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SkyPlot(s.satellites)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    R.string.const_gps to Constellation.Gps, R.string.const_glonass to Constellation.Glonass,
                    R.string.const_galileo to Constellation.Galileo, R.string.const_beidou to Constellation.BeiDou,
                    R.string.const_other to Constellation.Other,
                ).filter { (_, c) -> c != Constellation.Other || s.count(c) > 0 }.forEach { (name, c) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(name), style = bodyStyle(13))
                        Text(s.count(c).toString(), style = bodyStyle(13, FontWeight.Bold, TextPrimary))
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)
                .background(Surface1, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(stringResource(R.string.signal_per_satellite), Modifier.padding(bottom = 14.dp), style = bodyStyle(13))
            Row(
                Modifier.fillMaxWidth().height(100.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                s.satellites.sortedByDescending { it.cn0 }.take(TOP_SATELLITES).forEach { sat ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            Modifier.fillMaxWidth()
                                .height((sat.cn0 * 1.1f).dp.coerceIn(4.dp, 75.dp))
                                .background(cn0Color(sat.cn0), RoundedCornerShape(4.dp))
                        )
                        Box(
                            Modifier.height(20.dp).padding(top = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(sat.label, style = bodyStyle(10, FontWeight.Medium))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, suffix: String, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight().background(Surface1, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = bodyStyle(12))
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Bottom) {
            Text(value, style = displayStyle(22, FontWeight.SemiBold))
            if (suffix.isNotEmpty()) Text(suffix, style = bodyStyle(13))
        }
    }
}

/** Sky plot: azimuth around, elevation from the rim (horizon) to the centre (overhead). */
@Composable
private fun SkyPlot(satellites: List<Satellite>) {
    Canvas(Modifier.size(132.dp).clipToBounds()) {
        val sc = size.width / 132f
        val c = Offset(66f * sc, 66f * sc)
        drawCircle(Color(0xFF2A3644), 62f * sc, c, style = Stroke(2f * sc))
        drawCircle(Quiet, 40f * sc, c, style = Stroke(2f * sc))
        drawCircle(Quiet, 18f * sc, c, style = Stroke(2f * sc))
        drawLine(Quiet, Offset(66f * sc, 4f * sc), Offset(66f * sc, 128f * sc))
        drawLine(Quiet, Offset(4f * sc, 66f * sc), Offset(128f * sc, 66f * sc))
        // Only satellites with a real position: Android reports azimuth and elevation as 0 or NaN before it knows them,
        // which put stray dots in the wrong place.
        satellites.filter { it.cn0 > 0f && !it.azimuthDeg.isNaN() && !it.elevationDeg.isNaN() && it.elevationDeg in 0f..90f }.forEach { sat ->
            val r = 62f * (1f - sat.elevationDeg / 90f)
            val a = Math.toRadians(sat.azimuthDeg.toDouble())
            val p = Offset(c.x + (r * sin(a)).toFloat() * sc, c.y - (r * cos(a)).toFloat() * sc)
            drawCircle(if (sat.usedInFix) cn0Color(sat.cn0) else Weak, (if (sat.cn0 >= OK_CN0) 6f else 4f) * sc, p)
        }
    }
}
