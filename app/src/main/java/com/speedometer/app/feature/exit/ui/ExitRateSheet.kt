package com.speedometer.app.feature.exit.ui

import com.speedometer.app.core.ads.startActivityAwayFromApp
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.speedometer.app.R
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.Line3
import com.speedometer.app.core.designsystem.PrimaryButton
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.bodyStyle

private val Scrim = Color(0xFF06080A)
private const val STAR = "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9L12 3z"
private const val DEFAULT_RATING = 4

/** Opens this app's Play Store page, falling back to the web page when no Play Store is installed. */
fun openPlayStore(context: Context) {
    val id = context.packageName
    try {
        context.startActivityAwayFromApp(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")))
    } catch (e: ActivityNotFoundException) {
        context.startActivityAwayFromApp(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")))
    }
}

/**
 * Exit and rate sheet, from design/Exit.dc.html: shown when the user backs out of Home. Rate us opens the Play
 * Store page; the stars only choose what to show, as the Play Store takes the real rating.
 */
@Composable
fun ExitRateSheet(onStay: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    var rating by rememberSaveable { mutableIntStateOf(DEFAULT_RATING) }
    Dialog(onDismissRequest = onStay, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Scrim), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Surface1)
                    .navigationBarsPadding().verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(width = 44.dp, height = 5.dp).background(Line3, RoundedCornerShape(3.dp)))
                Text(
                    stringResource(R.string.exit_title), Modifier.padding(top = 24.dp),
                    style = bodyStyle(26, FontWeight.Bold, TextPrimary), textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.exit_body), Modifier.padding(top = 8.dp),
                    style = bodyStyle(15).copy(lineHeight = 22.sp), textAlign = TextAlign.Center,
                )
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { n ->
                        val on = n <= rating
                        Box(
                            Modifier.size(52.dp).clickable(role = Role.Button) { rating = n }
                                .semantics { contentDescription = "$n" },
                            contentAlignment = Alignment.Center,
                        ) { StrokeIcon(STAR, if (on) Amber else Color(0xFF5A6776), size = 38.dp, box = 24f, filled = on) }
                    }
                }
                Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(stringResource(R.string.rate_us), onClick = { openPlayStore(context); onStay() })
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp))
                            .border(1.dp, Line3, RoundedCornerShape(26.dp)).clickable(role = Role.Button, onClick = onStay),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.stay), style = bodyStyle(16, FontWeight.Bold, TextPrimary)) }
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onExit),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.exit_app), style = bodyStyle(15, color = TextMuted)) }
                }
            }
        }
    }
}
