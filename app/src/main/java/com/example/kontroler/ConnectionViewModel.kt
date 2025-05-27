package com.example.kontroler

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConnectionViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    // --- Sensor ---
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val servoValue = mutableStateOf(90)  // pozycja bazowa serwa
    private var servoOffset = 0f          // offset od rolla telefonu

    fun startSensorListening() {
        sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stopSensorListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            val orientationAngles = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            val rollRadians = orientationAngles[2]    // roll - przechylenie na boki
            val rollDegrees = Math.toDegrees(rollRadians.toDouble()).toFloat()

            // Ograniczamy max offset do np. ±30 stopni i skalujemy do ±10 dla serwa
            val maxRollDegrees = 30f
            val maxServoOffset = 10f

            // Skalowanie roll do zakresu sterowania serwem
            servoOffset = (rollDegrees.coerceIn(-maxRollDegrees, maxRollDegrees) / maxRollDegrees) * maxServoOffset
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Wartość sterująca serwem z uwzględnieniem offsetu
    fun getServoControlValue(): Int {
        val rawValue = servoValue.value + servoOffset
        return rawValue.toInt().coerceIn(0, 180)
    }

    // --- Connection and Streaming ---
    var streaming = mutableStateOf(false)
    var eng1State = mutableStateOf(false)
    var eng2State = mutableStateOf(false)
    var bitmap = mutableStateOf<Bitmap?>(null)
    var thrustValue = mutableStateOf(90)
    var isConnected = mutableStateOf(false)
    var ograniczenie = mutableStateOf(false)

    private var pingPongJob: Job? = null

    fun startPingPong(ip: String, commandPort: Int) {
        if (pingPongJob?.isActive == true) return
        if (!isConnected.value) {
            Log.w("PingPong", "Nie rozpoczęto pingowania – brak połączenia")
            return
        }

        pingPongJob = viewModelScope.launch {
            val ticker = ticker(delayMillis = 4500, initialDelayMillis = 0)
            for (event in ticker) {
                try {
                    val response = withTimeoutOrNull(5000L) {
                        sendCommandToEsp32(ip, commandPort, "PING", isConnected.value)
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

    suspend fun sendCommandToEsp32(ip: String, port: Int, command: String, isConnected: Boolean): String? = withContext(
        Dispatchers.IO) {
        if (!isConnected) {
            Log.w("ESP32_Command", "Brak połączenia z ESP32. Komenda nie została wysłana.")
            return@withContext null
        }

        try {
            Log.d("ESP32_Command", "Wysyłam komendę: $command na IP: $ip, port: $port")
            Socket(ip, port).use { socket ->
                val output = socket.getOutputStream()
                output.write("$command\n".toByteArray())
                output.flush()

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

    var lastFrame: Bitmap? = null

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
                        lastFrame = it.copy(Bitmap.Config.ARGB_8888, false) // kopia do snapshotu
                        onFrame(it)
                    }
                    delay(50)
                }

                output.write("STREAM_STOP\n".toByteArray())
                output.flush()
            }
        } catch (e: Exception) {
            Log.e("ESP32_Stream", "Błąd przy odbiorze strumienia", e)
        }
    }

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

//robienie zdjęć

    private val _savedImageUri = MutableStateFlow<Uri?>(null)
    val savedImageUri: StateFlow<Uri?> = _savedImageUri

    fun setSavedImageUri(uri: Uri?) {
        _savedImageUri.value = uri
    }



    fun saveBitmapToFile(context: Context, bitmap: Bitmap, filename: String = "snapshot.jpg"): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (imageUri != null) {
            resolver.openOutputStream(imageUri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(imageUri, contentValues, null, null)
        }

        return imageUri
    }

    suspend fun saveSnapshot(
        context: Context,
        bitmap: Bitmap,
        onImageSaved: (android.net.Uri?) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            // 🔄 Obrót bitmapy o 180 stopni
            val matrix = Matrix().apply { postRotate(180f) }
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0,
                bitmap.width, bitmap.height,
                matrix, true
            )

            val filename = "snapshot_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Kontroler")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            imageUri?.let { uri ->
                resolver.openOutputStream(uri).use { outputStream ->
                    if (outputStream != null) {
                        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                Log.d("saveSnapshot", "Obrócone zdjęcie zapisane: $uri")
                onImageSaved(uri)
            } ?: run {
                Log.e("saveSnapshot", "Nie udało się utworzyć Uri dla zapisu")
                onImageSaved(null)
            }
        } catch (e: Exception) {
            Log.e("saveSnapshot", "Błąd zapisu zdjęcia", e)
            onImageSaved(null)
        }
    }
}