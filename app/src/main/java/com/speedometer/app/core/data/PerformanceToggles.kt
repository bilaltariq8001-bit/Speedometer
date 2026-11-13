package com.speedometer.app.core.data

/**
 * Debug-only performance isolation toggles to benchmark and isolate lag sources on physical devices.
 * Active only when BuildConfig.DEBUG is true.
 */
object PerformanceToggles {
    var perfDisableMap: Boolean = false
    var perfSimpleSpeedText: Boolean = false
    var perfFusedOnly: Boolean = false
    var perfDisableRawGps: Boolean = false
    var perfDisableNotificationUpdates: Boolean = false
    var perfReduceMapUpdateRate: Boolean = false
    var perfDisableGnssMonitor: Boolean = false
    var perfDisableActivityRecognition: Boolean = false
}
