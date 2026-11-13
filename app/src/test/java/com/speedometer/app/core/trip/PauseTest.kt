package com.speedometer.app.core.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseTest {
    private fun engine() = TripEngine().apply { start() }
    private var t = 0L
    private fun TripEngine.fix(kmh: Float) { t += 1000; onSample(LocationSample(kmh / 3.6f, 5f, t, 1.0, 1.0)) }

    @Test fun aPauseWhileDrivingIsNotUndoneByTheNextFix() {
        val e = engine()
        repeat(5) { e.fix(60f) }
        e.pause()
        repeat(10) { e.fix(60f) }
        assertEquals(TripPhase.Paused, e.state.value.phase)
    }

    @Test fun pausedAtAStandstillResumesWhenTheVehicleMovesOff() {
        val e = engine()
        repeat(5) { e.fix(0f) }
        e.pause()
        e.fix(0f)
        e.fix(30f)
        assertEquals(TripPhase.Recording, e.state.value.phase)
    }
}
