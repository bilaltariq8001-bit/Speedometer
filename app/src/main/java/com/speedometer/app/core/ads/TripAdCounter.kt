package com.speedometer.app.core.ads

import android.content.Context

/**
 * Counts trips the user has completed (saved), for the Start trip interstitial: once [startAdAfterTrips] trips are done,
 * every Start trip tap shows an interstitial first. Kept in its own preference so deleting trips does not reset it.
 */
object TripAdCounter {
    private const val PREFS = "ads"
    private const val KEY = "completed_trips"

    fun completed(context: Context): Int = prefs(context).getInt(KEY, 0)

    fun onTripSaved(context: Context) {
        val p = prefs(context)
        p.edit().putInt(KEY, p.getInt(KEY, 0) + 1).apply()
    }

    /** True from the 4th trip on (after 3 completed trips), or after the Remote Config count `start_trip_inter_after_trips`. */
    fun startAdDue(context: Context, startAdAfterTrips: Long): Boolean = completed(context) >= startAdAfterTrips

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
