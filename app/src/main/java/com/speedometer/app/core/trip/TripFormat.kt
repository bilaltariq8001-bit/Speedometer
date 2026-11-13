package com.speedometer.app.core.trip

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Text for trips: automatic title, "Today · 8:12 AM" style times, duration. */
object TripFormat {
    /** Automatic trip title from the start time of day. */
    fun title(startMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val hour = Instant.ofEpochMilli(startMs).atZone(zone).hour
        return when {
            hour < 5 -> "Night trip"
            hour < 12 -> "Morning trip"
            hour < 17 -> "Afternoon trip"
            hour < 21 -> "Evening trip"
            else -> "Night trip"
        }
    }

    fun whenLabel(startMs: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val at = Instant.ofEpochMilli(startMs).atZone(zone)
        val day = when (val back = today.toEpochDay() - at.toLocalDate().toEpochDay()) {
            0L -> "Today"
            1L -> "Yesterday"
            in 2L..6L -> at.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            else -> at.format(DateTimeFormatter.ofPattern("d MMM", locale))
        }
        return day + " · " + at.format(DateTimeFormatter.ofPattern("h:mm a", locale))
    }

    fun duration(seconds: Int): String = if (seconds < 60) "$seconds s" else "${seconds / 60} min"
}
