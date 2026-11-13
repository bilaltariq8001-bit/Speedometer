package com.speedometer.app.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class GeoPoint(val lat: Double, val lon: Double)

fun GeoPoint.toCoordinateText(): String = String.format(Locale.ROOT, "%.5f, %.5f", lat, lon)

/**
 * Where the phone is. Works the same with or without internet:
 *  - GPS needs no data connection, and Play services' fused provider keeps working offline (it simply falls back to
 *    satellites), so neither is ever skipped because the network is down;
 *  - every cached position the phone has (fused, GPS, network, passive) is considered, newest wins;
 *  - the last good fix is also kept on disk, so even a cold, offline start can put the user on the map instantly.
 */
@Singleton
class CurrentLocation @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val manager by lazy { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }
    @Volatile private var lastSavedMs = 0L

    /** The best position the phone already knows, instantly (well under a second), or null. Good enough to centre a map. */
    suspend fun last(): GeoPoint? {
        if (!hasLocationPermission()) return stored()
        val fusedLast = fusedLastLocation()
        val best = (listOfNotNull(fusedLast) + systemLastLocations()).maxByOrNull { it.time }
        if (best != null) {
            remember(best)
            return GeoPoint(best.latitude, best.longitude)
        }
        return stored()
    }

    /**
     * A fresh position. The fused provider and the raw GPS are asked at the same time and the first answer wins, so an
     * offline phone (no assisted location) is never stuck waiting on one source before the other is even tried.
     * Falls back to [last] when neither answers in time.
     */
    suspend fun get(): GeoPoint? {
        if (!hasLocationPermission()) return stored()
        val recent = systemLastLocations().filter { System.currentTimeMillis() - it.time <= RECENT_FIX_MS }.maxByOrNull { it.time }
        if (recent != null) {
            remember(recent)
            return GeoPoint(recent.latitude, recent.longitude)
        }
        // Both sources start together; the first real position wins and the other request is cancelled.
        val fresh = withTimeoutOrNull(FRESH_TIMEOUT_MS) {
            channelFlow<Location?> {
                launch { send(fusedCurrentLocation()) }
                launch { send(currentGpsLocation()) }
            }.take(2).firstOrNull { it != null }
        }
        if (fresh != null) {
            remember(fresh)
            return GeoPoint(fresh.latitude, fresh.longitude)
        }
        return last()
    }

    /** Keeps [loc] as the last good fix on disk (throttled), so the next start, online or not, can show it at once. */
    fun remember(loc: Location) {
        val now = System.currentTimeMillis()
        if (loc.time < prefs.getLong(KEY_TIME, 0L) || now - lastSavedMs < SAVE_EVERY_MS) return
        lastSavedMs = now
        prefs.edit()
            .putLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(loc.latitude))
            .putLong(KEY_LON, java.lang.Double.doubleToRawLongBits(loc.longitude))
            .putLong(KEY_TIME, loc.time)
            .apply()
    }

    private fun stored(): GeoPoint? {
        if (!prefs.contains(KEY_LAT)) return null
        return GeoPoint(
            java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAT, 0L)),
            java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LON, 0L)),
        )
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasFine(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Checked by the callers.
    private suspend fun fusedLastLocation(): Location? = runCatching {
        withTimeoutOrNull(LAST_LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { cont ->
                fused.lastLocation
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
        }
    }.getOrNull()

    @SuppressLint("MissingPermission") // Checked by the callers.
    private suspend fun fusedCurrentLocation(): Location? = runCatching {
        suspendCancellableCoroutine<Location?> { cont ->
            val cancel = com.google.android.gms.tasks.CancellationTokenSource()
            cont.invokeOnCancellation { cancel.cancel() }
            val priority = if (hasFine()) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            fused.getCurrentLocation(priority, cancel.token)
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
    }.getOrNull()

    /** Every cached position the system providers hold. None of these need a data connection to read. */
    @SuppressLint("MissingPermission")
    private fun systemLastLocations(): List<Location> {
        val providers = runCatching { manager.allProviders }.getOrDefault(emptyList())
        return providers.mapNotNull { provider ->
            if (provider == LocationManager.GPS_PROVIDER && !hasFine()) return@mapNotNull null
            try { manager.getLastKnownLocation(provider) } catch (_: Exception) { null }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentGpsLocation(): Location? {
        if (!hasFine()) return null
        if (LocationManager.GPS_PROVIDER !in manager.allProviders || !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return null
        return suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdates(this)
                    if (cont.isActive) cont.resume(location)
                }
                override fun onProviderDisabled(provider: String) {
                    manager.removeUpdates(this)
                    if (cont.isActive) cont.resume(null)
                }
            }
            cont.invokeOnCancellation { manager.removeUpdates(listener) }
            try {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private companion object {
        const val PREFS = "last_location"
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_TIME = "time"
        const val SAVE_EVERY_MS = 15_000L
        const val RECENT_FIX_MS = 30_000L
        const val FRESH_TIMEOUT_MS = 20_000L
        const val LAST_LOCATION_TIMEOUT_MS = 1_500L
    }
}
