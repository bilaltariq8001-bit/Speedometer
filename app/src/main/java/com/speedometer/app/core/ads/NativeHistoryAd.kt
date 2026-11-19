package com.speedometer.app.core.ads

import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.speedometer.app.core.analytics.AdAnalytics
import com.speedometer.app.core.common.findActivity
import com.speedometer.app.core.common.isNetworkConnected
import com.speedometer.app.core.common.rememberInternetConnected
import java.util.concurrent.atomic.AtomicBoolean

/** Inline native placement for the History trip list. No placeholder is left behind on load failure. */
@Composable
fun HistoryNativeAd(
    model: NativeAdModel,
    analytics: AdAnalytics,
    modifier: Modifier = Modifier,
) = NativeAdCard(model, analytics, placement = "history", compact = false, modifier = modifier)

/**
 * One native ad card for any screen. [compact] is a single slim row (icon, headline, one line of text, button) used on the
 * setup screens; the full card is used in lists. Nothing is drawn until an ad has loaded, and nothing is left on failure.
 */
@Composable
fun NativeAdCard(
    model: NativeAdModel,
    analytics: AdAnalytics,
    placement: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Inflating the ad's Android views mid-transition dropped frames; it is added once the screen has settled.
    if (!com.speedometer.app.core.designsystem.LocalScreenSettled.current.value) return
    NativeAdCardBody(model, analytics, placement, compact, modifier)
}

@Composable
private fun NativeAdCardBody(
    model: NativeAdModel,
    analytics: AdAnalytics,
    placement: String,
    compact: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return
    val internetConnected = rememberInternetConnected()
    if (!model.isAdEnabled || model.adNativeId.isBlank() || !internetConnected || !activity.isNetworkConnected()) return

    // A native ad preloaded ahead of time shows instantly; otherwise one is requested now.
    val cached = remember(model.adNativeId) { NativeAdCache.take(model.adNativeId) }
    var loaded by remember { mutableStateOf(cached != null) }
    val adRef = remember { arrayOf(cached) }
    val disposed = remember { AtomicBoolean(false) }
    DisposableEffect(activity, model.adNativeId) {
        disposed.set(false)
        if (adRef[0] == null) {
            NativeAdCache.load(activity.applicationContext, model.adNativeId, analytics, placement) { nativeAd ->
                if (disposed.get() || activity.isFinishing || activity.isDestroyed) {
                    nativeAd.destroy()
                } else {
                    adRef[0] = nativeAd
                    loaded = true
                }
            }
        }
        onDispose {
            disposed.set(true)
            adRef[0]?.destroy()
            adRef[0] = null
            // Have the next one ready for the next screen.
            NativeAdCache.preload(activity.applicationContext, model, analytics, placement)
        }
    }

    if (loaded) {
        Box(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) 72.dp else 112.dp),
                factory = { ctx -> if (compact) createCompactNativeAdView(ctx, adRef[0]) else createNativeAdView(ctx, adRef[0]) },
                update = { view -> adRef[0]?.let { populateNativeAdView(view, it) } },
                onRelease = { it.destroy() },
            )
        }
    }
}

/**
 * Keeps one native ad ready so the History list shows it with no wait. Native ads are used within an hour of loading,
 * as AdMob recommends; an older one is dropped and replaced.
 */
object NativeAdCache {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var ad: NativeAd? = null
    private var adUnit: String? = null
    private var loadedAtMs = 0L
    private var loading = false

    /** Loads one ad for [model] in the background if none is ready. */
    fun preload(context: android.content.Context, model: NativeAdModel, analytics: AdAnalytics, placement: String = "history") {
        if (!model.isAdEnabled || model.adNativeId.isBlank()) return
        main.post {
            if (fresh(model.adNativeId) || loading || !context.isNetworkConnected()) return@post
            load(context, model.adNativeId, analytics, placement) { loaded ->
                ad?.destroy()
                ad = loaded; adUnit = model.adNativeId; loadedAtMs = android.os.SystemClock.elapsedRealtime()
            }
        }
    }

    /** The ready ad for [adUnitId], removed from the cache, or null. Main thread only. */
    fun take(adUnitId: String): NativeAd? {
        if (!fresh(adUnitId)) return null
        return ad.also { ad = null; adUnit = null }
    }

    private fun fresh(adUnitId: String): Boolean {
        val current = ad ?: return false
        if (adUnit != adUnitId || android.os.SystemClock.elapsedRealtime() - loadedAtMs > MAX_AGE_MS) {
            current.destroy(); ad = null; adUnit = null
            return false
        }
        return true
    }

    /** One request; [onLoaded] runs on the main thread. Failures are only reported to analytics. */
    fun load(context: android.content.Context, adUnitId: String, analytics: AdAnalytics, placement: String = "history", onLoaded: (NativeAd) -> Unit) {
        loading = true
        AdMobInitializer.initializeAdmob(context) {
            val request = NativeAdRequest.Builder(adUnitId, listOf(NativeAd.NativeAdType.NATIVE)).build()
            runCatching {
                NativeAdLoader.load(request, object : NativeAdLoaderCallback {
                    override fun onNativeAdLoaded(nativeAd: NativeAd) {
                        nativeAd.adEventCallback = object : NativeAdEventCallback {
                            override fun onAdImpression() { analytics.adShown("native", placement) }
                            override fun onAdClicked() { analytics.adClicked("native", placement) }
                        }
                        analytics.adLoaded("native", placement)
                        main.post { loading = false; onLoaded(nativeAd) }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        analytics.adFailedToLoad("native", placement, adError.code.toString())
                        main.post { loading = false }
                    }
                })
            }.onFailure { main.post { loading = false } }
        }
    }

