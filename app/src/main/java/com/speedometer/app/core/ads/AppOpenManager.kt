package com.speedometer.app.core.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
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
 * App-open ad, shown after the first safe Home frame on launch and when the user returns from the background:
 *  - never over the splash/setup flow, and never until consent (UMP) allows ads;
 *  - never while a trip is recording, paused, or being saved, nor on live trip screens ([AdGate]);
 *  - never when returning from the system (permission dialog, settings, share): [AdGate.suppressNextAppOpen];
 *  - never while another full-screen ad is on screen, nor inside the shared cooldown after one;
 *  - limited by the shared full-screen ad cooldown;
 *  - only on [MainActivity], only while the app is in the foreground (a background show is an AdMob policy violation);
 *  - an ad older than 4 hours is dropped, and the next one loads after this one closes.
 * It can be switched off remotely with `app_open_enabled`.
 */
@Singleton
class AppOpenManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: AdConfig,
    private val trips: TripRepository,
    private val analytics: AdAnalytics,
) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private var appOpenAd: AppOpenAd? = null
    private var isLoading = false
    private var loadTimeMs = 0L
    private var currentActivity: Activity? = null
    private var returnedFromBackground = false
    private var isColdStart = true
    private var coldStartHomePending = false
    private var showPending = false
    @Volatile private var isShowing = false

    fun start(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        // Wait until the splash/setup flow reaches Home before showing on a cold launch.
        if (isColdStart) {
            isColdStart = false
            coldStartHomePending = true
            return
        }
        returnedFromBackground = true
    }

    override fun onStop(owner: LifecycleOwner) {
        // ProcessLifecycleOwner signals the next foreground transition.
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is AdActivity) return
        currentActivity = activity
        if (returnedFromBackground) {
            returnedFromBackground = false
            showPending = true
        }
        tryShowPending()
    }

    private fun tripActive() = trips.state.value.phase.let { it == TripPhase.Recording || it == TripPhase.Paused }

    /** Call when Home first becomes visible after startup; this avoids covering splash and onboarding. */
    fun onHomeVisible(eligible: Boolean = true) {
        if (!coldStartHomePending) return
        coldStartHomePending = false
        if (!eligible) return
        showPending = true
        tryShowPending()
    }

    /** Called after Remote Config or UMP resolves, in case startup was waiting for either one. */
    fun onAdConfigurationReady() {
        fetchAd()
        tryShowPending()
    }

    private fun tryShowPending() {
        if (!showPending) return
        if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val activity = currentActivity
        if (activity !is MainActivity || activity.isFinishing || activity.isDestroyed) return
        // If UMP has not answered yet, keep the request pending. AdsController retries when it resolves.
        if (!config.canRequestAds()) return
        if (AdGate.consumeAppOpenSuppression()) {
            showPending = false
            return
        }
        val model = config.appOpen()
        if (!model.isAdEnabled || model.adAppOpenId.isEmpty() || !context.isNetworkConnected() || tripActive() ||
            AdGate.tripSaving || AdGate.isRestrictedRoute() || AdGate.fullScreenAdVisible || isShowing ||
            AdGate.sinceLastFullScreenAdMs() < config.interstitialCooldownSec * 1000L
        ) {
            showPending = false
            return
        }
        if (!isAdAvailable) {
            fetchAd()
            return
        }
        showPending = false
        showAd(activity, appOpenAd ?: return)
    }

    /** Loads an ad ahead of time. Called after consent, and again after each show. */
    fun fetchAd() {
        val model = config.appOpen()
        if (!config.canRequestAds() || !model.isAdEnabled || model.adAppOpenId.isEmpty()) return
        if (isAdAvailable || isLoading) return
        isLoading = true
        AdMobInitializer.initializeAdmob(context) {
            try {
                AppOpenAd.load(
                    AdRequest.Builder(model.adAppOpenId).build(),
                    object : AdLoadCallback<AppOpenAd> {
                        override fun onAdLoaded(ad: AppOpenAd) {
                            appOpenAd = ad
                            loadTimeMs = System.currentTimeMillis()
                            isLoading = false
                            analytics.adLoaded("app_open", "app_open")
                            tryShowPending()
                        }

                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            isLoading = false
                            Log.w(TAG, "load failed: ${adError.message}")
                            analytics.adFailedToLoad("app_open", "app_open", adError.code.toString())
                        }
                    },
                )
            } catch (e: Exception) {
                isLoading = false
                Log.e(TAG, "load threw", e)
            }
        }
    }

    private val isAdAvailable: Boolean
        get() = appOpenAd != null && System.currentTimeMillis() - loadTimeMs < MAX_AGE_MS

    private fun showAd(activity: Activity, ad: AppOpenAd) {
        isShowing = true
        ad.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdShowedFullScreenContent() {
                AdGate.fullScreenAdVisible = true
                analytics.adShown("app_open", "app_open")
            }

            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowing = false
                AdGate.fullScreenAdVisible = false
                AdGate.markFullScreenAdClosed()
                fetchAd()
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                Log.w(TAG, "failed to show: $fullScreenContentError")
                appOpenAd = null
                isShowing = false
                AdGate.fullScreenAdVisible = false
            }

            override fun onAdClicked() {
                analytics.adClicked("app_open", "app_open")
            }
        }
        try {
            ad.show(activity)
        } catch (e: Exception) {
            Log.e(TAG, "show threw", e)
            isShowing = false
            appOpenAd = null
            fetchAd()
        }
    }

    override fun onActivityStarted(activity: Activity) { if (activity !is AdActivity) currentActivity = activity }
    override fun onActivityDestroyed(activity: Activity) { if (currentActivity === activity) currentActivity = null }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) { if (activity !is AdActivity) currentActivity = activity }
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private companion object {
        const val TAG = "AppOpenManager"
        const val MAX_AGE_MS = 4 * 60 * 60 * 1000L
    }
}
