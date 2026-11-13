package com.speedometer.app.core.alerts

import android.content.Context
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import com.speedometer.app.R
import com.speedometer.app.core.data.AppLanguage
import com.speedometer.app.core.data.GuardOption
import com.speedometer.app.core.data.Settings
import com.speedometer.app.core.data.SettingsRepository
import com.speedometer.app.core.trip.GuardLevel
import com.speedometer.app.core.trip.TripPhase
import com.speedometer.app.core.trip.TripRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Limit Guard alerts. While the speed is over the limit, every 5 seconds the phone vibrates and a voice says to slow
 * down and what the limit is (a short beep stands in if the phone has no voice ready). An alert also sounds at once
 * when the level rises, and Mute silences all of it for a while. Which parts play follows the Settings switches.
 */
@Singleton
class GuardAlerts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trips: TripRepository,
    private val settings: SettingsRepository,
    private val analytics: com.speedometer.app.core.analytics.Analytics,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var job: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    /** True when the phone's voice has no data for the app language, so the English words are spoken instead. */
    private var voiceEnglish = false
    private var focusRequest: AudioFocusRequest? = null
    private var muteUntilMs = 0L

    /** Strings in the language chosen in Settings; the application context alone would use the phone language. */
    private val localized: Context get() = AppLanguage.wrap(context)

    private val english: Context
        get() = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.US) })

    /** Starts watching the live trip. Safe to call more than once. */
    fun start() {
        if (job?.isActive == true) return
        // The voice takes a moment to start; getting it ready now means the first warning is spoken, not just beeped.
        runCatching { ensureVoice() }
        job = scope.launch {
            val policy = AlertPolicy()
            var lastLevel = GuardLevel.Off
            combine(trips.state, settings.settings) { s, set -> s to set }.collect { (s, set) ->
                val level = if (s.phase == TripPhase.Recording) s.guard else GuardLevel.Off
                // One analytics event per time the speed goes over the limit, not one per repeated warning.
                if (level != GuardLevel.Off && lastLevel == GuardLevel.Off) analytics.event("limit_alert_fired", "level" to level.name)
                lastLevel = level
                // Monotonic clock: a wall-clock change (time zone, network time) must not delay or repeat an alert.
                if (policy.shouldFire(level, android.os.SystemClock.elapsedRealtime())) {
                    val limit = s.limitKmh?.let { set.unit.fromKmh(it).roundToInt() }
                    fire(level, limit, set)
                }
                // The phone buzzes without a break for as long as the speed stays over the limit (until muted or back under it).
                val wantBuzz = level != GuardLevel.Off && !isMuted && set.option(GuardOption.VibrateOnAlert)
                if (wantBuzz && !buzzing) runCatching { startBuzz() } else if (!wantBuzz && buzzing) stopBuzz()
            }
        }
    }

    /** Silences alerts for [minutes]. */
    fun mute(minutes: Int = MUTE_MINUTES) {
        muteUntilMs = System.currentTimeMillis() + minutes * 60_000L
        stopBuzz()
    }

    val isMuted: Boolean get() = System.currentTimeMillis() < muteUntilMs

    /** Speaks "a saved zone is ahead", when the Spoken zone warnings switch is on. */
    fun announceZone(name: String, limitKmh: Float, set: Settings) {
        if (isMuted || !set.option(GuardOption.SpokenZoneWarnings)) return
        runCatching { speak(localized.getString(R.string.tts_zone_ahead, name, set.unit.fromKmh(limitKmh).roundToInt())) }
    }

    /** Each output is tried on its own: a failing voice, tone or vibration must never stop the alerts that follow. */
    private fun fire(level: GuardLevel, limit: Int?, set: Settings) {
        if (isMuted || level == GuardLevel.Off) return
        if (set.option(GuardOption.AlertTones)) {
            runCatching {
                val words = if (voiceEnglish) english else localized
                val said = speak(limit?.let { words.getString(R.string.tts_slow_down_limit, it) } ?: words.getString(R.string.tts_slow_down))
                // No voice ready or none installed: a beep still warns the driver.
                if (!said) {
                    if (level == GuardLevel.Soft) tone(ToneGenerator.TONE_PROP_BEEP, SOFT_MS) else tone(ToneGenerator.TONE_PROP_BEEP2, FIRM_MS)
                }
            }
        }
    }

    private fun tone(type: Int, ms: Int) {
        runCatching {
            val gen = ToneGenerator(AudioManager.STREAM_ALARM, TONE_VOLUME)
            gen.startTone(type, ms)
            handler.postDelayed({ gen.release() }, ms + RELEASE_SLACK_MS)
        }
    }

    private fun ensureVoice(): TextToSpeech = tts ?: TextToSpeech(context) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            runCatching {
                val engine = tts ?: return@runCatching
                val wanted = Locale.forLanguageTag(AppLanguage.tags[AppLanguage.saved(context)] ?: "en")
                val result = engine.setLanguage(wanted)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // No voice for this language: speak the English warning in an English voice, which is better than nonsense.
                    voiceEnglish = true
                    engine.setLanguage(Locale.US)
                }
                engine.setSpeechRate(SPEECH_RATE)
                engine.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                )
            }
        }
    }.also { tts = it }

    /** True when the text was handed to a ready voice; false while the voice is starting or when the phone has none. */
    private fun speak(text: String): Boolean = runCatching {
        val engine = ensureVoice()
        if (!ttsReady) return@runCatching false
        requestDucking()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "guard") == TextToSpeech.SUCCESS
    }.getOrDefault(false)

    /** Lowers music or a navigation voice while the warning is spoken, then gives the sound back. */
    private fun requestDucking() {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            ).build().also { focusRequest = it }
        audio.requestAudioFocus(request)
        handler.removeCallbacks(releaseFocus)
        handler.postDelayed(releaseFocus, FOCUS_HOLD_MS)
    }

    private val releaseFocus = Runnable {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
    }

    private var buzzing = false

    private val vibrator: Vibrator
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private fun stopBuzz() {
        if (!buzzing) return
        buzzing = false
        runCatching { vibrator.cancel() }
    }

    /** Full-strength buzz that repeats on its own until [stopBuzz], and is allowed even on silent or Do not disturb. */
    private fun startBuzz() {
        val vibrator = vibrator
        if (!vibrator.hasVibrator()) return
        val timings = longArrayOf(0, BUZZ_ON_MS, BUZZ_OFF_MS)
        val amplitudes = intArrayOf(0, 255, 0)
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, 0)
        } else {
            VibrationEffect.createWaveform(timings, 0)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
        buzzing = true
    }

    private companion object {
        const val MUTE_MINUTES = 5
        const val SOFT_MS = 250
        const val FIRM_MS = 400
        const val TONE_VOLUME = 100
        const val RELEASE_SLACK_MS = 200L
        const val BUZZ_ON_MS = 700L
        const val BUZZ_OFF_MS = 300L
        const val SPEECH_RATE = 0.95f
        const val FOCUS_HOLD_MS = 4_000L
    }
}
