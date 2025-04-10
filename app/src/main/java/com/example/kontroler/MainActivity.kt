package com.example.kontroler

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.asImageBitmap
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {

                Esp32StreamViewer(ip = "192.168.4.1", port = 80) // IP i port twojego ESP32


            }
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

            output.write("STREAM\n".toByteArray())
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
                if (bmp != null) onFrame(bmp)
                delay(50) // ok. 20 FPS
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}



// Główna funkcja UI
@Composable
fun Esp32StreamViewer(ip: String, port: Int) {
    var streaming by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    val scope = rememberCoroutineScope()

    // Rozpoczęcie/stopowanie strumienia
    LaunchedEffect(streaming) {
        if (streaming) {
            streamFramesFromEsp32(ip, port, onFrame = {
                bitmap = it
            }, stopSignal = { !streaming })
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CustomSwitch(
            engineName = "Podgląd na żywo",
            isActive = streaming,
            onClick = { streaming = !streaming }
        )

        Spacer(Modifier.height(16.dp))

        when {
            streaming && bitmap == null -> CircularProgressIndicator()
            bitmap != null -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Podgląd ESP32",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}