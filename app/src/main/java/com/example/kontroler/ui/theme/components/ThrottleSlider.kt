package com.example.kontroler.ui.theme.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ThrottleSlider(
    modifier: Modifier = Modifier,
    minValue: Float = 0f,
    maxValue: Float = 180f,
    onValueChange:  (Float) -> Unit
) {
    var position by remember { mutableStateOf(90f) }
    val animatedPosition by animateFloatAsState(targetValue = position, label = "ThrottleAnimation")

    Box(
        modifier = modifier
            .size(width = 15.dp, height = 200.dp)
            .background(Color.Gray, shape = RoundedCornerShape(20.dp))
            .border(4.dp, Color.LightGray, shape = RoundedCornerShape(20.dp))
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    position = (position - delta / 5f).coerceIn(minValue, maxValue)
                    onValueChange(position)
                }
            )
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val trackWidth = size.width / 4
            val trackHeight = size.height
            val knobHeight = size.height / 6
            val knobWidth = trackWidth * 2
            val knobY = (1 - (animatedPosition / maxValue)) * (trackHeight - knobHeight)

            drawRoundRect(
                color = Color.DarkGray,
                size = Size(trackWidth, trackHeight),
                topLeft = Offset((size.width - trackWidth) / 2, 0f),
                cornerRadius = CornerRadius(10f, 10f)
            )

            drawRoundRect(
                color = Color.Red,
                size = Size(trackWidth, trackHeight * (animatedPosition / maxValue)),
                topLeft = Offset((size.width - trackWidth) / 2, trackHeight * (1 - (animatedPosition / maxValue)))
            )

            drawRoundRect(
                color = Color.White,
                size = Size(knobWidth, knobHeight),
                topLeft = Offset((size.width - knobWidth) / 2, knobY),
                cornerRadius = CornerRadius(20f, 20f)
            )
        }
    }
}
