package com.speedometer.app.core.ads

/** One interstitial placement, read from Remote Config keys `<placement>_inter_*`. */
data class InterstitialAdModel(
    val adInterstitialId: String = "",
    val isAdEnabled: Boolean = false,
    val shouldLoadNextAd: Boolean = false,
    val isCappingEnabled: Boolean = false,
)

/** One banner slot on a screen, read from Remote Config keys `<slot>_*`. */
data class InScreenAdModel(
    val adBannerId: String = "",
    val isAdEnabled: Boolean = false,
    val isBannerCollapsing: Boolean = false,
    val isBannerTop: Boolean = false,
)

/** The app-open ad, read from `app_open_id` and `app_open_enabled`. */
data class AppOpenAdModel(
    val adAppOpenId: String = "",
    val isAdEnabled: Boolean = false,
)

/** A native ad placement, read from `<placement>_native_id` and `<placement>_native_enabled`. */
data class NativeAdModel(
    val adNativeId: String = "",
    val isAdEnabled: Boolean = false,
)
