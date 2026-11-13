package com.speedometer.app.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleLogicTest {
    @Test fun gearBands() {
        assertEquals("N", GearEstimator.coupe(0f))
        assertEquals("1", GearEstimator.coupe(20f))
        assertEquals("3", GearEstimator.coupe(90f))
        assertEquals("6", GearEstimator.coupe(230f))
        assertEquals("2", GearEstimator.bike(45f))
    }

    @Test fun zeroToHundredTimesARun() {
        val z = ZeroToHundred()
        z.update(0f, 0)
        listOf(20f, 45f, 70f, 90f, 105f).forEachIndexed { i, v -> z.update(v, i + 1) }
        // Moving off was first seen at t=1, so the run started at t=0 and reached 100 at t=5.
        assertEquals(5, z.lastRunSec)
    }

    @Test fun stoppingCancelsARunAndNoRunWithoutStop() {
        val z = ZeroToHundred()
        z.update(0f, 0)
        z.update(40f, 1)
        z.update(1f, 2)
        z.update(60f, 3)
        assertNull(z.lastRunSec)
    }

    @Test fun rollingStartIsNotARun() {
        val z = ZeroToHundred()
        z.update(50f, 0)
        z.update(120f, 1)
        assertNull(z.lastRunSec)
    }
}
