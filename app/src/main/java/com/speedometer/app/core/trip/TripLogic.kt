package com.speedometer.app.core.trip

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Rules from reference/build-plan.md and design-spec.md "Behaviour rules". */

enum class GpsQuality { Strong, Weak, Lost }

object GpsQualityRules {
    const val LOST_AFTER_SECONDS = 10
    const val STRONG_BELOW_M = 20f

    /** Phones report 5 to 20 m outdoors, so strong is under 20 m; anything worse is weak, and no fix for 10 s is lost. */
    fun classify(accuracyMeters: Float?, secondsSinceFix: Int): GpsQuality = when {
        secondsSinceFix >= LOST_AFTER_SECONDS -> GpsQuality.Lost
        accuracyMeters == null -> GpsQuality.Weak
        accuracyMeters < STRONG_BELOW_M -> GpsQuality.Strong
        else -> GpsQuality.Weak
    }
}

object Score {
    const val HARD_BRAKE_PENALTY = 6.0
    const val OVER_LIMIT_PENALTY_PER_SEC = 0.7
    const val HARD_BRAKE_MIN_KMH = 45

    /** 100 - 6 per hard brake - 0.7 per second over (limit + 5), clamped 0..100. */
    fun compute(hardBrakes: Int, secondsOverLimitPlus5: Int): Int {
        val raw = 100.0 - HARD_BRAKE_PENALTY * hardBrakes - OVER_LIMIT_PENALTY_PER_SEC * secondsOverLimitPlus5
        return raw.coerceIn(0.0, 100.0).toInt()
    }
}

enum class GuardLevel { Off, Soft, Firm, Spoken }

object LimitGuard {
    /**
     * Level 1 at +5, 2 at +10, 3 at +20 over a saved zone's limit (the default [toleranceKmh] of 5). Off when the
     * GPS is lost or no limit is known, and, with [requireStrongGps], also when it is only weak.
     *
     * The speed is compared as it is shown on the gauge (rounded to a whole km/h), so the alarm never starts while the
     * gauge still reads the limit. With a tolerance of 0 (the alarm starts above the limit) level 1 is the first km/h
     * over the limit, level 2 is 5 over and level 3 is 15 over. [wasActive] lowers the level-1 threshold by 2 km/h, so
     * a speed hovering around the limit does not switch the alarm off and on again.
     */
    fun level(
        speedKmh: Float, limitKmh: Float?, gps: GpsQuality, toleranceKmh: Float = 5f,
        requireStrongGps: Boolean = true, wasActive: Boolean = false,
    ): GuardLevel {
        if (limitKmh == null || gps == GpsQuality.Lost) return GuardLevel.Off
        if (requireStrongGps && gps != GpsQuality.Strong) return GuardLevel.Off
        val over = speedKmh.roundToInt() - limitKmh.roundToInt()
        val soft = if (toleranceKmh <= 0f) 1 else toleranceKmh.roundToInt()
        val start = if (wasActive) soft - HYSTERESIS_KMH else soft
        return when {
            over >= soft + 15 -> GuardLevel.Spoken
            over >= soft + 5 -> GuardLevel.Firm
            over >= start -> GuardLevel.Soft
            else -> GuardLevel.Off
        }
    }

    private const val HYSTERESIS_KMH = 2
}

/** The four Smooth score parts shown on Results. Acceleration and cornering need sensor data not read yet. */
object ScoreParts {
    fun braking(hardBrakes: Int): Int = (100.0 - Score.HARD_BRAKE_PENALTY * hardBrakes).coerceIn(0.0, 100.0).toInt()
    fun limitCompliance(secondsOver: Int): Int =
        (100.0 - Score.OVER_LIMIT_PENALTY_PER_SEC * secondsOver).coerceIn(0.0, 100.0).toInt()
}

object TripRules {
    const val MIN_SAVE_SECONDS = 8
    fun isTooShortToSave(durationSeconds: Int) = durationSeconds < MIN_SAVE_SECONDS
}

object SpeedFilter {
    const val STOPPED_BELOW_KMH = 1f
    fun toKmh(metersPerSecond: Float): Float {
        val kmh = metersPerSecond * 3.6f
        return if (kmh < STOPPED_BELOW_KMH) 0f else kmh
    }

