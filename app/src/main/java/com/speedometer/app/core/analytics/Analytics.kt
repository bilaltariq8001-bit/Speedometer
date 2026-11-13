package com.speedometer.app.core.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.speedometer.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only class that talks to Firebase Analytics. Every event goes through [AnalyticsContract] (unknown names are dropped),
 * carries `user_cohort`, `app_language` and `tracking_version`, and never carries location or personal data.
 * Debug builds print events in Logcat and send nothing. Without google-services.json nothing is sent either.
 */
@Singleton
class Analytics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val state: AnalyticsStateStore,
) {
    private val firebase: FirebaseAnalytics? = runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAnalytics.getInstance(context)
    }.getOrNull()

    private val language: String get() = Locale.getDefault().language.take(AnalyticsContract.MAX_PROPERTY_VALUE)

    /** Sends [name] with up to 22 parameters. Values are cut to 100 characters. */
    fun event(name: String, vararg params: Pair<String, Any?>) {
        if (!AnalyticsContract.validEvent(name)) {
            if (BuildConfig.DEBUG) Log.w(TAG, "dropped: '$name' is not in AnalyticsContract.events")
            return
        }
        val bundle = Bundle()
        params.take(AnalyticsContract.MAX_PARAMS).forEach { (key, value) ->
            if (key.length > AnalyticsContract.MAX_PARAM_NAME || value == null) return@forEach
            when (value) {
                is Number -> bundle.putLong(key, value.toLong())
                is Boolean -> bundle.putString(key, value.toString())
                else -> bundle.putString(key, value.toString().take(AnalyticsContract.MAX_PARAM_VALUE))
            }
        }
        bundle.putString("user_cohort", state.cohort)
        bundle.putString("app_language", language)
        bundle.putString("tracking_version", TRACKING_VERSION)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "event $name ${params.joinToString { "${it.first}=${it.second}" }} cohort=${state.cohort}")
            return
        }
        firebase?.logEvent(name, bundle)
    }

    fun setUserProperty(name: String, value: String) {
        if (!AnalyticsContract.validProperty(name, value)) return
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "user property $name=$value")
            return
        }
        firebase?.setUserProperty(name, value)
    }

    /** Pushes the user properties that describe the user right now. */
    fun refreshUserProperties() {
        setUserProperty("user_cohort", state.cohort)
        setUserProperty("onboarding_state", state.onboardingState)
        setUserProperty("app_language", language)
    }

    fun screenEnter(screen: String) = event("screen_enter", "screen" to screen)

    /** Starts the app foreground/background events. Called once from the Application class. */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = event("app_foregrounded")
            override fun onStop(owner: LifecycleOwner) = event("app_backgrounded")
        })
    }

    /** First launch with analytics: an existing user (they finished onboarding before) starts as returning. */
    fun seed(isExistingUser: Boolean) {
        state.seedExistingUser(isExistingUser)
        refreshUserProperties()
    }

    fun onboardingStep(step: Int) {
        if (step == 1) {
            state.onboardingState = "in_progress"
            event("onboarding_started")
            refreshUserProperties()
        }
        event("onboarding_step_viewed", "step" to step)
    }

    fun onboardingSkipped(step: Int) = event("onboarding_skipped", "step" to step)

    fun onboardingCompleted() {
        state.onboardingState = "completed"
        event("onboarding_completed")
        refreshUserProperties()
    }

    /** The first saved trip makes the user a returning user. */
    fun tripSaved(durationBucket: String) {
        event("trip_save_success", "duration_bucket" to durationBucket)
        if (state.onTripSaved()) refreshUserProperties()
    }

    private companion object {
        const val TAG = "Analytics"
        const val TRACKING_VERSION = "1"
    }
}
