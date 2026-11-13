package com.speedometer.app.core.map

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.speedometer.app.BuildConfig
import com.speedometer.app.core.designsystem.Danger
import com.speedometer.app.core.designsystem.Ok
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.cos
import kotlin.math.sin

/** Ask the map to move its camera to [point]; a new [id] makes the same point count again (for a centre-on-me button). */
@Immutable
data class CameraTarget(val point: LatLng, val id: Int = 0, val zoom: Double = 15.0)

@Immutable
data class MapLine(val points: List<LatLng>, val color: Color, val widthDp: Float = 5f)

/** A dot on the map: start, end, current position. */
@Immutable
data class MapDot(val at: LatLng, val color: Color, val radiusDp: Float = 6f, val stroke: Color? = null)

/** A circular saved zone. */
@Immutable
data class MapZone(val at: LatLng, val radiusM: Int, val color: Color)

/**
 * Map with OpenStreetMap raster tiles (no key), darkened to match the app. Lines, dots and zones are drawn from
 * GeoJSON sources. Tile use must stay inside the OSM tile usage policy; heavy use needs another tile source.
 */
@Composable
fun RouteMap(
    lines: List<MapLine>,
    modifier: Modifier = Modifier,
    dots: List<MapDot> = emptyList(),
    zones: List<MapZone> = emptyList(),
    /** Camera: fit everything once, or keep the last point in view. */
    follow: Boolean = false,
    /** Shows + and - buttons on the map. Pinch and double tap always work. */
    zoomControls: Boolean = false,
    /** When set, the camera moves to this route (a selected trip); back to null, it shows everything again. */
    focus: List<LatLng>? = null,
    /** Zoom out this many levels after fitting everything, for more surroundings. */
    zoomOutBy: Double = 0.0,
    /** The fit never zooms in past this level, so a few close-together routes still show their surroundings. */
    maxFitZoom: Double = 19.0,
    /** Called with the point when the map is tapped. */
    onMapClick: ((LatLng) -> Unit)? = null,
    /** False: the map does not move by itself when lines or dots change; the caller drives the camera with [flyTo]. */
    autoFit: Boolean = true,
    flyTo: CameraTarget? = null,
    /** Called with the middle of the map whenever the camera stops moving (and once when the map is ready). */
    onCameraIdle: ((LatLng) -> Unit)? = null,
    /** Room kept free at the bottom when the map fits everything, for a card drawn over the map. */
    fitBottomPaddingDp: Int = 0,
    /** Room kept free at the top (a header over the map) and at the left (a side panel) when the map fits everything. */
    fitTopPaddingDp: Int = 0,
    fitLeftPaddingDp: Int = 0,
    /** Extra top space for the built-in compass when the map is drawn behind a status bar or header. */
    compassTopPaddingDp: Int = 0,
) {
    val dens = LocalContext.current.resources.displayMetrics.density
    val bottomPx = (fitBottomPaddingDp * dens).toInt()
    val topPx = (fitTopPaddingDp * dens).toInt()
    val leftPx = (fitLeftPaddingDp * dens).toInt()
    val compassTopPx = (compassTopPaddingDp * dens).toInt()
    val idleHandler by androidx.compose.runtime.rememberUpdatedState(onCameraIdle)
    val clickHandler by androidx.compose.runtime.rememberUpdatedState(onMapClick)
    fun org.maplibre.android.maps.MapLibreMap.looseFit() {
        val z = (cameraPosition.zoom - zoomOutBy).coerceAtMost(maxFitZoom)
        if (z != cameraPosition.zoom) moveCamera(CameraUpdateFactory.zoomTo(z))
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember(context, lifecycle) { createMapView(context) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var fitKey by remember { mutableIntStateOf(-1) }
    var userPanning by remember { mutableStateOf(false) }
    LaunchedEffect(flyTo) { if (flyTo != null) userPanning = false }
    var introDone by remember { mutableStateOf(false) }
    var introSettled by remember { mutableStateOf(false) }

    DisposableEffect(lifecycle, mapView) {
        var disposed = false
        map = null
        styleReady = false
        fitKey = -1
        introDone = false
        introSettled = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        mapView.onCreate(null)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        mapView.getMapAsync { m ->
            if (disposed) return@getMapAsync
            map = m
            m.setMaxZoomPreference(MAX_RASTER_ZOOM)
            m.uiSettings.isLogoEnabled = false
            // The map stays north-up everywhere: no compass button to overlap other buttons, and no accidental rotation.
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            if (compassTopPx > 0) m.uiSettings.setCompassMargins(0, compassTopPx, 0, 0)
            m.addOnMapClickListener { p -> clickHandler?.let { it(p); true } ?: false }
            m.addOnCameraIdleListener { m.cameraPosition.target?.let { t -> idleHandler?.invoke(t) } }
            m.uiSettings.isAttributionEnabled = false
            m.uiSettings.setCompassGravity(android.view.Gravity.TOP or android.view.Gravity.END)
            m.uiSettings.setCompassMargins(0, if (compassTopPx > 0) compassTopPx else (24 * dens).toInt(), (24 * dens).toInt(), 0)
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    userPanning = true
                }
            }
            m.setStyle(Style.Builder().fromJson(DARK_OSM_STYLE)) { style ->
                if (disposed) return@setStyle
                addLayers(style)
                styleReady = true
            }
        }
        onDispose {
            disposed = true
            map = null
            styleReady = false
            fitKey = -1
            lifecycle.removeObserver(observer)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    // True once the opening zoom has finished, so the follow loop below never cuts it short.
    // Keyed on "there is a point" rather than on the lines and dots themselves: those change every second during a trip, which
    // cancelled the opening zoom half way, so introSettled never became true and the follow loop below never ran.
    val hasPoint = dots.isNotEmpty() || lines.any { it.points.isNotEmpty() }
    val latestLines by androidx.compose.runtime.rememberUpdatedState(lines)
    val latestDots by androidx.compose.runtime.rememberUpdatedState(dots)
    LaunchedEffect(styleReady, hasPoint) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady || introDone) return@LaunchedEffect
        val initialPt = latestDots.lastOrNull()?.at ?: latestLines.lastOrNull()?.points?.lastOrNull() ?: return@LaunchedEffect
        introDone = true
        if (!follow) { introSettled = true; return@LaunchedEffect }
        // Opening: start a little out from the final zoom and ease in over under a second. Zooming in from a world-sized view
        // makes the map load and stretch tiles at every level on the way, which looked laggy and blurry.
        m.moveCamera(CameraUpdateFactory.newLatLngZoom(initialPt, FOLLOW_ZOOM - INTRO_ZOOM_IN))
        delay(120L)
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(initialPt, FOLLOW_ZOOM), INTRO_MS)
        delay(INTRO_MS.toLong() + 60L)
        introSettled = true
    }

    // A. Route + zones effect (decoupled from dots)
    LaunchedEffect(lines, zones, styleReady) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady || !introDone) return@LaunchedEffect
        if (com.speedometer.app.core.data.PerformanceToggles.perfReduceMapUpdateRate) {
            delay(1500L)
        }
        val (linesText, zonesText) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val optLines = lines.map { it.copy(points = downsample(it.points, 1000)) }
            Pair(linesJson(optLines), zonesJson(zones))
        }
        m.getStyle { style ->
            if (map === m) {
                style.getSourceAs<GeoJsonSource>(LINES)?.setGeoJson(linesText)
                style.getSourceAs<GeoJsonSource>(ZONES)?.setGeoJson(zonesText)
            }
        }
        val all = lines.flatMap { it.points } + zones.map { it.at }
        if (all.isEmpty() || !autoFit) return@LaunchedEffect
        val key = lines.sumOf { it.points.size } * 31 + zones.size
        if (key != fitKey) {
            if (all.size == 1) m.moveCamera(CameraUpdateFactory.newLatLngZoom(all.first(), FOLLOW_ZOOM))
            else {
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(all).build(), FIT_PADDING + leftPx, FIT_PADDING + topPx, FIT_PADDING, FIT_PADDING + bottomPx))
                m.looseFit()
            }
            fitKey = key
        }
    }

    // B. Dots effect (decoupled from route lines)
    LaunchedEffect(dots, lines, styleReady) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val routeEndpoints = lines.flatMap { line ->
            if (line.points.size < 2) emptyList()
            else listOf(
                MapDot(line.points.first(), Ok, 9f, Color.White),
                MapDot(line.points.last(), Danger, 9f, Color.White),
            )
        }
        val endpointPoints = routeEndpoints.map { it.at }
        val visibleDots = dots.filterNot { dot ->
            endpointPoints.any { it.latitude == dot.at.latitude && it.longitude == dot.at.longitude }
        }
        val dotsText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            dotsJson(visibleDots + routeEndpoints)
        }
        m.getStyle { style ->
            if (map === m) style.getSourceAs<GeoJsonSource>(DOTS)?.setGeoJson(dotsText)
        }
    }

    // C. Camera follow effect for smooth continuous tracking
    val followPoint = dots.lastOrNull()?.at ?: lines.lastOrNull()?.points?.lastOrNull()
    val latestFollowPoint by androidx.compose.runtime.rememberUpdatedState(followPoint)

    LaunchedEffect(follow, styleReady, introSettled) {
        while (true) {
            delay(1000L)
            if (!follow || !styleReady || !introSettled) continue
            val pt = latestFollowPoint ?: continue
            map?.easeCamera(CameraUpdateFactory.newLatLng(pt), 800)
        }
    }

    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(flyTo, styleReady) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady || flyTo == null) return@LaunchedEffect
        userPanning = false
        val update = CameraUpdateFactory.newLatLngZoom(flyTo.point, flyTo.zoom)
        if (m.cameraPosition.zoom < 4.0) m.moveCamera(update) else m.animateCamera(update, FLY_MS)
    }
    LaunchedEffect(focus, styleReady) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        if (focus != null && focus.size >= 2) {
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(focus).build(), FIT_PADDING + 40 + leftPx, FIT_PADDING + 40 + topPx, FIT_PADDING + 40, FIT_PADDING + 40 + bottomPx))
            focused = true
        } else if (focused) {
            focused = false
            val all = lines.flatMap { it.points }
            if (all.size >= 2) {
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(all).build(), FIT_PADDING + leftPx, FIT_PADDING + topPx, FIT_PADDING, FIT_PADDING + bottomPx))
                m.looseFit()
            }
        }
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (zoomControls) {
            Column(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ZoomButton("+") { map?.animateCamera(CameraUpdateFactory.zoomIn(), 350) }
                ZoomButton("2212") { map?.animateCamera(CameraUpdateFactory.zoomOut(), 350) }
            }
        }
    }
}

