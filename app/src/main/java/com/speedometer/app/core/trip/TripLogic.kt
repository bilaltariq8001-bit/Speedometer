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

    fun reset() { value = 0f; started = false }

    fun next(rawKmh: Float, dtSec: Double, accuracyM: Float?): Float {
        if (!started) { started = true; value = rawKmh; return value }
        // No car changes speed faster than about 6 m/s2 (22 km/h every second); anything beyond that is noise.
        val maxStep = (MAX_ACCEL_KMH_PER_S * dtSec.coerceIn(0.2, MAX_DT_SEC)).toFloat()
        val clamped = rawKmh.coerceIn(value - maxStep, value + maxStep)
        val error = clamped - value
        var next = value
        // Small differences are ignored: 0.5 km/h, or 1% of the speed when that is more, so the digits hold still at cruise.
        if (abs(error) >= maxOf(DEAD_BAND_KMH, value * DEAD_BAND_FRACTION)) {
            // Calm for small differences, quick for big ones, so real braking and acceleration show without delay.
            val base = if (accuracyM == null || accuracyM > POOR_ACCURACY_M) ALPHA_POOR else ALPHA_GOOD
            val alpha = (base + abs(error) / ALPHA_RAMP_KMH).coerceAtMost(ALPHA_MAX)
            next = value + error * alpha
        }
        if (rawKmh < STANDSTILL_KMH && next < STANDSTILL_HOLD_KMH) next = 0f
        value = next
        return value
    }

    private companion object {
        const val MAX_ACCEL_KMH_PER_S = 22.0
        const val MAX_DT_SEC = 3.0
        const val ALPHA_GOOD = 0.3f
        const val ALPHA_POOR = 0.15f
        const val POOR_ACCURACY_M = 25f
        const val DEAD_BAND_KMH = 0.5f
        const val DEAD_BAND_FRACTION = 0.01f
        const val ALPHA_RAMP_KMH = 30f
        const val ALPHA_MAX = 0.8f
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
