package com.speedometer.app.core.trip

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.speedometer.app.BuildConfig

enum class TripPhase { Idle, Recording, Paused, Finished }

/** Where the position comes from: nothing yet, an approximate fix (Wi-Fi and mobile network), or the GPS. */
enum class LocationSource { None, Approximate, Gps }

/** One GPS reading. [timeMs] is the fix time, so the engine needs no clock of its own. */
data class LocationSample(
    val speedMps: Float,
    val accuracyM: Float?,
    val timeMs: Long,
    val lat: Double = Double.NaN,
    val lon: Double = Double.NaN,
    /** Direction of travel in degrees, or NaN when unknown. */
    val bearingDeg: Float = Float.NaN,
)

data class TripState(
    val phase: TripPhase = TripPhase.Idle,
    val speedKmh: Float = 0f,
    val distanceKm: Float = 0f,
    val durationSec: Int = 0,
    val maxKmh: Float = 0f,
    val avgKmh: Float = 0f,
    val score: Int = 100,
    val limitKmh: Float? = null,
    val guard: GuardLevel = GuardLevel.Off,
    val gps: GpsQuality = GpsQuality.Strong,
    val accelMs2: Float = 0f,
    val hardBrakes: Int = 0,
    val secondsOver: Int = 0,
    val accuracyM: Float? = null,
    /** Sideways acceleration from the change of heading, m/s2; positive turns right. */
    val lateralMs2: Float = 0f,
    /** Strongest combined sideways and fore-aft force this trip, in g. */
    val peakG: Float = 0f,
    /** Number of recorded route points; changes whenever the route grows. */
    val routeSize: Int = 0,
    /** Highest Limit Guard level reached, as [GuardLevel.ordinal]. */
    val peakGuard: Int = 0,
    /** True when the engine paused itself because the fix was lost; it resumes on the next fix. */
    val autoPaused: Boolean = false,
    /** True from the start of a trip until the first location arrives (a cold GPS start can take half a minute). */
    val searching: Boolean = true,
    /** Where the latest position came from. Speed and distance are only ever measured from [LocationSource.Gps]. */
    val locationSource: LocationSource = LocationSource.None,
    /** Accuracy in metres of the latest approximate (Wi-Fi or mobile network) fix, or null when there is none lately. */
    val approxAccuracyM: Float? = null,
) {
    val tooShortToSave: Boolean get() = TripRules.isTooShortToSave(durationSec)
}

/**
 * Trip state machine: Idle, Recording, Paused, Finished. Pure Kotlin; fed by [onSample] and [onNoFix].
 * Rules: reference/build-plan.md "Trip engine".
 */
class TripEngine {
    private val _state = MutableStateFlow(TripState())
    val state: StateFlow<TripState> = _state.asStateFlow()

    private val smoother = SpeedSmoother()
    private var lastTimeMs: Long? = null
    private var lastSmoothKmh = 0f
    /** The average speed as shown: eased towards the true value, slowly while the GPS is weak so a noisy fix cannot jerk it. */
    private var shownAvgKmh = 0f
    private var lastBearing = Float.NaN
    private var braking = false
    private var overSeconds = 0f
    private var distanceM = 0.0
    private var durationS = 0.0
    private val routePoints = mutableListOf<RoutePoint>()
    private var lastRouteSec = -ROUTE_EVERY_SEC
    private var approxIdleSec = 0

    /** Snapshot of the route recorded so far. */
    fun route(): List<RoutePoint> = synchronized(routePoints) { routePoints.toList() }

    /** The first and the latest position of this trip from any source (GPS, or Wi-Fi and mobile network), for the map and From / To. */
    @Volatile var firstKnown: RoutePoint? = null
        private set
    @Volatile var lastKnown: RoutePoint? = null
        private set

    private fun noteKnown(lat: Double, lon: Double, speedKmh: Float = 0f) {
        if (lat.isNaN() || lon.isNaN()) return
        val p = RoutePoint(lat, lon, speedKmh, durationS.toInt())
        if (firstKnown == null) firstKnown = p
        lastKnown = p
    }

    var limitKmh: Float? = null
        set(value) {
            field = value
            _state.update { it.copy(limitKmh = value) }
        }

    /** How far over the limit the alarm starts: 5 for a saved zone, the user's own choice for their speed limit. */
    var limitToleranceKmh: Float = 5f

    /** True when the limit is the one the user set in Settings: the alarm then also works while the fix is only weak. */
    var limitIsUserSet: Boolean = false

    fun start() {
        smoother.reset()
        approxIdleSec = 0
        lastTimeMs = null
        lastSmoothKmh = 0f
        shownAvgKmh = 0f
        lastBearing = Float.NaN
        braking = false
        overSeconds = 0f
        distanceM = 0.0
        durationS = 0.0
        synchronized(routePoints) { routePoints.clear() }
        firstKnown = null
        lastKnown = null
        lastRouteSec = -ROUTE_EVERY_SEC
        _state.value = TripState(phase = TripPhase.Recording, limitKmh = limitKmh)
    }

