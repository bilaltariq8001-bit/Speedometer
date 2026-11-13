package com.speedometer.app.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.speedometer.app.core.trip.GpsQuality
import com.speedometer.app.core.trip.GpsQualityRules
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class Constellation(val prefix: String) { Gps("G"), Glonass("R"), Galileo("E"), BeiDou("C"), Other("S") }

data class Satellite(
    val constellation: Constellation,
    val svid: Int,
    val cn0: Float,
    val azimuthDeg: Float,
    val elevationDeg: Float,
    val usedInFix: Boolean,
) {
    val label: String get() = constellation.prefix + svid
}

data class GnssSnapshot(
    val satellites: List<Satellite> = emptyList(),
    val accuracyM: Float? = null,
    val altitudeM: Double? = null,
    val secondsSinceFix: Int = 0,
    val permissionGranted: Boolean = true,
    /** The phone's location switch. Off means no fixes can arrive, whatever the last reading said. */
    val deviceLocationOn: Boolean = true,
    /** Accuracy in metres of the latest Wi-Fi or mobile-network position (from the fused provider), or null when there is none lately. */
    val approxAccuracyM: Float? = null,
) {
    val used: Int get() = satellites.count { it.usedInFix }
    val quality: GpsQuality get() = if (!deviceLocationOn) GpsQuality.Lost else GpsQualityRules.classify(accuracyM, secondsSinceFix)
    /** No GPS fix, but the phone does know roughly where it is (indoors, typically). Speed still needs GPS. */
    val approximate: Boolean get() = approxAccuracyM != null && quality == GpsQuality.Lost && deviceLocationOn
    fun count(c: Constellation) = satellites.count { it.usedInFix && it.constellation == c }
}

/** Reads satellite status and the latest fix from the platform while a screen is collecting. */
@Singleton
class GnssMonitor @Inject constructor(@ApplicationContext private val context: Context) {

    @SuppressLint("MissingPermission") // Checked below; the flow emits an empty snapshot without it.
    fun snapshots(): Flow<GnssSnapshot> = callbackFlow {
        // Without the permission there is nothing to read yet. Keep checking, so that the readings start by themselves
        // as soon as it is granted (for example from the dialog on Home) instead of staying on "no signal" for good.
        while (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            trySend(GnssSnapshot(permissionGranted = false))
            delay(PERMISSION_POLL_MS)
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val handler = Handler(Looper.getMainLooper())
        var sats = emptyList<Satellite>()
        var accuracy: Float? = null
        var altitude: Double? = null
        var lastFixMs = System.currentTimeMillis()
        var lastStatusMs = System.currentTimeMillis()
        var approxAccuracy: Float? = null
        var approxMs = 0L

        fun push() {
            val now = System.currentTimeMillis()
            val on = androidx.core.location.LocationManagerCompat.isLocationEnabled(lm)
            // The satellite list is only trusted while Android keeps reporting it (about once a second).
            if (!on || now - lastStatusMs > STALE_STATUS_MS) sats = emptyList()
            val approx = approxAccuracy?.takeIf { now - approxMs <= APPROX_FRESH_MS }
            trySend(GnssSnapshot(sats, accuracy, altitude, ((now - lastFixMs) / 1000).toInt(), true, on, approx))
        }

        val status = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(s: GnssStatus) {
                lastStatusMs = System.currentTimeMillis()
                sats = List(s.satelliteCount) { i ->
                    Satellite(
                        constellation = when (s.getConstellationType(i)) {
                            GnssStatus.CONSTELLATION_GPS -> Constellation.Gps
                            GnssStatus.CONSTELLATION_GLONASS -> Constellation.Glonass
                            GnssStatus.CONSTELLATION_GALILEO -> Constellation.Galileo
                            GnssStatus.CONSTELLATION_BEIDOU -> Constellation.BeiDou
                            else -> Constellation.Other // QZSS, IRNSS, SBAS and unknown
                        },
                        svid = s.getSvid(i), cn0 = s.getCn0DbHz(i), azimuthDeg = s.getAzimuthDegrees(i),
                        elevationDeg = s.getElevationDegrees(i), usedInFix = s.usedInFix(i),
                    )
                }
                push()
            }
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                accuracy = if (loc.hasAccuracy()) loc.accuracy else null
                altitude = if (loc.hasAltitude()) loc.altitude else null
                lastFixMs = System.currentTimeMillis()
                push()
            }
            override fun onProviderDisabled(provider: String) = push()
            override fun onProviderEnabled(provider: String) = push()
        }
        // The fused provider combines GPS, Wi-Fi and mobile-network signals. Here it is only asked for a rough position, which
        // is what is available indoors when no satellite is in view; the satellite readings above stay GPS only.
        val fusedClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
        val fusedCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                val l = result.lastLocation ?: return
                approxAccuracy = if (l.hasAccuracy()) l.accuracy else DEFAULT_APPROX_M
                approxMs = System.currentTimeMillis()
                push()
            }
        }
        val networkListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                approxAccuracy = if (loc.hasAccuracy()) loc.accuracy else DEFAULT_APPROX_M
                approxMs = System.currentTimeMillis()
                push()
            }
        }
        // No Google services on this phone: the platform network provider gives the same kind of rough position.
        fun startNetworkFallback() {
            if (lm.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                runCatching { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, APPROX_UPDATE_MS, 0f, networkListener, Looper.getMainLooper()) }
            }
        }
        try {
            fusedClient.requestLocationUpdates(
                com.google.android.gms.location.LocationRequest.Builder(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, APPROX_UPDATE_MS).build(),
                fusedCallback, Looper.getMainLooper(),
            ).addOnFailureListener { startNetworkFallback() }
        } catch (e: Exception) {
            startNetworkFallback()
        }
        lm.registerGnssStatusCallback(status, handler)
        if (lm.allProviders.contains(LocationManager.GPS_PROVIDER)) {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, UPDATE_MS, 0f, listener, Looper.getMainLooper())
        }
        // Keep "seconds since fix" fresh between callbacks.
        val tick = object : Runnable {
            override fun run() { push(); handler.postDelayed(this, UPDATE_MS) }
        }
        handler.postDelayed(tick, UPDATE_MS)
        push()
        awaitClose {
            handler.removeCallbacks(tick)
            lm.unregisterGnssStatusCallback(status)
            lm.removeUpdates(listener)
            runCatching { fusedClient.removeLocationUpdates(fusedCallback) }
            runCatching { lm.removeUpdates(networkListener) }
        }
    }

    /** True when the phone's location switch is on (separate from the app permission). */
    fun isDeviceLocationOn(): Boolean =
        androidx.core.location.LocationManagerCompat.isLocationEnabled(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)

    private companion object {
        const val UPDATE_MS = 1000L
        const val STALE_STATUS_MS = 4000L
        const val PERMISSION_POLL_MS = 1000L
        const val APPROX_FRESH_MS = 30_000L
        const val APPROX_UPDATE_MS = 4_000L
        const val DEFAULT_APPROX_M = 100f
    }
}
