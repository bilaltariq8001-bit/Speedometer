package com.speedometer.app.feature.permissions.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.speedometer.app.R
import com.speedometer.app.core.designsystem.GhostButton
import com.speedometer.app.core.designsystem.PrimaryButton
import com.speedometer.app.core.designsystem.Radii
import com.speedometer.app.core.designsystem.Surface3
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.TextSoft

@Composable
fun AppDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .background(Surface3, RoundedCornerShape(Radii.sheet))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

/** App-owned explainer shown before the Android system location prompt. */
@Composable
fun LocationExplainerDialog(onContinue: () -> Unit, onNotNow: () -> Unit) {
    AppDialog(onNotNow) {
        Text(stringResource(R.string.loc_title), color = TextPrimary, style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.loc_body), color = TextSoft, style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.loc_point1), color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.loc_point2), color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        PrimaryButton(stringResource(R.string.continue_label), onContinue)
        GhostButton(stringResource(R.string.not_now), onNotNow)
    }
}

/** Shown when the phone's location switch is off, from design/GpsOff.dc.html. */
@Composable
fun DeviceLocationOffDialog(onTurnOn: () -> Unit, onNoThanks: () -> Unit) {
    AppDialog(onNoThanks) {
        // Red crosshair badge, centred title and text, as in the design.
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(48.dp).background(androidx.compose.ui.graphics.Color(0xFF2A1416), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                com.speedometer.app.core.designsystem.StrokeIcon(
                "M12 9a3 3 0 1 0 0 6a3 3 0 0 0 0-6 M12 4a8 8 0 1 0 0 16a8 8 0 0 0 0-16 M12 2v3M12 19v3M2 12h3M19 12h3",
                androidx.compose.ui.graphics.Color(0xFFFF5A4F), size = 26.dp, box = 24f,
                )
            }
        }
        Text(
            stringResource(R.string.location_off_title), color = TextPrimary, style = MaterialTheme.typography.headlineMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.location_off_body), color = TextSoft, style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(stringResource(R.string.turn_on), onTurnOn)
        GhostButton(stringResource(R.string.no_thanks), onNoThanks)
    }
}

/** Shown when location is denied for good: hands off to the app's system settings page. */
@Composable
fun LocationDeniedDialog(onOpenSettings: () -> Unit, onNotNow: () -> Unit) {
    AppDialog(onNotNow) {
        Text(stringResource(R.string.denied_title), color = TextPrimary, style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.denied_body), color = TextSoft, style = MaterialTheme.typography.bodyLarge)
        Text("1  " + stringResource(R.string.denied_step1), color = TextMuted)
        Text("2  " + stringResource(R.string.denied_step2), color = TextMuted)
        Text("3  " + stringResource(R.string.denied_step3), color = TextMuted)
        PrimaryButton(stringResource(R.string.open_settings), onOpenSettings)
        GhostButton(stringResource(R.string.not_now), onNotNow)
    }
}

/** Shown before the notification permission is asked for (Android 13 and up), from design/PermNotif.dc.html. */
@Composable
fun NotificationExplainerDialog(onContinue: () -> Unit, onNotNow: () -> Unit) {
    AppDialog(onNotNow) {
        Text(stringResource(R.string.notif_ask_title), color = TextPrimary, style = MaterialTheme.typography.headlineMedium)
        listOf(R.string.notif_ask_1, R.string.notif_ask_2, R.string.notif_ask_3).forEach {
            Text("•  " + stringResource(it), color = TextSoft, style = MaterialTheme.typography.bodyLarge)
        }
        Text(stringResource(R.string.notif_ask_foot), color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        PrimaryButton(stringResource(R.string.continue_label), onContinue)
        GhostButton(stringResource(R.string.not_now), onNotNow)
    }
}

/** Shown before the physical-activity permission is asked for, when Auto-detect trips is turned on. */
@Composable
fun ActivityDetectionExplainer(onAllow: () -> Unit, onNotNow: () -> Unit) {
    AppDialog(onNotNow) {
        Text(stringResource(R.string.ad_explain_title), color = TextPrimary, style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.ad_explain_body), color = TextSoft, style = MaterialTheme.typography.bodyLarge)
        listOf(R.string.ad_point1, R.string.ad_point2).forEach {
            Text("•  " + stringResource(it), color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        PrimaryButton(stringResource(R.string.ad_allow), onAllow)
        GhostButton(stringResource(R.string.not_now), onNotNow)
    }
}

/** Shown when location permission was removed after it had been granted, from design/PermAutoReset.dc.html. */
@Composable
fun PermissionsResetDialog(onRestore: () -> Unit, onLater: () -> Unit) {
    AppDialog(onLater) {
        Text(stringResource(R.string.reset_title), color = TextPrimary, style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.reset_body), color = TextSoft, style = MaterialTheme.typography.bodyLarge)
        PrimaryButton(stringResource(R.string.restore_permissions), onRestore)
        GhostButton(stringResource(R.string.later), onLater)
    }
}

/**
 * Returns the handler for the Auto-detect switch. Turning it on first explains and asks for the activity
 * permission (when the phone needs it); [set] receives the final state.
 */
@Composable
fun rememberAutoDetectSwitch(hasPermission: () -> Boolean, set: (Boolean) -> Unit): (Boolean) -> Unit {
    var showExplainer by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> set(granted) }
    if (showExplainer) {
        ActivityDetectionExplainer(
            onAllow = {
                showExplainer = false
                launcher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
            },
            onNotNow = { showExplainer = false; set(false) },
        )
    }
    return { on ->
        when {
            !on -> set(false)
            hasPermission() -> set(true)
            else -> showExplainer = true
        }
    }
}
