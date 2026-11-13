package com.speedometer.app.core.ads

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Entry point to the ads of the app: startup, consent, and the two ways a screen asks for an interstitial. */
@Singleton
class AdsController @Inject constructor(
    val config: AdConfig,
    val interstitial: InterstitialManager,
    private val appOpen: AppOpenManager,
    val analytics: com.speedometer.app.core.analytics.AdAnalytics,
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var inFlight = false

    /** Called once from the Application class: starts watching for the app returning to the front, and fetches Remote Config. */
    fun init(app: Application) {
        appOpen.start(app)
        config.fetch {
            preloadIfAllowed(app)
            appOpen.onAdConfigurationReady()
        }
    }

    /** Called when UMP has answered (from MainActivity). Ads may load from here on, if consent allows. */
    fun onConsentResolved(app: Application, canRequestAds: Boolean) {
        config.setCanRequestAds(canRequestAds)
        preloadIfAllowed(app)
        appOpen.onAdConfigurationReady()
    }

    /** Shows the startup app-open ad once the splash/setup flow reaches Home. */
    fun onHomeVisible(eligible: Boolean = true) = appOpen.onHomeVisible(eligible)

    private fun preloadIfAllowed(app: Application) {
        if (!config.canRequestAds()) return
        interstitial.preload(app, PRELOAD_PLACEMENT)
        appOpen.fetchAd()
    }

    /**
     * Click (forward) interstitial: shows the ad for [placement] if allowed, then runs [onDismiss] exactly once. The user
     * always goes on, whatever stops the ad.
     */
    fun showClickAd(activity: Activity, placement: String, onDismiss: () -> Unit) {
        interstitial.show(activity, placement, onDismiss)
    }

    /**
     * Back-press interstitial for [placement] (a Remote Config prefix such as `gauge_style_back`). Returns false when the press
     * was consumed because another back ad is still on its way, so a double back shows one ad and leaves once.
     */
    fun showBackAd(activity: Activity, placement: String, onDismiss: () -> Unit): Boolean {
        if (inFlight) return false
        inFlight = true
        val once = AtomicBoolean(false)
        val finish = {
            if (once.compareAndSet(false, true)) {
                inFlight = false
                main.post { onDismiss() }
            }
        }
        // Safety for the wait before the ad is on screen only: a path that never answers must not freeze Back. It is cancelled
        // once the ad shows, because a user may watch an ad for longer than any timeout.
        val timeout = Runnable { if (!once.get()) finish() }
        main.postDelayed(timeout, IN_FLIGHT_TIMEOUT_MS)
        interstitial.show(
            activity, placement,
            onDismiss = { main.removeCallbacks(timeout); finish() },
            onShowAd = { main.removeCallbacks(timeout) },
        )
        return true
    }

    private companion object {
        const val PRELOAD_PLACEMENT = "trip_saved_click"
        const val IN_FLIGHT_TIMEOUT_MS = 8_000L
    }
}
