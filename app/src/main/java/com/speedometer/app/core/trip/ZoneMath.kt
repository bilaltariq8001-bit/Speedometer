package com.speedometer.app.core.trip

import com.speedometer.app.core.database.ZoneEntity

object ZoneMath {
    /**
     * Limit of the saved zone the point is inside, or null. Disabled zones are ignored; when zones overlap
     * the lowest limit wins, so Guard errs on the safe side.
     */
    fun limitAt(zones: List<ZoneEntity>, lat: Double, lon: Double): Float? =
        zones.filter { it.enabled && GeoMath.distanceM(lat, lon, it.lat, it.lon) <= it.radiusM }
            .minOfOrNull { it.limitKmh }
            ?.toFloat()
}
