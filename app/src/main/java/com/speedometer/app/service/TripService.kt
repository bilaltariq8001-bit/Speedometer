package com.speedometer.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.speedometer.app.MainActivity
import com.speedometer.app.BuildConfig
import com.speedometer.app.R
import com.speedometer.app.core.alerts.GuardAlerts
import com.speedometer.app.core.data.AppLanguage
import com.speedometer.app.core.data.Settings
import com.speedometer.app.core.database.SavedTripsRepository
import com.speedometer.app.core.trip.RouteGaps
import com.speedometer.app.core.trip.TripFormat
import com.speedometer.app.core.data.SettingsRepository
import com.speedometer.app.core.database.ZoneEntity
import com.speedometer.app.core.database.ZonesRepository
import com.speedometer.app.core.trip.LocationSample
import com.speedometer.app.core.trip.GeoMath
import com.speedometer.app.core.trip.ZoneMath
import com.speedometer.app.core.trip.TripPhase
import com.speedometer.app.core.trip.TripRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service (location type) that owns location updates while a trip is recording,
 * so the UI can come and go. Pause and Stop are actions on its ongoing notification.
 */
@AndroidEntryPoint
class TripService : Service() {
    @Inject lateinit var trips: TripRepository
    @Inject lateinit var zonesRepo: ZonesRepository
    @Inject lateinit var alerts: GuardAlerts
    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var savedTrips: SavedTripsRepository

    @Volatile private var zones: List<ZoneEntity> = emptyList()
    @Volatile private var settingsNow = Settings()
    private val lastZoneDistance = mutableMapOf<Long, Double>()
    private val announcedZones = mutableSetOf<Long>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var lastFixElapsedMs = 0L
    private var running = false
    private var userEnded = false

    /** Outlives the service, so a trip can still be saved while it is being destroyed. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastLocation: Location? = null

    private var lastGpsMs = 0L
    private var lastAcceptedMs = 0L

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) android.util.Log.d("TripServiceDiag", "Service onCreate()")
    }

    /** The phone's own GPS, the same source the Home signal pill and GPS status read. */
    private val gpsListener = object : android.location.LocationListener {
        override fun onLocationChanged(location: Location) {
            if (BuildConfig.DEBUG) dbgRaw++
            handle(location, fromGps = true)
        }
        override fun onProviderEnabled(provider: String) {
            if (BuildConfig.DEBUG) android.util.Log.d("TripServiceDiag", "GPS provider enabled: $provider")
        }
        override fun onProviderDisabled(provider: String) {
            if (BuildConfig.DEBUG) android.util.Log.d("TripServiceDiag", "GPS provider disabled: $provider")
        }
    }

