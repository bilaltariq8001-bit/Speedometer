package com.speedometer.app.feature.insights

import com.speedometer.app.core.database.TripEntity
import com.speedometer.app.feature.insights.domain.InsightsCalculator
import com.speedometer.app.feature.insights.domain.Weakest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class InsightsCalculatorTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 19)

    private fun trip(daysAgo: Long, score: Int, peakGuard: Int = 0, brakes: Int = 0, over: Int = 0, dur: Int = 600) =
        TripEntity(
            title = "t", startMs = today.minusDays(daysAgo).atTime(12, 0).toInstant(zone).toEpochMilli(),
            durationSec = dur, distanceKm = 5f, maxKmh = 60f, avgKmh = 40f, score = score,
            hardBrakes = brakes, secondsOver = over, peakGuard = peakGuard,
        )

    @Test fun noTripsGivesNoAverage() {
        val i = InsightsCalculator.compute(emptyList(), today, zone)
        assertNull(i.avgScore)
        assertEquals(0, i.streakDays)
        assertEquals(7, i.days.size)
    }

    @Test fun averageAndDeltaVsLastWeek() {
        val i = InsightsCalculator.compute(listOf(trip(0, 90), trip(2, 80), trip(8, 70)), today, zone)
        assertEquals(85, i.avgScore)
        assertEquals(15, i.deltaVsLastWeek)
    }

    @Test fun streakStopsAtLevelThreeOrMissingDay() {
        val trips = listOf(trip(0, 80), trip(1, 80), trip(2, 80, peakGuard = 3), trip(3, 80))
        assertEquals(2, InsightsCalculator.compute(trips, today, zone).streakDays)
        assertEquals(0, InsightsCalculator.compute(listOf(trip(1, 80)), today, zone).streakDays)
    }

    @Test fun goalCountsTripsAboveEightyCappedAtFive() {
        val trips = (0L..6L).map { trip(it, 90) } + trip(0, 50)
        assertEquals(5, InsightsCalculator.compute(trips, today, zone).goalDone)
    }

    @Test fun overLimitShareAndWeakest() {
        val i = InsightsCalculator.compute(listOf(trip(0, 70, brakes = 0, over = 60, dur = 600)), today, zone)
        assertEquals(10f, i.overLimitPct!!, 0.01f)
        assertEquals(Weakest.LimitCompliance, i.weakest)
        val j = InsightsCalculator.compute(listOf(trip(0, 70, brakes = 3, over = 0)), today, zone)
        assertEquals(Weakest.Braking, j.weakest)
    }
}
