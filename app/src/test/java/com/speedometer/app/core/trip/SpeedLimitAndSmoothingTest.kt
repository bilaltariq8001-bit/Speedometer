package com.speedometer.app.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedLimitAndSmoothingTest {
    private val limit = 125f

    @Test fun noAlarmAtOrBelowTheLimitItself() {
        assertEquals(GuardLevel.Off, LimitGuard.level(120f, limit, GpsQuality.Strong, toleranceKmh = 0f))
        assertEquals(GuardLevel.Off, LimitGuard.level(125f, limit, GpsQuality.Strong, toleranceKmh = 0f))
        // The gauge still reads 125 at 125.4 km/h, so the alarm must not start yet.
        assertEquals(GuardLevel.Off, LimitGuard.level(125.4f, limit, GpsQuality.Strong, toleranceKmh = 0f))
    }

    @Test fun alarmStartsWhenTheGaugeReadsAboveTheLimit() {
        assertEquals(GuardLevel.Soft, LimitGuard.level(125.6f, limit, GpsQuality.Strong, toleranceKmh = 0f)) // gauge: 126
        assertEquals(GuardLevel.Firm, LimitGuard.level(131f, limit, GpsQuality.Strong, toleranceKmh = 0f))
        assertEquals(GuardLevel.Spoken, LimitGuard.level(141f, limit, GpsQuality.Strong, toleranceKmh = 0f))
    }

    @Test fun aToleranceMovesTheStartOfTheAlarm() {
        assertEquals(GuardLevel.Off, LimitGuard.level(129f, limit, GpsQuality.Strong, toleranceKmh = 5f))
        assertEquals(GuardLevel.Soft, LimitGuard.level(130f, limit, GpsQuality.Strong, toleranceKmh = 5f))
        assertEquals(GuardLevel.Off, LimitGuard.level(134f, limit, GpsQuality.Strong, toleranceKmh = 10f))
        assertEquals(GuardLevel.Soft, LimitGuard.level(135f, limit, GpsQuality.Strong, toleranceKmh = 10f))
    }

    @Test fun theAlarmDoesNotFlickerAroundTheLimit() {
        // Once on, it stays on for 2 km/h below the start, then goes off.
        assertEquals(GuardLevel.Soft, LimitGuard.level(125.4f, limit, GpsQuality.Strong, toleranceKmh = 0f, wasActive = true))
        assertEquals(GuardLevel.Off, LimitGuard.level(122f, limit, GpsQuality.Strong, toleranceKmh = 0f, wasActive = true))
    }

    @Test fun aUserLimitStillAlarmsOnAWeakFixButAZoneDoesNot() {
        assertEquals(GuardLevel.Soft, LimitGuard.level(130f, limit, GpsQuality.Weak, 0f, requireStrongGps = false))
        assertEquals(GuardLevel.Off, LimitGuard.level(130f, limit, GpsQuality.Weak, 0f, requireStrongGps = true))
        assertEquals(GuardLevel.Off, LimitGuard.level(130f, limit, GpsQuality.Lost, 0f, requireStrongGps = false))
    }

    @Test fun savedZonesKeepTheirPlusFiveTenTwenty() {
        assertEquals(GuardLevel.Off, LimitGuard.level(64f, 60f, GpsQuality.Strong))
        assertEquals(GuardLevel.Soft, LimitGuard.level(65f, 60f, GpsQuality.Strong))
        assertEquals(GuardLevel.Firm, LimitGuard.level(70f, 60f, GpsQuality.Strong))
        assertEquals(GuardLevel.Spoken, LimitGuard.level(80f, 60f, GpsQuality.Strong))
    }

    @Test fun engineAlarmFollowsTheConfiguredLimit() {
        val e = TripEngine().apply { limitKmh = 125f; limitToleranceKmh = 0f; limitIsUserSet = true; start() }
        var t = 0L
        fun fix(kmh: Float) { t += 1000; e.onSample(LocationSample(kmh / 3.6f, 30f, t)) } // 30 m accuracy: weak, still counts
        repeat(15) { fix(124f) }
        assertEquals(GuardLevel.Off, e.state.value.guard)
        repeat(15) { fix(132f) }
        assertTrue(e.state.value.guard != GuardLevel.Off)
    }

    @Test fun aSteadySpeedDoesNotJitter() {
        val s = SpeedSmoother()
        val rnd = java.util.Random(7)
        val shown = List(60) { s.next(80f + (rnd.nextFloat() - 0.5f) * 6f, 1.0, 8f) } // GPS noise of +-3 km/h
        val settled = shown.drop(10)
        assertTrue("range ${settled.max() - settled.min()}", settled.max() - settled.min() < 3f)
        assertEquals(80f, settled.average().toFloat(), 1.5f)
    }

    @Test fun theSmootherFollowsARealAcceleration() {
        val s = SpeedSmoother()
        var v = 0f
        repeat(12) { v = s.next(10f * (it + 1), 1.0, 5f) } // 10 km/h more every second
        assertTrue("reached $v", v > 90f)
    }

    @Test fun aSpikeIsCutDownAndStopSnapsToZero() {
        val s = SpeedSmoother()
        repeat(5) { s.next(60f, 1.0, 5f) }
        assertTrue(s.next(140f, 1.0, 5f) < 85f) // one wild fix
        val stopped = SpeedSmoother()
        stopped.next(2f, 1.0, 5f)
        assertEquals(0f, stopped.next(0.5f, 1.0, 5f), 0f)
    }
}
