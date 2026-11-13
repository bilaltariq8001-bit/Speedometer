package com.speedometer.app

import android.app.Application
import com.speedometer.app.core.alerts.GuardAlerts
import com.speedometer.app.core.autodetect.AutoDetect
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SpeedometerApp : Application() {
    @Inject lateinit var guardAlerts: GuardAlerts
    @Inject lateinit var autoDetect: AutoDetect
    @Inject lateinit var ads: com.speedometer.app.core.ads.AdsController
    @Inject lateinit var analytics: com.speedometer.app.core.analytics.Analytics

    override fun onCreate() {
        super.onCreate()
        // Alerts watch the live trip for the whole process, so they work with or without the trip service.
        // Load the map's native library now, on a background thread, so the first map screen does not stall the UI thread for it.
        Thread { runCatching { System.loadLibrary("maplibre") } }.apply { priority = Thread.MIN_PRIORITY }.start()
        guardAlerts.start()
        autoDetect.start()
        analytics.start()
        ads.init(this)
    }
}
