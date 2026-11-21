package com.speedometer.app.core.common

import android.app.Activity
import android.content.pm.ActivityInfo
import com.speedometer.app.core.data.ScreenOrientation

object OrientationLock {
    /** The orientation chosen in Settings, as last applied. */
    @Volatile private var chosen: ScreenOrientation = ScreenOrientation.Portrait

    /** True while the exit dialog holds the screen in portrait (Back pressed in landscape). Survives the rotation's recreate. */
    @Volatile var holdPortrait: Boolean = false
        private set

    fun apply(activity: Activity, orientation: ScreenOrientation) {
        chosen = orientation
        val requested = if (holdPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else when (orientation) {
            ScreenOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ScreenOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (activity.requestedOrientation != requested) activity.requestedOrientation = requested
    }

    /** Back in landscape: turn to portrait for the exit dialog ([on] true), or go back to the chosen orientation (false). */
    fun portraitForExit(activity: Activity, on: Boolean) {
        holdPortrait = on
        apply(activity, chosen)
    }

    /** True once [apply] has run with the saved choice, so [reapply] never turns the screen to a default by mistake. */
    @Volatile private var known = false

    /** Records the saved choice without turning the screen (a full-screen ad is up). */
    fun remember(orientation: ScreenOrientation) { chosen = orientation }

    /** The saved choice applied again, when the app comes back to the front (a full-screen ad may have turned it). */
    fun reapply(activity: Activity) { if (known) apply(activity, chosen) }

    fun markKnown() { known = true }

    /** Drops the portrait hold without turning the screen (the app is closing). */
    fun clearHold() { holdPortrait = false }
}
