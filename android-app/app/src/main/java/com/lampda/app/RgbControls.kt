package com.lampda.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun LampTypeSelector(type: LampType, onChange: (LampType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        AppText("Lamp type", style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }) { AppText(type.label) }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                LampType.entries.forEach { option ->
                    DropdownMenuItem(text = { AppText(option.label) }, onClick = {
                        onChange(option)
                        expanded = false
                    })
                }
            }
        }
    }
}

private val ColorPresets = listOf(
    "Red" to RgbColor(255, 0, 0), "Orange" to RgbColor(255, 100, 0),
    "Yellow" to RgbColor(255, 255, 0), "Green" to RgbColor(0, 255, 0),
    "Cyan" to RgbColor(0, 255, 255), "Blue" to RgbColor(0, 0, 255),
    "Purple" to RgbColor(180, 0, 255), "White" to RgbColor(),
)

private fun RgbColor.displayColor(): Color = Color(red / 255f, green / 255f, blue / 255f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RgbColorControls(color: RgbColor, onChange: (RgbColor) -> Unit, onCommit: (RgbColor) -> Unit,
    expanded: Boolean, onToggle: () -> Unit) {
    val colorLabel = uiString("Color")
    var hex by remember { mutableStateOf(color.hex) }
    var draft by remember(color) { mutableStateOf(color) }
    var wheelView by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(color) { hex = color.hex }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(28.dp).background(color.displayColor(), RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)))
            Column(Modifier.weight(1f)) {
                AppText("Color", fontWeight = FontWeight.SemiBold)
                AppText(color.hex, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            AppText(if (expanded) "Hide" else "Show", color = MaterialTheme.colorScheme.primary)
        }
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(wheelView, onClick = { wheelView = true }, label = { AppText("Color wheel") })
                FilterChip(!wheelView, onClick = { wheelView = false }, label = { Text("RGB") })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).background(color.displayColor(), RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .semantics { contentDescription = "$colorLabel ${color.hex}" })
                TextField(value = hex, onValueChange = { value ->
                    hex = value.take(7)
                    RgbColor.fromHex(hex)?.let(onCommit)
                }, modifier = Modifier.weight(1f), singleLine = true, label = { AppText("HEX") },
                    isError = RgbColor.fromHex(hex) == null)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPresets.forEach { (name, preset) ->
                    val accessibleName = uiString(name)
                    Box(Modifier.size(40.dp)
                        .selectable(color == preset, role = Role.RadioButton, onClick = { onCommit(preset) })
                        .padding(4.dp).background(preset.displayColor(), CircleShape)
                        .border(if (color == preset) 3.dp else 1.dp,
                            if (color == preset) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                            CircleShape)
                        .semantics { contentDescription = accessibleName })
                }
            }
            if (wheelView) ColorWheel(color, onChange, onCommit)
            else {
                RgbChannel("Red", draft.red, Color(0xFFFF6868),
                    { draft = draft.copy(red = it); onChange(draft) }, { onCommit(draft) })
                RgbChannel("Green", draft.green, Color(0xFF6DD997),
                    { draft = draft.copy(green = it); onChange(draft) }, { onCommit(draft) })
                RgbChannel("Blue", draft.blue, Color(0xFF80AEFF),
                    { draft = draft.copy(blue = it); onChange(draft) }, { onCommit(draft) })
            }
        }
    }
}

@Composable
private fun RgbChannel(label: String, value: Int, tint: Color, onChange: (Int) -> Unit, onCommit: () -> Unit) {
    val accessibleLabel = uiString(label)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText(label, style = MaterialTheme.typography.bodySmall)
            AppText(value.toString(), style = MaterialTheme.typography.bodySmall)
        }
        Slider(value.toFloat(), onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..255f, onValueChangeFinished = onCommit,
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint),
            modifier = Modifier.semantics { contentDescription = accessibleLabel })
    }
}
