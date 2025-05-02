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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.kontroler.ui.theme.components.CustomSwitch
import com.example.kontroler.ui.theme.components.ThrottleSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.EOFException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
@OptIn(ObsoleteCoroutinesApi::class)
@Composable
fun Esp32StreamViewer(ip: String, commandPort: Int, streamPort: Int) {
    var streaming by remember { mutableStateOf(false) }
    var eng1State by remember { mutableStateOf(false) }
    var eng2State by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var servoValue by remember { mutableStateOf(0) }
    var thrustValue by remember { mutableStateOf(90) }
    var isConnected by remember { mutableStateOf(false) }

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

    LaunchedEffect(isConnected) {
        if (isConnected) {
            // Synchronizujemy stan przy pierwszym połączeniu
            val state = synchronizeState(ip, commandPort)
            state?.let {
                eng1State = it["ENG1"] == "ON"
                eng2State = it["ENG2"] == "ON"
                thrustValue = it["ENG2_VAL"]?.toIntOrNull() ?: 0
                servoValue = it["SERVO"]?.toIntOrNull() ?: 90
                streaming = it["STREAM"] == "ON"
            }
        }
    }

    LaunchedEffect(Unit) {


        // serwo
        launch {
            val ticker = ticker(delayMillis = 10, initialDelayMillis = 0)
            var lastServo = servoValue
            var currentServo = servoValue

            for (event in ticker) {
                // Sprawdzamy, czy serwo jest w trakcie interpolacji
                if (currentServo != servoValue) {
                    currentServo += when {
                        currentServo < servoValue -> 1
                        currentServo > servoValue -> -1
                        else -> 0
                    }
                    // Wysyłamy komendę tylko jeśli zmieniła się wartość serwa
                    if (currentServo != lastServo) {
                        sendCommandToEsp32(ip, commandPort, "SERVO_SET:$currentServo")
                        lastServo = currentServo
                    }
                }
            }
        }

        // ciąg (ENG2)
        launch {
            val ticker = ticker(delayMillis = 10, initialDelayMillis = 0)
            var lastThrust = thrustValue
            var currentThrust = thrustValue

            for (event in ticker) {
                if (eng2State) {
                    // ENG2 włączony: wysyłamy tylko wtedy, gdy ciąg zmienia się w stosunku do wartości zadanej
                    if (currentThrust != thrustValue) {
                        currentThrust += when {
                            currentThrust < thrustValue -> 1
                            currentThrust > thrustValue -> -1
                            else -> 0
                        }
                        if (currentThrust != lastThrust) {
                            sendCommandToEsp32(ip, commandPort, "ENG2_SET:$currentThrust")
                            lastThrust = currentThrust
                        }
                    }
                }
                // Gdy ENG2 jest wyłączony, nie robimy nic - nie wysyłamy danych.
            }
        }
        // Ping-Pong
        launch {
            val ticker = ticker(delayMillis = 5000, initialDelayMillis = 0)

            for (event in ticker) {
                try {
                    if (isConnected) {
                        // Wysłanie PING i oczekiwanie na odpowiedź PONG przez 500 ms
                        val response = withTimeoutOrNull(500L) {
                            sendCommandToEsp32(ip, commandPort, "PING")
                        }

                        val pongReceived = response?.trim() == "PONG"

                        if (!pongReceived) {
                            Log.e("PingPong", "Brak odpowiedzi PONG w ciągu 500 ms")
                        }

                        // Zaktualizowanie statusu po otrzymaniu odpowiedzi
                        isConnected = pongReceived
                    }
                } catch (e: Exception) {
                    Log.e("PingPong", "Błąd przy pingowaniu: ${e.message}")
                    isConnected = false
                }
            }
        }
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
                    minValue = 0,
                    maxValue = 180,
                    initialValue = 90,
                    modifier = Modifier.width(100.dp),
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
                    //Wskaźnik połączenia
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(16.dp)
                            .background(if (isConnected) Color.Green else Color.Red, shape = RoundedCornerShape(50))
                    )
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
                    minValue = 0,
                    maxValue = 100,
                    initialValue = 0,
                    modifier = Modifier.width(100.dp),
                    onValueChange = { thrustValue = it }
                )
            }
        }
    }
}



// Wysyłanie komendy do ESP32 z logami
suspend fun sendCommandToEsp32(ip: String, port: Int, command: String): String? = withContext(Dispatchers.IO) {
    try {
        Log.d("ESP32_Command", "Wysyłam komendę: $command na IP: $ip, port: $port")

        Socket(ip, port).use { socket ->
            val output = socket.getOutputStream()
            output.write("$command\n".toByteArray())
            output.flush()

            // Tylko jeśli komenda to "PING", odbieramy odpowiedź
            if (command.uppercase() == "PING") {
                val input = socket.getInputStream()
                val reader = BufferedReader(InputStreamReader(input))
                val response = reader.readLine()
                Log.d("ESP32_Command", "Odpowiedź z ESP32: $response")
                return@withContext response
            }
        }

        Log.d("ESP32_Command", "Komenda wysłana pomyślnie: $command")
        return@withContext null
    } catch (e: Exception) {
        Log.e("ESP32_Command", "Błąd przy komunikacji: $command", e)
        return@withContext null
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

suspend fun synchronizeState(ip: String, port: Int): Map<String, String>? = withContext(Dispatchers.IO) {
    try {
        Socket(ip, port).use { socket ->
            val output = PrintWriter(socket.getOutputStream(), true)
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))

            output.println("SYNCH")

            val result = mutableMapOf<String, String>()
            var line: String?
            var started = false

            while (input.readLine().also { line = it } != null) {
                if (line == "STATE_BEGIN") {
                    started = true
                } else if (line == "STATE_END") {
                    break
                } else if (started && line!!.contains(":")) {
                    val parts = line!!.split(":", limit = 2)
                    result[parts[0]] = parts[1]
                }
            }

            return@withContext result
        }
    } catch (e: Exception) {
        Log.e("SYNCH", "Błąd przy synchronizacji: ${e.message}")
        return@withContext null
    }
}

