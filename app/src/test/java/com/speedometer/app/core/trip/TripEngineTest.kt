package com.speedometer.app.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripEngineTest {
    private fun feed(e: TripEngine, kmh: Float, seconds: Int, startMs: Long, accuracy: Float = 5f): Long {
        var t = startMs
        repeat(seconds) {
            t += 1000
            e.onSample(LocationSample(kmh / 3.6f, accuracy, t))
            e.tick(1.0)
        }
        return t
    }

    @Test fun integratesDistanceAndDuration() {
        val e = TripEngine().apply { start() }
        e.onSample(LocationSample(10f, 5f, 0))
        feed(e, 36f, 100, 0)
        val s = e.state.value
        assertEquals(100, s.durationSec)
        assertEquals(1.0f, s.distanceKm, 0.15f) // 10 m/s for 100 s
    }

    @Test fun overLimitLowersScoreAndRaisesGuard() {
        val e = TripEngine().apply { limitKmh = 60f; start() }
        feed(e, 90f, 10, 0)
        val s = e.state.value
        assertEquals(GuardLevel.Spoken, s.guard)
        assertTrue(s.score < 100)
    }

    @Test fun weakGpsPausesGuard() {
        val e = TripEngine().apply { limitKmh = 60f; start() }
        feed(e, 90f, 5, 0, accuracy = 30f)
        assertEquals(GuardLevel.Off, e.state.value.guard)
        assertEquals(GpsQuality.Weak, e.state.value.gps)
    }

    @Test fun hardBrakeCountsOncePerPress() {
        val e = TripEngine().apply { start() }
        var t = feed(e, 80f, 5, 0)
        repeat(4) { i ->
            t += 1000
            e.onSample(LocationSample(maxOf(0f, 80f - 25f * (i + 1)) / 3.6f, 5f, t))
        }
        assertEquals(1, e.state.value.hardBrakes)
    }

    @Test fun lostFixKeepsRecording() {
        val e = TripEngine().apply { start() }
        val t = feed(e, 50f, 3, 0)
        e.onNoFix(10)
        assertEquals(TripPhase.Recording, e.state.value.phase)
        assertEquals(GpsQuality.Lost, e.state.value.gps)
        e.onSample(LocationSample(10f, 5f, t + 20_000))
        assertEquals(TripPhase.Recording, e.state.value.phase)
    }

    @Test fun coldStartIsSearchingNotLost() {
        val e = TripEngine().apply { start() }
        e.onNoFix(30)
        assertEquals(TripPhase.Recording, e.state.value.phase)
        assertTrue(e.state.value.searching)
        feed(e, 40f, 2, 0)
        assertEquals(false, e.state.value.searching)
    }

    @Test fun shortTripCannotBeSaved() {
        val e = TripEngine().apply { start() }
        feed(e, 30f, 5, 0)
        e.finish()
        assertTrue(e.state.value.tooShortToSave)
    }

    @Test fun pausedTripResumesWhenMovingAgain() {
        val e = TripEngine().apply { start() }
        var t = feed(e, 40f, 5, 0)
        e.pause()
        t = feed(e, 0f, 3, t)
        assertEquals(TripPhase.Paused, e.state.value.phase)
        feed(e, 30f, 1, t)
        assertEquals(TripPhase.Recording, e.state.value.phase)
    }

    @Test fun speedIsHeldDuringAShortSignalLoss() {
        val e = TripEngine().apply { start() }
        feed(e, 50f, 6, 0)
        val before = e.state.value
        e.onNoFix(10)
        assertEquals(GpsQuality.Lost, e.state.value.gps)
        assertTrue("speed stays near the last speed", e.state.value.speedKmh > 40f)
        assertTrue("distance keeps growing", e.state.value.distanceKm > before.distanceKm)
    }

    @Test fun speedReadsZeroAfterALongSignalLoss() {
        val e = TripEngine().apply { start() }
        feed(e, 50f, 6, 0)
        e.onNoFix(90)
        assertEquals(0f, e.state.value.speedKmh, 0.01f)
    }

    @Test fun calculates200KmhHighSpeedAccurately() {
        val e = TripEngine().apply { start() }
        feed(e, 200f, 30, 0)
        val s = e.state.value
        assertTrue("Speed reaches near 200 km/h", s.speedKmh > 180f)
        assertEquals(200f, s.maxKmh, 10f)
    }
}
