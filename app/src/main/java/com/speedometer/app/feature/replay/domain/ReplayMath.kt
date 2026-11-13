package com.speedometer.app.feature.replay.domain

import com.speedometer.app.core.trip.Score
import com.speedometer.app.core.trip.RoutePoint

/** A notable moment in a trip, shown in the Moments list. */
data class Moment(val tSec: Int, val fromKmh: Int, val toKmh: Int)

/** Interpolation and detection over a recorded route. Points are ordered by time. */
object ReplayMath {
    private const val HARD_BRAKE_MS2 = 3.0f

    fun durationSec(points: List<RoutePoint>): Int = points.lastOrNull()?.tSec ?: 0

    private fun segment(points: List<RoutePoint>, t: Float): Pair<RoutePoint, RoutePoint>? {
        if (points.size < 2) return null
        val i = points.indexOfLast { it.tSec <= t }.coerceIn(0, points.size - 2)
        return points[i] to points[i + 1]
    }

    private fun fraction(a: RoutePoint, b: RoutePoint, t: Float): Float {
        val span = (b.tSec - a.tSec).toFloat()
        return if (span <= 0f) 0f else ((t - a.tSec) / span).coerceIn(0f, 1f)
    }

    fun speedAt(points: List<RoutePoint>, t: Float): Float {
        val (a, b) = segment(points, t) ?: return points.firstOrNull()?.speedKmh ?: 0f
        return a.speedKmh + (b.speedKmh - a.speedKmh) * fraction(a, b, t)
    }

    /** Position at time [t] as latitude and longitude. */
    fun positionAt(points: List<RoutePoint>, t: Float): Pair<Double, Double>? {
        val (a, b) = segment(points, t) ?: return points.firstOrNull()?.let { it.lat to it.lon }
        val f = fraction(a, b, t).toDouble()
        return (a.lat + (b.lat - a.lat) * f) to (a.lon + (b.lon - a.lon) * f)
    }

    /** Distance driven by time [t], from the recorded speeds. */
    fun distanceKmAt(points: List<RoutePoint>, t: Float): Float {
        var km = 0.0
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            if (a.tSec >= t) break
            val end = minOf(b.tSec.toFloat(), t)
            val span = end - a.tSec
            val avgKmh = (a.speedKmh + speedAt(points, end)) / 2.0
            km += avgKmh * span / 3600.0
        }
        return km.toFloat()
    }

    /** Hard brakes: a drop of at least 3 m/s squared between two points while above the hard brake speed. */
    fun hardBrakes(points: List<RoutePoint>): List<Moment> {
        val out = mutableListOf<Moment>()
        var braking = false
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val dt = (b.tSec - a.tSec).toFloat()
            if (dt <= 0f) continue
            val decel = (a.speedKmh - b.speedKmh) / 3.6f / dt
            if (decel >= HARD_BRAKE_MS2 && a.speedKmh > Score.HARD_BRAKE_MIN_KMH) {
                if (!braking) out += Moment(a.tSec, a.speedKmh.toInt(), b.speedKmh.toInt())
                braking = true
            } else if (decel < HARD_BRAKE_MS2 / 2) braking = false
        }
        return out
    }
}
