package com.speedometer.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.speedometer.app.core.data.Vehicle

/** Stroke icon of each vehicle, in a 32 unit box (the same drawings as the vehicle picker in setup). */
fun vehicleIconPath(v: Vehicle): String = when (v) {
    Vehicle.Car -> "M5 20h22v-5l-3-6H8l-3 6v5z M5 15h22 M9 20v3 M23 20v3"
    Vehicle.Motorbike -> "M6 22a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M26 22a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M10 22h8l4-8h-6 M16 10h5"
    Vehicle.Bicycle -> "M7 23a5 5 0 1 0 0-10 5 5 0 0 0 0 10z M25 23a5 5 0 1 0 0-10 5 5 0 0 0 0 10z M7 18l5-8h8l5 8 M12 10l5 8"
    Vehicle.Walking -> "M16 8a2 2 0 1 0 0-4 2 2 0 0 0 0 4z M16 10l-3 8 M16 10l4 6 M13 18l-2 8 M16 10v8l4 8"
    Vehicle.Train -> "M8 6h16v14a3 3 0 0 1-3 3H11a3 3 0 0 1-3-3V6z M8 14h16 M12 27l3-4 M20 27l-3-4"
    Vehicle.Boat -> "M4 20h24l-4 6H8l-4-6z M16 4v16 M16 6l8 10H16"
}

/** The vehicle saved with a trip, or Car for trips saved before vehicles were recorded. */
fun vehicleFromName(name: String?): Vehicle = Vehicle.entries.firstOrNull { it.name == name } ?: Vehicle.Car

private val SelectorVehicles = listOf(Vehicle.Walking, Vehicle.Bicycle, Vehicle.Motorbike, Vehicle.Car)

/**
 * Vehicle menu: walking, bicycle, motorbike, car. The chosen one has an amber outline. It is only shown where the
 * vehicle can be changed (Home, before a trip); during a trip it is not offered, so the vehicle cannot change.
 */
@Composable
fun VehicleSelector(selected: Vehicle, onSelect: (Vehicle) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface1).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        SelectorVehicles.forEach { v ->
            val on = v == selected
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(14.dp))
                    .border(2.dp, if (on) Amber else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable(role = Role.RadioButton) { onSelect(v) }
                    .semantics { contentDescription = v.name },
                contentAlignment = Alignment.Center,
            ) { StrokeIcon(vehicleIconPath(v), if (on) Amber else TextMuted, size = 22.dp, box = 32f) }
        }
    }
}
