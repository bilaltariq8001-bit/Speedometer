package com.speedometer.app.core.ads

import android.content.Context

/**
 * When the Start trip interstitial shows. Trips are counted per app session: the ad shows on the 4th trip, then the
 * 7th, 11th, 16th, 22nd... (the gap grows by one each time: 3, 4, 5, 6...). Quitting the app (Exit, or the app being
 * closed) starts the count again, so the next session shows it on its 4th trip again.
 *
 * The first ad trip is `start_trip_inter_after_trips` + 1 (Remote Config, default 3 → the 4th trip).
 * The all-time count is still kept in preferences for reference; it no longer decides the ad.
 */
object TripAdCounter {
    private const val PREFS = "ads"
    private const val KEY = "completed_trips"

    /** Trips completed (saved) in this app session. Lives in memory, so a closed app starts again at 0. */
    @Volatile private var sessionCompleted = 0
    /** The session trip number the ad was last shown for, so starting the same trip again does not show it twice. */
    @Volatile private var shownForTrip = 0

    fun completed(context: Context): Int = prefs(context).getInt(KEY, 0)

    fun onTripSaved(context: Context) {
        sessionCompleted++
        val p = prefs(context)
        p.edit().putInt(KEY, p.getInt(KEY, 0) + 1).apply()
    }

    /** The user quit the app: the next trips count from the start again. */
    fun resetSession() {
        sessionCompleted = 0
        shownForTrip = 0
    }

    /** Whether the trip about to start (this session's next trip) gets the Start trip ad. */
    @Suppress("UNUSED_PARAMETER")
    fun startAdDue(context: Context, startAdAfterTrips: Long): Boolean {
        val next = sessionCompleted + 1
        return next != shownForTrip && isAdTrip(next, (startAdAfterTrips + 1).toInt())
    }

    /** Call when the Start trip ad was shown for this session's next trip. */
    fun onStartAdShown() {
        shownForTrip = sessionCompleted + 1
    }

    /** True for first, first+3, first+3+4, first+3+4+5... (4, 7, 11, 16, 22 with first = 4). */
    internal fun isAdTrip(trip: Int, first: Int): Boolean {
        var k = first.coerceAtLeast(1)
        var gap = 3
        while (k < trip) { k += gap; gap++ }
        return k == trip
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
