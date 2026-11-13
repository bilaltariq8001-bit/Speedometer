package com.speedometer.app.core.analytics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The small facts the analytics keep between launches, in their own preferences file.
 *
 * `user_cohort` is `first_time` until the user saves their first trip, then `returning`. People who already had the app
 * before analytics existed (they finished onboarding, or have saved trips) start as `returning` (see [seedExistingUser]).
 * Reinstalling counts as a new first-time user, because these facts live on the phone.
 */
@Singleton
class AnalyticsStateStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("analytics_state", Context.MODE_PRIVATE)

    val cohort: String get() = prefs.getString(KEY_COHORT, FIRST_TIME) ?: FIRST_TIME

    val isSeeded: Boolean get() = prefs.getBoolean(KEY_SEEDED, false)

    /** Runs once, on the first launch with analytics: an existing user starts as returning. */
    fun seedExistingUser(isExistingUser: Boolean) {
        if (isSeeded) return
        prefs.edit().putBoolean(KEY_SEEDED, true).apply { if (isExistingUser) putString(KEY_COHORT, RETURNING) }.apply()
    }

    /** The first saved trip ends the first-time period. Returns true when the cohort changed. */
    fun onTripSaved(): Boolean {
        if (cohort == RETURNING) return false
        prefs.edit().putString(KEY_COHORT, RETURNING).apply()
        return true
    }

    var onboardingState: String
        get() = prefs.getString(KEY_ONBOARDING, "not_started") ?: "not_started"
        set(value) { prefs.edit().putString(KEY_ONBOARDING, value).apply() }

    companion object {
        const val FIRST_TIME = "first_time"
        const val RETURNING = "returning"
        private const val KEY_COHORT = "user_cohort"
        private const val KEY_SEEDED = "seeded"
        private const val KEY_ONBOARDING = "onboarding_state"
    }
}