    // Debug-only counters for the once-a-second "TripLoc" log line: where fixes arrive and where they are turned away.
    private var dbgFused = 0
    private var dbgRaw = 0
    private var dbgAccepted = 0
    private var dbgRejApprox = 0
    private var dbgRejPreferred = 0
    private var dbgRejGap = 0
    private var dbgRegistered = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (BuildConfig.DEBUG) dbgFused++
            handle(result.lastLocation ?: return, fromGps = false)
        }
    }

    /**
     * Both sources feed one pipeline. The GPS provider is preferred; the fused provider only fills in when GPS has
     * been silent for 5 s, and two readings less than 0.4 s apart count once. Before, the trip listened to the fused
     * provider alone, which could stay quiet while the Home pill (raw GPS) still showed a strong signal.
     */
    private fun handle(loc: Location, fromGps: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val gpsGrade = fromGps || (loc.hasSpeed() && loc.hasAccuracy() && loc.accuracy <= FUSED_GPS_GRADE_M)
        if (BuildConfig.DEBUG) {
            android.util.Log.d(
                "TripServiceDiag",
                "handle() source=${if (fromGps) "RAW" else "FUSED"} time=${loc.time} elapsed=$now " +
                    "accuracy=${if (loc.hasAccuracy()) loc.accuracy else null} speed=${if (loc.hasSpeed()) loc.speed else null} " +
                    "hasSpeed=${loc.hasSpeed()} hasAccuracy=${loc.hasAccuracy()} lastGpsMs=$lastGpsMs lastAcceptedMs=$lastAcceptedMs lastFixElapsedMs=$lastFixElapsedMs gpsGrade=$gpsGrade",
            )
        }
        if (!gpsGrade) {
            if (BuildConfig.DEBUG) android.util.Log.d("TripServiceDiag", "Rejected sample: source=${if (fromGps) "RAW" else "FUSED"} reason=GPS_GRADE_FAILED accuracy=${if (loc.hasAccuracy()) loc.accuracy else null}")
            dbgRejApprox++
            trips.onApproximate(if (loc.hasAccuracy()) loc.accuracy else UNKNOWN_ACCURACY_M, loc.latitude, loc.longitude)
            return
        }
        if (!fromGps && now - lastGpsMs < GPS_PREFERRED_MS) {
            if (BuildConfig.DEBUG) android.util.Log.d("TripServiceDiag", "Rejected sample: source=FUSED reason=GPS_PREFERRED_ACTIVE delta=${now - lastGpsMs}ms")
            dbgRejPreferred++
            return
        }
        if (now - lastAcceptedMs < MIN_GAP_MS) {
            if (BuildConfig.DEBUG) android.util.Log.d("TripServiceDiag", "Rejected sample: source=${if (fromGps) "RAW" else "FUSED"} reason=MIN_GAP_ACTIVE delta=${now - lastAcceptedMs}ms")
            dbgRejGap++
            return
        }
        if (BuildConfig.DEBUG) {
            android.util.Log.d("TripServiceDiag", "Accepted sample: source=${if (fromGps) "RAW" else "FUSED"} speed=${speedOf(loc)} accuracy=${if (loc.hasAccuracy()) loc.accuracy else null} calling trips.onSample()")
            dbgAccepted++
        }
        if (fromGps) lastGpsMs = now
        lastAcceptedMs = now
        run {
            lastFixElapsedMs = SystemClock.elapsedRealtime()
            val zoneLimit = ZoneMath.limitAt(zones, loc.latitude, loc.longitude)
            val set = settingsNow
            when {
                zoneLimit != null -> trips.setLimit(zoneLimit)
                set.speedLimitOn -> trips.setLimit(set.speedLimitKmh.toFloat(), 0f, userSet = true)
                else -> trips.setLimit(null)
            }
            checkZonesAhead(loc.latitude, loc.longitude)
            trips.onSample(
                LocationSample(
                    speedOf(loc), if (loc.hasAccuracy()) loc.accuracy else null, loc.time, loc.latitude, loc.longitude,
                    if (loc.hasBearing()) loc.bearing else Float.NaN,
                ),
            )
            lastLocation = loc
        }
    }

    private fun speedOf(loc: Location): Float {
        val prev = lastLocation
        val seconds = if (prev != null) (loc.time - prev.time) / 1000f else 0f
        val fromDistance = if (prev != null && seconds in MIN_DISTANCE_SPEED_S..MAX_DISTANCE_SPEED_S) prev.distanceTo(loc) / seconds else 0f
        if (loc.hasSpeed()) {
            return if (loc.speed < ZERO_SPEED_MS && fromDistance > MOVING_MS) fromDistance else loc.speed
        }
        return fromDistance
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        }
        when (intent?.action) {
            ACTION_PAUSE -> { trips.pause(); refreshNotification() }
            ACTION_RESUME -> { trips.resume(); refreshNotification() }
            ACTION_STOP -> { stopTrip(); return START_NOT_STICKY }
            ACTION_RESET -> { if (running) { trips.start(); lastFixElapsedMs = SystemClock.elapsedRealtime() } }
            ACTION_DISCARD -> { discardTrip(); return START_NOT_STICKY }
            else -> {
                if (!running) {
                    beginTrip()
                }
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun beginTrip() {
        if (running) return
        running = true
        trips.start()
        createChannel()
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        }
        lastFixElapsedMs = SystemClock.elapsedRealtime()
        holdWakeLock()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_MS)
            .setMinUpdateIntervalMillis(UPDATE_MS / 2)
            .setMinUpdateDistanceMeters(0f)
            .setMaxUpdateDelayMillis(0L)
            .setWaitForAccurateLocation(false)
            .build()
        lastGpsMs = 0L
        lastAcceptedMs = 0L
        runCatching {
            client.lastLocation.addOnSuccessListener { l ->
                if (l != null && System.currentTimeMillis() - l.time < SEED_MAX_AGE_MS) trips.seedKnown(l.latitude, l.longitude)
            }
        }
        val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        var locationProviderStarted = false
        // Fused location is helpful, but a failure here must not prevent direct GNSS from recording offline.
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper()).addOnFailureListener { e ->
                if (BuildConfig.DEBUG) android.util.Log.w("TripServiceDiag", "Fused location unavailable", e)
            }
            locationProviderStarted = true
        } catch (e: SecurityException) {
            if (BuildConfig.DEBUG) android.util.Log.e("TripServiceDiag", "SecurityException during fused location request", e)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.w("TripServiceDiag", "Fused location request failed; using device GPS", e)
        }
        try {
            if (lm.allProviders.contains(android.location.LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 0L, 0f, gpsListener, Looper.getMainLooper())
                locationProviderStarted = true
            }
        } catch (e: SecurityException) {
            if (BuildConfig.DEBUG) android.util.Log.e("TripServiceDiag", "SecurityException during device GPS request", e)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("TripServiceDiag", "Device GPS request failed", e)
        }
        if (!locationProviderStarted) {
            stopTrip()
            return
        }
        dbgRegistered = true
        watchSystemState()
        scope.launch { zonesRepo.zones.collect { zones = it } }
        var vehicleTaken = false
        scope.launch {
            settingsRepo.settings.collect {
                settingsNow = it
                if (!vehicleTaken) { trips.vehicle = it.vehicle.name; vehicleTaken = true }
            }
        }
        scope.launch {
            var lastTickMs = SystemClock.elapsedRealtime()
            var lastRestartMs = 0L
            var ticks = 0
            while (true) {
                delay(UPDATE_MS)
                val nowMs = SystemClock.elapsedRealtime()
                trips.tick((nowMs - lastTickMs) / 1000.0)
                lastTickMs = nowMs
                trips.onNoFix(((SystemClock.elapsedRealtime() - lastFixElapsedMs) / 1000).toInt())
                if (BuildConfig.DEBUG) logLocationHealth(nowMs)
                val silentSec = (SystemClock.elapsedRealtime() - lastFixElapsedMs) / 1000
                if (++ticks % 60 == 0) holdWakeLock()
                if (silentSec >= WATCHDOG_SILENT_S && nowMs - lastRestartMs >= WATCHDOG_RETRY_MS) {
                    lastRestartMs = nowMs
                    restartLocationUpdates()
                }
                val now = trips.state.value
                if (now.phase == TripPhase.Paused && !now.autoPaused &&
                    System.currentTimeMillis() - trips.pausedAtMs >= PAUSE_LIMIT_MS
                ) {
                    stopTrip()
                    return@launch
                }
                refreshNotification()
                if (!notificationShown && !AppVisibility.resumed.value) showNotification()
            }
        }
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private fun logLocationHealth(nowMs: Long) {
        val pm = getSystemService(android.os.PowerManager::class.java)
        android.util.Log.d(
            "TripLoc",
            "t=${trips.state.value.durationSec}s sinceAccepted=${(nowMs - lastAcceptedMs) / 1000.0}s fused/s=$dbgFused raw/s=$dbgRaw " +
                "accepted=$dbgAccepted rejected[approx=$dbgRejApprox preferred=$dbgRejPreferred gap=$dbgRejGap] registered=$dbgRegistered " +
                "screenOn=${pm.isInteractive} appVisible=${AppVisibility.resumed.value} idle=${pm.isDeviceIdleMode} powerSave=${pm.isPowerSaveMode} " +
                "speed=${trips.state.value.speedKmh} gps=${trips.state.value.gps}",
        )
        dbgFused = 0; dbgRaw = 0; dbgAccepted = 0; dbgRejApprox = 0; dbgRejPreferred = 0; dbgRejGap = 0
    }

    private val systemStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!running) return
            holdWakeLock()
            restartLocationUpdates()
        }
    }
    private var watchingSystemState = false

    private fun watchSystemState() {
        if (watchingSystemState) return
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(android.os.PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        androidx.core.content.ContextCompat.registerReceiver(this, systemStateReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        watchingSystemState = true
    }

    private fun holdWakeLock() {
        runCatching {
            val lock = wakeLock ?: getSystemService(android.os.PowerManager::class.java)
                .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "speedometer:trip").also { it.setReferenceCounted(false); wakeLock = it }
            lock.acquire(WAKE_LOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    @SuppressLint("MissingPermission")
    private fun restartLocationUpdates() {
        if (BuildConfig.DEBUG) android.util.Log.d("TripServiceDiag", "Watchdog executing restartLocationUpdates()")
        removeGpsListener()
        runCatching { client.removeLocationUpdates(callback) }
        dbgRegistered = false
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_MS)
            .setMinUpdateIntervalMillis(UPDATE_MS / 2)
            .setWaitForAccurateLocation(false)
            .build()
        runCatching { client.requestLocationUpdates(request, callback, Looper.getMainLooper()) }
            .onFailure { e -> if (BuildConfig.DEBUG) android.util.Log.w("TripServiceDiag", "Fused location restart failed", e) }
        runCatching {
            val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            if (lm.allProviders.contains(android.location.LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 0L, 0f, gpsListener, Looper.getMainLooper())
                dbgRegistered = true
            }
        }.onFailure { e -> if (BuildConfig.DEBUG) android.util.Log.e("TripServiceDiag", "Device GPS restart failed", e) }
    }

    private var notificationShown = true
    private var lastNotifiedSpeed = -1f
    private var lastNotifiedDistance = -1f
    private var lastNotifiedDuration = 0
    private var lastNotifyTimeMs = 0L

    private fun refreshNotification() {
        if (!running || !notificationShown || com.speedometer.app.core.data.PerformanceToggles.perfDisableNotificationUpdates) return
        val s = trips.state.value
        val now = SystemClock.elapsedRealtime()
        val speedDiff = kotlin.math.abs(s.speedKmh - lastNotifiedSpeed)
        val distDiff = kotlin.math.abs(s.distanceKm - lastNotifiedDistance)
        val timeDiff = kotlin.math.abs(s.durationSec - lastNotifiedDuration)
        
        val shouldUpdate = lastNotifyTimeMs == 0L ||
            now - lastNotifyTimeMs >= 3000L ||
            speedDiff >= 2f ||
            distDiff >= 0.1f ||
            timeDiff >= 30 ||
            s.phase != TripPhase.Recording

        if (shouldUpdate) {
            lastNotifiedSpeed = s.speedKmh
            lastNotifiedDistance = s.distanceKm
            lastNotifiedDuration = s.durationSec
            lastNotifyTimeMs = now
            runCatching {
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
                notificationShown = true
            }
        }
    }

    private fun hideNotification() {
        if (!running || !notificationShown) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notificationShown = false
    }

    private fun showNotification() {
        if (!running || notificationShown) return
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            notificationShown = true
        }
    }

    private fun checkZonesAhead(lat: Double, lon: Double) {
        zones.filter { it.enabled }.forEach { z ->
            val d = GeoMath.distanceM(lat, lon, z.lat, z.lon)
            val edge = d - z.radiusM
            val previous = lastZoneDistance.put(z.id, d)
            if (edge > ZONE_RESET_M) {
                announcedZones.remove(z.id)
            } else if (edge in 0.0..ZONE_AHEAD_M && previous != null && d < previous && announcedZones.add(z.id)) {
                alerts.announceZone(z.name, z.limitKmh.toFloat(), settingsNow)
            }
        }
    }

    private fun tripUnsaved() = trips.state.value.phase.let { it == TripPhase.Recording || it == TripPhase.Paused }

    private fun discardTrip() {
        userEnded = true
        trips.finish()
        trips.reset()
        client.removeLocationUpdates(callback)
        removeGpsListener()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun stopTrip() {
        userEnded = true
        if (tripUnsaved()) persistUnsaved(announce = false) else trips.finish()
        client.removeLocationUpdates(callback)
        removeGpsListener()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun removeGpsListener() {
        runCatching { (getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager).removeUpdates(gpsListener) }
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) android.util.Log.d("TripServiceDiag", "Service onDestroy()")
        releaseWakeLock()
        if (watchingSystemState) runCatching { unregisterReceiver(systemStateReceiver) }
        watchingSystemState = false
        client.removeLocationUpdates(callback)
        removeGpsListener()
        if (running && !userEnded && tripUnsaved()) persistUnsaved(announce = true)
        scope.cancel()
        super.onDestroy()
    }

    private fun persistUnsaved(announce: Boolean) {
        val state = trips.state.value
        val route = trips.routeForSave()
        val started = trips.startedAtMs
        val vehicle = trips.vehicle
        trips.finish()
        if (state.tooShortToSave) {
            trips.reset()
            return
        }
        ioScope.launch {
            savedTrips.save(state, started, TripFormat.title(started), RouteGaps.fill(route), vehicle)
            trips.reset()
            if (announce) postInterrupted(state.distanceKm)
        }
    }

    private fun postInterrupted(km: Float) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, getString(R.string.notif_alerts_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            INTERRUPTED_ID,
            NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_speed)
                .setColor(0xFFFFB020.toInt())
                .setContentTitle(getString(R.string.interrupted_title))
                .setContentText(getString(R.string.interrupted_body, km))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build(),
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val s = trips.state.value
        val paused = s.phase == TripPhase.Paused
        val mph = settingsNow.mph
        val speed = if (mph) s.speedKmh * 0.621371f else s.speedKmh
        val distance = if (mph) s.distanceKm * 0.621371f else s.distanceKm
        val time = "%d:%02d:%02d".format(s.durationSec / 3600, s.durationSec / 60 % 60, s.durationSec % 60)
        val line = getString(R.string.notif_stats, speed.toInt(), if (mph) "mph" else "km/h", distance, if (mph) "mi" else "km")
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setAction(ACTION_OPEN_TRIP)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_speed)
            .setColor(0xFFFFB020.toInt())
            .setColorized(false)
            .setContentTitle(getString(if (paused) R.string.notif_paused else R.string.notif_recording))
            .setContentText(line)
            .setSubText(time)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line + "\n" + getString(R.string.notif_time, time)))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .addAction(
                if (paused) R.drawable.ic_stat_play else R.drawable.ic_stat_pause,
                getString(if (paused) R.string.resume else R.string.pause),
                action(if (paused) ACTION_RESUME else ACTION_PAUSE),
            )
            .addAction(R.drawable.ic_stat_stop, getString(R.string.end_trip), action(ACTION_STOP))
            .build()
    }

    private fun action(name: String) = PendingIntent.getService(
        this, name.hashCode(), Intent(this, TripService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CHANNEL_ID = "trip"
        private const val NOTIFICATION_ID = 1
        private const val INTERRUPTED_ID = 2
        private const val ALERT_CHANNEL_ID = "trip_alerts"
        private const val UPDATE_MS = 1000L
        private const val GPS_PREFERRED_MS = 5000L
        private const val FUSED_GPS_GRADE_M = 100f
        private const val WATCHDOG_SILENT_S = 5
        private const val WATCHDOG_RETRY_MS = 8_000L
        private const val WAKE_LOCK_MS = 2 * 60 * 1000L
        private const val ZERO_SPEED_MS = 1f
        private const val MOVING_MS = 5f
        private const val MIN_DISTANCE_SPEED_S = 0.5f
        private const val MAX_DISTANCE_SPEED_S = 6f
        private const val UNKNOWN_ACCURACY_M = 100f
        private const val MIN_GAP_MS = 400L
        private const val SEED_MAX_AGE_MS = 10 * 60 * 1000L
        private const val ZONE_AHEAD_M = 400.0
        private const val MPS_TO_KMH = 3.6f
        private const val PAUSE_LIMIT_MS = 30 * 60 * 1000L
        private const val ZONE_RESET_M = 600.0
        const val ACTION_PAUSE = "com.speedometer.app.PAUSE"
        const val ACTION_RESUME = "com.speedometer.app.RESUME"
        const val ACTION_STOP = "com.speedometer.app.STOP"
        const val ACTION_RESET = "com.speedometer.app.RESET"
        const val ACTION_DISCARD = "com.speedometer.app.DISCARD"
        const val ACTION_OPEN_TRIP = "com.speedometer.app.OPEN_TRIP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TripService::class.java))
        }

        fun reset(context: Context) {
            context.startService(Intent(context, TripService::class.java).setAction(ACTION_RESET))
        }

        fun discard(context: Context) {
            context.startService(Intent(context, TripService::class.java).setAction(ACTION_DISCARD))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TripService::class.java).setAction(ACTION_STOP))
        }
    }
}
