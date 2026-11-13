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
 * App-open ad. One ad is kept loaded ahead of time, so it can show the instant an opportunity comes:
 *  - opportunities: the first Home frame of a cold start, and every return from the background;
 *  - if the ad is not ready at that moment, the opportunity is skipped (no waiting, no late ad over a screen the user is
 *    already using) and loading carries on for the next one. Cold start alone allows a short grace, [COLD_START_GRACE_MS],
 *    which any touch or screen change ends;
 *  - never during onboarding, never on the cold start that follows a fresh onboarding, never until consent allows ads;
 *  - never while a trip is recording, paused, or being saved, nor on live trip screens ([AdGate]);
 *  - never when returning from the system (permission dialog, settings, share): [AdGate.suppressNextAppOpen];
 *  - never while another full-screen ad is on screen, nor inside the shared full-screen cooldown;
 *  - only on [MainActivity], only while the app is in the foreground (a background show is an AdMob policy violation);
 *  - an ad older than 4 hours is dropped and replaced; failed loads retry with backoff; the next ad loads after a dismissal.
 * Everything runs on the main thread (SDK callbacks are posted there), so no state is touched from two threads.
 * It can be switched off remotely with `app_open_enabled`.
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
    private var isColdStart = true
    private var coldStartHomePending = false

    /** An opportunity that is still open: cold-start grace only. Holds the time it ends. */
    private var graceUntilMs = 0L
    private var graceTouchMs = 0L
    private var graceRoute: String? = null

    fun start(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    // ---------------------------------------------------------------- opportunities

    override fun onStart(owner: LifecycleOwner) {
        // Wait until the splash/setup flow reaches Home before showing on a cold launch.
        if (isColdStart) {
            isColdStart = false
            coldStartHomePending = true
            return
        }
        // Back from the background. The activity is about to resume; decide there, with the activity in hand.
        main.post { opportunity("foreground", allowGrace = false) }
    }

    override fun onStop(owner: LifecycleOwner) {
        graceUntilMs = 0L
        // Keep an ad ready for the next return.
        main.post { fetchAd() }
    }

    /** Call when Home first becomes visible after startup; this avoids covering splash and onboarding. */
    fun onHomeVisible(eligible: Boolean = true) = runOnMain {
        if (!coldStartHomePending) return@runOnMain
        coldStartHomePending = false
        if (!eligible) return@runOnMain skip("cold start: trip starting or active")
        if (AdGate.onboardingDoneAtLaunch != true) return@runOnMain skip("cold start right after onboarding")
        opportunity("cold start", allowGrace = true)
    }

    /** Called after Remote Config or UMP resolves: load if we can. */
    fun onAdConfigurationReady() = runOnMain { fetchAd() }

    private fun opportunity(source: String, allowGrace: Boolean) {
        val reason = blockReason()
        if (reason != null) {
            if (reason != SUPPRESSED) fetchAd()
            return skip("$source: $reason")
        }
        expireIfStale()
        val activity = currentActivity as? MainActivity ?: return skip("$source: no MainActivity")
        val ad = appOpenAd
        if (state == AdState.Ready && ad != null) {
            showAd(activity, ad, source)
            return
        }
        if (allowGrace && state == AdState.Loading) {
            // Only on cold start, only briefly, and cancelled by any touch or navigation (see [stillInGrace]).
            graceUntilMs = AdLog.now() + COLD_START_GRACE_MS
            graceTouchMs = AdGate.lastTouchMs
            graceRoute = AdGate.currentRoute
            AdLog.d("app_open", "$source: ad still loading, waiting up to ${COLD_START_GRACE_MS}ms")
            main.postDelayed({ if (graceUntilMs != 0L && !stillInGrace()) { graceUntilMs = 0L; skip("$source: not ready in time") } }, COLD_START_GRACE_MS)
            return
        }
        skip("$source: ad not ready (state=$state)")
        fetchAd()
    }

    private fun stillInGrace(): Boolean =
        graceUntilMs != 0L && AdLog.now() <= graceUntilMs && AdGate.lastTouchMs == graceTouchMs && AdGate.currentRoute == graceRoute

    /** Why an app-open ad may not show right now, or null when it may. */
    private fun blockReason(): String? {
        if (AdGate.consumeAppOpenSuppression()) return SUPPRESSED
        val model = config.appOpen()
        return when {
            !AdGate.adsUnlocked.value -> "onboarding not finished"
            !config.canRequestAds() -> "no consent yet"
            !model.isAdEnabled || model.adAppOpenId.isEmpty() -> "disabled by Remote Config"
            !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> "app in background"
            tripActive() || AdGate.tripSaving -> "trip active or saving"
            AdGate.isRestrictedRoute() -> "restricted screen ${AdGate.currentRoute}"
            AdGate.fullScreenAdVisible || state == AdState.Showing -> "another full-screen ad is showing"
            AdGate.sinceLastFullScreenAdMs() < config.interstitialCooldownSec * 1000L -> "inside full-screen cooldown"
            else -> null
        }
    }

    private fun tripActive() = trips.state.value.phase.let { it == TripPhase.Recording || it == TripPhase.Paused }

    private fun skip(reason: String) = AdLog.skipped("app_open", reason)

    // ---------------------------------------------------------------- loading

    /** Loads an ad ahead of time if none is ready or loading. Safe to call any time; duplicates are dropped. */
    fun fetchAd() = runOnMain {
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
        // Cold-start grace still open and the user has not touched anything: show now. Otherwise keep it for next time.
        if (stillInGrace()) {
            graceUntilMs = 0L
            val activity = currentActivity as? MainActivity
            if (activity != null && blockReason() == null) showAd(activity, ad, "cold start (within grace)")
        }
    }

    private fun onFailed(code: String, message: String?) {
        state = if (AdLog.isNoFill(code)) AdState.NoFill else AdState.Failed
        AdLog.failed("app_open", "app_open", requestStartedMs, code, message)
        analytics.adFailedToLoad("app_open", "app_open", code)
        graceUntilMs = 0L
        // Backoff: 30 s, 60 s, 120 s, then wait for the next natural trigger (foreground, network back).
        if (failures < MAX_RETRIES) {
            val delay = RETRY_BASE_MS * (1L shl failures) * (if (state == AdState.NoFill) 2 else 1)
            failures++
            AdLog.d("app_open", "retry #$failures in ${delay}ms")
            main.postDelayed(retry, delay)
        }
    }

    private val retry = Runnable { if (state == AdState.Failed || state == AdState.NoFill) { state = AdState.NotInitialized; fetchAd() } }

    /** Network back: try again, with a fresh retry budget. */
    fun onNetworkAvailable() = runOnMain {
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
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) { if (activity !is AdActivity) currentActivity = activity }
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private companion object {
        const val MAX_AGE_MS = 4 * 60 * 60 * 1000L
        const val COLD_START_GRACE_MS = 1_500L
        const val MAX_RETRIES = 3
        const val RETRY_BASE_MS = 30_000L
        const val SUPPRESSED = "returning from a system screen"
    }
}
