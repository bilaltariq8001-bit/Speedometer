package com.speedometer.app.core.ads

import android.os.SystemClock
import com.speedometer.app.core.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared facts the ad managers check before showing a full-screen ad, so an interstitial and an app-open ad never
 * overlap and none appears where it must not.
 */
object AdGate {
    @Volatile var fullScreenAdVisible = false

    /** Full-screen ads must wait until the saved trip write and its progress state have both completed. */
    @Volatile var tripSaving = false

    /** When the last full-screen ad (interstitial or app-open) closed. Both kinds respect the same cooldown. */
    @Volatile var lastFullScreenAdMs = 0L

    /** The screen the user is on, kept up to date by the navigation host. */
    @Volatile var currentRoute: String? = null

    @Volatile private var suppressAppOpen = false

    // ---------------------------------------------------------------- onboarding

    private val _adsUnlocked = MutableStateFlow(false)

    /** True once onboarding is finished. Nothing loads or shows before that: setup stays completely ad-free. */
    val adsUnlocked: StateFlow<Boolean> = _adsUnlocked

    /** Null until settings are read; then whether onboarding was already done when this process started. */
    @Volatile var onboardingDoneAtLaunch: Boolean? = null
        private set

    /** Fed from the settings store; the first value also records the state at launch. */
    fun onOnboardingState(done: Boolean) {
        if (onboardingDoneAtLaunch == null) onboardingDoneAtLaunch = done
        if (done) _adsUnlocked.value = true
    }

    // ---------------------------------------------------------------- user interaction

    /** Last time the user touched the screen. An app-open ad that is late never jumps in after the user started using the app. */
    @Volatile var lastTouchMs = 0L
        private set

    fun markUserInteraction() { lastTouchMs = SystemClock.elapsedRealtime() }

    // ---------------------------------------------------------------- system screens

    /**
     * Call right before the app hands the screen to the system (permission dialog, settings, share sheet). Coming back
     * from there is not "returning to the app", so the next app-open ad is skipped.
     */
    fun suppressNextAppOpen() { suppressAppOpen = true }

    fun consumeAppOpenSuppression(): Boolean = suppressAppOpen.also { suppressAppOpen = false }

    fun markFullScreenAdClosed() { lastFullScreenAdMs = SystemClock.elapsedRealtime() }

    fun sinceLastFullScreenAdMs(): Long =
        if (lastFullScreenAdMs == 0L) Long.MAX_VALUE else SystemClock.elapsedRealtime() - lastFullScreenAdMs

    /** Setup screens: never any ad, of any kind. */
    val onboardingRoutes = setOf(Routes.Splash, Routes.Language, Routes.Onboarding, Routes.Vehicle)

    /** Screens with no app-open ad: setup screens and everything the driver may be looking at during a trip. */
    private val noAppOpenRoutes = onboardingRoutes + setOf(Routes.LanguageChange, Routes.Live, Routes.LiveMap, Routes.Hud)

    /**
     * Screens without the banner: setup, Settings (as before), and the HUD, which is mirrored onto the windscreen
     * at night, where a bright banner would distract the driver.
     */
    private val noBannerRoutes = onboardingRoutes + setOf(Routes.Settings, Routes.Hud)

    fun isRestrictedRoute(): Boolean = currentRoute == null || currentRoute in noAppOpenRoutes

    fun bannerAllowedOn(route: String?): Boolean = route != null && route !in noBannerRoutes
}

/** Opens a system screen (settings, store). Coming back from it is not "returning to the app", so no app-open ad follows. */
fun android.content.Context.startActivityAwayFromApp(intent: android.content.Intent) {
    AdGate.suppressNextAppOpen()
    startActivity(intent)
}
