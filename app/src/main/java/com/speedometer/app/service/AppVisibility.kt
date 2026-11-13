package com.speedometer.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the app's screen is in front of the user. While it is, the trip needs no notification (the user is looking
 * at the trip), so the service takes its notification down, and puts it back as the app goes to the background.
 */
object AppVisibility {
    private val _resumed = MutableStateFlow(false)
    val resumed: StateFlow<Boolean> = _resumed.asStateFlow()

    fun set(resumed: Boolean) { _resumed.value = resumed }
}
