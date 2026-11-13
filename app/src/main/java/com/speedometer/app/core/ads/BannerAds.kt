package com.speedometer.app.core.ads

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.speedometer.app.core.analytics.AdAnalytics
import com.speedometer.app.core.common.findActivity
import com.speedometer.app.core.common.rememberInternetConnected
import com.speedometer.app.core.designsystem.Surface1

/**
 * Where the app's single banner goes: above or below the screens. Read once per Remote Config refresh.
 * The host uses it to decide which side of the screens the [AppBanner] sits on, and which insets it takes over.
 */
/** True while the app banner sits below the screens and owns the gesture-bar inset (screens then add no room for it). */
val LocalBannerBelow = androidx.compose.runtime.compositionLocalOf { false }

fun AdsController.bannerOnTop(slot: String): Boolean = config.inScreen(slot).isBannerTop

/**
 * The app's one banner, hosted once by the navigation host outside the screens, so it survives navigation: moving
 * between Home, Trips, Insights and the rest reuses the same AdView and the same loaded ad. Before, each screen created
 * its own AdView, so every navigation threw the loaded banner away and sent a fresh request (a shimmer and a wait each time).
 *
 * - [visible] false (setup screens, Settings, HUD) only folds the slot; the ad stays loaded for the next screen.
 * - Nothing is requested before onboarding is done, before consent, offline, or when Remote Config switches the slot off.
 * - The slot keeps its adaptive height while loading (a quiet placeholder), so content does not jump when the ad arrives.
 *   A failed load or no-fill folds the slot and retries with backoff; a late success unfolds it.
 * - The AdView and its request live in `remember`/`DisposableEffect`, never in a composition path that re-runs, so
 *   recomposition never sends a request.
 *
 * Returns nothing; [onShownChange] reports whether the slot currently takes space, so the host knows whether the banner
 * or the screen pads for the gesture bar.
 */
@Composable
fun AppBanner(
    ads: AdsController,
    slot: String,
    visible: Boolean,
    onTop: Boolean,
    onShownChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return
    val unlocked by AdGate.adsUnlocked.collectAsStateWithLifecycle()
    val consent by ads.config.consent.collectAsStateWithLifecycle()
    val configVersion by ads.config.version.collectAsStateWithLifecycle()
    val online = rememberInternetConnected()
    val model = remember(configVersion) { ads.config.inScreen(slot) }
    val enabled = unlocked && consent && model.isAdEnabled && model.adBannerId.isNotEmpty()

    val density = LocalDensity.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    // Adaptive anchored size for the current orientation and width. Rotation recreates the activity, so this runs once.
    val adSize = remember(widthDp) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp) }
    val slotHeight = remember(adSize) { with(density) { adSize.getHeightInPixels(activity).coerceAtLeast(0).toDp() } }

    val holder = remember(activity) { BannerHolder(activity, slot, ads.analytics) }
    DisposableEffect(holder) { onDispose { holder.destroy() } }

    // The one place a request starts. Keys are the facts that make a request possible, not the screen.
    LaunchedEffect(holder, enabled, online, model.adBannerId, adSize) {
        if (enabled && online) holder.ensureLoaded(model, adSize)
    }

    val state = holder.state
    val takesSpace = enabled && visible && (state == AdState.Ready || (state == AdState.Loading && !holder.loadingTooLong))
    val currentOnShown by rememberUpdatedState(onShownChange)
    LaunchedEffect(takesSpace) { currentOnShown(takesSpace) }

    if (!enabled) return
    val insets = if (onTop) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding()
    Box(
        Modifier.fillMaxWidth().then(if (takesSpace) insets else Modifier).height(if (takesSpace) slotHeight else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { holder.container },
            update = { it.visibility = if (takesSpace && state == AdState.Ready) View.VISIBLE else View.GONE },
            // The container belongs to the holder, which outlives this node; only detach it.
            onRelease = { (it.parent as? android.view.ViewGroup)?.removeView(it) },
        )
        if (takesSpace && state == AdState.Loading) BannerPlaceholder(slotHeight)
    }
}

/**
 * Owns the banner's AdView and its state for one activity. One request at a time; a ready ad is kept and reused until the
 * activity goes; AdMob's own refresh (set in the AdMob console) replaces the creative in place.
 */
