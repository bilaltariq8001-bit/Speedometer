package com.speedometer.app.feature.replay

import com.speedometer.app.core.trip.RouteCodec
import com.speedometer.app.core.trip.RoutePoint
import com.speedometer.app.feature.replay.domain.ReplayMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayMathTest {
    private val pts = listOf(
        RoutePoint(0.0, 0.0, 36f, 0),
        RoutePoint(0.0, 0.001, 36f, 10),
        RoutePoint(0.0, 0.002, 80f, 12),
        RoutePoint(0.0, 0.003, 20f, 14),
        RoutePoint(0.0, 0.004, 20f, 24),
    )

    @Test fun speedAndPositionInterpolate() {
        assertEquals(36f, ReplayMath.speedAt(pts, 5f), 0.01f)
        assertEquals(58f, ReplayMath.speedAt(pts, 11f), 0.01f)
        assertEquals(0.0005, ReplayMath.positionAt(pts, 5f)!!.second, 1e-9)
    }

    @Test fun distanceIntegratesSpeed() {
        // 36 km/h for 10 s is 0.1 km.
        assertEquals(0.1f, ReplayMath.distanceKmAt(pts, 10f), 0.001f)
    }

    @Test fun detectsOneHardBrake() {
        val moments = ReplayMath.hardBrakes(pts)
        assertEquals(1, moments.size)
        assertEquals(12, moments[0].tSec)
        assertEquals(80, moments[0].fromKmh)
        assertEquals(20, moments[0].toKmh)
    }

    @Test fun routeCodecRoundTrips() {
        val back = RouteCodec.decode(RouteCodec.encode(pts))
        assertEquals(pts, back)
        assertTrue(RouteCodec.decode(ByteArray(0)).isEmpty())
    }
}
