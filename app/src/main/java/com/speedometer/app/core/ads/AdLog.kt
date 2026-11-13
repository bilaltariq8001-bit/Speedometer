package com.speedometer.app.core.ads

import android.os.SystemClock
import android.util.Log
import com.speedometer.app.BuildConfig

/** Where one ad (or ad slot) is in its life. Kept by each manager so a load or a show never runs twice. */
enum class AdState { NotInitialized, Loading, Ready, Showing, Dismissed, Failed, NoFill, Expired }

/**
 * Timing log for every ad format, in Logcat under the tag `AdPerf` (debug builds only, so release builds stay quiet).
 * `adb logcat -s AdPerf` shows the whole picture: SDK init time, each request, its load time, shows, dismissals,
 * failures with their codes, skipped duplicates, and why an ad was not shown.
 */
object AdLog {
    private const val TAG = "AdPerf"

    /** Process start, so every line can say how long after launch it happened. */
    private val processStartMs = SystemClock.elapsedRealtime()

    fun now(): Long = SystemClock.elapsedRealtime()

    fun d(format: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "[+${now() - processStartMs}ms] $format: $message")
    }

    fun w(format: String, message: String) {
        if (BuildConfig.DEBUG) Log.w(TAG, "[+${now() - processStartMs}ms] $format: $message")
    }

    fun requestStarted(format: String, placement: String): Long {
        d(format, "request start ($placement)")
        return now()
    }

    fun loaded(format: String, placement: String, startedMs: Long) =
        d(format, "loaded ($placement) in ${now() - startedMs}ms")

    fun failed(format: String, placement: String, startedMs: Long, code: String, message: String?) =
        w(format, "load failed ($placement) after ${now() - startedMs}ms code=$code${if (isNoFill(code)) " NO_FILL" else ""} $message")

    fun skipped(format: String, reason: String) = d(format, "not shown: $reason")

    /** No fill is "no ad to give right now", not an error: it is retried later and more slowly. */
    fun isNoFill(code: String): Boolean = code == "3" || code.contains("NO_FILL", ignoreCase = true)
}
