package com.speedometer.app.feature.home.ui

import com.speedometer.app.core.ads.startActivityAwayFromApp
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.em
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.speedometer.app.BuildConfig
import com.speedometer.app.core.data.MeterStyle
import com.speedometer.app.feature.gauges.ui.meterNameRes
import com.speedometer.app.R
import com.speedometer.app.core.common.findActivity
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.BodyFamily
import com.speedometer.app.core.designsystem.Danger
import com.speedometer.app.core.designsystem.DisplayFamily
import com.speedometer.app.core.designsystem.GhostButton
import com.speedometer.app.core.designsystem.GAUGE_START_DEG
import com.speedometer.app.core.designsystem.GAUGE_SWEEP_DEG
import com.speedometer.app.core.designsystem.Line2
import com.speedometer.app.core.designsystem.Line3
import com.speedometer.app.core.designsystem.Ok
import com.speedometer.app.core.trip.GpsQuality
import com.speedometer.app.core.designsystem.Ice
import com.speedometer.app.core.location.GeoPoint
import com.speedometer.app.core.location.toCoordinateText
import com.speedometer.app.core.trip.SpeedUnit
import kotlin.math.roundToInt
import com.speedometer.app.core.map.MapDot
import com.speedometer.app.core.map.RouteMap
import org.maplibre.android.geometry.LatLng
import com.speedometer.app.core.designsystem.rememberLayout
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.Surface2
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.TextSoft
import com.speedometer.app.feature.exit.ui.ExitRateSheet
import com.speedometer.app.feature.permissions.ui.DeviceLocationOffDialog
import com.speedometer.app.feature.permissions.ui.LocationDeniedDialog
import com.speedometer.app.feature.permissions.ui.NotificationExplainerDialog
import com.speedometer.app.feature.permissions.ui.PermissionsResetDialog
import com.speedometer.app.feature.permissions.ui.LocationExplainerDialog
import com.speedometer.app.service.TripService

private val locationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)


private val PermissionsOffBg = androidx.compose.ui.graphics.Color(0xFF2A2110)
/** Below this height (split-screen, tiny windows) the fixed layout cannot fit even with a small gauge, so only then does Home scroll. */
private val COMPACT_SCREEN = 560.dp

/** After this long with neither a GPS fix nor a rough position, the screens say there is no signal instead of "searching". */
private const val GIVE_UP_SECONDS = 40
private val GpsOffBg = androidx.compose.ui.graphics.Color(0xFF2A1416)
private val GpsOffText = androidx.compose.ui.graphics.Color(0xFFFF8A80)

private enum class Dialog { None, Explainer, Denied, LocationOff, Notification, Reset }

private fun body(sizeSp: Int, weight: FontWeight = FontWeight.Normal, color: Color = TextMuted) =
    TextStyle(fontFamily = BodyFamily, fontWeight = weight, fontSize = sizeSp.sp, color = color)

private fun display(sizeSp: Int, weight: FontWeight, color: Color = TextPrimary) =
    TextStyle(fontFamily = DisplayFamily, fontWeight = weight, fontSize = sizeSp.sp, color = color)

/**
 * Home, from design/Home.dc.html. Start trip runs the permission flow: own explainer, system prompt,
 * own denied dialog with a settings handoff. The explainer also opens once on the first visit after setup.
 */
