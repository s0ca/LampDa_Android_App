package com.lampda.app

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class WheelSelection(val hue: Float, val saturation: Float)

internal fun wheelSelection(x: Float, y: Float, radius: Float): WheelSelection {
    require(radius > 0f)
    val hue = ((Math.toDegrees(atan2(y.toDouble(), x.toDouble())) + 360.0) % 360.0).toFloat()
    return WheelSelection(hue, (hypot(x, y) / radius).coerceIn(0f, 1f))
}

private data class WheelColor(val hue: Float, val saturation: Float, val value: Float) {
    fun rgb(): RgbColor {
        val packed = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
        return RgbColor(AndroidColor.red(packed), AndroidColor.green(packed), AndroidColor.blue(packed))
    }
}

private fun RgbColor.wheelColor(): WheelColor {
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(red, green, blue, hsv)
    return WheelColor(hsv[0], hsv[1], hsv[2])
}

@Composable
internal fun ColorWheel(color: RgbColor, onChange: (RgbColor) -> Unit, onCommit: (RgbColor) -> Unit) {
    var selected by remember { mutableStateOf(color.wheelColor()) }
    LaunchedEffect(color) {
        // Keep the chosen hue when selecting white or black, where RGB cannot encode it.
        if (selected.rgb() != color) selected = color.wheelColor()
    }
    val change by rememberUpdatedState(onChange)
    val commit by rememberUpdatedState(onCommit)
    val description = uiString("Color wheel")
    val rainbow = remember { listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(1f)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = minOf(size.width, size.height) / 2f - 12.dp.toPx()
                    if ((down.position - center).getDistance() > radius) return@awaitEachGesture
                    fun select(position: Offset) {
                        val point = wheelSelection(position.x - center.x, position.y - center.y, radius)
                        selected = selected.copy(hue = if (point.saturation < 0.001f) selected.hue else point.hue,
                            saturation = point.saturation)
                        change(selected.rgb())
                    }
                    down.consume()
                    select(down.position)
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        pressed = pointer.pressed
                        pointer.consume()
                        if (pressed) select(pointer.position)
                    }
                    commit(selected.rgb())
                }
            }) {
            val radius = size.minDimension / 2f - 12.dp.toPx()
            drawCircle(Brush.sweepGradient(rainbow, center), radius, center)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius), radius, center)
            val angle = Math.toRadians(selected.hue.toDouble())
            val marker = center + Offset((cos(angle) * radius * selected.saturation).toFloat(),
                (sin(angle) * radius * selected.saturation).toFloat())
            drawCircle(Color.Black, 9.dp.toPx(), marker, style = Stroke(4.dp.toPx()))
            drawCircle(Color.White, 9.dp.toPx(), marker, style = Stroke(2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppText("Color intensity")
            AppText("${(selected.value * 100).roundToInt()}%")
        }
        Slider(selected.value, onValueChange = {
            selected = selected.copy(value = it)
            change(selected.rgb())
        }, valueRange = 0f..1f, onValueChangeFinished = { commit(selected.rgb()) }, modifier = Modifier.fillMaxWidth())
    }
}