    /** Short moving average over the most recent [window] samples. */
    fun smooth(samples: List<Float>, window: Int = 3): Float {
        if (samples.isEmpty()) return 0f
        val tail = samples.takeLast(max(1, min(window, samples.size)))
        return tail.sum() / tail.size
    }
}

/**
 * Turns the raw speed of each GPS fix into a steady reading. Raw GPS speed jitters by a few km/h from one fix to the
 * next, which made the gauge number flicker. This keeps it responsive but calm:
 *  - a jump larger than any car can do in the time between two fixes is cut down (a spike),
 *  - the new reading is blended with the old one, trusting it less when the fix is poor,
 *  - changes under about 0.5 km/h (1% of the speed) are ignored, and a near-standstill reading snaps to 0 instead of creeping.
 */
class SpeedSmoother {
    var value = 0f
        private set
    private var started = false
    /** How many fixes in a row were far off the shown speed in the same direction (a real speed change, not noise). */
    private var trend = 0
    private var trendSign = 0

    fun reset() { value = 0f; started = false; trend = 0; trendSign = 0 }

    fun next(rawKmh: Float, dtSec: Double, accuracyM: Float?): Float {
        if (!started) { started = true; value = rawKmh; return value }
        // Moving off from 0: the motion gate has already proved the movement, so show the real speed at once instead of
        // climbing up to it from 0 over several seconds.
        if (value == 0f && rawKmh >= TAKE_OFF_KMH && accuracyM != null && accuracyM <= POOR_ACCURACY_M) {
            value = rawKmh; trend = 0; trendSign = 0; return value
        }
        val diff = rawKmh - value
        val sign = if (diff > 0f) 1 else -1
        trend = if (abs(diff) >= TREND_KMH) (if (sign == trendSign) trend + 1 else 1) else 0
        trendSign = if (trend > 0) sign else 0
        val following = trend >= TREND_FIXES
        // One wild fix is limited to about 6 m/s2 (22 km/h a second). When fixes agree (two in a row far off in the
        // same direction) the speed really changed: follow it straight away. Before, the shown speed climbed in
        // 20 to 30 km/h steps (10, 40, 70, 90) while the rider was already at 90.
        val accel = if (following) MAX_FOLLOW_KMH_PER_S else MAX_ACCEL_KMH_PER_S
        val maxStep = (accel * dtSec.coerceIn(0.2, MAX_DT_SEC)).toFloat()
        val clamped = rawKmh.coerceIn(value - maxStep, value + maxStep)
        val error = clamped - value
        var next = value
        // Small differences are ignored: 0.5 km/h, or 1% of the speed when that is more, so the digits hold still at cruise.
        if (abs(error) >= maxOf(DEAD_BAND_KMH, value * DEAD_BAND_FRACTION)) {
            val alpha = if (following) ALPHA_FOLLOW else {
                // Calm for small differences, quick for big ones.
                val base = if (accuracyM == null || accuracyM > POOR_ACCURACY_M) ALPHA_POOR else ALPHA_GOOD
                (base + abs(error) / ALPHA_RAMP_KMH).coerceAtMost(ALPHA_MAX)
            }
            next = value + error * alpha
        }
        if (rawKmh < STANDSTILL_KMH && next < STANDSTILL_HOLD_KMH) next = 0f
        value = next
        return value
    }

    private companion object {
        const val MAX_ACCEL_KMH_PER_S = 22.0
        const val MAX_FOLLOW_KMH_PER_S = 60.0
        const val MAX_DT_SEC = 3.0
        const val ALPHA_GOOD = 0.3f
        const val ALPHA_POOR = 0.15f
        const val ALPHA_FOLLOW = 1f
        const val POOR_ACCURACY_M = 25f
        const val DEAD_BAND_KMH = 0.5f
        const val DEAD_BAND_FRACTION = 0.01f
        const val ALPHA_RAMP_KMH = 30f
        const val ALPHA_MAX = 0.8f
        const val TREND_KMH = 7f
        const val TREND_FIXES = 2
        const val TAKE_OFF_KMH = 6f
        const val STANDSTILL_KMH = 2f
        const val STANDSTILL_HOLD_KMH = 3f
    }
}


