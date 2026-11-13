package com.speedometer.app.core.analytics

import javax.inject.Inject
import javax.inject.Singleton

/** Ad events for every format: [format] is interstitial, banner or app_open, [placement] is the Remote Config prefix. */
@Singleton
class AdAnalytics @Inject constructor(private val analytics: Analytics) {
    fun adLoaded(format: String, placement: String) =
        analytics.event("ads_loaded", "format" to format, "placement" to placement)

    fun adFailedToLoad(format: String, placement: String, errorCode: String) =
        analytics.event("ads_failed", "format" to format, "placement" to placement, "error_code" to errorCode)

    fun adShown(format: String, placement: String) =
        analytics.event("ads_shown", "format" to format, "placement" to placement)

    fun adClicked(format: String, placement: String) =
        analytics.event("ads_clicked", "format" to format, "placement" to placement)
}
