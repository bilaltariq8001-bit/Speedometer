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
    /**
     * True while an interstitial covers a screen change made behind it: screen transitions are skipped, so when the ad
     * closes the new screen is already there, with no glimpse of the old one.
     */
    @Volatile var navigateInstantly: Boolean = false

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

    /**
     * True once onboarding is finished. Full-screen ads and the banner wait for it. Native ads on the setup screens and
     * the app-open preload do not, so the first app-open ad is ready when a new user reaches Home.
     */
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
    fun suppressNextAppOpen() { suppressAppOpen = true; suppressedAppLeft = false }

    /** Whether the app went to the background after [suppressNextAppOpen]. */
    @Volatile private var suppressedAppLeft = false

    /** The app went to the background: a pending suppression now covers the coming return. */
    fun onAppBackgrounded() { if (suppressAppOpen) suppressedAppLeft = true }

    /**
     * The app's screen is back without the app having gone to the background (an in-app dialog such as a permission
     * prompt): the suppression was not used, so drop it. Otherwise it would wrongly swallow the next real return.
     */
    fun onAppScreenResumed() { if (suppressAppOpen && !suppressedAppLeft) suppressAppOpen = false }

    /** True only for a return from a system screen the app itself opened. Clears the flag either way. */
    fun consumeAppOpenSuppression(): Boolean = (suppressAppOpen && suppressedAppLeft).also { suppressAppOpen = false; suppressedAppLeft = false }

    fun markFullScreenAdClosed() { lastFullScreenAdMs = SystemClock.elapsedRealtime() }

    fun sinceLastFullScreenAdMs(): Long =
        if (lastFullScreenAdMs == 0L) Long.MAX_VALUE else SystemClock.elapsedRealtime() - lastFullScreenAdMs

    /** Setup screens: no full-screen ad and no banner; only the slim native card drawn by the screens themselves. */
    val onboardingRoutes = setOf(Routes.Splash, Routes.Language, Routes.Onboarding, Routes.Vehicle)

    /** Screens with no app-open ad: setup screens and everything the driver may be looking at during a trip. */
    private val noAppOpenRoutes = onboardingRoutes + setOf(Routes.LanguageChange, Routes.Live, Routes.LiveMap, Routes.Hud)

    /**
     * Screens without the banner: setup, Settings (as before), and every screen shown while driving (live speedometer,
     * live map, HUD): an ad there distracts the driver and invites accidental taps on a mounted phone.
     */
    private val noBannerRoutes = onboardingRoutes + setOf(Routes.Settings, Routes.Hud, Routes.Live, Routes.LiveMap)

    fun isRestrictedRoute(): Boolean = currentRoute == null || currentRoute in noAppOpenRoutes

    fun bannerAllowedOn(route: String?): Boolean = route != null && route !in noBannerRoutes
}

/** Opens a system screen (settings, store). Coming back from it is not "returning to the app", so no app-open ad follows. */
fun android.content.Context.startActivityAwayFromApp(intent: android.content.Intent) {
    AdGate.suppressNextAppOpen()
    startActivity(intent)
}