enum class SpeedUnit(val label: String, val perKmh: Float) {
    Kmh("km/h", 1f),
    Mph("mph", 0.621371f),
    Ms("m/s", 0.277778f);

    fun fromKmh(kmh: Float) = kmh * perKmh
}

object DistanceUnit {
    const val MILES_PER_KM = 0.621371f
    fun fromKm(km: Float, mph: Boolean) = if (mph) km * MILES_PER_KM else km
}

/**
 * Stops a phone that is standing still (indoors, on a table, in a pocket) from showing speed. Indoors the GPS bounces
 * off walls, so its position jumps by tens of metres and it can report 10 to 25 km/h while nobody moves.
 *
 * While standing still, speed reads 0 until movement is proven: [CONFIRM_FIXES] fixes in a row with a good fix
 * (accuracy within [MAX_START_ACCURACY_M]), a speed of at least [START_KMH] that is clearly larger than its own error,
 * and the position at least [START_DISTANCE_M] (or the two fixes' accuracy, when larger) away from where the phone
 * stood. Once moving, speeds pass straight through, and [STOP_FIXES] slow fixes in a row count as standing still again.
 * Pure Kotlin so it can be tested; the trip service feeds it every accepted fix.
 */
class MotionGate {
    var moving = false
        private set
    private var anchorLat = Double.NaN
    private var anchorLon = Double.NaN
    private var anchorAcc = 0f
    private var goodFixes = 0
    private var slowFixes = 0

    fun reset() {
        moving = false
        anchorLat = Double.NaN
        anchorLon = Double.NaN
        anchorAcc = 0f
        goodFixes = 0
        slowFixes = 0
    }

    /** Returns the speed to use in m/s: [speedMps] once the phone is really moving, 0 while it stands still. */
    fun filter(speedMps: Float, speedAccuracyMps: Float?, accuracyM: Float?, lat: Double, lon: Double): Float {
        val hasPos = !lat.isNaN() && !lon.isNaN()
        val kmh = speedMps * 3.6f
        if (moving) {
            slowFixes = if (kmh < STOP_KMH) slowFixes + 1 else 0
            if (slowFixes >= STOP_FIXES) {
                moving = false
                goodFixes = 0
                if (hasPos) setAnchor(lat, lon, accuracyM ?: UNKNOWN_ACCURACY_M)
                return 0f
            }
            return speedMps
        }
        val acc = accuracyM ?: UNKNOWN_ACCURACY_M
        if (!hasPos) { goodFixes = 0; return 0f }
        if (anchorLat.isNaN()) setAnchor(lat, lon, acc)
        val speedTrusted = speedAccuracyMps == null || speedAccuracyMps <= 0f || speedMps >= SPEED_ERROR_FACTOR * speedAccuracyMps
        val credible = acc <= MAX_START_ACCURACY_M && kmh >= START_KMH && speedTrusted
        if (!credible) {
            goodFixes = 0
            // A good slow fix moves the standing point along (walking to the car), so it never goes stale.
            if (acc <= MAX_START_ACCURACY_M && kmh < START_KMH) setAnchor(lat, lon, acc)
            return 0f
        }
        goodFixes++
        val away = GeoMath.distanceM(anchorLat, anchorLon, lat, lon)
        val needed = (acc + anchorAcc).toDouble().coerceIn(START_DISTANCE_M, MAX_START_DISTANCE_M)
        if (goodFixes >= CONFIRM_FIXES && away >= needed) {
            moving = true
            slowFixes = 0
            return speedMps
        }
        return 0f
    }

    private fun setAnchor(lat: Double, lon: Double, acc: Float) {
        anchorLat = lat
        anchorLon = lon
        anchorAcc = acc
    }

    companion object {
        const val START_KMH = 6f
        const val STOP_KMH = 3f
        const val CONFIRM_FIXES = 3
        const val STOP_FIXES = 3
        const val MAX_START_ACCURACY_M = 25f
        const val START_DISTANCE_M = 25.0
        const val MAX_START_DISTANCE_M = 40.0
        const val SPEED_ERROR_FACTOR = 2f
        const val UNKNOWN_ACCURACY_M = 100f
    }
}
