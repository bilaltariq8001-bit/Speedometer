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
import com.speedometer.app.core.common.isNetworkConnected
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class GeoPoint(val lat: Double, val lon: Double)

fun GeoPoint.toCoordinateText(): String = String.format(Locale.ROOT, "%.5f, %.5f", lat, lon)

/** One-shot current position, used to place a saved zone. */
@Singleton
class CurrentLocation @Inject constructor(@ApplicationContext private val context: Context) {

    /** The phone's last known position, instantly, or null. Good enough to centre a map while a precise fix is found. */
    @SuppressLint("MissingPermission") // Checked first.
    suspend fun last(): GeoPoint? {
        if (!hasLocationPermission()) return null
        val gps = lastGpsLocation()
        // Offline or devices without working Play services should use the phone's own GPS cache immediately.
        if (!context.isNetworkConnected()) return gps?.let { GeoPoint(it.latitude, it.longitude) }
        val fused = runCatching {
            withTimeoutOrNull(LAST_LOCATION_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    LocationServices.getFusedLocationProviderClient(context).lastLocation
                        .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                }
            }
        }.getOrNull()
        return listOfNotNull(fused, gps).maxByOrNull { it.time }?.let { GeoPoint(it.latitude, it.longitude) }
    }

    /**
     * A fresh position from the fused provider or the device GPS. The direct GPS fallback works without network access.
     */
    @SuppressLint("MissingPermission") // Checked first.
    suspend fun get(): GeoPoint? {
        if (!hasLocationPermission()) return null
        val offline = !context.isNetworkConnected()
        val cachedGps = lastGpsLocation()
        // A recent GPS fix is already a useful map position and does not need network-assisted location.
        if (offline && cachedGps != null && System.currentTimeMillis() - cachedGps.time <= OFFLINE_CACHE_MAX_AGE_MS) {
            return GeoPoint(cachedGps.latitude, cachedGps.longitude)
        }
        if (!offline) {
            val fresh = runCatching {
                withTimeoutOrNull(FRESH_TIMEOUT_MS) {
                    suspendCancellableCoroutine<GeoPoint?> { cont ->
                        val cancel = com.google.android.gms.tasks.CancellationTokenSource()
                        cont.invokeOnCancellation { cancel.cancel() }
                        LocationServices.getFusedLocationProviderClient(context)
                            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token)
                            .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc?.let { GeoPoint(it.latitude, it.longitude) }) }
                            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                    }
                }
            }.getOrNull()
            if (fresh != null) return fresh
        }

        val gps = currentGpsLocation()
        if (gps != null) return GeoPoint(gps.latitude, gps.longitude)
        if (cachedGps != null) return GeoPoint(cachedGps.latitude, cachedGps.longitude)
        return last()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun lastGpsLocation(): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            if (LocationManager.GPS_PROVIDER in manager.allProviders) manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
        } catch (_: SecurityException) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentGpsLocation(): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (LocationManager.GPS_PROVIDER !in manager.allProviders || !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return null

        return withTimeoutOrNull(GPS_TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { cont ->
                lateinit var listener: LocationListener
                listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (!cont.isActive) return
                        manager.removeUpdates(this)
                        cont.resume(location)
                    }
                }
                cont.invokeOnCancellation { manager.removeUpdates(listener) }
                try {
                    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
                } catch (_: IllegalArgumentException) {
                    if (cont.isActive) cont.resume(null)
                } catch (_: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    private companion object {
        const val FRESH_TIMEOUT_MS = 12_000L
        const val GPS_TIMEOUT_MS = 25_000L
        const val LAST_LOCATION_TIMEOUT_MS = 2_000L
        const val OFFLINE_CACHE_MAX_AGE_MS = 10 * 60 * 1000L
    }
}