    /** True once the vehicle has been seen standing still since the user paused, so a pause made while driving is not undone by the next fix. */
    private var stoodStillWhilePaused = false

    fun pause() {
        stoodStillWhilePaused = false
        _state.update { if (it.phase == TripPhase.Recording) it.copy(phase = TripPhase.Paused, autoPaused = false) else it }
    }

    fun resume() {
        lastTimeMs = null
        _state.update {
            if (it.phase == TripPhase.Paused) it.copy(phase = TripPhase.Recording, autoPaused = false) else it
        }
    }

    fun finish() = _state.update {
        // The saved trip gets the exact average, not the eased one.
        if (it.phase == TripPhase.Idle) it else it.copy(phase = TripPhase.Finished, speedKmh = 0f, guard = GuardLevel.Off, avgKmh = rawAvgKmh())
    }

    private fun rawAvgKmh(): Float = if (durationS > 0) ((distanceM / 1000.0) / (durationS / 3600.0)).toFloat() else 0f

    /** Called once a second. Strong GPS follows the true average closely; weak GPS moves it slowly; no GPS holds it still. */
    private fun easedAvg(quality: GpsQuality): Float {
        val raw = rawAvgKmh()
        val step = when (quality) { GpsQuality.Strong -> 0.3f; GpsQuality.Weak -> 0.06f; GpsQuality.Lost -> 0f }
        shownAvgKmh = if (shownAvgKmh <= 0f) raw else shownAvgKmh + step * (raw - shownAvgKmh)
        return shownAvgKmh
    }

    fun reset() {
        synchronized(routePoints) { routePoints.clear() }
        firstKnown = null
        lastKnown = null
        _state.value = TripState(limitKmh = limitKmh)
    }

    /**
     * No fix for [secondsSinceFix] seconds. At 10 s the GPS counts as lost and the trip keeps recording. A short
     * dropout (a tunnel, a car park, a blocked sky) is not the car stopping, so the last speed stays on screen for up
     * to [LOST_HOLD_SECONDS] and the distance keeps growing at that speed; after that the speed reads 0. When a fix
     * comes back, the next reading takes over.
     */
    fun onNoFix(secondsSinceFix: Int) {
        if (BuildConfig.DEBUG) {
            runCatching { android.util.Log.d("TripEngineDiag", "onNoFix called secondsSinceFix=$secondsSinceFix phase=${_state.value.phase} searching=${_state.value.searching}") }
        }
        // An approximate fix that stops arriving is dropped after a while, so the screen never shows an old position as current.
        if (_state.value.approxAccuracyM != null && ++approxIdleSec > APPROX_STALE_SECONDS) {
            _state.update { it.copy(approxAccuracyM = null, locationSource = if (it.searching) LocationSource.None else it.locationSource) }
        }
        val s = _state.value
        // Before the first fix the GPS is still starting up, not lost, so nothing is held or estimated.
        if (s.phase != TripPhase.Recording || s.searching) return
        val q = GpsQualityRules.classify(null, secondsSinceFix)
        val holding = secondsSinceFix in 1..LOST_HOLD_SECONDS && lastSmoothKmh > 0f
        if (holding) distanceM += lastSmoothKmh / 3.6 // about one call a second
        _state.update {
            val km = (distanceM / 1000.0).toFloat()
            when {
                q == GpsQuality.Lost -> it.copy(gps = q, guard = GuardLevel.Off, speedKmh = if (holding) lastSmoothKmh else 0f, distanceKm = km)
                holding -> it.copy(gps = q, speedKmh = lastSmoothKmh, distanceKm = km)
                else -> it.copy(gps = q)
            }
        }
    }


    /**
     * A position that is not GPS grade: a Wi-Fi or mobile network fix, or a fused fix without a real speed. It tells the
     * screen that the phone knows roughly where it is, but it never moves the speed, the distance or the route: those
     * are measured from GPS only, because network positions jump around and would invent speed.
     */
    fun onApproximate(accuracyM: Float, lat: Double = Double.NaN, lon: Double = Double.NaN) {
        if (_state.value.phase != TripPhase.Recording) return
        noteKnown(lat, lon)
        approxIdleSec = 0
        _state.update {
            // Once the GPS is delivering, an approximate fix adds nothing; it only matters while the GPS has nothing.
            if (it.locationSource == LocationSource.Gps && it.gps != GpsQuality.Lost) it
            else it.copy(approxAccuracyM = accuracyM, locationSource = LocationSource.Approximate)
        }
    }

    /** Wall-clock time: called about once a second by the service, so the timer counts every second even without a GPS fix. */
    /** Gives a trip that has no position yet a starting point (the phone's last known location). */
    fun seedKnown(lat: Double, lon: Double) {
        if (_state.value.phase == TripPhase.Recording && firstKnown == null) noteKnown(lat, lon)
    }

    fun tick(seconds: Double) {
        if (seconds <= 0 || _state.value.phase != TripPhase.Recording) return
        durationS += seconds
        _state.update {
            it.copy(
                durationSec = durationS.toInt(),
                avgKmh = if (it.searching || durationS <= 0) it.avgKmh else easedAvg(it.gps),
            )
        }
    }

