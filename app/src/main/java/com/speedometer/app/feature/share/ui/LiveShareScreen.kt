package com.speedometer.app.feature.share.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedometer.app.R
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.Danger
import com.speedometer.app.core.designsystem.Ok
import com.speedometer.app.core.designsystem.PrimaryButton
import com.speedometer.app.core.designsystem.ScrollPage
import com.speedometer.app.core.designsystem.Segmented
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.TextSoft
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.core.trip.DistanceUnit
import com.speedometer.app.core.trip.SpeedUnit
import com.speedometer.app.feature.permissions.ui.rememberLocationGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val Field = Color(0xFF0B0E11)

/**
 * Live Share, from design/Share.dc.html. There is no server behind the app, so the link is a map link to the position
 * you shared from, not a live feed: friends see where you were when you started or last updated. Arrival and
 * remaining distance come from the newest destination saved on the Nav screen (straight-line distance).
 */
@Composable
fun LiveShareScreen(onBack: () -> Unit, vm: LiveShareViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui by vm.ui.collectAsState()
    val gate = rememberLocationGate()
    gate.dialogs()
    val s = ui.session
    val mi = ui.unit == SpeedUnit.Mph

    // Check the stop rule whenever the screen is open: an hour passed, or the last position is at the destination.
    LaunchedEffect(s?.startedAtMs, s?.stopOption) {
        while (true) { vm.checkStopRule(); delay(15_000) }
    }

    fun sendOrStart() {
        gate.require {
            scope.launch {
                val snap = vm.startOrUpdate()
                if (snap == null) com.speedometer.app.core.designsystem.AppMessages.show(context.getString(R.string.share_no_location), com.speedometer.app.core.designsystem.MessageKind.Error)
            }
        }
    }

    ScrollPage(bottom = {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp, top = 8.dp)) {
            if (s != null) {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).border(1.dp, Danger, RoundedCornerShape(28.dp))
                        .clickable(role = Role.Button) { vm.stop(); onBack() },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.stop_sharing_label), style = bodyStyle(17, FontWeight.Bold, Danger)) }
            } else {
                PrimaryButton(stringResource(R.string.start_sharing), onClick = ::sendOrStart)
            }
        }
    }) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                StrokeIcon("M15 5l-7 7 7 7", TextPrimary, size = 24.dp, box = 24f)
            }
            Text(stringResource(R.string.tile_live_share), Modifier.padding(start = 4.dp), style = displayStyle(22, FontWeight.SemiBold))
        }

        // Status card
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)
                .background(Surface1, RoundedCornerShape(24.dp)).padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(10.dp).background(if (s != null) Ok else TextMuted, CircleShape))
                Text(
                    stringResource(if (s != null) R.string.sharing_on else R.string.sharing_off),
                    style = bodyStyle(16, FontWeight.Bold, TextPrimary),
                )
            }
            Text(
                stringResource(if (s != null) R.string.sharing_on_body else R.string.sharing_off_body),
                Modifier.padding(top = 6.dp), style = bodyStyle(14).copy(lineHeight = 20.sp),
            )
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
                MiniCard(
                    stringResource(R.string.share_arriving),
                    ui.arrivalMs?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime().format(time) } ?: "--",
                    Modifier.weight(1f),
                )
                MiniCard(
                    stringResource(R.string.share_remaining),
                    ui.remainingKm?.let { "%.1f %s".format(DistanceUnit.fromKm(it, mi), if (mi) "mi" else "km") } ?: "--",
                    Modifier.weight(1f),
                )
            }
            Text(
                if (ui.destination != null) stringResource(R.string.share_estimate_note, ui.destination!!.name.ifBlank { stringResource(R.string.place_destination) })
                else stringResource(R.string.share_no_destination),
                Modifier.padding(top = 10.dp), style = bodyStyle(12, color = TextMuted),
            )
        }

        if (s != null) {
            val link = "https://maps.google.com/?q=%.6f,%.6f".format(Locale.ROOT, s.lat, s.lon)
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                Text(stringResource(R.string.share_your_link), style = bodyStyle(13))
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(Surface1).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) { Text(link.removePrefix("https://"), style = bodyStyle(15, color = TextPrimary), maxLines = 1) }
                    Box(
                        Modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(Amber)
                            .clickable(role = Role.Button) {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText("link", link))
                                com.speedometer.app.core.designsystem.AppMessages.show(context.getString(R.string.share_copied))
                            }.padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.share_copy), style = bodyStyle(15, FontWeight.Bold, Bg)) }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineAction(stringResource(R.string.share_send), Modifier.weight(1f)) {
                        scope.launch {
                            val snap = vm.snapshot() ?: return@launch
                            context.startActivity(Intent.createChooser(shareIntent(context, snap), null))
                        }
                    }
                    OutlineAction(stringResource(R.string.share_update_position), Modifier.weight(1f)) { sendOrStart() }
                }
            }

            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                Text(stringResource(R.string.stop_sharing_label), Modifier.padding(bottom = 8.dp), style = bodyStyle(13))
                Segmented(
                    listOf(stringResource(R.string.stop_at_arrival), stringResource(R.string.stop_in_hour), stringResource(R.string.stop_manual)),
                    s.stopOption, { vm.setStopOption(it) },
                )
            }

            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)) {
                Text(stringResource(R.string.viewing_now), Modifier.padding(bottom = 8.dp), style = bodyStyle(13))
                Text(stringResource(R.string.share_no_viewers), style = bodyStyle(14, color = TextSoft))
            }
        }
    }
}

@Composable
private fun MiniCard(label: String, value: String, modifier: Modifier) {
    Column(modifier.height(56.dp).background(Field, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
        Text(label, style = bodyStyle(12))
        Text(value, style = displayStyle(20, FontWeight.SemiBold))
    }
}

@Composable
private fun OutlineAction(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).border(1.dp, com.speedometer.app.core.designsystem.Line3, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = bodyStyle(14, FontWeight.Bold, TextPrimary)) }
}

/** A share-sheet message with a map link for the position, and the speed when a trip is recording. */
private fun shareIntent(context: Context, snap: ShareSnapshot): Intent {
    val link = "https://maps.google.com/?q=%.6f,%.6f".format(Locale.ROOT, snap.point.lat, snap.point.lon)
    val text = snap.speed?.let { context.getString(R.string.share_text_speed, link, it, snap.unit.label) }
        ?: context.getString(R.string.share_text, link)
    return Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
}
