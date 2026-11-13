package com.speedometer.app.feature.permissions.ui

import com.speedometer.app.core.ads.startActivityAwayFromApp
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private val locationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private enum class Step { None, Explainer, Denied, DeviceOff }

/**
 * One place for every screen that needs the phone's location (GPS status, Zones, Live Share, Home).
 * [require] runs `onReady` straight away when Precise location is granted and the device location switch is on;
 * otherwise it walks the user through the app's own explainer, the system prompt, and the denied or
 * "location is off" dialogs, and runs `onReady` once everything is in place. Call [dialogs] once in the screen.
 */
class LocationGate internal constructor(
    val require: (onReady: () -> Unit) -> Unit,
    val dialogs: @Composable () -> Unit,
)

fun hasPreciseLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

fun isDeviceLocationOn(context: Context): Boolean =
    androidx.core.location.LocationManagerCompat.isLocationEnabled(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)

@Composable
fun rememberLocationGate(): LocationGate {
    val context = LocalContext.current
    var step by remember { mutableStateOf(Step.None) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun finishIfReady() {
        when {
            !hasPreciseLocation(context) -> step = Step.Denied
            !isDeviceLocationOn(context) -> step = Step.DeviceOff
            else -> { step = Step.None; pending?.invoke(); pending = null }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { finishIfReady() }

    val require: (() -> Unit) -> Unit = { onReady ->
        pending = onReady
        when {
            !hasPreciseLocation(context) -> step = Step.Explainer
            !isDeviceLocationOn(context) -> step = Step.DeviceOff
            else -> { pending = null; onReady() }
        }
    }

    val dialogs: @Composable () -> Unit = {
        when (step) {
            Step.Explainer -> LocationExplainerDialog(
                onContinue = { step = Step.None; launcher.launch(locationPermissions) },
                onNotNow = { step = Step.None; pending = null },
            )
            Step.Denied -> LocationDeniedDialog(
                onOpenSettings = {
                    step = Step.None
                    context.startActivityAwayFromApp(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
                onNotNow = { step = Step.None; pending = null },
            )
            Step.DeviceOff -> DeviceLocationOffDialog(
                onTurnOn = { step = Step.None; context.startActivityAwayFromApp(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                onNoThanks = { step = Step.None; pending = null },
            )
            Step.None -> Unit
        }
    }
    return LocationGate(require, dialogs)
}

/**
 * Shows the "Turn on location" dialog whenever the phone's location switch is off while you are in the app, on any
 * screen it is placed on. It reads the switch again when the app returns to the front and whenever Android reports a
 * change. "No thanks" hides it until the switch has been turned on and off again.
 */
@Composable
fun LocationOffPrompt() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(isDeviceLocationOn(context)) }
    var dismissed by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) on = isDeviceLocationOn(context)
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { on = isDeviceLocationOn(context) }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(
            context, receiver, android.content.IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); context.unregisterReceiver(receiver) }
    }
    androidx.compose.runtime.LaunchedEffect(on) { if (on) dismissed = false }
    if (!on && !dismissed) {
        DeviceLocationOffDialog(
            onTurnOn = { dismissed = true; context.startActivityAwayFromApp(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            onNoThanks = { dismissed = true },
        )
    }
}