private class BannerHolder(
    private val activity: Activity,
    private val slot: String,
    private val analytics: AdAnalytics,
) {
    private val main = Handler(Looper.getMainLooper())
    val container: FrameLayout = FrameLayout(activity)
    private var banner: BannerAd? = null
    private var adView: AdView? = null
    private var loadedFor: String? = null
    private var failures = 0
    private var destroyed = false
    private var pendingRetry: Runnable? = null

    var state by mutableStateOf(AdState.NotInitialized)
        private set

    /** After [LOADING_PLACEHOLDER_MS] the placeholder folds away while the request carries on; a late ad still shows. */
    var loadingTooLong by mutableStateOf(false)
        private set

    fun ensureLoaded(model: InScreenAdModel, adSize: AdSize) {
        if (destroyed) return
        val key = "${model.adBannerId}|$adSize"
        if (loadedFor == key && (state == AdState.Ready || state == AdState.Loading)) {
            AdLog.d("banner", "[$slot] duplicate load prevented (state=$state)")
            return
        }
        load(model, adSize, key)
    }

    private fun load(model: InScreenAdModel, adSize: AdSize, key: String) {
        pendingRetry?.let { main.removeCallbacks(it) }
        pendingRetry = null
        loadedFor = key
        state = AdState.Loading
        loadingTooLong = false
        main.postDelayed({ if (state == AdState.Loading) loadingTooLong = true }, LOADING_PLACEHOLDER_MS)
        val started = AdLog.requestStarted("banner", slot)
        AdMobInitializer.initializeAdmob(activity) {
            if (destroyed || activity.isFinishing || activity.isDestroyed) return@initializeAdmob
            val request = BannerAdRequest.Builder(model.adBannerId, adSize).also { builder ->
                if (model.isBannerCollapsing) {
                    builder.setGoogleExtrasBundle(Bundle().apply { putString("collapsible", if (model.isBannerTop) "top" else "bottom") })
                }
            }.build()
            // One AdView per activity. A new request into a fresh view replaces the old one only after a failure.
            val view = adView ?: AdView(activity).also { adView = it; container.addView(it) }
            view.loadAd(request, object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) {
                    main.post {
                        if (destroyed || activity.isFinishing || activity.isDestroyed) { ad.destroy(); return@post }
                        ad.adEventCallback = object : BannerAdEventCallback {
                            override fun onAdImpression() { analytics.adShown("banner", slot) }
                            override fun onAdClicked() { analytics.adClicked("banner", slot) }
                        }
                        if (banner !== ad) banner?.destroy()
                        banner = ad
                        failures = 0
                        state = AdState.Ready
                        AdLog.loaded("banner", slot, started)
                        analytics.adLoaded("banner", slot)
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    val code = adError.code.toString()
                    main.post {
                        if (destroyed) return@post
                        AdLog.failed("banner", slot, started, code, adError.message)
                        analytics.adFailedToLoad("banner", slot, code)
                        state = if (AdLog.isNoFill(code)) AdState.NoFill else AdState.Failed
                        scheduleRetry(model, adSize, key)
                    }
                }
            })
        }
    }

    /** 20 s, 40 s, 80 s (double for no-fill), then stop until the network comes back or the activity is recreated. */
    private fun scheduleRetry(model: InScreenAdModel, adSize: AdSize, key: String) {
        if (failures >= MAX_RETRIES) return
        val delay = RETRY_BASE_MS * (1L shl failures) * (if (state == AdState.NoFill) 2 else 1)
        failures++
        AdLog.d("banner", "[$slot] retry #$failures in ${delay}ms")
        val r = Runnable { pendingRetry = null; if (!destroyed) load(model, adSize, key) }
        pendingRetry = r
        main.postDelayed(r, delay)
    }

    fun destroy() {
        destroyed = true
        pendingRetry?.let { main.removeCallbacks(it) }
        main.removeCallbacksAndMessages(null)
        banner?.destroy()
        banner = null
        container.removeAllViews()
        adView = null
    }
}

/** Placeholder shown while a banner is loading. */
@Composable
private fun BannerPlaceholder(height: Dp) {
    val alpha by rememberInfiniteTransition(label = "banner-shimmer").animateFloat(
        initialValue = 0.7f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "banner-shimmer-alpha",
    )
    Box(Modifier.fillMaxWidth().height(height).alpha(alpha).background(Surface1))
}

private const val LOADING_PLACEHOLDER_MS = 4_000L
private const val RETRY_BASE_MS = 20_000L
private const val MAX_RETRIES = 3
