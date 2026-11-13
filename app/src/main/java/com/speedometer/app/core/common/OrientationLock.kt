package com.speedometer.app.core.common

import android.app.Activity
import android.content.pm.ActivityInfo
import com.speedometer.app.core.data.ScreenOrientation

object OrientationLock {
    fun apply(activity: Activity, orientation: ScreenOrientation) {
        val requested = when (orientation) {
            ScreenOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ScreenOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (activity.requestedOrientation != requested) activity.requestedOrientation = requested
    }
}
