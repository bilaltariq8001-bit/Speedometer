package com.speedometer.app.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripExportTest {
    @Test fun csvHasSummaryAndOneLinePerPoint() {
        val state = TripState(durationSec = 120, distanceKm = 1.5f, maxKmh = 60f, avgKmh = 45f, score = 91, hardBrakes = 1)
        val csv = TripExport.toCsv("Morning, trip", 0L, state, listOf(RoutePoint(33.0, 73.0, 40f, 2), RoutePoint(33.1, 73.1, 42.5f, 4)))
        val lines = csv.lines().filter { it.isNotBlank() }
        assertTrue(lines.contains("title,Morning  trip"))
        assertTrue(lines.contains("smooth_score,91"))
        assertEquals("t_s,lat,lon,speed_kmh", lines[8])
        assertEquals("4,33.100000,73.100000,42.5", lines.last())
    }
}

class RouteGapsTest {
    @Test fun fillsOnlyRealGaps() {
        val pts = listOf(
            RoutePoint(0.0, 0.0, 50f, 0), RoutePoint(0.0, 0.001, 50f, 2),
            RoutePoint(0.0, 0.011, 30f, 22), RoutePoint(0.0, 0.012, 30f, 24),
        )
        val filled = RouteGaps.fill(pts)
        // The 20 s gap between t=2 and t=22 gets points at 4, 6, ... 20; the 2 s steps are left alone.
        assertEquals(4 + 9, filled.size)
        assertEquals(listOf(0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24), filled.map { it.tSec })
        assertEquals(0.002, filled[2].lon, 1e-9)
        assertEquals(RouteGaps.fill(pts.take(1)), pts.take(1))
    }
}
