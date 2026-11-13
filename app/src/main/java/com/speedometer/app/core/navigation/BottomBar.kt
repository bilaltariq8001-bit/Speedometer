package com.speedometer.app.core.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.speedometer.app.core.designsystem.Surface2
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedometer.app.R
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.BodyFamily
import com.speedometer.app.core.designsystem.Nav
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.TextMuted

private data class Tab(val route: String, val label: Int, val icon: String)

private val tabs = listOf(
    Tab(Routes.Home, R.string.tab_drive, "M4.9 19a9 9 0 1 1 14.2 0 M12 13l4-4"),
    Tab(Routes.History, R.string.tab_trips, "M12 3a9 9 0 1 0 0 18a9 9 0 0 0 0-18 M12 7v5l3 2"),
    Tab(Routes.Insights, R.string.tab_insights, "M5 20V10M12 20V4M19 20v-7"),
    Tab(
        Routes.Settings, R.string.tab_settings,
        "M4 7h10M18 7h2M4 17h2M10 17h10 M16 5a2 2 0 1 0 0 4a2 2 0 0 0 0-4 M8 15a2 2 0 1 0 0 4a2 2 0 0 0 0-4",
    ),
)

val tabRoutes: Set<String> = tabs.map { it.route }.toSet()

/** Bottom bar from the design: Nav background, 1px top line, 24 icons, 12 medium labels. */
@Composable
fun BottomBar(current: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Nav)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(androidx.compose.ui.graphics.Color(0xFF1B2530)))
        Row(
            Modifier.fillMaxWidth().widthIn(max = 560.dp).navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
        ) {
            tabs.forEach { t ->
                val on = t.route == current
                val color = if (on) Amber else TextMuted
                Column(
                    Modifier.weight(1f).heightIn(min = 56.dp).clickable(role = Role.Tab) { onSelect(t.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    StrokeIcon(t.icon, color, size = 24.dp, box = 24f)
                    Text(stringResource(t.label), style = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = color))
                }
            }
        }
    }
}

private val RailLine = androidx.compose.ui.graphics.Color(0xFF1B2530)

/**
 * Left rail for landscape windows, from design HomeL (72dp, labels 11sp) and HomeT (96dp, selected item on a
 * rounded tile). Same four destinations as the bottom bar.
 */
@Composable
fun NavRail(current: String?, onSelect: (String) -> Unit, tablet: Boolean, modifier: Modifier = Modifier) {
    val width = if (tablet) 96.dp else 72.dp
    Row(modifier.fillMaxHeight().width(width + 1.dp).background(Nav).systemBarsPadding()) {
        Column(
            Modifier.width(width).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (tablet) 8.dp else 4.dp, Alignment.CenterVertically),
        ) {
            tabs.forEach { t ->
                val on = t.route == current
                val color = if (on) Amber else TextMuted
                Column(
                    Modifier.width(if (tablet) 76.dp else 56.dp).heightIn(min = if (tablet) 68.dp else 56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .then(if (tablet && on) Modifier.background(Surface2) else Modifier)
                        .clickable(role = Role.Tab) { onSelect(t.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (tablet) 4.dp else 2.dp, Alignment.CenterVertically),
                ) {
                    StrokeIcon(t.icon, color, size = 24.dp, box = 24f)
                    Text(
                        stringResource(t.label),
                        style = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = if (tablet) 12.sp else 11.sp, color = color),
                    )
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(RailLine))
    }
}
