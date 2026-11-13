package com.speedometer.app.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speedometer.app.R
import com.speedometer.app.core.data.MAX_SPEED_LIMIT_KMH
import com.speedometer.app.core.data.MIN_SPEED_LIMIT_KMH
import com.speedometer.app.core.data.Settings
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.Bg
import com.speedometer.app.core.designsystem.DesignSwitch
import com.speedometer.app.core.designsystem.PrimaryButton
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.Line2
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle
import kotlin.math.roundToInt

/** One step of the limit stepper, in the unit shown (km/h, mph or m/s). */
private fun stepIn(unitPerKmh: Float): Int = if (unitPerKmh < 0.5f) 2 else 5

/**
 * Speed limit alarm: a switch, the limit itself (shown and changed in the speed unit chosen above, kept in km/h), and
 * how far over the limit the alarm starts. While a trip records, going over the limit sounds the Limit Guard alert.
 */
@Composable
fun SpeedLimitBlock(
    settings: Settings,
    onOn: (Boolean) -> Unit,
    onLimitKmh: (Int) -> Unit,
    onTolerance: (Int) -> Unit = {},
) {
    val unit = settings.unit
    val shown = unit.fromKmh(settings.speedLimitKmh.toFloat()).roundToInt()

    fun change(deltaUnits: Int) {
        val next = shown + deltaUnits
        onLimitKmh((next / unit.perKmh).roundToInt().coerceIn(MIN_SPEED_LIMIT_KMH, MAX_SPEED_LIMIT_KMH))
    }

    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        Text(stringResource(R.string.speed_limit_title), Modifier.padding(bottom = 8.dp), style = bodyStyle(13))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.speed_limit_alarm), style = bodyStyle(16, FontWeight.Bold, TextPrimary))
                    Text(stringResource(R.string.speed_limit_alarm_sub), Modifier.padding(top = 2.dp), style = bodyStyle(13))
                }
                DesignSwitch(settings.speedLimitOn, onOn)
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 1.dp).background(Line2.copy(alpha = 0.6f)))
            val enabled = settings.speedLimitOn
            val disabledAlpha = if (enabled) 1f else 0.42f
            Row(
                Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Stepper("M5 12h14", stringResource(R.string.speed_limit_less), enabled) { change(-stepIn(unit.perKmh)) }
                Column(
                    Modifier.weight(1f).semantics { if (!enabled) disabled() },
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.speed_limit_value),
                        style = bodyStyle(13).copy(color = TextPrimary.copy(alpha = disabledAlpha)),
                        maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            shown.toString(),
                            style = displayStyle(28, FontWeight.Bold, TextPrimary.copy(alpha = disabledAlpha)),
                            maxLines = 1,
                        )
                        Text(
                            " " + unit.label,
                            Modifier.padding(bottom = 4.dp),
                            style = bodyStyle(13).copy(color = TextPrimary.copy(alpha = disabledAlpha)),
                            maxLines = 1,
                        )
                    }
                }
                Stepper("M12 5v14M5 12h14", stringResource(R.string.speed_limit_more), enabled) { change(stepIn(unit.perKmh)) }
            }
        }
    }
}

@Composable
private fun Stepper(icon: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Bg.copy(alpha = if (enabled) 1f else 0.42f))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .then(if (enabled) Modifier else Modifier.semantics { disabled() }),
        contentAlignment = Alignment.Center,
    ) { StrokeIcon(icon, Amber.copy(alpha = if (enabled) 1f else 0.42f), size = 22.dp, box = 24f) }
}
