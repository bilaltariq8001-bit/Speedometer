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

    @Volatile private var app: Application? = null

    /**
     * Called once from the Application class. Ads used to wait for Remote Config (up to 10 s) and for the first ad request
     * before the SDK even started. Now, for a user who already gave consent, the SDK starts and the first ads load right
     * away with the cached/default config; Remote Config refreshes the values in parallel.
     */
    fun init(app: Application) {
        this.app = app
        appOpen.start(app)
        if (config.canRequestAds()) {
            AdMobInitializer.initializeAdmob(app) {}
            preloadIfAllowed(app)
        }
        config.fetch {
            preloadIfAllowed(app)
            appOpen.onAdConfigurationReady()
        }
        watchNetwork(app)
    }

    /** Called when UMP has answered (from MainActivity). Ads may load from here on, if consent allows. */
    fun onConsentResolved(app: Application, canRequestAds: Boolean) {
        config.setCanRequestAds(canRequestAds)
        if (canRequestAds) AdMobInitializer.initializeAdmob(app) {}
        preloadIfAllowed(app)
        appOpen.onAdConfigurationReady()
    }

    /** Shows the startup app-open ad once the splash/setup flow reaches Home. */
    fun onHomeVisible(eligible: Boolean = true) = appOpen.onHomeVisible(eligible)

    /** Starts loading [placement]'s interstitial as soon as its screen opens, so it is ready when the user leaves. */
    fun warmUp(context: android.content.Context, placement: String) {
        if (config.canRequestAds()) interstitial.preload(context.applicationContext, placement)
    }

    private fun preloadIfAllowed(app: Application) {
        if (!config.canRequestAds()) return
        interstitial.preload(app, PRELOAD_PLACEMENT)
        appOpen.fetchAd()
        NativeAdCache.preload(app, config.native(NATIVE_PLACEMENT), analytics)
    }

    /** When the phone gets internet back, fill whatever could not load while it was offline. */
    private fun watchNetwork(app: Application) {
        runCatching {
            val cm = app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
                    if (caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        main.removeCallbacks(reload)
                        main.postDelayed(reload, NETWORK_SETTLE_MS)
                    }
                }
            })
        }
    }

    private val reload = Runnable { app?.let { preloadIfAllowed(it) } }

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
        const val NATIVE_PLACEMENT = "history"
        const val NETWORK_SETTLE_MS = 1_500L
    }
}