    fun onSample(sample: LocationSample) {
        if (BuildConfig.DEBUG) {
            runCatching { android.util.Log.d("TripEngineDiag", "onSample entered speedMps=${sample.speedMps} accuracyM=${sample.accuracyM} timeMs=${sample.timeMs}") }
        }
        val before = _state.value
        if (before.phase == TripPhase.Paused && before.autoPaused) {
            lastTimeMs = null
            _state.value = before.copy(phase = TripPhase.Recording, autoPaused = false)
        } else if (before.phase == TripPhase.Paused) {
            if (sample.speedMps * 3.6f < STANDSTILL_KMH) stoodStillWhilePaused = true
        }
        if (_state.value.phase == TripPhase.Paused && stoodStillWhilePaused && sample.speedMps * 3.6f >= AUTO_RESUME_KMH) {
            // Paused by the user, then the vehicle starts moving: recording continues by itself (design: Trip paused).
            resume()
        }
        if (_state.value.phase != TripPhase.Recording) return

        val gps = GpsQualityRules.classify(sample.accuracyM, 0)
        val dt = lastTimeMs?.let { (sample.timeMs - it) / 1000.0 } ?: 0.0
        lastTimeMs = sample.timeMs
        val smooth = smoother.next(SpeedFilter.toKmh(sample.speedMps), dt, sample.accuracyM)
        val validStep = dt > 0 && dt <= MAX_STEP_SEC

        var accel = 0f
        var lateral = 0f
        var newBrake = false
        if (validStep) {
            val mps = smooth / 3.6
            if (mps < MAX_SANE_MPS) distanceM += mps * dt
            accel = (smooth - lastSmoothKmh) / 3.6f / dt.toFloat()
            if (!sample.bearingDeg.isNaN() && !lastBearing.isNaN() && smooth > LATERAL_MIN_KMH) {
                val turn = Math.toRadians(headingChange(lastBearing, sample.bearingDeg).toDouble()).toFloat()
                lateral = ((smooth / 3.6f) * turn / dt.toFloat()).coerceIn(-MAX_LATERAL_MS2, MAX_LATERAL_MS2)
            }
            if (accel <= -HARD_BRAKE_MS2 && lastSmoothKmh > Score.HARD_BRAKE_MIN_KMH) {
                if (!braking) {
                    braking = true
                    newBrake = true
                }
            } else if (accel > -HARD_BRAKE_MS2 / 2) braking = false
        }
        lastSmoothKmh = smooth
        lastBearing = sample.bearingDeg

        noteKnown(sample.lat, sample.lon, smooth)
        val limit = limitKmh
        val guard = LimitGuard.level(
            smooth, limit, gps, limitToleranceKmh, requireStrongGps = !limitIsUserSet, wasActive = _state.value.guard != GuardLevel.Off,
        )
        if (validStep && guard != GuardLevel.Off) overSeconds += dt.toFloat()

        val sec = durationS.toInt()
        if (!sample.lat.isNaN() && !sample.lon.isNaN() && sec - lastRouteSec >= ROUTE_EVERY_SEC) {
            lastRouteSec = sec
            synchronized(routePoints) { routePoints.add(RoutePoint(sample.lat, sample.lon, smooth, sec)) }
        }

        _state.update {
            val brakes = it.hardBrakes + if (newBrake) 1 else 0
            it.copy(
                accuracyM = sample.accuracyM, routeSize = synchronized(routePoints) { routePoints.size }, searching = false,
                locationSource = LocationSource.Gps, approxAccuracyM = null,
                speedKmh = smooth, gps = gps, guard = guard, accelMs2 = accel, hardBrakes = brakes,
                distanceKm = (distanceM / 1000.0).toFloat(), maxKmh = maxOf(it.maxKmh, smooth),
                secondsOver = overSeconds.toInt(), peakGuard = maxOf(it.peakGuard, guard.ordinal),
                lateralMs2 = lateral,
                peakG = maxOf(it.peakG, kotlin.math.hypot(lateral, accel) / GRAVITY_MS2),
                score = Score.compute(brakes, overSeconds.toInt()),
            )
        }
    }

    private companion object {
        const val AUTO_RESUME_KMH = 8f
        const val STANDSTILL_KMH = 3f
        const val ROUTE_EVERY_SEC = 1
        const val LATERAL_MIN_KMH = 5f
        const val MAX_LATERAL_MS2 = 15f
        const val GRAVITY_MS2 = 9.81f
        const val LOST_HOLD_SECONDS = 60
        const val APPROX_STALE_SECONDS = 20
        const val MAX_STEP_SEC = 30.0
        const val MAX_SANE_MPS = 90.0
        const val HARD_BRAKE_MS2 = 3.0f
    }
}

/** Signed change of heading from [from] to [to] in degrees, in -180..180; positive is a right turn. */
fun headingChange(from: Float, to: Float): Float {
    var d = (to - from) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}
