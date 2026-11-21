package com.speedometer.app.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionGateTest {
    private val lat0 = 31.5204
    private val lon0 = 74.3587
    /** Metres north of the start as latitude. */
    private fun north(m: Double) = lat0 + m / 111_320.0

    @Test fun indoorJitterNeverShowsSpeed() {
        val g = MotionGate()
        // Indoors: poor accuracy, position jumping, 20 km/h reported.
        repeat(30) { i ->
            val v = g.filter(5.5f, 3f, 35f, north(if (i % 2 == 0) 0.0 else 40.0), lon0)
            assertEquals(0f, v, 0f)
        }
    }

    @Test fun sharpFixesWithoutRealMovementStayAtZero() {
        val g = MotionGate()
        repeat(10) { assertEquals(0f, g.filter(5.5f, null, 10f, north(it % 2 * 5.0), lon0), 0f) }
    }

    @Test fun realDrivingStartsAfterConfirmation() {
        val g = MotionGate()
        g.filter(0f, 0.3f, 8f, lat0, lon0)
        var shown = 0f
        for (s in 1..10) shown = g.filter(8f, 0.5f, 8f, north(8.0 * s), lon0)
        assertTrue(g.moving)
        assertEquals(8f, shown, 0f)
    }

    @Test fun stopsAfterThreeSlowFixes() {
        val g = MotionGate()
        g.filter(0f, null, 8f, lat0, lon0)
        for (s in 1..6) g.filter(8f, null, 8f, north(8.0 * s), lon0)
        assertTrue(g.moving)
        repeat(3) { g.filter(0.2f, null, 8f, north(48.0), lon0) }
        assertTrue(!g.moving)
    }
}
