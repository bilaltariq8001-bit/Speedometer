package com.speedometer.app.core.ads

import android.os.SystemClock
import com.speedometer.app.core.navigation.Routes

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

    /**
     * Call right before the app hands the screen to the system (permission dialog, settings, share sheet). Coming back
     * from there is not "returning to the app", so the next app-open ad is skipped.
     */
    fun suppressNextAppOpen() { suppressAppOpen = true }

    fun consumeAppOpenSuppression(): Boolean = suppressAppOpen.also { suppressAppOpen = false }

    fun markFullScreenAdClosed() { lastFullScreenAdMs = SystemClock.elapsedRealtime() }

    fun sinceLastFullScreenAdMs(): Long =
        if (lastFullScreenAdMs == 0L) Long.MAX_VALUE else SystemClock.elapsedRealtime() - lastFullScreenAdMs

    /** Screens with no app-open ad: setup screens and everything the driver may be looking at during a trip. */
    private val noAppOpenRoutes = setOf(
        Routes.Splash, Routes.Language, Routes.LanguageChange, Routes.Onboarding, Routes.Vehicle,
        Routes.Live, Routes.LiveMap, Routes.Hud,
    )

    fun isRestrictedRoute(): Boolean = currentRoute == null || currentRoute in noAppOpenRoutes
}

/** Opens a system screen (settings, store). Coming back from it is not "returning to the app", so no app-open ad follows. */
fun android.content.Context.startActivityAwayFromApp(intent: android.content.Intent) {
    AdGate.suppressNextAppOpen()
    startActivity(intent)
}
