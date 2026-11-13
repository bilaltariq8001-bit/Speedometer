package com.speedometer.app.core.analytics

/**
 * The allow-list of everything the app may send to Firebase Analytics. An event not listed here is dropped, so a typo or a
 * stray event can never reach the reports. Limits follow GA4: event name up to 40 characters, parameter name up to 40,
 * parameter value up to 100, user property name up to 24 and value up to 36. No coordinates, routes or personal data ever
 * go into an event.
 */
object AnalyticsContract {
    const val MAX_EVENT_NAME = 40
    const val MAX_PARAM_NAME = 40
    const val MAX_PARAM_VALUE = 100
    const val MAX_PARAMS = 22 // Analytics adds user_cohort, app_language and tracking_version
    const val MAX_PROPERTY_NAME = 24
    const val MAX_PROPERTY_VALUE = 36

    val events = setOf(
        // app and screens
        "screen_enter",
        "app_foregrounded",
        "app_backgrounded",
        // onboarding
        "onboarding_started",
        "onboarding_step_viewed",
        "onboarding_skipped",
        "onboarding_completed",
        // trips
        "trip_started",
        "trip_paused",
        "trip_resumed",
        "trip_save_success",
        "trip_discarded",
        // speed limit alert
        "speed_limit_changed",
        "limit_alert_fired",
        // meters and vehicle
        "meter_style_changed",
        "vehicle_changed",
        // ads
        "ads_loaded",
        "ads_failed",
        "ads_shown",
        "ads_clicked",
    )

    val userProperties = setOf("user_cohort", "onboarding_state", "app_language")

    fun validEvent(name: String) = name in events && name.length <= MAX_EVENT_NAME

    fun validProperty(name: String, value: String) =
        name in userProperties && name.length <= MAX_PROPERTY_NAME && value.length <= MAX_PROPERTY_VALUE
}
