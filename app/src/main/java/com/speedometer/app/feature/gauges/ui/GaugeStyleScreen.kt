package com.speedometer.app.feature.gauges.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.speedometer.app.R
import com.speedometer.app.core.data.MeterStyle
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.DisplayFamily
import com.speedometer.app.core.designsystem.Ice
import com.speedometer.app.core.designsystem.Line
import com.speedometer.app.core.designsystem.PrimaryButton
import com.speedometer.app.core.designsystem.ScrollPage
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle

/** Display name of a meter, shared with Settings. */
fun meterNameRes(style: MeterStyle): Int = when (style) {
    MeterStyle.Dial -> R.string.meter_name_dial
    MeterStyle.Led -> R.string.meter_name_led
    MeterStyle.Bar -> R.string.meter_name_bar
    MeterStyle.Rings -> R.string.meter_name_rings
    MeterStyle.Graph -> R.string.meter_name_graph
    MeterStyle.GForce -> R.string.meter_name_gforce
    MeterStyle.Neon -> R.string.meter_name_neon
    MeterStyle.Sports -> R.string.model_sports
    MeterStyle.Superbike -> R.string.model_superbike
}

/**
 * Gauge style, from design/Gauges.dc.html. The design also lists a G-force pad and Vehicle meters; those need
 * sensor and vehicle data the app does not read yet, so they are not offered.
 */
@Composable
fun GaugeStyleScreen(
    onBack: () -> Unit, onVehicleMeters: () -> Unit, onPreview: (MeterStyle) -> Unit,
    vm: GaugeStyleViewModel = hiltViewModel(),
) {
    val saved by vm.style.collectAsState()
    var picked by androidx.compose.runtime.saveable.rememberSaveable(saved) { mutableStateOf(saved) }

    ScrollPage(bottom = {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp, top = 8.dp)) {
            PrimaryButton(
                stringResource(R.string.use_meter, stringResource(meterNameRes(picked))),
                onClick = { vm.save(picked, onBack) },
            )
        }
    }) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                StrokeIcon("M15 5l-7 7 7 7", TextPrimary, size = 24.dp, box = 24f)
            }
            Text(stringResource(R.string.more_gauge_style), Modifier.padding(start = 4.dp), style = displayStyle(22, FontWeight.SemiBold))
        }
        Text(stringResource(R.string.gauge_intro), Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp), style = bodyStyle(14))
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MeterStyle.entries.filter { it.generic && it != MeterStyle.Led }.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems.forEach { s -> StyleCard(s, s == picked, Modifier.weight(1f), onPreview = { onPreview(s) }) { picked = s } }
                    if (rowItems.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp).heightIn(min = 64.dp)
                .clip(RoundedCornerShape(18.dp)).background(Surface1)
                .clickable(role = Role.Button, onClick = onVehicleMeters).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StrokeIcon("M5 20h22v-5l-3-6H8l-3 6v5z M5 15h22 M9 20v3 M23 20v3", Amber, size = 26.dp, box = 32f)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.vehicle_meters), style = bodyStyle(15, FontWeight.Bold, TextPrimary))
                Text(stringResource(R.string.vehicle_meters_sub), Modifier.padding(top = 2.dp), style = bodyStyle(13))
            }
            StrokeIcon("M9 6l6 6-6 6", com.speedometer.app.core.designsystem.TextMuted, size = 20.dp, box = 24f)
        }
    }
}

@Composable
private fun StyleCard(style: MeterStyle, selected: Boolean, modifier: Modifier, onPreview: () -> Unit, onClick: () -> Unit) {
    val m = rememberTextMeasurer()
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(Surface1)
            .border(2.dp, if (selected) Amber else Color.Transparent, RoundedCornerShape(22.dp))
            .clickable(role = Role.RadioButton, onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(Modifier.size(width = 140.dp, height = 96.dp).align(Alignment.CenterHorizontally)) {
            val sc = size.width / 140f
            when (style) {
                MeterStyle.Dial -> dial(sc)
                MeterStyle.Led -> led(m, sc)
                MeterStyle.Bar -> bar(m, sc)
                MeterStyle.Rings -> rings(sc)
                MeterStyle.Graph -> graph(sc)
                MeterStyle.GForce -> gforce(sc)
                MeterStyle.Neon -> neon(sc)
                MeterStyle.Sports, MeterStyle.Superbike -> Unit
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(meterNameRes(style)), Modifier.weight(1f), style = bodyStyle(15, FontWeight.Bold, TextPrimary))
            Text(
                stringResource(R.string.preview),
                Modifier.heightIn(min = 32.dp).clickable(role = Role.Button, onClick = onPreview).padding(start = 8.dp),
                style = bodyStyle(13, FontWeight.Bold, Amber),
            )
        }
    }
}

