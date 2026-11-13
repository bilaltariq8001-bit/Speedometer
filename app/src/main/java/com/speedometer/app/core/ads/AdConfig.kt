package com.speedometer.app.core.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.speedometer.app.R
import com.speedometer.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every ad setting, read from Firebase Remote Config. The in-app defaults are in res/xml/remote_config_defaults.xml
 * and are used before the first fetch, offline, and when Firebase is not set up (no google-services.json).
 * Keys follow one lowercase schema: `<placement>_inter_*`, `<slot>_in_screen_ad_*`, `app_open_*` and four global ones.
 */
@Singleton
class AdConfig @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("ads", Context.MODE_PRIVATE)

    private val defaults: Map<String, String> by lazy { readDefaults() }

    private val remote: FirebaseRemoteConfig? = runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) null
        else FirebaseRemoteConfig.getInstance().also { rc ->
            rc.setConfigSettingsAsync(FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(FETCH_INTERVAL_S).build())
            rc.setDefaultsAsync(R.xml.remote_config_defaults)
        }
    }.getOrNull()

    // ---------------------------------------------------------------- consent

    /** True once UMP says ads may be requested. Remembered, and checked before every ad request of every type. */
    fun canRequestAds(): Boolean =
        prefs.getBoolean(KEY_CAN_REQUEST_ADS, false) ||
            runCatching { UserMessagingPlatform.getConsentInformation(context).canRequestAds() }.getOrDefault(false)

    fun setCanRequestAds(value: Boolean) {
        prefs.edit().putBoolean(KEY_CAN_REQUEST_ADS, value).apply()
    }

    // ---------------------------------------------------------------- fetching

    /** Fetches and activates the latest values, then calls [onDone] once (also on failure, or after 10 seconds). */
    fun fetch(onDone: () -> Unit) {
        val rc = remote ?: return onDone()
        var done = false
        val main = Handler(Looper.getMainLooper())
        val finish = { if (!done) { done = true; onDone() } }
        main.postDelayed(finish, FETCH_TIMEOUT_MS)
        runCatching { rc.fetchAndActivate().addOnCompleteListener { main.post(finish) } }.onFailure { finish() }
    }

    // ---------------------------------------------------------------- raw values

    private fun string(key: String): String = (remote?.getString(key) ?: defaults[key]).orEmpty()
    private fun boolean(key: String): Boolean = string(key).trim().lowercase().let { it == "true" || it == "1" }
    private fun long(key: String): Long = string(key).trim().toLongOrNull() ?: 0L

    // ---------------------------------------------------------------- global capping keys

    val interstitialCacheSize: Int get() = long("interstitial_cache_size").coerceIn(1L, 5L).toInt()
    val isInterstitialTimeCappingEnabled: Boolean get() = boolean("is_interstitial_time_capping_enabled")
    val interstitialCooldownSec: Long get() = long("interstitial_cooldown_seconds")
    val interstitialClickCap: Long get() = long("interstitial_click_cap")

    // ---------------------------------------------------------------- placements

    fun interstitial(prefix: String) = if (BuildConfig.DEBUG) {
        InterstitialAdModel(
            adInterstitialId = TEST_INTERSTITIAL_AD_UNIT_ID,
            isAdEnabled = true,
            shouldLoadNextAd = true,
            isCappingEnabled = true,
        )
    } else {
        InterstitialAdModel(
            adInterstitialId = string("${prefix}_inter_id"),
            isAdEnabled = boolean("${prefix}_inter_enabled"),
            shouldLoadNextAd = boolean("${prefix}_inter_should_load"),
            isCappingEnabled = boolean("${prefix}_inter_counter_enabled"),
        )
    }

    fun inScreen(prefix: String) = if (BuildConfig.DEBUG) {
        InScreenAdModel(adBannerId = TEST_BANNER_AD_UNIT_ID, isAdEnabled = true)
    } else {
        InScreenAdModel(
            adBannerId = string("${prefix}_banner_ad_id"),
            isAdEnabled = boolean("${prefix}_in_screen_ad_enabled"),
            isBannerCollapsing = boolean("${prefix}_banner_is_collapsing"),
            isBannerTop = string("${prefix}_ad_position").trim().equals("TOP", ignoreCase = true),
        )
    }

    fun appOpen() = if (BuildConfig.DEBUG) {
        AppOpenAdModel(adAppOpenId = TEST_APP_OPEN_AD_UNIT_ID, isAdEnabled = true)
    } else {
        AppOpenAdModel(adAppOpenId = string("app_open_id"), isAdEnabled = boolean("app_open_enabled"))
    }

    fun native(prefix: String) = if (BuildConfig.DEBUG) {
        NativeAdModel(adNativeId = TEST_NATIVE_AD_UNIT_ID, isAdEnabled = true)
    } else {
        NativeAdModel(adNativeId = string("${prefix}_native_id"), isAdEnabled = boolean("${prefix}_native_enabled"))
    }

    // ---------------------------------------------------------------- defaults file

    /** Reads res/xml/remote_config_defaults.xml, for when Firebase is not available. */
    private fun readDefaults(): Map<String, String> {
        val map = HashMap<String, String>()
        runCatching {
            val parser = context.resources.getXml(R.xml.remote_config_defaults)
            var key: String? = null
            var event = parser.eventType
            var tag = ""
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (event) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> tag = parser.name
                    org.xmlpull.v1.XmlPullParser.TEXT -> when (tag) {
                        "key" -> key = parser.text.trim()
                        "value" -> key?.let { map[it] = parser.text.trim() }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> tag = ""
                }
                event = parser.next()
            }
        }
        return map
    }

    private companion object {
        const val TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
        const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
        const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
        const val KEY_CAN_REQUEST_ADS = "can_request_ads"
        const val FETCH_INTERVAL_S = 3600L
        const val FETCH_TIMEOUT_MS = 5_000L
    }
}
