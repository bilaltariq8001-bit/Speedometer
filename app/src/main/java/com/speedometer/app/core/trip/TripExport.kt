package com.speedometer.app.core.trip

import java.time.Instant

/** A finished trip as CSV text: a summary block, then one line per recorded route point. */
object TripExport {
    fun toCsv(title: String, startMs: Long, state: TripState, route: List<RoutePoint>): String = buildString {
        appendLine("title,${title.replace(',', ' ')}")
        appendLine("started,${Instant.ofEpochMilli(startMs)}")
        appendLine("duration_s,${state.durationSec}")
        appendLine("distance_km,${"%.3f".format(java.util.Locale.ROOT, state.distanceKm)}")
        appendLine("max_kmh,${"%.1f".format(java.util.Locale.ROOT, state.maxKmh)}")
        appendLine("avg_kmh,${"%.1f".format(java.util.Locale.ROOT, state.avgKmh)}")
        appendLine("smooth_score,${state.score}")
        appendLine("hard_brakes,${state.hardBrakes}")
        appendLine()
        appendLine("t_s,lat,lon,speed_kmh")
        route.forEach {
            appendLine("${it.tSec},${"%.6f".format(java.util.Locale.ROOT, it.lat)},${"%.6f".format(java.util.Locale.ROOT, it.lon)},${"%.1f".format(java.util.Locale.ROOT, it.speedKmh)}")
        }
    }
}
