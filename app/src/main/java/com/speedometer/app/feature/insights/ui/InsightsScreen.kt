package com.speedometer.app.feature.insights.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import java.time.LocalDate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.speedometer.app.R
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Ice
import com.speedometer.app.core.designsystem.Line
import com.speedometer.app.core.designsystem.Ok
import com.speedometer.app.core.designsystem.ScrollPage
import com.speedometer.app.core.designsystem.rememberLayout
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextSoft
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.core.designsystem.tripScoreColor
import com.speedometer.app.feature.insights.domain.GOAL_SCORE
import com.speedometer.app.feature.insights.domain.GOAL_TRIPS
import com.speedometer.app.feature.insights.domain.Insights
import com.speedometer.app.feature.insights.domain.Weakest

/** Content width in landscape; the design has no landscape Insights board, so this layout is ours. */
private val LANDSCAPE_MAX_WIDTH = 1000.dp

/** Insights, from design/Insights.dc.html, read from saved trips of the last 7 days. */
@Composable
fun InsightsScreen(vm: InsightsViewModel = hiltViewModel()) {
    val data by vm.insights.collectAsState()
    val byDay by vm.tripsByDay.collectAsState()
    val unit by vm.unit.collectAsState()
    var openDay by rememberSaveable { mutableStateOf<String?>(null) }
    val i = data ?: return

    val landscape = rememberLayout().isLandscape
    ScrollPage(maxWidth = if (landscape) LANDSCAPE_MAX_WIDTH else null) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
            Text(stringResource(R.string.tab_insights), style = displayStyle(26, FontWeight.SemiBold))
            Text(stringResource(R.string.last_seven_days), Modifier.padding(top = 4.dp), style = bodyStyle(14))
        }
        if (i.avgScore == null) {
            Text(
                stringResource(R.string.insights_empty), Modifier.fillMaxWidth().padding(40.dp),
                style = bodyStyle(15), textAlign = TextAlign.Center,
            )
            return@ScrollPage
        }
        if (landscape) {
            // Two columns: the score and goal on the left, streak, time over limit and the tip on the right.
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    ScoreCard(i) { openDay = it.toString() }
                    GoalCard(i.goalDone)
                }
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 0.dp, end = 20.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StreakCard(i.streakDays, Modifier.weight(1f).fillMaxHeight())
                        OverLimitCard(i, Modifier.weight(1f).fillMaxHeight())
                    }
                    i.weakest?.let { Tip(it) }
                }
            }
        } else {
            ScoreCard(i) { openDay = it.toString() }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 20.dp, end = 20.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StreakCard(i.streakDays, Modifier.weight(1f).fillMaxHeight())
                OverLimitCard(i, Modifier.weight(1f).fillMaxHeight())
            }
            GoalCard(i.goalDone)
            i.weakest?.let { Tip(it) }
        }
        Box(Modifier.height(16.dp))
    }

    openDay?.let { key ->
        val date = LocalDate.parse(key)
        DaySheet(date, byDay[date].orEmpty(), unit, onClose = { openDay = null })
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(Surface1, RoundedCornerShape(20.dp))) { content() }
}

@Composable
private fun ScoreCard(i: Insights, onDay: (LocalDate) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)
            .background(Surface1, RoundedCornerShape(24.dp)).padding(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(stringResource(R.string.avg_smooth_score), style = bodyStyle(13))
                Text(i.avgScore.toString(), style = displayStyle(48, FontWeight.Bold).copy(lineHeight = 53.sp))
            }
            i.deltaVsLastWeek?.let {
                Text(
                    (if (it >= 0) "+" else "") + it + " " + stringResource(R.string.vs_last_week),
                    Modifier.padding(bottom = 8.dp), style = bodyStyle(14, FontWeight.Bold, if (it >= 0) Ok else com.speedometer.app.core.designsystem.Danger),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).height(112.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            i.days.forEach { d ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClickLabel = d.date.toString()) { onDay(d.date) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Bar height is score * 0.8 dp as in the design; a day with no trips shows a short track.
                    // Every bar sits on the same baseline, and every letter on the same line under it.
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.fillMaxWidth().height(((d.score ?: 0) * 0.8f).dp.coerceAtLeast(4.dp))
                                .background(d.score?.let { tripScoreColor(it) } ?: Line, RoundedCornerShape(8.dp)),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(d.letter, style = bodyStyle(12))
                }
            }
        }
    }
}

@Composable
private fun StreakCard(days: Int, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StrokeIcon(
                    "M12 2c1 4 5 5 5 10a5 5 0 0 1-10 0c0-2 1-3 2-4 0 2 1 3 2 3 0-4-1-6 1-9z",
                    Amber, size = 16.dp, box = 24f, filled = true,
                )
                Text(stringResource(R.string.streak), style = bodyStyle(13))
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                Text(days.toString(), style = displayStyle(30, FontWeight.Bold))
                Text(" " + stringResource(R.string.days), Modifier.padding(bottom = 4.dp), style = bodyStyle(14, FontWeight.Medium))
            }
            Text(stringResource(R.string.streak_note), Modifier.padding(top = 2.dp), style = bodyStyle(12))
        }
    }
}

@Composable
private fun OverLimitCard(i: Insights, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(stringResource(R.string.time_over_limit), style = bodyStyle(13))
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                Text(i.overLimitPct?.let { "%.1f".format(it) } ?: "--", style = displayStyle(30, FontWeight.Bold))
                Text(" %", Modifier.padding(bottom = 4.dp), style = bodyStyle(14, FontWeight.Medium))
            }
            val prev = i.overLimitPctLastWeek
            val now = i.overLimitPct
            if (prev != null && now != null && now < prev) {
                Text(stringResource(R.string.down_from, "%.1f".format(prev)), Modifier.padding(top = 2.dp), style = bodyStyle(12, color = Ok))
            }
        }
    }
}

@Composable
private fun GoalCard(done: Int) {
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)
            .background(Surface1, RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.weekly_goal), style = bodyStyle(16, FontWeight.Bold, com.speedometer.app.core.designsystem.TextPrimary))
            Text(stringResource(R.string.goal_progress, done, GOAL_TRIPS, GOAL_SCORE), style = displayStyle(16, FontWeight.SemiBold, Amber))
        }
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(8.dp).background(Line, RoundedCornerShape(4.dp))) {
            Box(Modifier.fillMaxWidth(done / GOAL_TRIPS.toFloat()).height(8.dp).background(Amber, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun Tip(weakest: Weakest) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)
            .background(Surface1, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StrokeIcon("M9 18h6M10 21h4M12 3a6 6 0 0 0-4 10.5c.7.7 1 1.5 1 2.5h6c0-1 .3-1.8 1-2.5A6 6 0 0 0 12 3z", Ice, size = 24.dp, box = 24f)
        Text(
            stringResource(if (weakest == Weakest.Braking) R.string.tip_weak_braking else R.string.tip_weak_limit),
            style = bodyStyle(14, color = TextSoft).copy(lineHeight = 20.sp),
        )
    }
}
