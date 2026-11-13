package com.speedometer.app.core.alerts

import com.speedometer.app.core.trip.GuardLevel

/**
 * Decides when a Limit Guard alert plays: when the level rises (including the first time over the limit), and again
 * every [repeatMs] while the speed stays over it. Never twice inside that gap for the same level, and never while Off.
 * Pure logic with an injected clock, so it is unit tested without a phone.
 */
class AlertPolicy(private val repeatMs: Long = REPEAT_MS) {
    private var last = GuardLevel.Off
    private var lastFiredMs = Long.MIN_VALUE / 2
    private var lastFiredLevel = GuardLevel.Off

    fun shouldFire(level: GuardLevel, nowMs: Long): Boolean {
        // A rise right after an alert (speed hovering at the limit) is not a new alert, unless the level is higher than the one just sounded.
        val rose = level.ordinal > last.ordinal &&
            (nowMs - lastFiredMs >= MIN_GAP_MS || level.ordinal > lastFiredLevel.ordinal)
        val repeat = level != GuardLevel.Off && nowMs - lastFiredMs >= repeatMs
        last = level
        if (level == GuardLevel.Off) return false
        if (rose || repeat) {
            lastFiredMs = nowMs
            lastFiredLevel = level
            return true
        }
        return false
    }

    fun reset() {
        last = GuardLevel.Off
        lastFiredLevel = GuardLevel.Off
        lastFiredMs = Long.MIN_VALUE / 2
    }

    companion object {
        const val REPEAT_MS = 5_000L
        const val MIN_GAP_MS = 3_000L
    }
}
