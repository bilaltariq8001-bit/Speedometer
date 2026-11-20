package com.speedometer.app.core.map

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.speedometer.app.core.common.isNetworkConnected
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * Keeps the map fully usable with no internet.
 *
 * While the phone is online, two kinds of map are saved into MapLibre's offline database (never evicted, never expired):
 * - a world overview (zoom 0 to [WORLD_MAX_ZOOM]): countries, coasts, big cities, so zooming out offline never goes blank;
 * - the streets around the user and around any place a map shows (up to [RADIUS_KM] km each way, zoom [AREA_MIN_ZOOM] to
 *   [AREA_MAX_ZOOM]). The tiles are vector tiles, so offline they stay sharp at every zoom up to street level.
 * The style, its icons and its fonts are saved with them. Up to [MAX_AREAS] areas are kept (home, work, places driven to);
 * the oldest goes when a new one is added. Every [REFRESH_DAYS] days an area is saved again (the tile server publishes new
 * map data weekly and the old files go away), and the old copy is deleted once the new one is complete.
 *
 * Tile server: OpenFreeMap allows offline downloads of at most 3,000 tiles / 50 MB each, 4 parallel requests and an app
 * User-Agent (see [MapSetup]); every area is sized to stay well inside that ([AREA_TILE_LIMIT]).
 */
object OfflineMaps {
    private const val TAG = "OfflineMaps"
    private const val RADIUS_KM = 12.0
    private const val MIN_RADIUS_KM = 4.0
    private const val SAME_AREA_KM = 5.0
    private const val AREA_MIN_ZOOM = 4.0
    private const val AREA_MAX_ZOOM = 14.0 // vector source max zoom: deeper zooms are drawn from these tiles
    private const val WORLD_MAX_ZOOM = 3.0
    private const val AREA_TILE_LIMIT = 350
    private const val MAX_AREAS = 8
    private const val REFRESH_DAYS = 7
    private const val DOWNLOAD_RUN_MS = 15 * 60_000L
    private const val KIND_AREA = "area"
    private const val KIND_WORLD = "world"
    private const val DAY_MS = 24 * 60 * 60_000L

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var busy = false
    private var pending: Pair<Double, Double>? = null

