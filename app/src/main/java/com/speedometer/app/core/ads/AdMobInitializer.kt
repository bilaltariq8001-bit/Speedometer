package com.speedometer.app.core.ads

import android.content.Context
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.speedometer.app.BuildConfig
import com.speedometer.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Starts the Google Mobile Ads SDK once, off the main thread, and queues callers until it is ready. */
object AdMobInitializer {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var attempted = false
    @Volatile private var ready = false

    /** True once the SDK has started (or failed to); ad requests made now go out at once. */
    val isReady: Boolean get() = ready
    private val pending = mutableListOf<() -> Unit>()

    fun initializeAdmob(context: Context, onResult: () -> Unit) {
        synchronized(this) {
            if (ready) { onResult(); return }
            pending.add(onResult)
            if (attempted) return
            attempted = true
        }
        val started = AdLog.now()
        AdLog.d("sdk", "MobileAds.initialize start")
        val appContext = context.applicationContext
        scope.launch {
            try {
                val testDeviceId = BuildConfig.ADMOB_TEST_DEVICE_ID
                if (BuildConfig.DEBUG && testDeviceId.isNotBlank()) {
                    MobileAds.setRequestConfiguration(
                        RequestConfiguration.Builder().setTestDeviceIds(listOf(testDeviceId)).build(),
                    )
                }
                val config = InitializationConfig.Builder(appContext.getString(R.string.admob_app_id)).build()
                MobileAds.initialize(appContext, config) {
                    AdLog.d("sdk", "MobileAds ready in ${AdLog.now() - started}ms")
                    release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "MobileAds initialization failed", e)
                release() // let callers carry on; their own checks stop a request that cannot be made
            }
        }
    }

    private fun release() {
        val callbacks = synchronized(this) {
            ready = true
            pending.toList().also { pending.clear() }
        }
        scope.launch(Dispatchers.Main) { callbacks.forEach { it() } }
    }

    private const val TAG = "AdMobInitializer"
}
