package com.speedometer.app.core.ads

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.speedometer.app.core.analytics.AdAnalytics
import com.speedometer.app.core.common.findActivity
import com.speedometer.app.core.common.isNetworkConnected
import com.speedometer.app.core.common.rememberInternetConnected
import com.speedometer.app.core.designsystem.Surface1

private enum class BannerState { Loading, Waiting, Shown, Hidden }

/**
 * A screen with one banner slot. The Remote Config keys of [slot] decide whether the ad is on (`<slot>_in_screen_ad_enabled`)
 * and whether it sits at the top or bottom (`<slot>_ad_position`). No consent, no network, or a failed load leaves no gap.
 * [clearStatusBarAtTop] is for screens drawn under the status bar: a top banner then keeps clear of it.
 */
@Composable
fun ScreenWithBanner(slot: String, clearStatusBarAtTop: Boolean = false, content: @Composable () -> Unit) {
    val ads = hiltViewModel<AdsViewModel>().ads
    val model = ads.config.inScreen(slot)
    val internetConnected = rememberInternetConnected()
    val show = model.isAdEnabled && model.adBannerId.isNotEmpty() && ads.config.canRequestAds() && internetConnected
    Column(Modifier.fillMaxSize()) {
        if (show && model.isBannerTop) BannerSlot(slot, model, Modifier.then(if (clearStatusBarAtTop) Modifier.statusBarsPadding() else Modifier))
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        if (show && !model.isBannerTop) BannerSlot(slot, model, Modifier)
    }
}

@Composable
private fun BannerSlot(slot: String, model: InScreenAdModel, modifier: Modifier) {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    val analytics = hiltViewModel<AdsViewModel>().ads.analytics
    var state by remember { mutableStateOf(BannerState.Loading) }
    if (state == BannerState.Hidden || !activity.isNetworkConnected()) return

    val density = LocalDensity.current
    val adSize = remember { adaptiveSize(activity) }
    val heightDp = remember(adSize) { with(density) { adSize.getHeightInPixels(activity).coerceAtLeast(0).toDp() } }
    val loaded = remember { arrayOfNulls<BannerAd>(1) }
    val disposed = remember { AtomicBoolean(false) }
    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            loaded[0]?.destroy()
            loaded[0] = null
        }
    }
    LaunchedEffect(slot, model.adBannerId) {
        // After the timeout the placeholder folds away, but the request keeps going: a banner that arrives late still
        // shows, instead of being thrown away (the old code hid it for good, so slow networks never saw a banner).
        kotlinx.coroutines.delay(AD_LOAD_UI_TIMEOUT_MS)
        if (state == BannerState.Loading) state = BannerState.Waiting
    }

    val slotHeight = if (state == BannerState.Waiting) 0.dp else heightDp
    Box(modifier.fillMaxWidth().height(slotHeight), contentAlignment = Alignment.Center) {
        // The factory runs once, so recomposition never sends a second request.
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                FrameLayout(ctx).also { container ->
                    loadBanner(activity, container, model, slot, adSize, analytics, disposed::get) { ad ->
                        if (ad != null) loaded[0] = ad
                        state = if (ad != null) BannerState.Shown else BannerState.Hidden
                    }
                }
            },
            onRelease = { container -> container.removeAllViews() },
        )
        if (state == BannerState.Loading) BannerShimmer(heightDp)
    }
}

private fun adaptiveSize(activity: Activity): AdSize {
    val metrics = activity.resources.displayMetrics
    val widthDp = (metrics.widthPixels / metrics.density).toInt()
    return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp)
}

private fun loadBanner(
    activity: Activity,
    container: FrameLayout,
    model: InScreenAdModel,
    slot: String,
    adSize: AdSize,
    analytics: AdAnalytics,
    isDisposed: () -> Boolean,
    onResult: (BannerAd?) -> Unit,
) {
    AdMobInitializer.initializeAdmob(activity) {
        if (isDisposed() || activity.isFinishing || activity.isDestroyed) return@initializeAdmob
        val request = BannerAdRequest.Builder(model.adBannerId, adSize).also { builder ->
            if (model.isBannerCollapsing) {
                builder.setGoogleExtrasBundle(Bundle().apply { putString("collapsible", if (model.isBannerTop) "top" else "bottom") })
            }
        }.build()
        val adView = AdView(activity)
        container.addView(adView)
        adView.loadAd(request, object : AdLoadCallback<BannerAd> {
            override fun onAdLoaded(ad: BannerAd) {
                if (isDisposed() || activity.isFinishing || activity.isDestroyed) {
                    ad.destroy()
                    return
                }
                ad.adEventCallback = object : BannerAdEventCallback {
                    override fun onAdImpression() { analytics.adShown("banner", slot) }
                    override fun onAdClicked() { analytics.adClicked("banner", slot) }
                }
                analytics.adLoaded("banner", slot)
                activity.runOnUiThread {
                    if (isDisposed() || activity.isFinishing || activity.isDestroyed) ad.destroy()
                    else onResult(ad)
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                analytics.adFailedToLoad("banner", slot, adError.code.toString())
                activity.runOnUiThread {
                    container.removeAllViews()
                    if (!isDisposed() && !activity.isFinishing && !activity.isDestroyed) onResult(null)
                }
            }
        })
    }
}

/** Placeholder shown while a banner is loading. */
@Composable
private fun BannerShimmer(height: Dp) {
    val alpha by rememberInfiniteTransition(label = "banner-shimmer").animateFloat(
        initialValue = 0.7f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "banner-shimmer-alpha",
    )
    Box(Modifier.fillMaxWidth().height(height).alpha(alpha).background(Surface1))
}

private const val AD_LOAD_UI_TIMEOUT_MS = 4_000L
