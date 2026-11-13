package com.speedometer.app.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteForSaveTest {
    @Test fun aTripWithOnlyOnePositionStillGetsALine() {
        val repo = TripRepository()
        repo.start()
        repo.onApproximate(30f, 33.6844, 73.0479)
        val route = repo.routeForSave()
        assertEquals(2, route.size)
    }

    @Test fun noPositionMeansNoRoute() {
        val repo = TripRepository()
        repo.start()
        assertTrue(repo.routeForSave().isEmpty())
    }

    @Test fun startAndEndAreAddedAroundAShortGpsRoute() {
        val repo = TripRepository()
        repo.start()
        repo.onApproximate(30f, 33.68440, 73.04790)
        repo.onSample(LocationSample(10f, 5f, 1_000, 33.68450, 73.04800))
        repo.onApproximate(30f, 33.68490, 73.04850)
        val route = repo.routeForSave()
        assertTrue("size ${route.size}", route.size >= 3)
    }
}
