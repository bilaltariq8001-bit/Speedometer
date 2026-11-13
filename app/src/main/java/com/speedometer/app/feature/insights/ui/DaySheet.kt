package com.speedometer.app.feature.insights.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.speedometer.app.R
import com.speedometer.app.core.database.TripEntity
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.Surface3
import com.speedometer.app.core.designsystem.TextMuted
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import com.speedometer.app.core.designsystem.tripScoreColor
import com.speedometer.app.core.trip.DistanceUnit
import com.speedometer.app.core.trip.SpeedUnit
import com.speedometer.app.core.trip.TripFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** One day of Insights: opened by tapping a bar. Lists that day's trips with their scores (display only). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(date: LocalDate, trips: List<TripEntity>, unit: SpeedUnit, onClose: () -> Unit) {
    val mi = unit == SpeedUnit.Mph
    val title = date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
    val zone = ZoneId.systemDefault()
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Surface3, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(
            Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = displayStyle(22, FontWeight.SemiBold))
            if (trips.isEmpty()) {
                Text(stringResource(R.string.no_trips_day), Modifier.padding(vertical = 16.dp), style = bodyStyle(15))
            } else {
                val km = trips.sumOf { it.distanceKm.toDouble() }.toFloat()
                val avg = trips.map { it.score }.average().toInt()
                Text(
                    pluralStringResource(R.plurals.trip_count, trips.size, trips.size) + " · " +
                        "%.1f %s".format(DistanceUnit.fromKm(km, mi), if (mi) "mi" else "km") + " · " +
                        stringResource(R.string.day_avg_score, avg),
                    style = bodyStyle(14),
                )
                trips.forEach { t ->
                    val c = tripScoreColor(t.score)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface1)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = bodyStyle(15, FontWeight.Bold, TextPrimary))
                            Text(
                                Instant.ofEpochMilli(t.startMs).atZone(zone).toLocalTime().format(time) + " · " +
                                    TripFormat.duration(t.durationSec) + " · " +
                                    "%.1f %s".format(DistanceUnit.fromKm(t.distanceKm, mi), if (mi) "mi" else "km"),
                                Modifier.padding(top = 2.dp), style = bodyStyle(13, color = TextMuted),
                            )
                        }
                        Box(Modifier.size(44.dp).border(3.dp, c, CircleShape), contentAlignment = Alignment.Center) {
                            Text(t.score.toString(), style = displayStyle(15, FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}
