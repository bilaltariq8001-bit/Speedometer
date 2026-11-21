package com.speedometer.app.core.ads

import org.junit.Assert.assertEquals
import org.junit.Test

class TripAdCounterTest {
    @Test fun adTripsGrowTheGapByOne() {
        val ads = (1..30).filter { TripAdCounter.isAdTrip(it, 4) }
        assertEquals(listOf(4, 7, 11, 16, 22, 29), ads)
    }
}
