package com.speedometer.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.speedometer.app.core.ads.showGDPRScreen
import com.speedometer.app.core.autodetect.AutoDetect
import com.speedometer.app.core.autodetect.StartRequest
import com.speedometer.app.service.AppVisibility
import com.speedometer.app.service.TripService
import com.speedometer.app.core.data.AppLanguage
import com.speedometer.app.core.designsystem.SpeedometerTheme
import com.speedometer.app.core.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: com.speedometer.app.core.data.SettingsRepository
    @Inject lateinit var ads: com.speedometer.app.core.ads.AdsController
    @Inject lateinit var guardAlerts: com.speedometer.app.core.alerts.GuardAlerts

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val freshStart = savedInstanceState == null
        // A new start of the app (after it was closed) always opens in portrait, even if it was left in landscape.
        // A rebuild (rotation, language change, restore) keeps the current choice.
        if (freshStart) com.speedometer.app.core.common.OrientationLock.apply(this, com.speedometer.app.core.data.ScreenOrientation.Portrait)
        lifecycleScope.launch {
            if (freshStart) runCatching { settingsRepository.setScreenOrientation(com.speedometer.app.core.data.ScreenOrientation.Portrait) }
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.settings.collect { settings ->
                    // While a full-screen ad is up it decides the screen's turn; fighting it rebuilt the activity again and
                    // again behind the ad. The saved choice is applied once the app is back in front (onResume).
                    if (!com.speedometer.app.core.ads.AdGate.fullScreenAdVisible) {
                        com.speedometer.app.core.common.OrientationLock.apply(this@MainActivity, settings.screenOrientation)
                    } else {
                        com.speedometer.app.core.common.OrientationLock.remember(settings.screenOrientation)
                    }
                    com.speedometer.app.core.common.OrientationLock.markKnown()
                }
            }
        }
        // Consent (UMP) first; ads load only once it allows them. Rotation recreates the activity, so ask only on a fresh start.
        if (savedInstanceState == null) {
            showGDPRScreen { _, canRequestAds -> ads.onConsentResolved(application, canRequestAds) }
        }
        enableEdgeToEdge()
        if (savedInstanceState == null || intent?.action == TripService.ACTION_OPEN_TRIP) handle(intent)
        setContent {
            // A live language change (AppLanguage.applyNow) updates the resources in place and bumps the revision: a fresh
            // configuration makes every string redraw in the new language, and the layout direction follows it (RTL).
            val revision by AppLanguage.revision
            val systemConfig = androidx.compose.ui.platform.LocalConfiguration.current
            val config = androidx.compose.runtime.remember(revision, systemConfig) {
                android.content.res.Configuration(resources.configuration)
            }
            val direction = if (config.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) {
                androidx.compose.ui.unit.LayoutDirection.Rtl
            } else {
                androidx.compose.ui.unit.LayoutDirection.Ltr
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides config,
                androidx.compose.ui.platform.LocalLayoutDirection provides direction,
            ) {
                SpeedometerTheme { AppNavHost() }
            }
        }
    }

    /** Any touch counts as "the user is using the app": a late app-open ad never jumps in after it. */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) com.speedometer.app.core.ads.AdGate.markUserInteraction()
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        AppVisibility.set(true)
        com.speedometer.app.core.common.OrientationLock.reapply(this)
    }

    /** Home or Recents pressed: if the over-speed alarm is ringing, the trip pauses right away. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        guardAlerts.pauseIfAlarmRinging()
    }

    override fun onStop() {
        super.onStop()
        // Minimised any other way (another app opened over it, screen off). Not for a rotation or a full-screen ad.
        if (!isChangingConfigurations && !com.speedometer.app.core.ads.AdGate.fullScreenAdVisible) guardAlerts.pauseIfAlarmRinging()
    }

    override fun onPause() {
        AppVisibility.set(false)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** Start trip on the auto-detect notice opens the app with this action. */
    private fun handle(intent: Intent?) {
        when (intent?.action)
        {
            AutoDetect.ACTION_START_TRIP -> StartRequest.request()
            TripService.ACTION_OPEN_TRIP -> {
                StartRequest.request()
                setIntent(Intent(intent).setAction(null))
            }
        }
    }
}
