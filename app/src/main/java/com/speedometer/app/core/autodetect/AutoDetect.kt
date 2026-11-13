package com.speedometer.app.core.autodetect

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.speedometer.app.MainActivity
import com.speedometer.app.R
import com.speedometer.app.core.data.AppLanguage
import com.speedometer.app.core.data.SettingsRepository
import com.speedometer.app.core.trip.TripPhase
import com.speedometer.app.core.trip.TripRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Set when the user taps "Start trip" on the auto-detect notice; the navigation host starts the trip. */
object StartRequest {
    var pending by mutableStateOf(false)
        private set

    fun request() { pending = true }
    fun consume() { pending = false }
}

/**
 * Auto-detect trips. While the setting is on and the activity-recognition permission is granted, Android tells
 * the app when the phone starts moving in a vehicle, and the app posts a notice asking whether to start a trip.
 * Nothing is recorded until the user taps Start trip, and no location is read outside a trip.
 */
@Singleton
class AutoDetect @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Keeps the detection registration in line with the Auto-detect setting. Call once at app start. */
    fun start() {
        scope.launch { settings.settings.map { it.autoDetect }.distinctUntilChanged().collect { sync(it) } }
    }

    /** True once the permission the detection needs is granted (it is a normal permission before Android 10). */
    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Checked in hasPermission() before registering.
    fun sync(enabled: Boolean) {
        val client = ActivityRecognition.getClient(context)
        val pending = transitionIntent(context)
        if (enabled && hasPermission()) {
            val request = ActivityTransitionRequest(
                listOf(
                    ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.IN_VEHICLE)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build(),
                ),
            )
            client.requestActivityTransitionUpdates(request, pending)
        } else {
            client.removeActivityTransitionUpdates(pending)
        }
    }

    companion object {
        const val ACTION_START_TRIP = "com.speedometer.app.START_TRIP"
        const val ACTION_TRANSITION = "com.speedometer.app.ACTIVITY_TRANSITION"
        const val ACTION_NOT_DRIVING = "com.speedometer.app.NOT_DRIVING"
        const val CHANNEL_ID = "auto_detect"
        const val NOTIFICATION_ID = 3

        fun transitionIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0, Intent(context, AutoDetectReceiver::class.java).setAction(ACTION_TRANSITION),
            // Mutable: Google Play services adds the transition result to the intent.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}

/** Receives vehicle transitions and posts the "Looks like you started driving" notice. */
@AndroidEntryPoint
class AutoDetectReceiver : BroadcastReceiver() {
    @Inject lateinit var trips: TripRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AutoDetect.ACTION_NOT_DRIVING -> {
                context.getSystemService(NotificationManager::class.java).cancel(AutoDetect.NOTIFICATION_ID)
                // "Not driving" means the phone guessed wrong: stay quiet for a long while.
                prefs(context).edit().putLong(KEY_QUIET_UNTIL, System.currentTimeMillis() + DISMISS_QUIET_MS).apply()
            }
            AutoDetect.ACTION_TRANSITION -> {
                if (!ActivityTransitionResult.hasResult(intent)) return
                val entered = ActivityTransitionResult.extractResult(intent)?.transitionEvents.orEmpty().any {
                    it.activityType == DetectedActivity.IN_VEHICLE && it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
                }
                val recording = trips.state.value.phase.let { it == TripPhase.Recording || it == TripPhase.Paused }
                if (entered && !recording && allowed(context)) post(AppLanguage.wrap(context))
            }
        }
    }

    /**
     * Activity recognition can misfire (a phone on a bed, a charger, a shaking table). So no notice at night, not
     * twice within a few hours, and not for a long while after the user said "Not driving".
     */
    private fun allowed(context: Context): Boolean {
        val now = System.currentTimeMillis()
        val p = prefs(context)
        if (now < p.getLong(KEY_QUIET_UNTIL, 0L)) return false
        if (now - p.getLong(KEY_LAST_NOTICE, 0L) < COOLDOWN_MS) return false
        p.edit().putLong(KEY_LAST_NOTICE, now).apply()
        return true
    }

    private fun prefs(context: Context) = context.getSharedPreferences("auto_detect", Context.MODE_PRIVATE)

    private fun post(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(AutoDetect.CHANNEL_ID, context.getString(R.string.ad_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val start = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java).setAction(AutoDetect.ACTION_START_TRIP)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dismiss = PendingIntent.getBroadcast(
            context, 2, Intent(context, AutoDetectReceiver::class.java).setAction(AutoDetect.ACTION_NOT_DRIVING),
            PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(
            AutoDetect.NOTIFICATION_ID,
            NotificationCompat.Builder(context, AutoDetect.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(context.getString(R.string.ad_notif_title))
                .setContentText(context.getString(R.string.ad_notif_text))
                .setAutoCancel(true)
                .setContentIntent(start)
                .addAction(0, context.getString(R.string.start_trip), start)
                .addAction(0, context.getString(R.string.ad_not_driving), dismiss)
                .build(),
        )
    }
}

private const val COOLDOWN_MS = 6 * 60 * 60 * 1000L
private const val DISMISS_QUIET_MS = 12 * 60 * 60 * 1000L
private const val KEY_LAST_NOTICE = "last_notice_ms"
private const val KEY_QUIET_UNTIL = "quiet_until_ms"
