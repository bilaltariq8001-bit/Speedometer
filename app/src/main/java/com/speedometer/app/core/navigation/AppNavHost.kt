package com.speedometer.app.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.speedometer.app.core.ads.AdGate
import com.speedometer.app.core.ads.AdsViewModel
import com.speedometer.app.core.ads.AppBanner
import com.speedometer.app.core.ads.bannerOnTop
import com.speedometer.app.core.ads.rememberBackPressAd
import com.speedometer.app.core.analytics.AnalyticsViewModel
import com.speedometer.app.core.autodetect.StartRequest
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.speedometer.app.core.designsystem.rememberLayout
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import com.speedometer.app.R
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.AppMessages
import com.speedometer.app.core.designsystem.MessageKind
import com.speedometer.app.core.designsystem.TextMuted
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.speedometer.app.feature.drive.ui.LiveDriveScreen
import com.speedometer.app.feature.drive.ui.HudScreen
import com.speedometer.app.feature.drive.ui.LiveMapScreen
import com.speedometer.app.feature.gauges.ui.GaugeStyleScreen
import com.speedometer.app.feature.gauges.ui.MeterPreviewScreen
import com.speedometer.app.core.data.MeterStyle
import com.speedometer.app.feature.gauges.ui.VehicleMetersScreen
import com.speedometer.app.feature.gps.ui.GpsStatusScreen
import com.speedometer.app.feature.share.ui.LiveShareScreen
import com.speedometer.app.feature.history.ui.HistoryScreen
import com.speedometer.app.feature.replay.ui.ReplayScreen
import com.speedometer.app.feature.tripmap.ui.TripMapScreen
import com.speedometer.app.feature.zones.ui.ZonesScreen
import com.speedometer.app.feature.insights.ui.InsightsScreen
import com.speedometer.app.feature.results.ui.ResultsScreen
import com.speedometer.app.feature.settings.ui.SettingsScreen
import com.speedometer.app.service.TripService
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.speedometer.app.core.common.findActivity
import com.speedometer.app.core.data.AppLanguage
import com.speedometer.app.core.data.Settings
import com.speedometer.app.feature.home.ui.HomeScreen
import com.speedometer.app.feature.onboarding.ui.LanguageScreen
import com.speedometer.app.feature.onboarding.ui.OnboardingScreen
import com.speedometer.app.feature.onboarding.ui.OnboardingViewModel
import com.speedometer.app.feature.onboarding.ui.SplashScreen
import com.speedometer.app.feature.onboarding.ui.VehicleScreen
import com.speedometer.app.feature.permissions.ui.rememberAutoDetectSwitch

private val RAIL_PHONE = 72.dp
private val RAIL_TABLET = 96.dp

/** Screens that do not show the "Turn on location" prompt: Home (own dialog) and the first-run setup. */
/** Screens that draw under the status bar themselves (maps, HUD, splash) and pad their own overlays. */
private val fullBleedRoutes = setOf(
    Routes.Splash, Routes.Live, Routes.LiveMap, Routes.TripMap, Routes.Hud,
)

private val noLocationPromptRoutes = setOf(Routes.Splash, Routes.Language, Routes.LanguageChange, Routes.Onboarding, Routes.Vehicle, Routes.Home)

private const val ALERT_RETURN_MS = 10_000L

/**
 * Each screen's own frame: the status bar and gesture bar insets (for screens that do not draw under them) and the back strip
 * of Trips, Insights and Settings. It belongs to the destination, so the screen that is leaving keeps exactly the frame it had
 * and the one that is arriving already has its own: nothing resizes during a transition.
 */
@Composable
private fun RouteFrame(route: String, content: @Composable () -> Unit) {
    val fullBleed = route in fullBleedRoutes
    val secondary = route == Routes.History || route == Routes.Insights || route == Routes.Settings
    Box(
        Modifier.fillMaxSize()
            .then(
                if (fullBleed) Modifier
                else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom)),
            )
            .then(if (secondary) Modifier.padding(top = BACK_STRIP_HEIGHT) else Modifier),
    ) { content() }
}

/**
 * A destination in its frame. The banner is no longer inside each screen: one [AppBanner] below (or above) the NavHost
 * serves every screen, so navigating keeps the loaded ad instead of creating a new AdView and request per screen.
 */
private fun androidx.navigation.NavGraphBuilder.framed(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit,
) = composable(route, arguments = arguments) { entry ->
    RouteFrame(route) { content(entry) }
}

private const val BANNER_SLOT = "bottom_banner"
private val BACK_STRIP_HEIGHT = 52.dp

