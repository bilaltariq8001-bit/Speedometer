package com.speedometer.app.core.trip

/**
 * Gear shown on the vehicle clusters. A phone cannot read the real gear, so this is an estimate from speed
 * bands, as in the design's clusters.
 */
object GearEstimator {
    private const val NEUTRAL_BELOW_KMH = 3f
    private val coupeBands = floatArrayOf(0f, 45f, 80f, 120f, 165f, 215f)
    private val bikeBands = floatArrayOf(0f, 40f, 75f, 115f, 160f, 215f)

    fun coupe(kmh: Float): String = pick(kmh, coupeBands)
    fun bike(kmh: Float): String = pick(kmh, bikeBands)

    private fun pick(kmh: Float, bands: FloatArray): String {
        if (kmh < NEUTRAL_BELOW_KMH) return "N"
        var gear = 1
        for (i in 1 until bands.size) if (kmh >= bands[i]) gear = i + 1
        return gear.toString()
    }
}

/**
 * Times a standing-start run to 100 km/h from one-second speed samples. A run starts when the vehicle moves off
 * after being stopped and ends at 100 km/h; slowing to a stop cancels it, and a rolling start never counts.
 */
class ZeroToHundred {
    private var armed = false
    private var startedAtSec: Int? = null

    /** Seconds of the last completed run, or null if none yet. */
    var lastRunSec: Int? = null
        private set

    fun update(speedKmh: Float, tSec: Int): Int? {
        when {
            speedKmh <= STOPPED_KMH -> { armed = true; startedAtSec = null }
            armed && startedAtSec == null -> startedAtSec = tSec - 1
        }
        val start = startedAtSec
        if (start != null && speedKmh >= TARGET_KMH) {
            lastRunSec = tSec - start
            armed = false
            startedAtSec = null
        }
        return lastRunSec
    }

    fun reset() {
        armed = false
        startedAtSec = null
        lastRunSec = null
    }

    private companion object {
        const val STOPPED_KMH = 3f
        const val TARGET_KMH = 100f
    }
}
