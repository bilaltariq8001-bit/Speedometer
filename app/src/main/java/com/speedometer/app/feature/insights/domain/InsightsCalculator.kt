package com.speedometer.app.feature.insights.domain

import com.speedometer.app.core.database.TripEntity
import com.speedometer.app.core.trip.GuardLevel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Weekly goal on the Insights screen: this many trips scoring above [GOAL_SCORE]. */
const val GOAL_TRIPS = 5
const val GOAL_SCORE = 80

data class DayBar(val letter: String, val score: Int?, val date: LocalDate)

enum class Weakest { Braking, LimitCompliance }

data class Insights(
    val avgScore: Int?,
    /** Average minus the previous 7 days' average; null when there is no previous week to compare. */
    val deltaVsLastWeek: Int?,
    val days: List<DayBar>,
    val streakDays: Int,
    /** Share of driving time over the limit plus 5, in percent. */
    val overLimitPct: Float?,
    val overLimitPctLastWeek: Float?,
    val goalDone: Int,
    val weakest: Weakest?,
)

/** Pure calculation from saved trips, so it can be unit tested. Windows are the last 7 days ending [today]. */
object InsightsCalculator {
    fun compute(trips: List<TripEntity>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Insights {
        fun day(t: TripEntity) = Instant.ofEpochMilli(t.startMs).atZone(zone).toLocalDate()
        val thisWeek = trips.filter { day(it) in today.minusDays(6)..today }
        val lastWeek = trips.filter { day(it) in today.minusDays(13)..today.minusDays(7) }

        val avg = thisWeek.takeIf { it.isNotEmpty() }?.map { it.score }?.average()?.toInt()
        val prevAvg = lastWeek.takeIf { it.isNotEmpty() }?.map { it.score }?.average()?.toInt()

        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        // Monday to Sunday of the current week; days not reached yet stay empty.
        val bars = (0..6).map { offset ->
            val d = monday.plusDays(offset.toLong())
            val scores = trips.filter { day(it) == d }.map { it.score }
            DayBar(letter(d.dayOfWeek), scores.takeIf { it.isNotEmpty() }?.average()?.toInt(), d)
        }

        return Insights(
            avgScore = avg,
            deltaVsLastWeek = if (avg != null && prevAvg != null) avg - prevAvg else null,
            days = bars,
            streakDays = streak(trips, today, zone),
            overLimitPct = overPct(thisWeek),
            overLimitPctLastWeek = overPct(lastWeek),
            goalDone = minOf(GOAL_TRIPS, thisWeek.count { it.score > GOAL_SCORE }),
            weakest = weakest(thisWeek),
        )
    }

    /** Consecutive days back from today with a trip and no Level 3 alert. */
    private fun streak(trips: List<TripEntity>, today: LocalDate, zone: ZoneId): Int {
        val byDay = trips.groupBy { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }
        var n = 0
        var d = today
        while (true) {
            val dayTrips = byDay[d] ?: break
            if (dayTrips.any { it.peakGuard > GuardLevel.Firm.ordinal }) break
            n++
            d = d.minusDays(1)
        }
        return n
    }

    private fun overPct(trips: List<TripEntity>): Float? {
        val total = trips.sumOf { it.durationSec }
        if (total <= 0) return null
        return trips.sumOf { it.secondsOver } * 100f / total
    }

    private fun weakest(trips: List<TripEntity>): Weakest? {
        if (trips.isEmpty()) return null
        val braking = trips.map { com.speedometer.app.core.trip.ScoreParts.braking(it.hardBrakes) }.average()
        val limit = trips.map { com.speedometer.app.core.trip.ScoreParts.limitCompliance(it.secondsOver) }.average()
        return if (braking <= limit) Weakest.Braking else Weakest.LimitCompliance
    }

    private fun letter(d: DayOfWeek) = when (d) {
        DayOfWeek.MONDAY -> "M"; DayOfWeek.TUESDAY -> "T"; DayOfWeek.WEDNESDAY -> "W"
        DayOfWeek.THURSDAY -> "T"; DayOfWeek.FRIDAY -> "F"; DayOfWeek.SATURDAY -> "S"; DayOfWeek.SUNDAY -> "S"
    }
}
