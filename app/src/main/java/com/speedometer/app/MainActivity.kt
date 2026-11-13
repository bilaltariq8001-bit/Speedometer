package com.speedometer.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.settings.collect { settings ->
                    com.speedometer.app.core.common.OrientationLock.apply(this@MainActivity, settings.screenOrientation)
                }
            }
        }
        // Consent (UMP) first; ads load only once it allows them. Rotation recreates the activity, so ask only on a fresh start.
        if (savedInstanceState == null) {
            showGDPRScreen { _, canRequestAds -> ads.onConsentResolved(application, canRequestAds) }
        }
        enableEdgeToEdge()
        if (savedInstanceState == null || intent?.action == TripService.ACTION_OPEN_TRIP) handle(intent)
        setContent { SpeedometerTheme { AppNavHost() } }
    }

    override fun onResume() {
        super.onResume()
        AppVisibility.set(true)
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
        when (intent?.action) {
            AutoDetect.ACTION_START_TRIP -> StartRequest.request()
            TripService.ACTION_OPEN_TRIP -> {
                StartRequest.request()
                setIntent(Intent(intent).setAction(null))
            }
        }
    }
}
