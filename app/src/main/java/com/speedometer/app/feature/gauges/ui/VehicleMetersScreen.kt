package com.speedometer.app.feature.gauges.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.speedometer.app.R
import com.speedometer.app.core.data.MeterStyle
import com.speedometer.app.core.designsystem.Amber
import com.speedometer.app.core.designsystem.PrimaryButton
import com.speedometer.app.core.designsystem.ScrollPage
import com.speedometer.app.core.designsystem.Segmented
import com.speedometer.app.core.designsystem.StrokeIcon
import com.speedometer.app.core.designsystem.Surface1
import com.speedometer.app.core.designsystem.Surface2
import com.speedometer.app.core.designsystem.TextPrimary
import com.speedometer.app.core.designsystem.TextSoft
import com.speedometer.app.core.designsystem.bodyStyle
import com.speedometer.app.core.designsystem.displayStyle

private const val CAR = "M5 20h22v-5l-3-6H8l-3 6v5z M5 15h22 M9 20v3 M23 20v3"
private const val BIKE = "M6 22a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M26 22a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M10 22h8l4-8h-6 M16 10h5"
private const val CYCLE = "M7 23a5 5 0 1 0 0-10 5 5 0 0 0 0 10z M25 23a5 5 0 1 0 0-10 5 5 0 0 0 0 10z M7 18l5-8h8l5 8 M12 10l5 8"

private enum class Category { Cars, Bikes, Other }

/** A vehicle model and the meter style it uses. */
private data class Model(
    val name: Int, val meter: Int, val icon: String, val style: MeterStyle, val category: Category,
)

private val stockModels = listOf(
    Model(R.string.model_sports, R.string.model_sports_meter, CAR, MeterStyle.Sports, Category.Cars),
    Model(R.string.model_sedan, R.string.model_sedan_meter, CAR, MeterStyle.Dial, Category.Cars),
    Model(R.string.model_suv, R.string.model_suv_meter, CAR, MeterStyle.Led, Category.Cars),
    Model(R.string.model_superbike, R.string.model_superbike_meter, BIKE, MeterStyle.Superbike, Category.Bikes),
    Model(R.string.model_bicycle, R.string.model_bicycle_meter, CYCLE, MeterStyle.Rings, Category.Other),
)

/** Choose a vehicle-specific meter layout. Previewing is separate from applying the selection. */
@Composable
fun VehicleMetersScreen(onBack: () -> Unit, onOpenCluster: (MeterStyle) -> Unit, vm: GaugeStyleViewModel = hiltViewModel()) {
    val saved by vm.style.collectAsState()
    var pickedStyle by androidx.compose.runtime.saveable.rememberSaveable(saved) {
        mutableStateOf(stockModels.firstOrNull { it.style == saved }?.style ?: stockModels.first().style)
    }
    val picked = stockModels.first { it.style == pickedStyle }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(picked.category.ordinal) }
    val shown = stockModels.filter { it.category.ordinal == tab }

    ScrollPage(bottom = {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp, top = 8.dp)) {
            PrimaryButton(stringResource(R.string.save_selection), onClick = {
                vm.save(picked.style, onBack)
            })
        }
    }) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                StrokeIcon("M15 5l-7 7 7 7", TextPrimary, size = 24.dp, box = 24f)
            }
            Text(stringResource(R.string.vehicle_meters), Modifier.padding(start = 4.dp), style = displayStyle(22, FontWeight.SemiBold))
        }
        Text(stringResource(R.string.vehicle_meters_intro), Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp), style = bodyStyle(14))

        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Segmented(
                listOf(stringResource(R.string.cat_cars), stringResource(R.string.cat_bikes), stringResource(R.string.cat_other)),
                tab, { tab = it },
            )
        }

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 68.dp).clip(RoundedCornerShape(18.dp)).background(Surface1)
                        .border(2.dp, if (m.style == picked.style) Amber else Color.Transparent, RoundedCornerShape(18.dp))
                        .clickable(role = Role.RadioButton) { pickedStyle = m.style }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconTile(m.icon, if (m.style == picked.style) Amber else TextSoft, 44)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(m.name), style = bodyStyle(16, FontWeight.Bold, TextPrimary))
                        Text(stringResource(m.meter), Modifier.padding(top = 2.dp), style = bodyStyle(13))
                    }
                    Text(
                        stringResource(R.string.preview),
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { onOpenCluster(m.style) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        style = bodyStyle(13, FontWeight.Bold, Amber),
                    )
                }
            }
        }
    }
}

@Composable
private fun IconTile(path: String, color: Color, size: Int) {
    Box(Modifier.size(size.dp).background(Surface2, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        StrokeIcon(path, color, size = 26.dp, box = 32f)
    }
}
