package com.speedometer.app.core.trip

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Process-wide owner of the engine, so the trip service and the UI share one trip. */
@Singleton
class TripRepository @Inject constructor() {
    private val engine = TripEngine()
    val state: StateFlow<TripState> get() = engine.state

    /** Wall-clock start of the current or last trip. */
    var startedAtMs: Long = 0L
        private set

    fun start(limitKmh: Float? = null) {
        engine.limitKmh = limitKmh
        startedAtMs = System.currentTimeMillis()
        engine.start()
    }

    fun route(): List<RoutePoint> = engine.route()

    /** The route to save: the recorded GPS points, plus the first and last known positions when the GPS route lacks them, so a short trip still has a line to draw (at least two points when any position is known). */
    /** The vehicle chosen when this trip started (a Vehicle name); it is saved with the trip and cannot change during it. */
    @Volatile var vehicle: String = "Car"

    fun routeForSave(): List<RoutePoint> {
        val route = engine.route().toMutableList()
        val first = startPoint()
        val last = endPoint()
        // A known position is added only when it is a little away from the GPS route but not so far that it is a stale position.
        fun joins(a: RoutePoint, b: RoutePoint) = GeoMath.distanceM(a.lat, a.lon, b.lat, b.lon).let { it > 5.0 && it < 300.0 }
        if (first != null && (route.isEmpty() || joins(first, route.first()))) route.add(0, first)
        if (last != null && (route.isEmpty() || joins(last, route.last()))) route.add(last)
        if (route.size == 1) route.add(route.first().copy(tSec = route.first().tSec + 1))
        return route
    }

    /** Sets the current limit (a saved zone, later the map limit); no-op when unchanged. */
    fun setLimit(limitKmh: Float?, toleranceKmh: Float = 5f, userSet: Boolean = false) {
        engine.limitToleranceKmh = toleranceKmh
        engine.limitIsUserSet = userSet
        if (engine.limitKmh != limitKmh) engine.limitKmh = limitKmh
    }

    /** When the trip was last paused by the user, for the "Paused for" readout and the 30 minute limit. */
    var pausedAtMs: Long = 0L
        private set

    fun pause() {
        pausedAtMs = System.currentTimeMillis()
        engine.pause()
    }
    fun resume() = engine.resume()
    fun finish() = engine.finish()
    fun reset() = engine.reset()
    fun onSample(sample: LocationSample) = engine.onSample(sample)
    fun onNoFix(seconds: Int) = engine.onNoFix(seconds)
    fun onApproximate(accuracyM: Float, lat: Double = Double.NaN, lon: Double = Double.NaN) = engine.onApproximate(accuracyM, lat, lon)

    /** Where this trip began and where it is now, from any location source; null before the first position. */
    fun startPoint(): RoutePoint? = engine.firstKnown ?: engine.route().firstOrNull()
    fun endPoint(): RoutePoint? = engine.lastKnown ?: engine.route().lastOrNull()
    fun tick(seconds: Double) = engine.tick(seconds)
    fun seedKnown(lat: Double, lon: Double) = engine.seedKnown(lat, lon)
}
