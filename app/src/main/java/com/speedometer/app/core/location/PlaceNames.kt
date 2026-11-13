package com.speedometer.app.core.location

import android.content.Context
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Street or area name for a point, from Android's built-in geocoder (which asks Google's location service over the
 * network). Returns null when the phone has no geocoder, is offline, or knows no name. Results are cached by
 * rounding the point to about 100 m, so the same place is not asked for twice.
 */
@Singleton
class PlaceNames @Inject constructor(@ApplicationContext private val context: Context) {
    private val cache = HashMap<String, String?>()

    suspend fun at(lat: Double, lon: Double): String? {
        val key = "%.3f,%.3f".format(Locale.ROOT, lat, lon)
        if (cache.containsKey(key)) return cache[key]
        val name = withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { a ->
                    a.thoroughfare ?: a.featureName?.takeIf { it.any(Char::isLetter) } ?: a.subLocality ?: a.locality
                }
            }.getOrNull()
        }
        // A failed lookup is not cached, so it is tried again later (for example once the phone is online).
        if (name != null) cache[key] = name
        return name
    }

    /** A found place: a readable address and where it is. */
    data class Found(val label: String, val lat: Double, val lon: Double)

    /** Places matching typed text, from the phone's geocoder (needs internet; the text is sent to Google's location service). */
    suspend fun search(query: String): List<Found> = withContext(Dispatchers.IO) {
        if (query.isBlank() || !Geocoder.isPresent()) return@withContext emptyList()
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault()).getFromLocationName(query.trim(), 5).orEmpty().mapNotNull { a ->
                val label = a.getAddressLine(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Found(label, a.latitude, a.longitude)
            }
        }.getOrDefault(emptyList())
    }

    private val addressCache = HashMap<String, String?>()

    /**
     * Full street address for a point, like "Plot 65, Bahria Safari Valley Rafi Block, Bahria Town, Rawalpindi, Pakistan".
     * Same source and limits as [at]: the phone's geocoder, which needs internet. Null when no address is known.
     */
    suspend fun address(lat: Double, lon: Double): String? {
        val key = "%.4f,%.4f".format(Locale.ROOT, lat, lon)
        addressCache[key]?.let { return it }
        val text = withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { a ->
                    // The first address line is the street part; add area, city and country when it leaves them out.
                    val line = a.getAddressLine(0)?.takeIf { it.isNotBlank() }
                    line ?: listOfNotNull(a.featureName, a.thoroughfare, a.subLocality, a.locality, a.countryName)
                        .filter { it.any(Char::isLetter) }.distinct().joinToString(", ").takeIf { it.isNotBlank() }
                }
            }.getOrNull()
        }
        if (text != null) addressCache[key] = text
        return text
    }
}
