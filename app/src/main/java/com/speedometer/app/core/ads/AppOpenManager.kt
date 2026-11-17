package com.speedometer.app.core.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdActivity
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.speedometer.app.MainActivity
import com.speedometer.app.core.analytics.AdAnalytics
import com.speedometer.app.core.common.isNetworkConnected
import com.speedometer.app.core.trip.TripPhase
import com.speedometer.app.core.trip.TripRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-open ad, shown on every launch and every return from the background.
 *  - launch: once Home appears after a fresh MainActivity (cold start, or reopening after backing out); resume: every
 *    time the app comes back to the foreground;
 *  - one ad is kept loaded ahead of time. If it is still loading at that moment, the opportunity stays open for up to
 *    [WAIT_FOR_AD_MS] and the ad shows the moment it loads;
 *  - never during onboarding/setup screens, never until consent allows ads;
 *  - never while a trip is recording, paused, or being saved, nor on live trip screens ([AdGate]);
 *  - never when returning from the system (permission dialog, settings, share): [AdGate.suppressNextAppOpen];
 *  - never on top of another full-screen ad; only on [MainActivity], only while the app is in the foreground;
 *  - an ad older than 4 hours is dropped and replaced; failed loads retry with backoff; the next ad loads after a dismissal.
 * Everything runs on the main thread. It can be switched off remotely with `app_open_enabled`.
 */
