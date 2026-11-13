package com.speedometer.app.feature.gauges.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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
import com.speedometer.app.core.data.MeterStyle
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.FitInside
import com.speedometer.app.core.designsystem.PrimaryButton
import com.speedometer.app.core.designsystem.rememberLayout
import com.speedometer.app.core.designsystem.ScrollPage
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.TextSoft
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.feature.drive.ui.Meter
import com.speedometer.app.feature.drive.ui.MeterData
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.sin

private const val FRAME_MS = 100L
private const val STEP = FRAME_MS / 1000f
private const val DEMO_LIMIT_KMH = 60f
private const val DEMO_SCORE = 91
private const val HISTORY_EVERY = 10

/** Synthetic speed for the preview, the same wave the design's meter pages use. */
internal fun demoSpeed(t: Float): Float =
    (70f + 38f * sin(t * 0.55f) + 14f * sin(t * 1.7f) + 6f * sin(t * 3.1f)).coerceIn(0f, 200f)

/**
 * A meter moving on made-up speeds, from design/MeterDial.dc.html and its sibling pages: the meter chips at the
 * top switch between meters, and Use this meter saves the choice. Nothing here is real driving data.
 */
@Composable
fun MeterPreviewScreen(initial: MeterStyle, onBack: () -> Unit, vm: GaugeStyleViewModel = hiltViewModel()) {
    val unit by vm.unit.collectAsState()
    var style by androidx.compose.runtime.saveable.rememberSaveable(initial) { androidx.compose.runtime.mutableStateOf(initial) }
    var t by remember { mutableFloatStateOf(0f) }
    var history by remember { androidx.compose.runtime.mutableStateOf(emptyList<Float>()) }
    LaunchedEffect(Unit) {
        var frame = 0
        while (true) {
            delay(FRAME_MS)
            t += STEP
            if (++frame % HISTORY_EVERY == 0) history = (history + demoSpeed(t)).takeLast(60)
        }
    }

    val speed = demoSpeed(t)
    val gx = 0.38f * sin(t * 0.9f) + 0.1f * sin(t * 2.3f)
    val gy = 0.3f * sin(t * 0.55f + 1f) + 0.08f * sin(t * 1.9f)
    val data = MeterData(
        speedKmh = speed,
        speedText = unit.fromKmh(speed).roundToInt().toString(),
        unit = unit.label,
        limitKmh = DEMO_LIMIT_KMH,
        limitText = unit.fromKmh(DEMO_LIMIT_KMH).roundToInt().toString(),
        score = DEMO_SCORE,
        accelMs2 = (demoSpeed(t) - demoSpeed(t - STEP)) / STEP / 3.6f,
        history = history,
        toUnit = { unit.fromKmh(it) },
        gLateral = gx, gLong = gy, gPeak = 0.62f,
        gTrail = (1..8).map { k -> val tt = t - k * 0.2f; (0.38f * sin(tt * 0.9f)) to (0.3f * sin(tt * 0.55f + 1f)) },
    )

    val header: @Composable () -> Unit = {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                StrokeIcon("M15 5l-7 7 7 7", TextPrimary, size = 24.dp, box = 24f)
            }
            Text(stringResource(meterNameRes(style)), Modifier.padding(start = 4.dp), style = displayStyle(22, FontWeight.SemiBold))
        }
    }
    val meterOptions: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MeterStyle.entries.filter { it.generic }.forEach { s ->
                val on = s == style
                Box(
                    Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) Amber else Surface1)
                        .clickable(role = Role.Button) { style = s }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(chipLabel(s)), style = bodyStyle(13, if (on) FontWeight.Bold else FontWeight.Medium, if (on) Bg else TextSoft))
                }
            }
        }
    }
    val useMeter: () -> Unit = { vm.save(style, onBack) }

    if (rememberLayout().isLandscape) {
        Column(Modifier.fillMaxSize().background(Bg).navigationBarsPadding()) {
            header()
            meterOptions()
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                FitInside(Modifier.fillMaxSize()) { Meter(style, data) }
            }
            Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp, top = 8.dp)) {
                PrimaryButton(stringResource(R.string.use_this_meter), onClick = useMeter)
            }
        }
    } else {
        ScrollPage(bottom = {
            Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp, top = 8.dp)) {
                PrimaryButton(stringResource(R.string.use_this_meter), onClick = useMeter)
            }
        }) {
            header()
            meterOptions()
            Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp), contentAlignment = Alignment.Center) {
                Meter(style, data)
            }
        }
    }
}

private fun chipLabel(s: MeterStyle): Int = when (s) {
    MeterStyle.Dial -> R.string.meter_dial
    MeterStyle.Led -> R.string.meter_led
    MeterStyle.Bar -> R.string.meter_bar
    MeterStyle.Rings -> R.string.meter_rings
    MeterStyle.Graph -> R.string.meter_graph
    MeterStyle.GForce -> R.string.meter_gforce
    MeterStyle.Neon -> R.string.meter_neon
    MeterStyle.Sports, MeterStyle.Superbike -> R.string.meter_dial
}