    /**
     * Makes sure the area around [lat],[lon] (and the world overview) is saved for offline use. Cheap to call often: it
     * returns at once when everything is saved and fresh, or when the phone is offline. While another download runs, the
     * newest request waits and starts when that one ends. An unfinished download is resumed.
     */
    fun ensureArea(context: Context, lat: Double, lon: Double) {
        val app = context.applicationContext
        main.post {
            if (!app.isNetworkConnected()) return@post
            if (busy) { pending = lat to lon; return@post }
            busy = true
            runCatching {
                MapSetup.init(app)
                val manager = OfflineManager.getInstance(app)
                manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                    override fun onList(offlineRegions: Array<OfflineRegion>?) {
                        runCatching { plan(app, manager, offlineRegions.orEmpty().toList(), lat, lon) }
                            .onFailure { Log.w(TAG, "plan failed", it); finish(app) }
                    }

                    override fun onError(error: String) {
                        Log.w(TAG, "list failed: $error"); finish(app)
                    }
                })
            }.onFailure { Log.w(TAG, "ensureArea failed", it); finish(app) }
        }
    }

    private fun plan(app: Context, manager: OfflineManager, regions: List<OfflineRegion>, lat: Double, lon: Double) {
        val now = System.currentTimeMillis()
        val areas = regions.filter { kindOf(it) == KIND_AREA }
        val worlds = regions.filter { kindOf(it) == KIND_WORLD }
        // Regions saved by the old raster version of the map are useless now: remove them.
        regions.filter { kindOf(it) == null }.forEach { delete(it) }

        val near = areas.filter { r -> centerOf(r)?.let { distanceKm(it.first, it.second, lat, lon) < SAME_AREA_KM } == true }
            .maxByOrNull { createdAt(it) }
        val world = worlds.maxByOrNull { createdAt(it) }
        when {
            near == null -> {
                // Make room first: the oldest saved areas go.
                areas.sortedBy { createdAt(it) }.take((areas.size - MAX_AREAS + 1).coerceAtLeast(0)).forEach { delete(it) }
                createArea(app, manager, lat, lon, replaces = emptyList())
            }
            now - createdAt(near) > REFRESH_DAYS * DAY_MS -> createArea(app, manager, lat, lon, replaces = listOf(near))
            world == null -> createWorld(app, manager, replaces = emptyList())
            now - createdAt(world) > REFRESH_DAYS * DAY_MS -> createWorld(app, manager, replaces = listOf(world))
            else -> resumeIfUnfinished(app, near) {
                resumeIfUnfinished(app, world) { finish(app) }
            }
        }
    }

    private fun resumeIfUnfinished(app: Context, region: OfflineRegion, otherwise: () -> Unit) {
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                if (status == null || status.isComplete) otherwise() else download(app, region, emptyList())
            }

            override fun onError(error: String?) = otherwise()
        })
    }

    private fun createArea(app: Context, manager: OfflineManager, lat: Double, lon: Double, replaces: List<OfflineRegion>) {
        // Biggest radius (up to RADIUS_KM) whose tile count stays inside the tile server's offline limit.
        var radius = RADIUS_KM
        var bounds = boundsAround(lat, lon, radius)
        while (radius > MIN_RADIUS_KM && tileCount(bounds, AREA_MIN_ZOOM.toInt(), AREA_MAX_ZOOM.toInt()) > AREA_TILE_LIMIT) {
            radius -= 1.0
            bounds = boundsAround(lat, lon, radius)
        }
        create(app, manager, bounds, AREA_MIN_ZOOM, AREA_MAX_ZOOM, meta(KIND_AREA, lat, lon), replaces)
    }

    private fun createWorld(app: Context, manager: OfflineManager, replaces: List<OfflineRegion>) {
        val bounds = LatLngBounds.from(85.0, 180.0, -85.0, -180.0)
        create(app, manager, bounds, 0.0, WORLD_MAX_ZOOM, meta(KIND_WORLD, 0.0, 0.0), replaces)
    }

    private fun create(
        app: Context, manager: OfflineManager, bounds: LatLngBounds, minZoom: Double, maxZoom: Double,
        metadata: JSONObject, replaces: List<OfflineRegion>,
    ) {
        val ratio = app.resources.displayMetrics.density.coerceIn(1f, 3f)
        val definition = OfflineTilePyramidRegionDefinition(MapSetup.styleUrl, bounds, minZoom, maxZoom, ratio)
        manager.createOfflineRegion(definition, metadata.toString().toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) = download(app, offlineRegion, replaces)
            override fun onError(error: String) { Log.w(TAG, "create failed: $error"); finish(app) }
        })
    }

    private fun download(app: Context, region: OfflineRegion, replaces: List<OfflineRegion>) {
        val runToken = Any()
        currentRun = runToken
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                if (status.isComplete) {
                    Log.d(TAG, "saved ${kindOf(region)}: ${status.completedResourceCount} files, ${status.completedResourceSize / 1024} KB")
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    region.setObserver(null)
                    // The new copy is complete, so the old one it replaces can go (frees its outdated tiles).
                    replaces.forEach { delete(it) }
                    if (currentRun === runToken) finish(app, again = true)
                }
            }

            override fun onError(error: OfflineRegionError) {
                // MapLibre retries failed files by itself and waits for the connection to come back; just log it.
                Log.w(TAG, "download error: ${error.reason} ${error.message}")
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                Log.w(TAG, "tile limit $limit exceeded")
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                region.setObserver(null)
                delete(region)
                if (currentRun === runToken) finish(app)
            }
        })
        activeRegion = region
        if (loadingMaps.isEmpty()) { paused = false; region.setDownloadState(OfflineRegion.STATE_ACTIVE) } else paused = true
        // Never stay busy forever: after a while stop this run; the next call resumes where it left off.
        main.postDelayed({
            if (currentRun === runToken && busy) {
                runCatching { region.setDownloadState(OfflineRegion.STATE_INACTIVE); region.setObserver(null) }
                finish(app)
            }
        }, DOWNLOAD_RUN_MS)
    }

    @Volatile private var currentRun: Any? = null

    // ------------------------------------------------------------ yield to the map on screen
    // The tile server allows only 4 requests at a time, so a background download would make the visible map wait for its
    // tiles. While any map on screen is loading or moving, the download pauses; it resumes 3 s after every map is idle.
    private var activeRegion: OfflineRegion? = null
    private var paused = false
    private val loadingMaps = HashSet<Any>()
    private const val RESUME_AFTER_IDLE_MS = 3_000L
    private val resume = Runnable {
        if (loadingMaps.isEmpty() && paused) {
            paused = false
            activeRegion?.let { runCatching { it.setDownloadState(OfflineRegion.STATE_ACTIVE) } }
        }
    }

    /** Called by each map: [loading] true while it loads tiles or moves, false when it is idle or gone. */
    fun setMapBusy(owner: Any, loading: Boolean) {
        main.post {
            if (loading) loadingMaps.add(owner) else loadingMaps.remove(owner)
            main.removeCallbacks(resume)
            if (loadingMaps.isNotEmpty()) {
                if (!paused) {
                    paused = true
                    activeRegion?.let { runCatching { it.setDownloadState(OfflineRegion.STATE_INACTIVE) } }
                }
            } else if (paused) {
                main.postDelayed(resume, RESUME_AFTER_IDLE_MS)
            }
        }
    }
    private var lastPoint: Pair<Double, Double>? = null

    /** Ends a run. With [again], checks once more (the world overview may still be missing), else runs a waiting request. */
    private fun finish(app: Context, again: Boolean = false) {
        currentRun = null
        activeRegion = null
        busy = false
        val next = pending ?: if (again) lastPoint else null
        pending = null
        if (next != null) ensureArea(app, next.first, next.second)
    }

    private fun delete(region: OfflineRegion) {
        runCatching {
            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() = Unit
                override fun onError(error: String) = Unit
            })
        }
    }

    private fun meta(kind: String, lat: Double, lon: Double): JSONObject {
        if (kind == KIND_AREA) lastPoint = lat to lon
        return JSONObject().put("kind", kind).put("lat", lat).put("lon", lon).put("t", System.currentTimeMillis())
    }

    private fun metaOf(region: OfflineRegion): JSONObject? = runCatching { JSONObject(String(region.metadata)) }.getOrNull()

    private fun kindOf(region: OfflineRegion): String? = metaOf(region)?.optString("kind")?.takeIf { it.isNotEmpty() }

    private fun centerOf(region: OfflineRegion): Pair<Double, Double>? =
        metaOf(region)?.let { if (it.has("lat") && it.has("lon")) it.getDouble("lat") to it.getDouble("lon") else null }

    private fun createdAt(region: OfflineRegion): Long = metaOf(region)?.optLong("t", 0L) ?: 0L

    private fun boundsAround(lat: Double, lon: Double, radiusKm: Double): LatLngBounds {
        val dLat = radiusKm / 111.32
        val dLon = radiusKm / (111.32 * cos(Math.toRadians(lat)).coerceAtLeast(0.1))
        return LatLngBounds.Builder()
            .include(LatLng((lat + dLat).coerceAtMost(85.0), lon + dLon))
            .include(LatLng((lat - dLat).coerceAtLeast(-85.0), lon - dLon))
            .build()
    }

    /** Number of map tiles that cover [bounds] from zoom [minZ] to [maxZ] (web mercator). */
    private fun tileCount(bounds: LatLngBounds, minZ: Int, maxZ: Int): Int {
        fun x(lon: Double, n: Double) = floor((lon + 180.0) / 360.0 * n).toInt()
        fun y(lat: Double, n: Double): Int {
            val r = Math.toRadians(lat)
            return floor((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * n).toInt()
        }
        var total = 0
        for (z in minZ..maxZ) {
            val n = 2.0.pow(z)
            val cols = x(bounds.longitudeEast, n) - x(bounds.longitudeWest, n) + 1
            val rows = y(bounds.latitudeSouth, n) - y(bounds.latitudeNorth, n) + 1
            total += cols * rows
        }
        return total
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, r)
        return r[0] / 1000.0
    }
}