@Composable
fun HomeScreen(
    onEndTrip: () -> Unit, onZones: () -> Unit, onGpsStatus: () -> Unit,
    onLiveShare: () -> Unit, onGaugeStyle: () -> Unit, onHud: () -> Unit,
    onHistory: () -> Unit = {}, onInsights: () -> Unit = {}, onSettings: () -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
    driveVm: com.speedometer.app.feature.drive.ui.LiveDriveViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settingsState by vm.settings.collectAsStateWithLifecycle()
    val settings = settingsState
    val trip by driveVm.trip.collectAsStateWithLifecycle()
    val chosenStyle = settings?.meterStyle ?: com.speedometer.app.core.data.MeterStyle.Dial
    var homeTab by androidx.compose.runtime.saveable.rememberSaveable(chosenStyle) {
        androidx.compose.runtime.mutableIntStateOf(if (chosenStyle == com.speedometer.app.core.data.MeterStyle.Led) 1 else 0)
    }
    fun tabSwipeModifier() = Modifier.pointerInput(homeTab) {
        var dragX = 0f
        detectHorizontalDragGestures(
            onHorizontalDrag = { _, dragAmount -> dragX += dragAmount },
            onDragEnd = {
                if (dragX > 48.dp.toPx()) homeTab = (homeTab - 1).coerceAtLeast(0)
                else if (dragX < -48.dp.toPx()) homeTab = (homeTab + 1).coerceAtMost(2)
            },
        )
    }
    val gps by vm.gps.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf(Dialog.None) }
    var granted by remember { mutableStateOf(hasLocation(context)) }
    // The phone's own location switch, kept current: re-read when the app comes back to the front and whenever
    // Android reports a change (for example from the quick settings panel).
    var locOn by remember { mutableStateOf(deviceLocationOn(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { locOn = deviceLocationOn(context); granted = hasLocation(context) }
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { locOn = deviceLocationOn(context) }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(
            context, receiver, android.content.IntentFilter(android.location.LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); context.unregisterReceiver(receiver) }
    }
    val deviceOff = granted && !locOn
    // Opening the app with location switched off brings up the "Turn on location" screen (design: Device location off).
    var offShown by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(deviceOff, settings?.locationAsked) {
        if (!deviceOff) { offShown = false }
        else if (!offShown && settings?.locationAsked == true && dialog == Dialog.None) { offShown = true; dialog = Dialog.LocationOff }
    }

    /** True on Android 13 and up until the notification permission has been granted or asked for once. */
    fun needsNotificationAsk(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            settings?.notificationAsked == false &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun begin() {
        TripService.start(context)
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { begin() }

    /** Next step of Start trip once location is ready: ask about notifications once, then record. */
    fun proceed() {
        if (needsNotificationAsk()) dialog = Dialog.Notification else begin()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // Speed needs Precise location; Approximate alone gives no speed, so it counts as not granted.
        granted = hasLocation(context)
        if (!granted) {
            val activity = context.findActivity()
            val canAskAgain = activity != null && locationPermissions.any {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
            val approximateOnly = result.values.any { it }
            dialog = if (canAskAgain && !approximateOnly) Dialog.None else Dialog.Denied
        }
    }

    // First visit after setup: show the location explainer over Home (design: PermLocation).
    LaunchedEffect(settings?.locationAsked) {
        if (settings?.locationAsked == false && !granted) dialog = Dialog.Explainer
    }

    val autoDetect = vm.autoDetectActive(settings?.autoDetect == true)

    // Permission was granted before and is gone now: Android reset it, or the user removed it.
    val permissionsReset = settings?.locationGrantedBefore == true && !granted
    LaunchedEffect(granted) { if (granted) vm.markLocationGranted() }
    var resetShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(permissionsReset) {
        if (permissionsReset && !resetShown) { resetShown = true; dialog = Dialog.Reset }
    }

    val layout = rememberLayout()
    var here by remember { mutableStateOf<GeoPoint?>(null) }
    LaunchedEffect(granted) { if (granted) here = vm.locate() }
    val place by androidx.compose.runtime.produceState<String?>(null, here) { val p = here; value = if (p != null) vm.placeAt(p) else null }
    val homeUnit = settings?.unit ?: SpeedUnit.Kmh
    var showExit by rememberSaveable { mutableStateOf(false) }
    var lastBackPressAt by rememberSaveable { mutableLongStateOf(0L) }
    BackHandler(enabled = dialog == Dialog.None) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackPressAt < 2000L) {
            showExit = true
        } else {
            lastBackPressAt = now
        }
    }

    val headerBar: @Composable (Boolean) -> Unit = { showGps ->
        // App name and the live GPS line on the left; Trips, Insights and Settings as icons on the right (there is no bottom bar).
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = display(22, FontWeight.Bold), maxLines = 1)
                if (showGps) {
                    GpsStatusLine(
                    granted && locOn, permissionsReset, gps.quality, gps.approximate, gps.secondsSinceFix >= GIVE_UP_SECONDS,
                    gps.used, gps.satellites.size, onGpsStatus,
                )
                }
            }
            HeaderIcon(
                HEADER_ORIENTATION_ICON,
                stringResource(if (layout.isLandscape) R.string.orientation_switch_to_portrait else R.string.orientation_switch_to_landscape),
            ) {
                vm.setScreenOrientation(
                    if (layout.isLandscape) com.speedometer.app.core.data.ScreenOrientation.Portrait
                    else com.speedometer.app.core.data.ScreenOrientation.Landscape,
                )
            }
            HeaderIcon(HEADER_HISTORY_ICON, R.string.tab_trips, onHistory)
            HeaderIcon(HEADER_INSIGHTS_ICON, R.string.tab_insights, onInsights)
            HeaderIcon(HEADER_SETTINGS_ICON, R.string.tab_settings, onSettings)
        }
    }
    val header: @Composable () -> Unit = { headerBar(true) }
    // Zones and Gauge style live in Settings; Home keeps the gauge, the vehicle menu, the four trip numbers and Start trip.
    // Vehicle menu (walk, bicycle, motorbike, car). It is only on this idle Home; once a trip starts, Home shows the live trip
    // instead, so the vehicle cannot be changed during a trip.
    val vehicleMenu: @Composable () -> Unit = {
        com.speedometer.app.core.designsystem.VehicleSelector(
            settings?.vehicle ?: com.speedometer.app.core.data.Vehicle.Car,
            { vm.setVehicle(it) },
            Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
        )
    }
    // The four numbers a trip will fill in, at rest (same cards as on the live trip screen).
    val idleStats: @Composable () -> Unit = {
        val speedUnit = settings?.unit?.label ?: "km/h"
        val distanceUnit = if (settings?.mph == true) "mi" else "km"
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                com.speedometer.app.feature.drive.ui.Stat(stringResource(R.string.duration_label), "00:00:00", TextPrimary, Modifier.weight(1f), com.speedometer.app.feature.drive.ui.StatIcons.Duration)
                com.speedometer.app.feature.drive.ui.Stat(stringResource(R.string.distance), "0.0", TextPrimary, Modifier.weight(1f), com.speedometer.app.feature.drive.ui.StatIcons.Distance, suffix = distanceUnit)
            }
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                com.speedometer.app.feature.drive.ui.Stat(stringResource(R.string.avg_speed_label), "0", TextPrimary, Modifier.weight(1f), com.speedometer.app.feature.drive.ui.StatIcons.Average, suffix = speedUnit)
                com.speedometer.app.feature.drive.ui.Stat(stringResource(R.string.max_speed_label), "0", TextPrimary, Modifier.weight(1f), com.speedometer.app.feature.drive.ui.StatIcons.Max, suffix = speedUnit)
            }
        }
    }
    val tiles: @Composable () -> Unit = {
        vehicleMenu()
        idleStats()
    }
    val startButton: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().height(if (layout.isTablet) 68.dp else 60.dp).clip(RoundedCornerShape(34.dp)).background(if (permissionsReset || deviceOff) Line2 else Amber)
                    .clickable(role = Role.Button) {
                        when {
                            !hasLocation(context) -> dialog = Dialog.Explainer
                            !deviceLocationOn(context) -> dialog = Dialog.LocationOff
                            else -> proceed()
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(22.dp)) {
                    val sc = size.width / 24f
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(7f * sc, 4f * sc); lineTo(20f * sc, 12f * sc); lineTo(7f * sc, 20f * sc); close()
                    }
                    drawPath(p, Bg)
                }
                Text(stringResource(R.string.start_trip), style = body(if (layout.isTablet) 20 else 18, FontWeight.Bold, if (permissionsReset || deviceOff) TextMuted else Bg))
            }
        }
    }

    // Gauge, Digital and Map are available before a trip starts, as on Live drive. They only change what Home shows
    // at rest; Start trip then opens Live drive, where these cards give way to the live controls.
    // Follow the style chosen in Gauge style: LED digital opens the Digital tab, any other style the Gauge tab.
    val modeTabs: @Composable () -> Unit = {
        com.speedometer.app.core.designsystem.Segmented(
            listOf(stringResource(meterNameRes(chosenStyle)), stringResource(R.string.tab_digital), stringResource(R.string.map_label)),
            homeTab,
            { i ->
                // The tabs only change what Home shows; they never overwrite the gauge style chosen in Gauge style.
                homeTab = i
            },
            Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp).then(tabSwipeModifier()),
        )
    }
    val restSpeedText = if (permissionsReset || deviceOff) "--" else "0"
    val unitLabel = settings?.unit?.label ?: "km/h"
    val gaugeArea: @Composable (Dp, Boolean) -> Unit = { maxG, fill ->
        when (homeTab) {
            // Use the same meter implementation as the live screen, including for the classic dial.
            0 -> if (settings == null) {
                Box(
                    Modifier.fillMaxWidth()
                        .then(if (fill) Modifier.fillMaxHeight() else Modifier.height(260.dp)),
                )
            } else FitOrNot(fill) {
                com.speedometer.app.feature.drive.ui.Meter(
                    chosenStyle,
                    com.speedometer.app.feature.drive.ui.MeterData(
                        0f, restSpeedText, unitLabel, null, null, 100, 0f, maxKmh = settings.rangeKmh,
                        toUnit = { homeUnit.fromKmh(it) },
                    ),
                    Modifier.padding(horizontal = 20.dp),
                )
            }
            1 -> FitOrNot(fill) {
                com.speedometer.app.feature.drive.ui.Meter(
                    com.speedometer.app.core.data.MeterStyle.Led,
                    com.speedometer.app.feature.drive.ui.MeterData(
                        0f, restSpeedText, unitLabel, null, null, 100, 0f,
                        maxKmh = settings?.rangeKmh ?: 200f,
                        toUnit = { homeUnit.fromKmh(it) },
                    ),
                    Modifier.padding(horizontal = 20.dp),
                )
            }
            else -> Box(
                Modifier.then(if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(260.dp))
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp).clip(RoundedCornerShape(24.dp)),
            ) {
                // Locate button: brings the map back to your position after you have dragged it away.
                var camera by remember { mutableStateOf<com.speedometer.app.core.map.CameraTarget?>(null) }
                var cameraId by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                val locateScope = androidx.compose.runtime.rememberCoroutineScope()
                RouteMap(
                    lines = emptyList(),
                    dots = here?.let { listOf(MapDot(LatLng(it.lat, it.lon), Ice, 9f, Bg)) } ?: emptyList(),
                    follow = here != null, flyTo = camera, modifier = Modifier.fillMaxSize(),
                    compassTopPaddingDp = 48,
                )
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(bottom = 12.dp, end = 12.dp).size(52.dp).clip(CircleShape).background(com.speedometer.app.core.designsystem.Nav)
                        .clickable(role = Role.Button) {
                            // Ask for a fresh position first, so the button goes to where you are now, not where the screen opened.
                            locateScope.launch {
                                vm.locate()?.let { here = it }
                                here?.let { cameraId += 1; camera = com.speedometer.app.core.map.CameraTarget(LatLng(it.lat, it.lon), cameraId, 15.0) }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { StrokeIcon("M12 9a3 3 0 1 0 0 6a3 3 0 0 0 0-6 M12 4a8 8 0 1 0 0 16a8 8 0 0 0 0-16 M12 2v3M12 19v3M2 12h3M19 12h3", Ice, size = 26.dp, box = 24f) }
            }
        }
    }

    if (trip.phase != com.speedometer.app.core.trip.TripPhase.Idle) {
        // The same top bar as the idle Home (name and Trips, Insights, Settings), with the fixed-size GPS signal, stays above the live trip.
        Column(Modifier.fillMaxSize().background(Bg)) {
            headerBar(true)
            Box(Modifier.weight(1f).fillMaxWidth()) {
        if (homeTab == 2) {
            com.speedometer.app.feature.drive.ui.LiveMapScreen(
                onBack = { homeTab = 0 },
                onEnd = { TripService.stop(context); onEndTrip() },
                vm = driveVm,
            )
        } else {
            com.speedometer.app.feature.drive.ui.LiveDriveScreen(
                initialDigital = homeTab == 1,
                onEnd = { TripService.stop(context); onEndTrip() },
                onMap = { homeTab = 2 },
                onGpsDetails = onGpsStatus,
                onHud = onHud,
                onShare = onLiveShare,
                vm = driveVm,
            )
        }
            }
        }
    } else if (layout.isLandscape) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
            val gaugePaneWeight = if (maxWidth < 700.dp) 0.52f else 0.5f
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(gaugePaneWeight).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    modeTabs()
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().then(tabSwipeModifier()), contentAlignment = Alignment.Center) {
                        gaugeArea(minOf(maxHeight * 1.12f, maxWidth * 0.95f), true)
                    }
                }
                Column(Modifier.weight(1f - gaugePaneWeight).fillMaxHeight().padding(end = 4.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        header()
                        Spacer(Modifier.height(4.dp))
                        if (homeTab != 2) vehicleMenu()
                        if (layout.isTablet && homeTab != 2) HomeMapPreview(here, place)
                        if (homeTab != 2) idleStats()
                    }
                    startButton()
                }
            }
        }
    } else BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
      if (maxHeight >= COMPACT_SCREEN) {
        // Tall screens: the gauge takes all the room between the header and the cards, so it is as big as it can be
        // and the Limit Guard card, the tiles and Start trip sit at the bottom.
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).widthIn(max = layout.contentMax).fillMaxWidth()) {
                header()
                modeTabs()
                if (homeTab == 2) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp, bottom = 8.dp).clip(RoundedCornerShape(0.dp))) {
                        gaugeArea(0.dp, true)
                    }
                } else {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().then(tabSwipeModifier()), contentAlignment = Alignment.Center) {
                        gaugeArea(minOf(maxHeight * (340f / 300f), if (layout.isTablet) 600.dp else 480.dp), true)
                    }
                    tiles()
                }
                Spacer(Modifier.height(10.dp))
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.widthIn(max = layout.contentMax)) { startButton() }
            }
        }
      } else {
        Column(Modifier.fillMaxSize().background(Bg)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = layout.contentMax).fillMaxWidth()) {
                    header()
                    modeTabs()
                    if (homeTab == 2) {
                        Box(Modifier.fillMaxWidth().height(360.dp).padding(top = 8.dp, bottom = 8.dp).clip(RoundedCornerShape(0.dp))) {
                            gaugeArea(360.dp, false)
                        }
                    } else {
                        Box(Modifier.padding(top = 12.dp).then(tabSwipeModifier())) { gaugeArea(if (layout.isTablet) 420.dp else 300.dp, false) }
                        tiles()
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.widthIn(max = layout.contentMax)) { startButton() }
            }
        }
      }
    }

    if (showExit) {
        ExitRateSheet(onStay = { showExit = false }, onExit = { context.findActivity()?.finish() })
    }

    when (dialog) {
        Dialog.Explainer -> LocationExplainerDialog(
            onContinue = { dialog = Dialog.None; vm.markLocationAsked(); launcher.launch(locationPermissions) },
            onNotNow = { dialog = Dialog.None; vm.markLocationAsked() },
        )
        Dialog.Denied -> LocationDeniedDialog(
            onOpenSettings = {
                dialog = Dialog.None
                context.startActivityAwayFromApp(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
            onNotNow = { dialog = Dialog.None },
        )
        Dialog.LocationOff -> DeviceLocationOffDialog(
            onTurnOn = {
                dialog = Dialog.None
                context.startActivityAwayFromApp(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            },
            onNoThanks = { dialog = Dialog.None },
        )
        Dialog.Notification -> NotificationExplainerDialog(
            onContinue = {
                dialog = Dialog.None
                vm.markNotificationAsked()
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onNotNow = { dialog = Dialog.None; vm.markNotificationAsked(); begin() },
        )
        Dialog.Reset -> PermissionsResetDialog(
            onRestore = { dialog = Dialog.None; launcher.launch(locationPermissions) },
            onLater = { dialog = Dialog.None },
        )
        Dialog.None -> Unit
    }
}

private fun deviceLocationOn(context: Context): Boolean =
    androidx.core.location.LocationManagerCompat.isLocationEnabled(context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager)

@Composable
private fun GpsPill(
    granted: Boolean, permissionsOff: Boolean, quality: GpsQuality, approximate: Boolean, gaveUp: Boolean, onClick: () -> Unit,
) {
    if (granted && !permissionsOff && quality != GpsQuality.Strong) return
    // Reflects the location permission only; live fix quality is shown once a trip is recording.
    val tint = when {
        permissionsOff -> Amber
        !granted -> Danger
        quality == GpsQuality.Strong -> Ok
        else -> Amber
    }
    val label = when {
        permissionsOff -> R.string.permissions_off
        !granted -> R.string.location_off
        quality == GpsQuality.Strong -> R.string.gps_strong_pill
        quality == GpsQuality.Weak -> R.string.gps_weak_pill
        approximate -> R.string.gps_approx_pill // no satellites, but a rough position from Wi-Fi and mobile network
        gaveUp -> R.string.gps_no_signal_pill // the search has gone on long enough that "searching" would be misleading
        else -> R.string.gps_searching_pill
    }
    Row(
        Modifier
            .widthIn(min = 118.dp, max = 118.dp)
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (permissionsOff) PermissionsOffBg else if (!granted) GpsOffBg else Surface1)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(tint, CircleShape))
        Text(
            stringResource(label),
            style = body(13, if (permissionsOff || !granted) FontWeight.Bold else FontWeight.Normal, if (permissionsOff) Amber else if (!granted) GpsOffText else TextSoft),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val HEADER_HISTORY_ICON = "M12 3a9 9 0 1 0 0 18a9 9 0 0 0 0-18 M12 7v5l3 2"
private const val HEADER_INSIGHTS_ICON = "M5 20V10M12 20V4M19 20v-7"
private const val HEADER_SETTINGS_ICON = "M4 7h10M18 7h2M4 17h2M10 17h10 M16 5a2 2 0 1 0 0 4a2 2 0 0 0 0-4 M8 15a2 2 0 1 0 0 4a2 2 0 0 0 0-4"
private const val HEADER_ORIENTATION_ICON = "M7 3h10v18H7z M4 8V5a2 2 0 0 1 2-2 M20 16v3a2 2 0 0 1-2 2"

/** A round 44 dp icon button for the top right of Home. */
@Composable
private fun HeaderIcon(path: String, description: Int, onClick: () -> Unit) {
    val label = stringResource(description)
    HeaderIcon(path, label, onClick)
}

@Composable
private fun HeaderIcon(path: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { StrokeIcon(path, Amber, size = 24.dp, box = 24f) }
}

/**
 * Signal bars and "GPS: Strong" under the app name, in a fixed-size box: the bars are always 20 x 14 dp and the line always the
 * same width, so nothing grows, shrinks or shifts as the state changes. Tap for GPS status.
 */
@Composable
private fun GpsStatusLine(
    granted: Boolean, permissionsOff: Boolean, quality: GpsQuality, approximate: Boolean, gaveUp: Boolean,
    used: Int, inView: Int, onClick: () -> Unit,
) {
    @Suppress("UNUSED_VARIABLE") val unusedCounts = used + inView
    // Only three states are shown, taken from the same live quality as the GPS status screen, so the two always agree:
    // Strong (green), Weak (amber) or No signal (red). A rough network position without GPS counts as Weak.
    val usable = granted && !permissionsOff
    val state = when {
        usable && quality == GpsQuality.Strong -> R.string.gps_state_strong
        usable && (quality == GpsQuality.Weak || approximate) -> R.string.gps_state_weak
        else -> R.string.gps_state_none
    }
    val bars = when (state) {
        R.string.gps_state_strong -> 4
        R.string.gps_state_weak -> 2
        else -> 0
    }
    val tint = when (state) {
        R.string.gps_state_strong -> Ok
        R.string.gps_state_weak -> Amber
        else -> Danger
    }
    Row(
        Modifier.width(176.dp).heightIn(min = 32.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(width = 20.dp, height = 14.dp)) {
            val barW = 3.dp.toPx()
            val gap = 2.dp.toPx()
            for (i in 0 until 4) {
                val h = size.height * (i + 1) / 4f
                drawRect(
                    if (i < bars || bars == 0) tint.copy(alpha = if (bars == 0) 0.9f else 1f) else TextMuted.copy(alpha = 0.35f),
                    topLeft = Offset(i * (barW + gap), size.height - h), size = Size(barW, h),
                )
            }
        }
        Text("GPS: " + stringResource(state), style = body(13), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Gauge at rest, 340 x 300 viewBox (arc centre 170,170, radius 130, 18 stroke). */
@Composable
private fun HomeGauge(maxGauge: Dp, topPadding: Dp = 20.dp, speedText: String = "0", unitLabel: String = "km/h") {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = topPadding), contentAlignment = Alignment.TopCenter) {
        val viewport = maxWidth
        val width = min(viewport, maxGauge)
        Box(Modifier.width(width).aspectRatio(340f / 300f)) {
            Canvas(Modifier.fillMaxSize()) {
                val sc = size.width / 340f
                val r = 130f * sc
                val tl = Offset(170f * sc - r, 170f * sc - r)
                val sz = Size(2 * r, 2 * r)
                drawArc(Surface2, GAUGE_START_DEG, GAUGE_SWEEP_DEG, false, tl, sz, style = Stroke(18f * sc, cap = StrokeCap.Round))
                drawArc(Amber, GAUGE_START_DEG, 0.01f, false, tl, sz, style = Stroke(18f * sc, cap = StrokeCap.Round))
            }
            Column(
                Modifier.fillMaxWidth().padding(top = width * 0.28f / (340f / 300f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    speedText, style = display(92, FontWeight.Bold, if (speedText == "0") TextPrimary else Line3).copy(
                        fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { min(92.dp, viewport * 0.24f).toSp() },
                        lineHeight = 1.em,
                    ),
                )
                Text(unitLabel, Modifier.padding(top = 4.dp), style = body(16), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun LimitGuardCard(onClick: () -> Unit, signText: String, place: String?, userLimitNote: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp)
            .clip(RoundedCornerShape(20.dp)).background(Surface1).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).background(TextPrimary, CircleShape).border(5.dp, Danger, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(signText, style = display(17, FontWeight.Bold, Bg)) }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.guard_is_on), style = body(16, FontWeight.Bold, TextPrimary))
            Text(
                userLimitNote ?: if (place != null) stringResource(R.string.guard_place_warns, place) else stringResource(R.string.guard_warns_at),
                Modifier.padding(top = 2.dp), style = body(13),
            )
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            StrokeIcon("M9 6l6 6-6 6", TextMuted, size = 22.dp, box = 24f)
        }
    }
}

@Composable
private fun Tile(label: Int, icon: String, modifier: Modifier, onClick: (() -> Unit)? = null, value: String? = null) {
    Column(
        modifier.heightIn(min = 84.dp).clip(RoundedCornerShape(18.dp)).background(Surface1)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        StrokeIcon(icon, Amber, size = 20.dp, box = 24f)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(stringResource(label), style = body(12, color = TextSoft))
            Text(value ?: " ", style = body(13, FontWeight.Bold, TextPrimary), color = if (value != null) TextPrimary else Color.Transparent)
        }
    }
}

/** True only with Precise (fine) location: the speed comes from GPS, which Approximate does not give. */
private fun hasLocation(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Map preview of where you are, from design/HomeT.dc.html (tablet). The design also names the street; the app has no place names. */
@Composable
private fun HomeMapPreview(point: GeoPoint?, place: String?) {
    if (point == null) return
    Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp).height(200.dp).clip(RoundedCornerShape(24.dp))) {
        val at = LatLng(point.lat, point.lon)
        RouteMap(
            lines = emptyList(), dots = listOf(MapDot(at, Ice, 9f, Bg)), follow = true,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(14.dp).background(Surface1, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.current_location), style = body(12))
            Text(place ?: point.toCoordinateText(), style = body(15, FontWeight.Bold, TextPrimary))
        }
    }
}

/** In the fixed Home layout a meter is scaled to the room between the tabs and the cards; in the scrolling one it is not. */
@Composable
private fun FitOrNot(fit: Boolean, content: @Composable () -> Unit) {
    if (fit) com.speedometer.app.core.designsystem.FitInside(Modifier.fillMaxSize(), content) else content()
}