private fun DrawScope.neon(sc: Float) {
    val r = 52f * sc
    val tl = Offset(70f * sc - r, 61.8f * sc - r)
    drawCircle(Color(0xFF0B0E12), 56f * sc, Offset(70f * sc, 61.8f * sc))
    drawArc(Line, 135f, 270f, false, tl, Size(2 * r, 2 * r), style = Stroke(6f * sc, cap = StrokeCap.Round))
    drawArc(com.speedometer.app.core.designsystem.Ok, 135f, 160f, false, tl, Size(2 * r, 2 * r), style = Stroke(6f * sc, cap = StrokeCap.Round))
    drawArc(com.speedometer.app.core.designsystem.Danger, 135f + 270f * 0.72f, 270f * 0.28f, false, tl, Size(2 * r, 2 * r), style = Stroke(3f * sc))
    drawLine(com.speedometer.app.core.designsystem.Ice, Offset(70f * sc, 62f * sc), Offset(94f * sc, 38f * sc), 2.5f * sc, StrokeCap.Round)
    drawCircle(com.speedometer.app.core.designsystem.Ice, 4f * sc, Offset(70f * sc, 62f * sc), style = Stroke(1.5f * sc))
}

private fun DrawScope.dial(sc: Float) {
    val r = 52f * sc
    val tl = Offset(70f * sc - r, 61.8f * sc - r)
    drawArc(Line, 152.2f, 235.6f, false, tl, Size(2 * r, 2 * r), style = Stroke(8f * sc, cap = StrokeCap.Round))
    drawLine(TextPrimary, Offset(70f * sc, 62f * sc), Offset(96f * sc, 34f * sc), 4f * sc, StrokeCap.Round)
    drawCircle(Amber, 6f * sc, Offset(70f * sc, 62f * sc))
}

private fun DrawScope.led(m: TextMeasurer, sc: Float) {
    val t = m.measure("88", TextStyle(color = Amber, fontSize = (44f * sc).toSp(), fontWeight = FontWeight.Bold, fontFamily = DisplayFamily))
    drawText(t, topLeft = Offset(70f * sc - t.size.width / 2f, 58f * sc - t.firstBaseline))
    for (i in 0 until 9) {
        drawRoundRect(if (i < 4) Amber else Line, Offset((12f + 12f * i) * sc, 72f * sc), Size(8f * sc, 12f * sc), CornerRadius(2f * sc))
    }
}

private fun DrawScope.bar(m: TextMeasurer, sc: Float) {
    drawRoundRect(Line, Offset(8f * sc, 30f * sc), Size(124f * sc, 14f * sc), CornerRadius(7f * sc))
    drawRoundRect(Amber, Offset(8f * sc, 30f * sc), Size(70f * sc, 14f * sc), CornerRadius(7f * sc))
    drawRoundRect(TextPrimary, Offset(96f * sc, 22f * sc), Size(3f * sc, 30f * sc), CornerRadius(1.5f * sc))
    val t = m.measure("62", TextStyle(color = TextPrimary, fontSize = (22f * sc).toSp(), fontWeight = FontWeight.Bold, fontFamily = DisplayFamily))
    drawText(t, topLeft = Offset(8f * sc, 80f * sc - t.firstBaseline))
}

private fun DrawScope.rings(sc: Float) {
    val c = Offset(70f * sc, 48f * sc)
    fun ring(r: Float, color: Color, frac: Float) {
        drawCircle(Line, r * sc, c, style = Stroke(7f * sc))
        drawArc(color, -90f, 360f * frac, false, Offset(c.x - r * sc, c.y - r * sc), Size(2 * r * sc, 2 * r * sc),
            style = Stroke(7f * sc, cap = StrokeCap.Round))
    }
    ring(40f, Amber, 0.62f)
    ring(27f, Ice, 0.86f)
}

private fun DrawScope.graph(sc: Float) {
    val pts = listOf(4f to 70f, 24f to 50f, 44f to 58f, 64f to 30f, 84f to 44f, 104f to 24f, 136f to 40f)
    val line = Path().apply { pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * sc, y * sc) else lineTo(x * sc, y * sc) } }
    val area = Path().apply { addPath(line); lineTo(136f * sc, 90f * sc); lineTo(4f * sc, 90f * sc); close() }
    drawPath(area, Amber.copy(alpha = 0.15f))
    drawPath(line, Amber, style = Stroke(4f * sc, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.gforce(sc: Float) {
    val c = Offset(70f * sc, 48f * sc)
    drawCircle(Line, 42f * sc, c, style = Stroke(3f * sc))
    drawCircle(Line, 21f * sc, c, style = Stroke(2f * sc))
    drawLine(Line, Offset(70f * sc, 6f * sc), Offset(70f * sc, 90f * sc), 2f * sc)
    drawLine(Line, Offset(28f * sc, 48f * sc), Offset(112f * sc, 48f * sc), 2f * sc)
    drawCircle(Amber, 8f * sc, Offset(84f * sc, 38f * sc))
}
