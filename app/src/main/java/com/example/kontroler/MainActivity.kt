package com.example.kontroler

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Log
import android.widget.SeekBar
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.ui.unit.dp
import okhttp3.*
import java.io.IOException


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LedControlApp()
        }
    }
}

@Composable
fun LedControlApp() {
    var pwmValue by remember { mutableStateOf(128) }  // Wartość PWM, domyślnie 128
    val ipAddress = "http://192.168.100.95"  // Adres IP ESP32

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Wypełnienie PWM: $pwmValue", style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(16.dp))

        // Manetka sterująca PWM
        ThrottleSlider(
            modifier = Modifier.padding(16.dp),
            minValue = 0f,
            maxValue = 255f,
            onValueChange = { newValue ->
                pwmValue = newValue.toInt()
                sendRequest("$ipAddress/set_pwm?value=$pwmValue")
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ThrottleSlider(
    modifier: Modifier = Modifier,
    minValue: Float = 0f,
    maxValue: Float = 255f,
    onValueChange: (Float) -> Unit
) {
    var position by remember { mutableStateOf(128f) }
    val animatedPosition by animateFloatAsState(targetValue = position, label = "ThrottleAnimation")

    Box(
        modifier = modifier
            .size(width = 120.dp, height = 320.dp) // Większy suwak
            .background(Color.Gray, shape = RoundedCornerShape(20.dp))
            .border(4.dp, Color.LightGray, shape = RoundedCornerShape(20.dp)) // Obwódka suwaka
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    position = (position - delta / 5f).coerceIn(minValue, maxValue)
                    onValueChange(position)
                }
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val trackWidth = size.width / 3
            val trackHeight = size.height
            val knobHeight = size.height / 5 // Większy uchwyt
            val knobWidth = trackWidth * 2  // Szerszy uchwyt
            val knobY = (1 - (animatedPosition / maxValue)) * (trackHeight - knobHeight)

            // Rysowanie toru suwaka (ciemnoszary z obwódką)
            drawRoundRect(
                color = Color.DarkGray,
                size = Size(trackWidth, trackHeight),
                topLeft = Offset((size.width - trackWidth) / 2, 0f),
                cornerRadius = CornerRadius(10f, 10f)
            )

            // Rysowanie wypełnienia (czerwony pasek)
            drawRoundRect(
                color = Color.Red,
                size = Size(trackWidth, trackHeight * (animatedPosition / maxValue)),
                topLeft = Offset((size.width - trackWidth) / 2, trackHeight * (1 - (animatedPosition / maxValue)))
            )

            // Rysowanie obwódki uchwytu (szara)
            drawRoundRect(
                color = Color.LightGray, // Obwódka
                size = Size(knobWidth + 8f, knobHeight + 8f),
                topLeft = Offset((size.width - (knobWidth + 8f)) / 2, knobY - 4f),
                cornerRadius = CornerRadius(24f, 24f)
            )

            // Rysowanie uchwytu (biały)
            drawRoundRect(
                color = Color.White,
                size = Size(knobWidth, knobHeight),
                topLeft = Offset((size.width - knobWidth) / 2, knobY),
                cornerRadius = CornerRadius(20f, 20f)
            )
        }
    }
}

fun sendRequest(url: String) {
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            e.printStackTrace()
        }

        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                println("Response: ${response.body?.string()}")
            }
        }
    })
}