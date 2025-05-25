package com.example.kontroler

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

class ConnectionViewModel : ViewModel() {

    // --- Globalny stan aplikacji ---
    var streaming = mutableStateOf(false)
    var eng1State = mutableStateOf(false)
    var eng2State = mutableStateOf(false)
    var bitmap = mutableStateOf<Bitmap?>(null)
    var servoValue = mutableStateOf(0)
    var thrustValue = mutableStateOf(90)
    var isConnected = mutableStateOf(false)




    private var pingPongJob: Job? = null

    fun startPingPong(ip: String, commandPort: Int) {
        // Nie uruchamiaj, jeśli już działa
        if (pingPongJob?.isActive == true) return

        // Nie uruchamiaj, jeśli połączenie nieaktywne
        if (!isConnected.value) {
            Log.w("PingPong", "Nie rozpoczęto pingowania – brak połączenia")
            return
        }

        pingPongJob = viewModelScope.launch {
            val ticker = ticker(delayMillis = 4500, initialDelayMillis = 0)
            for (event in ticker) {
                try {

                    val response = withTimeoutOrNull(5000L) {
                        sendCommandToEsp32(ip, commandPort, "PING", true)
                    }

                    val pongReceived = response?.trim() == "PONG"

                    if (!pongReceived) {
                        Log.e("PingPong", "Brak odpowiedzi PONG w ciągu 5000 ms")
                    }

                    isConnected.value = pongReceived

                } catch (e: Exception) {
                    Log.e("PingPong", "Błąd przy pingowaniu: ${e.message}")
                    isConnected.value = false
                }
            }

            Log.i("PingPong", "PingPongJob zakończony")
        }
    }



    // Wysyłanie komendy do ESP32 z logami
    suspend fun sendCommandToEsp32(ip: String, port: Int, command: String, isConnected: Boolean): String? = withContext(
        Dispatchers.IO) {
        if (!isConnected) {
            Log.w("ESP32_Command", "Brak połączenia z ESP32. Komenda nie została wysłana.")
            return@withContext null // Jeśli nie jesteśmy połączeni, nie wysyłamy komendy
        }


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

    suspend fun synchronizeState(ip: String, port: Int): Map<String, String>? = withContext(
        Dispatchers.IO) {
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


}