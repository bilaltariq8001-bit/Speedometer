package com.speedometer.app.core.map

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.speedometer.app.core.common.isNetworkConnected
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.io.File
import kotlin.math.cos

/**
 * Keeps the map usable with no internet. While the phone is online, the streets around the user (about [RADIUS_KM] km each
 * way, zoom [MIN_ZOOM] to [MAX_ZOOM]) are downloaded once into MapLibre's offline database, which never expires them. With
 * Wi-Fi and mobile data off, the map then draws those streets from the phone, plus everything already seen (ambient cache).
 * Up to [MAX_AREAS] areas are kept (home, work, places driven to); the oldest is removed when a new one is added.
 *
 * Tile server note: the public OpenStreetMap servers do not allow bulk downloads. Before release, set TILE_URL in
 * local.properties to a tile provider whose terms allow offline use (MapTiler, Stadia, Thunderforest, or your own server).
 */
object OfflineMaps {
    private const val TAG = "OfflineMaps"
    private const val RADIUS_KM = 6.0
    private const val SAME_AREA_KM = 3.0
    private const val MIN_ZOOM = 10.0
    private const val MAX_ZOOM = 16.0
    private const val MAX_AREAS = 6
    private const val STYLE_FILE = "offline_map_style.json"
    private const val DOWNLOAD_RUN_MS = 15 * 60_000L

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var busy = false

    /** Writes the map style (same tile source as the map) to a file, so an offline region can refer to it by URL. */
    private fun styleUrl(context: Context): String {
        val file = File(context.filesDir, STYLE_FILE)
        val json = MAP_STYLE_JSON
        if (!file.exists() || file.readText() != json) file.writeText(json)
        return "file://${file.absolutePath}"
    }

    /**
     * Makes sure the area around [lat],[lon] is saved for offline use. Cheap to call often (every Home visit): it returns at
     * once when the area is already saved, when the phone is offline, or while another download runs. An unfinished download
     * of the same area is resumed.
     */
    fun ensureArea(context: Context, lat: Double, lon: Double) {
        val app = context.applicationContext
        main.post {
            if (busy || !app.isNetworkConnected()) return@post
            busy = true
            runCatching {
                MapLibre.getInstance(app)
                val manager = OfflineManager.getInstance(app)
                manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                    override fun onList(offlineRegions: Array<OfflineRegion>?) {
                        val regions = offlineRegions.orEmpty().toList()
                        val near = regions.firstOrNull { r -> centerOf(r)?.let { distanceKm(it.first, it.second, lat, lon) < SAME_AREA_KM } == true }
                        if (near != null) resumeIfUnfinished(near) else createArea(app, manager, regions, lat, lon)
                    }

                    override fun onError(error: String) {
                        Log.w(TAG, "list failed: $error"); busy = false
                    }
                })
            }.onFailure { Log.w(TAG, "ensureArea failed", it); busy = false }
        }
    }

    private fun resumeIfUnfinished(region: OfflineRegion) {
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                if (status == null || status.isComplete) { busy = false; return }
                download(region)
            }

            override fun onError(error: String?) { busy = false }
        })
    }

    private fun createArea(context: Context, manager: OfflineManager, existing: List<OfflineRegion>, lat: Double, lon: Double) {
        // Make room first: the oldest saved area goes.
        if (existing.size >= MAX_AREAS) {
            existing.minByOrNull { createdAt(it) }?.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() = Unit
                override fun onError(error: String) = Unit
            })
        }
        val dLat = RADIUS_KM / 111.32
        val dLon = RADIUS_KM / (111.32 * cos(Math.toRadians(lat)).coerceAtLeast(0.1))
        val bounds = LatLngBounds.Builder()
            .include(LatLng(lat + dLat, lon + dLon))
            .include(LatLng(lat - dLat, lon - dLon))
            .build()
        val definition = OfflineTilePyramidRegionDefinition(styleUrl(context), bounds, MIN_ZOOM, MAX_ZOOM, 1f)
        val metadata = JSONObject().put("lat", lat).put("lon", lon).put("t", System.currentTimeMillis()).toString().toByteArray()
        manager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) = download(offlineRegion)
            override fun onError(error: String) { Log.w(TAG, "create failed: $error"); busy = false }
        })
    }

    private fun download(region: OfflineRegion) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                if (status.isComplete) {
                    Log.d(TAG, "area saved: ${status.completedResourceCount} tiles, ${status.completedResourceSize / 1024} KB")
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    region.setObserver(null)
                    busy = false
                }
            }

            override fun onError(error: OfflineRegionError) {
                // MapLibre retries failed tiles by itself (and waits for the connection to come back); just log it.
                Log.w(TAG, "download error: ${error.reason} ${error.message}")
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                busy = false
            }
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        // Never stay "busy" forever: after a while stop this run; the next Home visit resumes where it left off.
        main.postDelayed({
            if (busy) {
                runCatching { region.setDownloadState(OfflineRegion.STATE_INACTIVE); region.setObserver(null) }
                busy = false
            }
        }, DOWNLOAD_RUN_MS)
    }

    private fun meta(region: OfflineRegion): JSONObject? = runCatching { JSONObject(String(region.metadata)) }.getOrNull()

    private fun centerOf(region: OfflineRegion): Pair<Double, Double>? =
        meta(region)?.let { if (it.has("lat") && it.has("lon")) it.getDouble("lat") to it.getDouble("lon") else null }

    private fun createdAt(region: OfflineRegion): Long = meta(region)?.optLong("t", 0L) ?: 0L

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, r)
        return r[0] / 1000.0
    }
}