private fun NavHostController.goClearing(route: String, from: String) =
    navigate(route) { popUpTo(from) { inclusive = true } }

@Composable
fun AppNavHost() {
    val startContext = LocalContext.current
    val nav = rememberNavController()
    var splashCompleted by rememberSaveable { mutableStateOf(false) }
    val tripPhase: TripPhaseViewModel = hiltViewModel()
    val adsVm: AdsViewModel = hiltViewModel()
    val analytics = hiltViewModel<AnalyticsViewModel>().analytics
    val activity = startContext.findActivity()
    val tripNavigationBlockedMessage = stringResource(R.string.trip_navigation_blocked)
    val select: (String) -> Unit = { target ->
        if (tripPhase.tripActive() && target in setOf(Routes.History, Routes.Insights, Routes.Settings)) {
            AppMessages.show(tripNavigationBlockedMessage, MessageKind.Info)
        } else {
            nav.navigate(target) {
                popUpTo(Routes.Home) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    val vm: OnboardingViewModel = hiltViewModel()
    val settings by vm.state.collectAsStateWithLifecycle()
    val s = settings ?: Settings()

    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    // The ad managers and the analytics both need to know the screen. Route arguments are cut off, so tripmap?id=3 is tripmap.
    LaunchedEffect(route) {
        AdGate.currentRoute = route
        if (route != null) analytics.screenEnter(route.substringBefore('?').substringBefore('/'))
        if (route == Routes.Home) adsVm.ads.onHomeVisible(!StartRequest.pending && !tripPhase.tripActive())
    }
    // First launch with analytics: someone who already finished onboarding is an existing user and starts as returning.
    LaunchedEffect(settings != null) { if (settings != null) analytics.seed(s.onboardingDone) }
    // Start trip on the auto-detect notice: begin recording once the app is past the splash screen.
    LaunchedEffect(StartRequest.pending, settings, route) {
        if (StartRequest.pending && settings != null && route != null && route != Routes.Splash) {
            StartRequest.consume()
            val precise = ContextCompat.checkSelfPermission(startContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (s.onboardingDone && precise) {
                if (!tripPhase.tripActive()) TripService.start(startContext)
                nav.navigate(Routes.live()) {
                    popUpTo(Routes.Home) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    // A trip ended from the notification is saved by the service; the app, opened later, must not sit on a dead trip screen.
    var sawActiveTrip by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        var previous = com.speedometer.app.core.trip.TripPhase.Idle
        tripPhase.phase.collect { phase ->
            when {
                phase == com.speedometer.app.core.trip.TripPhase.Recording && previous == com.speedometer.app.core.trip.TripPhase.Idle ->
                    analytics.event("trip_started", "vehicle" to s.vehicle.name)
                phase == com.speedometer.app.core.trip.TripPhase.Paused && previous == com.speedometer.app.core.trip.TripPhase.Recording ->
                    analytics.event("trip_paused")
                phase == com.speedometer.app.core.trip.TripPhase.Recording && previous == com.speedometer.app.core.trip.TripPhase.Paused ->
                    analytics.event("trip_resumed")
            }
            previous = phase
            val onTripScreen = nav.currentBackStackEntry?.destination?.route.let {
                it == Routes.Home || it == Routes.Live || it == Routes.LiveMap || it == Routes.Hud
            }
            when (phase) {
                com.speedometer.app.core.trip.TripPhase.Recording, com.speedometer.app.core.trip.TripPhase.Paused -> sawActiveTrip = true
                com.speedometer.app.core.trip.TripPhase.Idle -> {
                    if (sawActiveTrip && onTripScreen) nav.navigate(Routes.Home) { popUpTo(Routes.Home) { inclusive = false }; launchSingleTop = true }
                    sawActiveTrip = false
                }
                else -> Unit
            }
        }
    }

    val layout = rememberLayout()
    val density = LocalDensity.current
    // No bottom bar or side rail any more: Home has Trips, Insights and Settings as icons at the top, and each of those screens
    // has a back button in a strip of its own.
    val showNav = false
    val secondaryScreen = route == Routes.History || route == Routes.Insights || route == Routes.Settings
    val railWidth = if (showNav && layout.isLandscape) (if (layout.isTablet) RAIL_TABLET else RAIL_PHONE) else 0.dp
    val showBottom = showNav && !layout.isLandscape
    var barHeight by remember { mutableStateOf(0.dp) }
    val fullBleed = route in fullBleedRoutes
    val startDest = remember { Routes.Splash }
    val transitions = remember { NavTransitions(NavTransitions.reduceMotion(startContext)) }
    LaunchedEffect(splashCompleted, settings, route) {
        if (splashCompleted && settings != null && route == Routes.Splash) {
            nav.goClearing(if (s.onboardingDone) Routes.Home else Routes.Language, Routes.Splash)
        }
    }
    // The nav host fills the window; the bar or rail is drawn over it and the host is padded to clear it.
    // One banner for the whole app (see AppBanner). While it takes space it owns the gesture-bar (or status-bar) inset, so
    // the screens above it do not pad for it a second time.
    val bannerConfigVersion by adsVm.ads.config.version.collectAsStateWithLifecycle()
    val bannerTop = remember(bannerConfigVersion) { adsVm.ads.bannerOnTop(BANNER_SLOT) }
    var bannerShown by remember { mutableStateOf(false) }
    val bannerVisible = AdGate.bannerAllowedOn(route) && s.onboardingDone
    val banner: @Composable () -> Unit = {
        AppBanner(adsVm.ads, BANNER_SLOT, bannerVisible, bannerTop) { bannerShown = it }
    }
    Box(Modifier.fillMaxSize().background(Bg)) {
    Column(Modifier.fillMaxSize()) {
    if (bannerTop) banner()
    Box(
        Modifier.weight(1f).then(
            when {
                !bannerShown -> Modifier
                bannerTop -> Modifier.consumeWindowInsets(WindowInsets.statusBars)
                else -> Modifier.consumeWindowInsets(WindowInsets.navigationBars)
            },
        ),
    ) {
    androidx.compose.runtime.CompositionLocalProvider(com.speedometer.app.core.ads.LocalBannerBelow provides (bannerShown && !bannerTop)) {
    NavHost(
        nav, startDestination = startDest,
        enterTransition = { transitions.enter(this) }, exitTransition = { transitions.exit(this) },
        popEnterTransition = { transitions.popEnter(this) }, popExitTransition = { transitions.popExit(this) },
        sizeTransform = { null },
        modifier = Modifier.fillMaxSize()
            .padding(start = railWidth, bottom = if (showBottom) barHeight else 0.dp)
            // The bar already sits above the gesture area, so the bottom inset is spent: nothing inside pads for it twice.
            .then(if (showBottom) Modifier.consumeWindowInsets(PaddingValues(bottom = barHeight)) else Modifier)
            // Keep content out from under the status bar and the camera cutout or side bars, and above the keyboard. Insets are
            // consumed as they are applied, so screens that also pad for them (the map screens) are not padded twice.
            // This container is the same for every screen, so it never changes size while one screen replaces another. What
            // differs per screen (status bar, gesture bar, back strip) is applied inside each screen by RouteFrame; applying it
            // here made the outgoing screen resize at the moment of navigation, which looked like a zoom.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .imePadding(),
    ) {
        framed(Routes.Splash) {
            SplashScreen(onDone = { splashCompleted = true })
        }
        framed(Routes.Language) {
            val context = LocalContext.current
            androidx.activity.compose.BackHandler {
                nav.goClearing(Routes.Onboarding, Routes.Language)
            }
            LanguageScreen(s.language) { name ->
                changeLanguage(context, vm, name)
                nav.goClearing(Routes.Onboarding, Routes.Language)
            }
        }
        framed(Routes.LanguageChange) {
            val context = LocalContext.current
            LanguageScreen(s.language) { name ->
                changeLanguage(context, vm, name)
                nav.popBackStack()
            }
        }
        framed(Routes.Onboarding) {
            var page by rememberSaveable { mutableIntStateOf(0) }
            LaunchedEffect(page) { analytics.onboardingStep(page + 1) }
            OnboardingScreen(
                page,
                onPage = { page = it },
                onBackHome = { nav.goClearing(Routes.Home, Routes.Onboarding) },
                onDone = {
                    // Skip and Get started both end here; Get started is the last (fifth) page.
                    if (page < 4) analytics.onboardingSkipped(page + 1)
                    nav.navigate(Routes.Vehicle) { launchSingleTop = true }
                },
            )
        }
        framed(Routes.Vehicle) {
            val setAutoDetect = rememberAutoDetectSwitch(vm::hasActivityPermission) { vm.setAutoDetect(it) }
            VehicleScreen(
                vehicle = s.vehicle, mph = s.mph, autoDetect = s.autoDetect,
                onVehicle = { vm.setVehicle(it) }, onMph = { vm.setMph(it) }, onAutoDetect = setAutoDetect,
                onFinish = {
                    analytics.onboardingCompleted()
                    vm.finish()
                    nav.goClearing(Routes.Home, Routes.Onboarding)
                },
            )
        }
        framed(Routes.Home) {
            // Have the Trips click ad loading while Home is open, so opening Trips never waits on it.
            androidx.compose.runtime.LaunchedEffect(Unit) { activity?.let { adsVm.ads.warmUp(it, "history_open_click") } }
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    HomeScreen(
                        onEndTrip = { nav.navigate(Routes.Results) { popUpTo(Routes.Home) } },
                        onHistory = {
                            // Click interstitial when opening Trips from Home. Never during a trip (that opens nothing anyway).
                            val open = { select(Routes.History) }
                            if (activity != null && !tripPhase.tripActive()) adsVm.ads.showClickAd(activity, "history_open_click", open) else open()
                        },
                        onInsights = { select(Routes.Insights) },
                        onSettings = { select(Routes.Settings) },
                        onZones = { nav.navigate(Routes.Zones) },
                        onGpsStatus = { nav.navigate(Routes.GpsStatus) },
                        onLiveShare = { nav.navigate(Routes.LiveShare) },
                        onGaugeStyle = { nav.navigate(Routes.GaugeStyle) },
                        onHud = { nav.navigate(Routes.Hud) },
                    )
                }
            }
        }
        framed(Routes.Live) {
            val context = LocalContext.current
            val initialDigital = nav.previousBackStackEntry?.savedStateHandle?.get<Boolean>("live_initial_digital")
            LiveDriveScreen(
                initialDigital = initialDigital,
                initialSettings = settings,
                onEnd = {
                    TripService.stop(context)
                    nav.navigate(Routes.Results) { popUpTo(Routes.Home) }
                },
                onMap = { nav.navigate(Routes.LiveMap) },
                onHud = { nav.navigate(Routes.Hud) },
                onShare = { nav.navigate(Routes.LiveShare) },
                onGpsDetails = { nav.navigate(Routes.GpsStatus) },
            )
        }
        framed(Routes.Hud) { HudScreen(onExit = { nav.popBackStack() }) }
        framed(Routes.LiveMap) {
            val context = LocalContext.current
            LiveMapScreen(
                onBack = { nav.popBackStack() },
                onEnd = {
                    TripService.stop(context)
                    nav.navigate(Routes.Results) { popUpTo(Routes.Home) }
                },
            )
        }
        framed(Routes.Results) {
            val goHome = { nav.popBackStack(Routes.Home, inclusive = false); Unit }
            val back = rememberBackPressAd("results_back", leave = goHome)
            androidx.compose.runtime.LaunchedEffect(Unit) { activity?.let { adsVm.ads.warmUp(it, "trip_saved_click") } }
            ResultsScreen(
                onClose = back,
                onSaved = {
                    val seconds = tripPhase.trip.value.durationSec
                    analytics.tripSaved(if (seconds < 60) "under_1m" else if (seconds < 300) "1_to_5m" else if (seconds < 1800) "5_to_30m" else "over_30m")
                    if (activity != null) adsVm.ads.showClickAd(activity, "trip_saved_click") { }
                },
                onSavedDone = { nav.popBackStack(Routes.Home, inclusive = false); Unit },
            )
        }
        framed(Routes.History) {
            HistoryScreen(
                onStartTrip = { nav.navigate(Routes.Home) { launchSingleTop = true } },
                onViewMap = { range -> nav.navigate(Routes.tripMap(-1L, range.name.lowercase())) },
                onViewTripMap = { id -> nav.navigate(Routes.tripMap(id)) },
                onReplay = { id -> nav.navigate(Routes.replay(id)) },
            )
        }
        framed(Routes.Insights) { InsightsScreen() }
        framed(Routes.Settings) {
            SettingsScreen(
                onZones = { nav.navigate(Routes.Zones) },
                onGaugeStyle = { nav.navigate(Routes.GaugeStyle) },
                onLanguage = { nav.navigate(Routes.LanguageChange) },
            )
        }
        framed(Routes.GaugeStyle) {
            val back = rememberBackPressAd("gauge_style_back") { nav.popBackStack() }
            GaugeStyleScreen(
                onBack = back,
                onVehicleMeters = { nav.navigate(Routes.VehicleMeters) },
                onPreview = { nav.navigate(Routes.meterPreview(it.name)) },
            )
        }
        framed(
            Routes.MeterPreview,
            arguments = listOf(navArgument("style") { type = NavType.StringType }),
        ) { entry ->
            val style = MeterStyle.entries.firstOrNull { it.name == entry.arguments?.getString("style") } ?: MeterStyle.Dial
            val back = rememberBackPressAd("meter_preview_back") { nav.popBackStack() }
            MeterPreviewScreen(style, onBack = back)
        }
        framed(Routes.VehicleMeters) {
            val back = rememberBackPressAd("vehicle_meters_back") { nav.popBackStack() }
            VehicleMetersScreen(onBack = back, onOpenCluster = { nav.navigate(Routes.meterPreview(it.name)) })
        }
        framed(Routes.LiveShare) { LiveShareScreen(onBack = { nav.popBackStack() }) }
        framed(Routes.Zones) {
            val back = rememberBackPressAd("zones_back") { nav.popBackStack() }
            ZonesScreen(onBack = back)
        }
        framed(Routes.GpsStatus) {
            val back = rememberBackPressAd("gps_status_back") { nav.popBackStack() }
            GpsStatusScreen(onBack = back)
        }
        framed(
            Routes.TripMap,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("range") { type = NavType.StringType; defaultValue = "week" },
            ),
        ) {
            val back = rememberBackPressAd("trip_map_back") { nav.popBackStack() }
            TripMapScreen(onBack = back, onOpenTrip = { id -> nav.navigate(Routes.replay(id)) })
        }
        framed(
            Routes.Replay,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) {
            val back = rememberBackPressAd("replay_back") { nav.popBackStack() }
            ReplayScreen(onBack = back)
        }
    }
    }

    if (showBottom) {
        BottomBar(
            route, select,
            Modifier.align(Alignment.BottomCenter).onSizeChanged { barHeight = with(density) { it.height.toDp() } },
        )
    }
    if (railWidth > 0.dp) NavRail(route, select, layout.isTablet, Modifier.align(Alignment.CenterStart))
    androidx.compose.animation.AnimatedVisibility(
        secondaryScreen, Modifier.align(Alignment.TopStart),
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)),
    ) {
        Box(
            Modifier.statusBarsPadding().padding(start = 8.dp, top = 6.dp).size(44.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                    if (!nav.popBackStack(Routes.Home, inclusive = false)) nav.navigate(Routes.Home) { launchSingleTop = true }
                },
            contentAlignment = Alignment.Center,
        ) { com.speedometer.app.core.designsystem.StrokeIcon("M15 5l-7 7 7 7", com.speedometer.app.core.designsystem.TextPrimary, size = 24.dp, box = 24f) }
    }
    // Red edge glow while over the limit: one layer over every screen (gauge, digital, map, Live map, HUD) and the bottom bar.
    // It sits above the content and takes no touches, so it never gets in the way.
    com.speedometer.app.core.designsystem.OverLimitGlow(tripPhase.isOverLimit)
    // Over the limit: the warning screen covers whichever screen is open (map, HUD, gauge...). "Got it" hides it for 10 seconds;
    // if the speed is still over the limit it comes back.
    val liveTrip by tripPhase.trip.collectAsStateWithLifecycle()
    val overLimit = liveTrip.phase == com.speedometer.app.core.trip.TripPhase.Recording &&
        liveTrip.guard != com.speedometer.app.core.trip.GuardLevel.Off && liveTrip.limitKmh != null &&
        !tripPhase.isMuted()
    var alertHidden by remember { mutableStateOf(false) }
    LaunchedEffect(alertHidden) {
        if (alertHidden) {
            kotlinx.coroutines.delay(ALERT_RETURN_MS)
            alertHidden = false
        }
    }
    LaunchedEffect(overLimit) { if (!overLimit) alertHidden = false }
    if (overLimit && !alertHidden) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } },
        ) {
            com.speedometer.app.feature.drive.ui.GuardAlertView(
                liveTrip, s.unit, onMute = { tripPhase.mute() }, onDismiss = { alertHidden = true },
            )
        }
    }
    com.speedometer.app.core.designsystem.AppMessageHost()
    // Location switched off while in the app: ask to turn it on. Home has its own, and the setup screens do not need it yet.
    if (route != null && route !in noLocationPromptRoutes) com.speedometer.app.feature.permissions.ui.LocationOffPrompt()
    }
    if (!bannerTop) banner()
    }
    }
}


/** Saves the language, then recreates the activity so every string is read in it. */
private fun changeLanguage(context: android.content.Context, vm: OnboardingViewModel, name: String) {
    if (AppLanguage.saved(context) == name) return
    AppLanguage.save(context, name)
    vm.setLanguage(name)
    context.findActivity()?.recreate()
}
