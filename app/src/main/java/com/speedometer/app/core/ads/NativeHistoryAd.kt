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
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return
    val internetConnected = rememberInternetConnected()
    if (!model.isAdEnabled || model.adNativeId.isBlank() || !internetConnected || !activity.isNetworkConnected()) return

    var loaded by remember { mutableStateOf(false) }
    val adRef = remember { arrayOfNulls<NativeAd>(1) }
    val disposed = remember { AtomicBoolean(false) }
    DisposableEffect(activity, model.adNativeId) {
        disposed.set(false)
        AdMobInitializer.initializeAdmob(activity) {
            if (disposed.get() || activity.isFinishing || activity.isDestroyed) return@initializeAdmob
            val request = NativeAdRequest.Builder(
                model.adNativeId,
                listOf(NativeAd.NativeAdType.NATIVE),
            ).build()
            NativeAdLoader.load(request, object : NativeAdLoaderCallback {
                override fun onNativeAdLoaded(nativeAd: NativeAd) {
                    if (disposed.get() || activity.isFinishing || activity.isDestroyed) {
                        nativeAd.destroy()
                        return
                    }
                    nativeAd.adEventCallback = object : NativeAdEventCallback {
                        override fun onAdImpression() { analytics.adShown("native", "history") }
                        override fun onAdClicked() { analytics.adClicked("native", "history") }
                    }
                    adRef[0] = nativeAd
                    analytics.adLoaded("native", "history")
                    activity.runOnUiThread { if (!disposed.get()) loaded = true else nativeAd.destroy() }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    analytics.adFailedToLoad("native", "history", adError.code.toString())
                }
            })
        }
        onDispose {
            disposed.set(true)
            adRef[0]?.destroy()
            adRef[0] = null
        }
    }

    if (loaded) {
        Box(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
                factory = { ctx -> createNativeAdView(ctx, adRef[0]) },
                update = { view -> adRef[0]?.let { populateNativeAdView(view, it) } },
                onRelease = { it.destroy() },
            )
        }
    }
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
    advertiser.visibility = if (ad.advertiser.isNullOrBlank()) View.GONE else View.VISIBLE
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
