package com.speedometer.app.core.trip

import java.nio.ByteBuffer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** One recorded point of a trip's route. [tSec] is seconds since the trip started. */
data class RoutePoint(val lat: Double, val lon: Double, val speedKmh: Float, val tSec: Int)

/** Compact blob for the database: 24 bytes per point. */
object RouteCodec {
    private const val POINT_BYTES = 24

    fun encode(points: List<RoutePoint>): ByteArray {
        val buf = ByteBuffer.allocate(points.size * POINT_BYTES)
        points.forEach { buf.putDouble(it.lat).putDouble(it.lon).putFloat(it.speedKmh).putInt(it.tSec) }
        return buf.array()
    }

    fun decode(bytes: ByteArray): List<RoutePoint> {
        if (bytes.size < POINT_BYTES) return emptyList()
        val buf = ByteBuffer.wrap(bytes)
        return List(bytes.size / POINT_BYTES) { RoutePoint(buf.getDouble(), buf.getDouble(), buf.getFloat(), buf.getInt()) }
    }
}

object GeoMath {
    private const val EARTH_M = 6_371_000.0

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_M * atan2(sqrt(a), sqrt(1 - a))
    }
}

/** Fills holes in a recorded route, such as a tunnel where GPS was lost, with straight-line points. */
object RouteGaps {
    /** A hole is a step longer than this many seconds. Normal steps are 2 s. */
    const val GAP_OVER_SECONDS = 6
    const val FILL_EVERY_SECONDS = 2

    fun fill(points: List<RoutePoint>): List<RoutePoint> {
        if (points.size < 2) return points
        val out = ArrayList<RoutePoint>(points.size)
        for (i in points.indices) {
            val a = points[i]
            out += a
            val b = points.getOrNull(i + 1) ?: continue
            val span = b.tSec - a.tSec
            if (span <= GAP_OVER_SECONDS) continue
            var t = a.tSec + FILL_EVERY_SECONDS
            while (t < b.tSec) {
                val f = (t - a.tSec).toDouble() / span
                out += RoutePoint(
                    a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f,
                    (a.speedKmh + (b.speedKmh - a.speedKmh) * f).toFloat(), t,
                )
                t += FILL_EVERY_SECONDS
            }
        }
        return out
    }
}
