package com.speedometer.app.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripLogicTest {
    @Test fun scoreStartsAt100() = assertEquals(100, Score.compute(0, 0))
    @Test fun scoreSubtractsBrakesAndTimeOver() = assertEquals(100 - 12 - 7, Score.compute(2, 10))
    @Test fun scoreClampsAtZero() = assertEquals(0, Score.compute(30, 500))

    @Test fun guardLevels() {
        val s = GpsQuality.Strong
        assertEquals(GuardLevel.Off, LimitGuard.level(54f, 50f, s))
        assertEquals(GuardLevel.Soft, LimitGuard.level(55f, 50f, s))
        assertEquals(GuardLevel.Firm, LimitGuard.level(60f, 50f, s))
        assertEquals(GuardLevel.Spoken, LimitGuard.level(70f, 50f, s))
    }

    @Test fun guardOffOnWeakGpsOrNoLimit() {
        assertEquals(GuardLevel.Off, LimitGuard.level(90f, 50f, GpsQuality.Weak))
        assertEquals(GuardLevel.Off, LimitGuard.level(90f, null, GpsQuality.Strong))
    }

    @Test fun gpsQuality() {
        assertEquals(GpsQuality.Strong, GpsQualityRules.classify(5f, 0))
        assertEquals(GpsQuality.Strong, GpsQualityRules.classify(15f, 0))
        assertEquals(GpsQuality.Weak, GpsQualityRules.classify(30f, 1))
        assertEquals(GpsQuality.Lost, GpsQualityRules.classify(5f, 10))
        assertEquals(GpsQuality.Weak, GpsQualityRules.classify(null, 0))
    }

    @Test fun tooShortRule() {
        assertTrue(TripRules.isTooShortToSave(7))
        assertFalse(TripRules.isTooShortToSave(8))
    }

    @Test fun speedFilter() {
        assertEquals(0f, SpeedFilter.toKmh(0.2f), 0f)
        assertEquals(36f, SpeedFilter.toKmh(10f), 0.01f)
        assertEquals(20f, SpeedFilter.smooth(listOf(0f, 10f, 20f, 30f)), 0.01f)
    }

    @Test fun units() {
        assertEquals(62.1371f, SpeedUnit.Mph.fromKmh(100f), 0.01f)
        assertEquals(6.21371f, DistanceUnit.fromKm(10f, true), 0.001f)
    }
}