@Singleton
class AppOpenManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: AdConfig,
    private val trips: TripRepository,
    private val analytics: AdAnalytics,
) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private val main = Handler(Looper.getMainLooper())

    private var state = AdState.NotInitialized
    private var appOpenAd: AppOpenAd? = null
    private var loadTimeMs = 0L
    private var requestStartedMs = 0L
    private var failures = 0
    private var currentActivity: Activity? = null
    /** Set when MainActivity is created fresh (process cold start, or reopened after back-exit): show once Home appears. */
    private var launchHomePending = false

    /** An open opportunity waiting for the ad to finish loading. Holds the time it gives up. */
    private var pendingUntilMs = 0L
    private var pendingSource = ""

    fun start(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    // ---------------------------------------------------------------- opportunities

    override fun onStart(owner: LifecycleOwner) {
        // A fresh launch is handled when Home appears (splash/setup stay ad-free).
        if (launchHomePending) return
        // Back from the background.
        main.post { opportunity("resume") }
    }

    override fun onStop(owner: LifecycleOwner) {
        cancelPending()
        // Keep an ad ready for the next return.
        main.post { fetchAd() }
    }

    /** Call when Home becomes visible; shows the launch ad once per fresh launch. */
    fun onHomeVisible(eligible: Boolean = true): Unit = runOnMain {
        if (!launchHomePending) return@runOnMain
        launchHomePending = false
        if (!eligible) return@runOnMain skip("launch: trip starting or active")
        opportunity("launch")
    }

    /** Called after Remote Config or UMP resolves: load if we can. */
    fun onAdConfigurationReady(): Unit = runOnMain { fetchAd() }

    /**
     * Shows the ad now if it is ready. If it is still loading (or ads are still being unlocked/consented a few ms after
     * launch), keeps the opportunity open for up to [WAIT_FOR_AD_MS] and shows the moment it is ready. Gives up if the
     * app goes to the background, a trip starts, or the user moves to a screen where app-open ads never show.
     */
    private fun opportunity(source: String) {
        if (AdGate.consumeAppOpenSuppression()) return skip("$source: $SUPPRESSED")
        pendingSource = source
        pendingUntilMs = AdLog.now() + WAIT_FOR_AD_MS
        main.removeCallbacks(checkPending)
        checkPending.run()
    }

    private val checkPending: Runnable = object : Runnable {
        override fun run() {
            if (pendingUntilMs == 0L) return
            val source = pendingSource
            hardBlockReason()?.let { cancelPending(); return skip("$source: $it") }
            expireIfStale()
            val ad = appOpenAd
            val activity = currentActivity as? MainActivity
            if (softBlockReason() == null && state == AdState.Ready && ad != null && activity != null) {
                cancelPending()
                return showAd(activity, ad, source)
            }
            if (AdLog.now() >= pendingUntilMs) {
                cancelPending()
                return skip("$source: not ready in ${WAIT_FOR_AD_MS}ms (state=$state, ${softBlockReason() ?: "ok"})")
            }
            if (state != AdState.Loading) fetchAd()
            main.postDelayed(this, POLL_MS)
        }
    }

    private fun cancelPending() {
        pendingUntilMs = 0L
        main.removeCallbacks(checkPending)
    }

    /** Never show in these cases; the opportunity is dropped. */
    private fun hardBlockReason(): String? {
        val model = config.appOpen()
        return when {
            !model.isAdEnabled || model.adAppOpenId.isEmpty() -> "disabled by Remote Config"
            !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> "app in background"
            tripActive() || AdGate.tripSaving -> "trip active or saving"
            AdGate.currentRoute != null && AdGate.isRestrictedRoute() -> "restricted screen ${AdGate.currentRoute}"
            else -> null
        }
    }

    /** Not yet: these clear on their own within moments (settings read, consent, another ad closing). */
    private fun softBlockReason(): String? = when {
        !AdGate.adsUnlocked.value -> "onboarding state not read yet"
        !config.canRequestAds() -> "no consent yet"
        AdGate.currentRoute == null -> "no screen yet"
        AdGate.fullScreenAdVisible || state == AdState.Showing -> "another full-screen ad is showing"
        else -> null
    }

    private fun tripActive() = trips.state.value.phase.let { it == TripPhase.Recording || it == TripPhase.Paused }

    private fun skip(reason: String) = AdLog.skipped("app_open", reason)

    // ---------------------------------------------------------------- loading

    /** Loads an ad ahead of time if none is ready or loading. Safe to call any time; duplicates are dropped. */
    fun fetchAd(): Unit = runOnMain {
        expireIfStale()
        val model = config.appOpen()
        if (!AdGate.adsUnlocked.value || !config.canRequestAds() || !model.isAdEnabled || model.adAppOpenId.isEmpty()) return@runOnMain
        if (state == AdState.Ready || state == AdState.Showing) return@runOnMain
        if (state == AdState.Loading) return@runOnMain AdLog.d("app_open", "duplicate load prevented")
        if (!context.isNetworkConnected()) return@runOnMain AdLog.d("app_open", "load deferred: offline")
        main.removeCallbacks(retry)
        state = AdState.Loading
        requestStartedMs = AdLog.requestStarted("app_open", "app_open")
        AdMobInitializer.initializeAdmob(context) {
            try {
                AppOpenAd.load(
                    AdRequest.Builder(model.adAppOpenId).build(),
                    object : AdLoadCallback<AppOpenAd> {
                        override fun onAdLoaded(ad: AppOpenAd) = runOnMain { onLoaded(ad) }
                        override fun onAdFailedToLoad(adError: LoadAdError) = runOnMain { onFailed(adError.code.toString(), adError.message) }
                    },
                )
            } catch (e: Exception) {
                runOnMain { onFailed("exception", e.message) }
            }
        }
    }

    private fun onLoaded(ad: AppOpenAd) {
        appOpenAd = ad
        loadTimeMs = System.currentTimeMillis()
        state = AdState.Ready
        failures = 0
        AdLog.loaded("app_open", "app_open", requestStartedMs)
        analytics.adLoaded("app_open", "app_open")
        // An opportunity is waiting for this ad: show it now.
        if (pendingUntilMs != 0L) { main.removeCallbacks(checkPending); checkPending.run() }
    }

    private fun onFailed(code: String, message: String?) {
        state = if (AdLog.isNoFill(code)) AdState.NoFill else AdState.Failed
        AdLog.failed("app_open", "app_open", requestStartedMs, code, message)
        analytics.adFailedToLoad("app_open", "app_open", code)
        // Backoff: 30 s, 60 s, 120 s, then wait for the next natural trigger (foreground, network back).
        if (failures < MAX_RETRIES) {
            val delay = RETRY_BASE_MS * (1L shl failures) * (if (state == AdState.NoFill) 2 else 1)
            failures++
            AdLog.d("app_open", "retry #$failures in ${delay}ms")
            main.postDelayed(retry, delay)
        }
    }

    private val retry: Runnable = Runnable { if (state == AdState.Failed || state == AdState.NoFill) { state = AdState.NotInitialized; fetchAd() } }

    /** Network back: try again, with a fresh retry budget. */
    fun onNetworkAvailable(): Unit = runOnMain {
        if (state == AdState.Failed || state == AdState.NoFill) { failures = 0; state = AdState.NotInitialized }
        fetchAd()
    }

    private fun expireIfStale() {
        if (state == AdState.Ready && System.currentTimeMillis() - loadTimeMs >= MAX_AGE_MS) {
            AdLog.d("app_open", "cached ad expired (older than 4 h)")
            appOpenAd = null
            state = AdState.Expired
        }
    }

    // ---------------------------------------------------------------- showing

    private fun showAd(activity: Activity, ad: AppOpenAd, source: String) {
        if (activity.isFinishing || activity.isDestroyed) return skip("$source: activity finishing")
        state = AdState.Showing
        appOpenAd = null
        AdGate.fullScreenAdVisible = true
        ad.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdShowedFullScreenContent() = runOnMain {
                AdLog.d("app_open", "shown ($source)")
                analytics.adShown("app_open", "app_open")
            }

            override fun onAdDismissedFullScreenContent() = runOnMain {
                AdLog.d("app_open", "dismissed")
                state = AdState.Dismissed
                AdGate.fullScreenAdVisible = false
                AdGate.markFullScreenAdClosed()
                fetchAd()
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) = runOnMain {
                AdLog.w("app_open", "failed to show: $fullScreenContentError")
                state = AdState.Failed
                AdGate.fullScreenAdVisible = false
                fetchAd()
            }

            override fun onAdClicked() { analytics.adClicked("app_open", "app_open") }
        }
        try {
            ad.show(activity)
        } catch (e: Exception) {
            AdLog.w("app_open", "show threw: ${e.message}")
            state = AdState.Failed
            AdGate.fullScreenAdVisible = false
            fetchAd()
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    // ---------------------------------------------------------------- activity tracking

    override fun onActivityStarted(activity: Activity) { if (activity !is AdActivity) currentActivity = activity }
    override fun onActivityResumed(activity: Activity) { if (activity !is AdActivity) currentActivity = activity }
    override fun onActivityDestroyed(activity: Activity) { if (currentActivity === activity) currentActivity = null }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is AdActivity) return
        currentActivity = activity
        // Fresh MainActivity (cold start, or reopened after the user backed out): it starts on the splash, so wait for Home.
        if (activity is MainActivity && savedInstanceState == null) {
            launchHomePending = true
            AdGate.currentRoute = null
        }
    }
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private companion object {
        const val MAX_AGE_MS = 4 * 60 * 60 * 1000L
        const val WAIT_FOR_AD_MS = 6_000L
        const val POLL_MS = 200L
        const val MAX_RETRIES = 3
        const val RETRY_BASE_MS = 10_000L
        const val SUPPRESSED = "returning from a system screen"
    }
}
