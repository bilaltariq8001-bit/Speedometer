package com.speedometer.app.core.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.speedometer.app.core.analytics.AdAnalytics
import com.speedometer.app.core.common.isNetworkConnected
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps up to `interstitial_cache_size` interstitials ready (loaded plus loading), shows the oldest one and refills the
 * cache. Capping is by time (`interstitial_cooldown_seconds`) or by eligible clicks (`interstitial_click_cap`), chosen
 * by one Remote Config boolean, and only applies to placements whose `_counter_enabled` key is on. The cooldown is
 * shared with the app-open ad through [AdGate]. [onDismiss] always runs, whatever stops the ad from showing.
 */
@Singleton
class InterstitialManager @Inject constructor(
    private val config: AdConfig,
    private val analytics: AdAnalytics,
) {
    private data class CachedInterstitial(val adUnitId: String, val ad: InterstitialAd)

    private val main = Handler(Looper.getMainLooper())
    private val cachedAds = ArrayDeque<CachedInterstitial>()
    private val pendingLoads = mutableMapOf<String, Int>()
    private var eligibleClicks = 0L
    private var showPending = false

    private val maxCachedAds: Int get() = config.interstitialCacheSize

    // ---------------------------------------------------------------- capping

    private fun passesTimeCap(): Boolean = AdGate.sinceLastFullScreenAdMs() >= config.interstitialCooldownSec * 1000L

    private fun passesClickCap(): Boolean = eligibleClicks > config.interstitialClickCap

    private fun passesCapping(model: InterstitialAdModel): Boolean {
        if (!model.isCappingEnabled) return true
        return if (config.isInterstitialTimeCappingEnabled) passesTimeCap() else passesClickCap()
    }

    // ---------------------------------------------------------------- cache slots

    private fun reserveLoadSlot(adUnitId: String): Boolean {
        val pending = pendingLoads[adUnitId] ?: 0
        if (cachedAds.count { it.adUnitId == adUnitId } + pending >= maxCachedAds) return false
        pendingLoads[adUnitId] = pending + 1
        return true
    }

    private fun onLoaded(adUnitId: String, ad: InterstitialAd) {
        releaseLoadSlot(adUnitId)
        if (cachedAds.count { it.adUnitId == adUnitId } < maxCachedAds) {
            cachedAds.addLast(CachedInterstitial(adUnitId, ad))
        }
    }

    private fun onLoadFailed(adUnitId: String) = releaseLoadSlot(adUnitId)

    private fun releaseLoadSlot(adUnitId: String) {
        val pending = pendingLoads[adUnitId] ?: return
        if (pending <= 1) pendingLoads.remove(adUnitId) else pendingLoads[adUnitId] = pending - 1
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun canLoad(context: Context, model: InterstitialAdModel): Boolean =
        AdGate.adsUnlocked.value && config.canRequestAds() && context.isNetworkConnected() && model.isAdEnabled && model.adInterstitialId.isNotEmpty()

    private fun canShow(activity: Activity, model: InterstitialAdModel): Boolean =
        activity is LifecycleOwner && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            !activity.isFinishing && !activity.isDestroyed && !AdGate.tripSaving &&
            !AdGate.fullScreenAdVisible && canLoad(activity, model)

    // ---------------------------------------------------------------- load

    /** Fills the cache from [placement]'s ad unit. Safe to call any time; it does nothing when the cache is full. */
    fun preload(context: Context, placement: String) {
        val model = config.interstitial(placement)
        onMain { requestOne(context, model, placement, "preload") }
    }

    /** Reserves a slot before the SDK starts; the callbacks only release or fill that slot. */
    private fun requestOne(context: Context, model: InterstitialAdModel, placement: String, reason: String, attempt: Int = 0): Boolean {
        if (!canLoad(context, model)) {
            Log.d(TAG, "[$placement] request skipped ($reason): not eligible")
            return false
        }
        if (!reserveLoadSlot(model.adInterstitialId)) {
            AdLog.d("interstitial", "[$placement] duplicate load prevented (cache full or loading)")
            return false
        }
        val appContext = context.applicationContext
        AdMobInitializer.initializeAdmob(appContext) {
            onMain {
                if (!canLoad(appContext, model)) {
                    onLoadFailed(model.adInterstitialId)
                    return@onMain
                }
                val started = AdLog.requestStarted("interstitial", placement)
                try {
                    InterstitialAd.load(
                        AdRequest.Builder(model.adInterstitialId).build(),
                        object : AdLoadCallback<InterstitialAd> {
                            override fun onAdLoaded(ad: InterstitialAd) {
                                onMain {
                                    AdLog.loaded("interstitial", placement, started)
                                    onLoaded(model.adInterstitialId, ad); analytics.adLoaded("interstitial", placement)
                                }
                            }

                            override fun onAdFailedToLoad(adError: LoadAdError) {
                                onMain {
                                    onLoadFailed(model.adInterstitialId)
                                    AdLog.failed("interstitial", placement, started, adError.code.toString(), adError.message)
                                    analytics.adFailedToLoad("interstitial", placement, adError.code.toString())
                                    // A couple of spaced-out retries, so one failed request (a slow network at launch) does
                                    // not leave the placement empty until the next show call. Kept short to respect AdMob.
                                    if (attempt < MAX_RETRIES) {
                                        main.postDelayed(
                                            { requestOne(appContext, model, placement, "retry ${attempt + 1}", attempt + 1) },
                                            RETRY_BASE_MS * (1L shl attempt),
                                        )
                                    }
                                }
                            }
                        },
                    )
                } catch (error: Exception) {
                    onLoadFailed(model.adInterstitialId)
                    Log.e(TAG, "[$placement] request threw", error)
                }
            }
        }
        return true
    }

    // ---------------------------------------------------------------- show

    /** Shows an ad for [placement] if allowed, then runs [onDismiss] exactly once. */
    fun show(activity: Activity, placement: String, onDismiss: () -> Unit, onShowAd: () -> Unit = {}) {
        val model = config.interstitial(placement)
        onMain {
            eligibleClicks++
            if (!canShow(activity, model) || showPending) {
                AdLog.skipped("interstitial", "[$placement] not eligible (consent/onboarding/offline/disabled/another ad/trip saving)")
                onDismiss()
                return@onMain
            }
            val matchingCached = cachedAds.firstOrNull { it.adUnitId == model.adInterstitialId }
            val pendingForUnit = pendingLoads[model.adInterstitialId] ?: 0
            if (model.shouldLoadNextAd && cachedAds.count { it.adUnitId == model.adInterstitialId } + pendingForUnit < maxCachedAds) {
                requestOne(activity, model, placement, "show call warmup")
            }
            if (!passesCapping(model)) {
                AdLog.skipped("interstitial", "[$placement] capped")
                onDismiss()
                return@onMain
            }
            if (matchingCached == null) {
                AdLog.skipped("interstitial", "[$placement] no ad ready yet")
                onDismiss()
                return@onMain
            }
            cachedAds.remove(matchingCached)
            showReservedAd(activity, matchingCached.ad, model, placement, onShowAd, onDismiss)
        }
    }

    private fun showReservedAd(
        activity: Activity,
        ad: InterstitialAd,
        model: InterstitialAdModel,
        placement: String,
        onShowAd: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        showPending = true
        var finished = false
        fun finish() {
            if (finished) return
            finished = true
            showPending = false
            AdGate.fullScreenAdVisible = false
            AdGate.markFullScreenAdClosed()
            onDismiss()
        }

        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdShowedFullScreenContent() {
                onMain {
                    showPending = false
                    AdGate.fullScreenAdVisible = true
                    eligibleClicks = 0L
                    onShowAd()
                    analytics.adShown("interstitial", placement)
                    if (model.shouldLoadNextAd) requestOne(activity, model, placement, "after show")
                }
            }

            override fun onAdDismissedFullScreenContent() {
                onMain {
                    if (model.shouldLoadNextAd) requestOne(activity, model, placement, "after dismiss")
                    finish()
                }
            }

            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                onMain {
                    Log.w(TAG, "[$placement] failed to show: $fullScreenContentError")
                    finish()
                }
            }

            override fun onAdClicked() {
                analytics.adClicked("interstitial", placement)
            }
        }
        try {
            ad.show(activity)
        } catch (error: Exception) {
            Log.e(TAG, "[$placement] show threw", error)
            finish()
        }
    }

    private companion object {
        const val TAG = "InterstitialManager"
        const val MAX_RETRIES = 2
        const val RETRY_BASE_MS = 10_000L
    }
}
