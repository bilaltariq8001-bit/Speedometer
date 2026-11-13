package com.speedometer.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Amber pill button, 48dp minimum touch target. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Bg),
    ) { Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold)) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, color = TextMuted, style = MaterialTheme.typography.titleMedium)
    }
}

/** Selectable card: amber border when selected. */
@Composable
fun SelectCard(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = Surface1,
        shape = RoundedCornerShape(Radii.chip),
        border = BorderStroke(2.dp, if (selected) Amber else Color.Transparent),
        modifier = modifier.heightIn(min = 48.dp).clickable(role = Role.RadioButton, onClick = onClick),
    ) { content() }
}

@Composable
fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(Surface1, RoundedCornerShape(Radii.card)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(body, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Amber, checkedThumbColor = Bg, uncheckedTrackColor = Line3),
        )
    }
}
