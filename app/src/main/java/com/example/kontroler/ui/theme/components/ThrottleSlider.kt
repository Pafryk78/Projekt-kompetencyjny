package com.example.kontroler.ui.theme.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun ThrottleSlider(
    modifier: Modifier = Modifier,
    minValue: Int = 0,
    maxValue: Int = 180,
    maxLimit: Int = maxValue,
    initialValue: Int = 90,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true
) {
    var position by remember { mutableStateOf(initialValue.coerceIn(minValue, maxLimit)) }
    val animatedPosition by animateFloatAsState(
        targetValue = position.toFloat(),
        label = "ThrottleAnimation"
    )

    Box(
        modifier = modifier
            .size(width = 15.dp, height = 200.dp)
            .background(Color.Gray, shape = RoundedCornerShape(20.dp))
            .border(4.dp, Color.LightGray, shape = RoundedCornerShape(20.dp))
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    if (!enabled) return@rememberDraggableState
                    val newPosition = (position - (delta / 5f).roundToInt())
                        .coerceIn(minValue, maxLimit) // ⛔ ograniczenie dynamiczne
                    if (newPosition != position) {
                        position = newPosition
                        onValueChange(newPosition)
                    }
                },
                enabled = enabled
            )
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackWidth = size.width / 4
            val trackHeight = size.height
            val knobHeight = size.height / 6
            val knobWidth = trackWidth * 2
            val knobY = (1 - (animatedPosition / maxValue)) * (trackHeight - knobHeight)

            // Tło ścieżki
            drawRoundRect(
                color = Color.DarkGray,
                size = Size(trackWidth, trackHeight),
                topLeft = Offset((size.width - trackWidth) / 2, 0f),
                cornerRadius = CornerRadius(10f, 10f)
            )

            // Wypełnienie zależne od pozycji
            drawRoundRect(
                color = Color.Red,
                size = Size(trackWidth, trackHeight * (animatedPosition / maxValue)),
                topLeft = Offset((size.width - trackWidth) / 2, trackHeight * (1 - (animatedPosition / maxValue)))
            )

            // Pokrętło
            drawRoundRect(
                color = Color.White,
                size = Size(knobWidth, knobHeight),
                topLeft = Offset((size.width - knobWidth) / 2, knobY),
                cornerRadius = CornerRadius(20f, 20f)
            )
        }
    }
}
