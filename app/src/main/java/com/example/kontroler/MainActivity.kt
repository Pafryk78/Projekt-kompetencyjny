package com.example.kontroler

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.InputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.example.kontroler.ui.theme.components.CustomSwitch
import com.example.kontroler.ui.theme.components.ThrottleSlider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val commandPort = 80
            val streamPort = 81
                Esp32StreamViewer(ip = "192.168.4.1", commandPort, streamPort) // IP i port twojego ESP32


            }
    }
}

// Główna funkcja UI
@Composable
fun Esp32StreamViewer(ip: String, commandPort: Int, streamPort: Int) {
    var streaming by remember { mutableStateOf(false) }
    var eng1State by remember { mutableStateOf(false) }
    var eng2State by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var servoValue by remember { mutableStateOf(90f) }
    var thrustValue by remember { mutableStateOf(90f) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(streaming) {
        if (streaming) {
            scope.launch {
                sendCommandToEsp32(ip, commandPort, "STREAM_START")
                streamFramesFromEsp32(ip, streamPort, onFrame = {
                    bitmap = it
                }, stopSignal = { !streaming })
            }
        } else {
            sendCommandToEsp32(ip, commandPort, "STREAM_STOP")
        }
    }

    LaunchedEffect(eng1State) {
        sendCommandToEsp32(ip, commandPort, if (eng1State) "ENG1_ON" else "ENG1_OFF")
    }

    LaunchedEffect(eng2State) {
        sendCommandToEsp32(ip, commandPort, if (eng2State) "ENG2_ON" else "ENG2_OFF")
    }

    LaunchedEffect(servoValue) {
        sendCommandToEsp32(ip, commandPort, "SERVO_${servoValue.toInt()}")
    }

    LaunchedEffect(thrustValue) {
        sendCommandToEsp32(ip, commandPort, "THRUST_${thrustValue.toInt()}")
    }

    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Lewy slider
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("Serwo", fontWeight = FontWeight.Bold)
                ThrottleSlider(
                    modifier = Modifier.width(30.dp),
                    onValueChange = { servoValue = it }
                )
            }

            // Środek: kamera i przyciski
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Obraz z kamery z zaokrągleniem
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = "Podgląd ESP32",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                        )
                    } else if (streaming) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }

                // Przyciski sklejone razem
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CustomSwitch(
                        actionName = "ENG1",
                        isActive = eng1State,
                        onClick = { eng1State = !eng1State }
                    )
                    CustomSwitch(
                        actionName = "ENG2",
                        isActive = eng2State,
                        onClick = { eng2State = !eng2State }
                    )
                    CustomSwitch(
                        actionName = "Kamerka",
                        isActive = streaming,
                        onClick = { streaming = !streaming }
                    )
                }
            }

            // Prawy slider
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("Ciąg", fontWeight = FontWeight.Bold)
                ThrottleSlider(
                    modifier = Modifier.width(30.dp),
                    onValueChange = { thrustValue = it }
                )
            }
        }
    }
}



// Wysyłanie komendy do ESP32 z logami
suspend fun sendCommandToEsp32(ip: String, port: Int, command: String) = withContext(Dispatchers.IO) {
    try {
        Log.d("ESP32_Command", "Wysyłam komendę: $command na IP: $ip, port: $port") // Log przed wysyłką komendy
        Socket(ip, port).use { socket ->
            val output = socket.getOutputStream()
            output.write("$command\n".toByteArray())
            output.flush()
        }
        Log.d("ESP32_Command", "Komenda wysłana pomyślnie: $command") // Log po wysyłce komendy
    } catch (e: Exception) {
        Log.e("ESP32_Command", "Błąd przy wysyłaniu komendy: $command", e) // Log błędu
    }
}

// Funkcja do odbioru strumienia
suspend fun streamFramesFromEsp32(
    ip: String,
    port: Int,
    onFrame: (Bitmap) -> Unit,
    stopSignal: () -> Boolean
) = withContext(Dispatchers.IO) {
    try {
        Socket(ip, port).use { socket ->
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // Inicjalizacja transmisji
            Log.d("ESP32_Stream", "Rozpoczynam transmisję wideo na IP: $ip, port: $port")
            output.write("STREAM_START\n".toByteArray())
            output.flush()

            while (!stopSignal()) {
                val header = ByteArray(6)
                input.readFully(header)

                if (!header.contentEquals("FRAME\n".toByteArray())) continue

                val lenBytes = ByteArray(4)
                input.readFully(lenBytes)
                val length = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).int

                val imageBytes = ByteArray(length)
                input.readFully(imageBytes)

                val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, length)
                bmp?.let {
                    onFrame(it) // Wywołanie funkcji na każdej klatce
                }
                delay(50) // Oczekiwanie na kolejną klatkę (około 20 FPS)
            }

            // Po zakończeniu transmisji, wyślij komendę STOP
            Log.d("ESP32_Stream", "Kończę transmisję wideo na IP: $ip, port: $port")
            output.write("STREAM_STOP\n".toByteArray())
            output.flush()
        }
    } catch (e: Exception) {
        Log.e("ESP32_Stream", "Błąd przy odbiorze strumienia", e)
    }
}

// Funkcja do pełnego odczytu z InputStream
fun InputStream.readFully(buffer: ByteArray) {
    var bytesRead = 0
    while (bytesRead < buffer.size) {
        val result = this.read(buffer, bytesRead, buffer.size - bytesRead)
        if (result == -1) throw EOFException("Stream ended before reading all bytes")
        bytesRead += result
    }
}