private const val MIN_CAMERA_FOLLOW_INTERVAL_MS = 1000L
private const val MIN_CAMERA_FOLLOW_DISTANCE_M = 2.0

private fun createMapView(context: Context): MapView {
    MapLibre.getInstance(context)
    return MapView(context).also { v ->
        v.setOnTouchListener { view, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) view.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
    }
}

@Composable
private fun ZoomButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color(0xE614181D)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
}

private const val LINES = "lines"
private const val DOTS = "dots"
private const val ZONES = "zones"
private const val FOLLOW_ZOOM = 15.0
private const val INTRO_ZOOM_IN = 1.2
private const val INTRO_MS = 800
private const val FOLLOW_EASE_MS = 600
private const val FLY_MS = 700
private const val MAX_RASTER_ZOOM = 19.0
private const val FIT_PADDING = 80

private fun downsample(points: List<LatLng>, max: Int = 1000): List<LatLng> {
    if (points.size <= max) return points
    val lastIndex = points.lastIndex
    val step = lastIndex / (max - 1).toDouble()
    return List(max - 1) { points[(it * step).toInt()] } + points.last()
}

private fun addLayers(style: Style) {
    style.addSource(GeoJsonSource(ZONES, EMPTY))
    style.addSource(GeoJsonSource(LINES, EMPTY))
    style.addSource(GeoJsonSource(DOTS, EMPTY))
    style.addLayer(FillLayer("zones-fill", ZONES).withProperties(
        PropertyFactory.fillColor(Expression.get("color")), PropertyFactory.fillOpacity(0.18f)))
    style.addLayer(LineLayer("zones-line", ZONES).withProperties(
        PropertyFactory.lineColor(Expression.get("color")), PropertyFactory.lineWidth(2f)))
    style.addLayer(LineLayer("lines", LINES).withProperties(
        PropertyFactory.lineColor(Expression.get("color")), PropertyFactory.lineWidth(Expression.get("w")),
        PropertyFactory.lineOpacity(Expression.get("opacity")),
        PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")))
    style.addLayer(CircleLayer("dots", DOTS).withProperties(
        PropertyFactory.circleColor(Expression.get("color")), PropertyFactory.circleRadius(Expression.get("r")),
        PropertyFactory.circleStrokeColor(Expression.get("stroke")), PropertyFactory.circleStrokeWidth(Expression.get("sw"))))
}

private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

private fun linesJson(lines: List<MapLine>): String = lines.filter { it.points.size >= 2 }.joinToString(
    prefix = """{"type":"FeatureCollection","features":[""", postfix = "]}", separator = ",",
) { l ->
    val coords = l.points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    """{"type":"Feature","properties":{"color":"${hex(l.color)}","opacity":${l.color.alpha},"w":${l.widthDp}},"geometry":{"type":"LineString","coordinates":[$coords]}}"""
}

private fun dotsJson(dots: List<MapDot>): String = dots.joinToString(
    prefix = """{"type":"FeatureCollection","features":[""", postfix = "]}", separator = ",",
) { d ->
    val stroke = d.stroke?.let { hex(it) } ?: hex(d.color)
    val sw = if (d.stroke != null) 3 else 0
    """{"type":"Feature","properties":{"color":"${hex(d.color)}","r":${d.radiusDp},"stroke":"$stroke","sw":$sw},"geometry":{"type":"Point","coordinates":[${d.at.longitude},${d.at.latitude}]}}"""
}

private fun zonesJson(zones: List<MapZone>): String = zones.joinToString(
    prefix = """{"type":"FeatureCollection","features":[""", postfix = "]}", separator = ",",
) { z ->
    val ring = (0..48).joinToString(",") { i ->
        val a = Math.toRadians(i * 360.0 / 48)
        val dLat = z.radiusM * cos(a) / 111_320.0
        val dLon = z.radiusM * sin(a) / (111_320.0 * cos(Math.toRadians(z.at.latitude)))
        "[${z.at.longitude + dLon},${z.at.latitude + dLat}]"
    }
    """{"type":"Feature","properties":{"color":"${hex(z.color)}"},"geometry":{"type":"Polygon","coordinates":[[$ring]]}}"""
}

/** OSM raster tiles in their normal colours, so streets and labels are easy to read. */
private val DARK_OSM_STYLE = """{
  "version": 8,
  "sources": {"osm": {"type": "raster", "tiles": ["${BuildConfig.TILE_URL}"], "tileSize": 256,
    "maxzoom": 19, "attribution": "© OpenStreetMap contributors"}},
  "layers": [
    {"id": "bg", "type": "background", "paint": {"background-color": "#F2EFE9"}},
    {"id": "osm", "type": "raster", "source": "osm",
     "paint": {"raster-brightness-max": 1.0, "raster-saturation": 0.0, "raster-contrast": 0.0}}
  ]
}"""