    private const val MAX_AGE_MS = 60 * 60 * 1000L
}

private fun createNativeAdView(context: android.content.Context, ad: NativeAd?): NativeAdView {
    val root = NativeAdView(context).apply {
        background = rounded(AndroidColor.rgb(20, 24, 29), 18f)
        setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 14))
    }
    val content = LinearLayout(context)
    content.apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            text = "Ad"
            textSize = 11f
            setTextColor(AndroidColor.rgb(185, 194, 204))
            background = rounded(AndroidColor.rgb(44, 52, 61), 4f)
            setPadding(dp(context, 5), dp(context, 2), dp(context, 5), dp(context, 2))
        }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(context, 8) })

        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val icon = ImageView(context).apply { id = ID_ICON; scaleType = ImageView.ScaleType.CENTER_CROP }
        top.addView(icon, LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)).apply { marginEnd = dp(context, 12) })
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val headline = TextView(context).apply { id = ID_HEADLINE; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(AndroidColor.WHITE); maxLines = 2 }
        val advertiser = TextView(context).apply { id = ID_ADVERTISER; textSize = 12f; setTextColor(AndroidColor.rgb(185, 194, 204)); setPadding(0, dp(context, 3), 0, 0) }
        texts.addView(headline)
        texts.addView(advertiser)
        top.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(top)

        val body = TextView(context).apply { id = ID_BODY; textSize = 14f; setTextColor(AndroidColor.rgb(221, 226, 232)); maxLines = 3; setPadding(0, dp(context, 10), 0, 0) }
        content.addView(body)
        val cta = TextView(context).apply {
            id = ID_CTA
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AndroidColor.rgb(20, 24, 29))
            gravity = android.view.Gravity.CENTER
            background = rounded(AndroidColor.rgb(255, 176, 32), 24f)
            setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 10))
        }
        content.addView(cta, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(context, 12) })
    }
    root.addView(content, LinearLayout.LayoutParams(-1, -2))
    if (ad != null) populateNativeAdView(root, ad)
    return root
}

/** Slim one-row native layout for the setup screens: Ad badge on the icon, headline, one line of body, a pill button. */
private fun createCompactNativeAdView(context: android.content.Context, ad: NativeAd?): NativeAdView {
    val root = NativeAdView(context).apply {
        background = rounded(AndroidColor.rgb(20, 24, 29), dp(context, 16).toFloat()).apply {
            setStroke(dp(context, 1), AndroidColor.rgb(38, 45, 53))
        }
        setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
    }
    val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
    val iconBox = android.widget.FrameLayout(context)
    val icon = ImageView(context).apply {
        id = ID_ICON
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(context, 10).toFloat())
            }
        }
    }
    iconBox.addView(icon, android.widget.FrameLayout.LayoutParams(dp(context, 44), dp(context, 44)))
    row.addView(iconBox, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(context, 12) })

    val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val titleRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
    titleRow.addView(TextView(context).apply {
        text = "Ad"
        textSize = 10f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(AndroidColor.rgb(20, 24, 29))
        background = rounded(AndroidColor.rgb(255, 176, 32), dp(context, 4).toFloat())
        setPadding(dp(context, 4), 0, dp(context, 4), 0)
    }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(context, 6) })
    val headline = TextView(context).apply {
        id = ID_HEADLINE; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(AndroidColor.WHITE)
        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
    }
    titleRow.addView(headline, LinearLayout.LayoutParams(0, -2, 1f))
    texts.addView(titleRow)
    val body = TextView(context).apply {
        id = ID_BODY; textSize = 12f; setTextColor(AndroidColor.rgb(185, 194, 204))
        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(context, 2), 0, 0)
    }
    texts.addView(body)
    val advertiser = TextView(context).apply { id = ID_ADVERTISER; visibility = View.GONE; tag = TAG_HIDE }
    texts.addView(advertiser)
    row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))

    val cta = TextView(context).apply {
        id = ID_CTA
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(AndroidColor.rgb(20, 24, 29))
        gravity = android.view.Gravity.CENTER
        maxLines = 1
        background = rounded(AndroidColor.rgb(255, 176, 32), dp(context, 20).toFloat())
        setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8))
    }
    row.addView(cta, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(context, 10) })
    root.addView(row, LinearLayout.LayoutParams(-1, -2))
    if (ad != null) populateNativeAdView(root, ad)
    return root
}

private fun populateNativeAdView(view: NativeAdView, ad: NativeAd) {
    val icon = view.findViewById<ImageView>(ID_ICON)
    val headline = view.findViewById<TextView>(ID_HEADLINE)
    val advertiser = view.findViewById<TextView>(ID_ADVERTISER)
    val body = view.findViewById<TextView>(ID_BODY)
    val cta = view.findViewById<TextView>(ID_CTA)
    icon.setImageDrawable(ad.icon?.drawable)
    icon.visibility = if (ad.icon == null) View.GONE else View.VISIBLE
    headline.text = ad.headline
    advertiser.text = ad.advertiser
    advertiser.visibility = if (advertiser.tag == TAG_HIDE || ad.advertiser.isNullOrBlank()) View.GONE else View.VISIBLE
    body.text = ad.body
    body.visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
    cta.text = ad.callToAction
    cta.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    view.iconView = icon
    view.headlineView = headline
    view.advertiserView = advertiser
    view.bodyView = body
    view.callToActionView = cta
    view.registerNativeAd(ad, null)
}

private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radiusDp
}

private fun dp(context: android.content.Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()

private const val ID_ICON = 0x710001
private const val ID_HEADLINE = 0x710002
private const val ID_ADVERTISER = 0x710003
private const val ID_BODY = 0x710004
private const val ID_CTA = 0x710005
private const val TAG_HIDE = "hide"
