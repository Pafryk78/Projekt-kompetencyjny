package com.example.kontroler.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.kontroler.ConnectionViewModel
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

@Composable
fun MainApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "stream_viewer") {
        composable("stream_viewer") {
            Esp32StreamViewer(
                ip = "192.168.4.1",
                commandPort = 80,
                streamPort = 81,
                navController = navController
            )
        }

        composable("settings") {
            SettingsScreen(navController = navController)
        }
    }
}

@OptIn(ObsoleteCoroutinesApi::class)
@Composable
fun Esp32StreamViewer(ip: String, commandPort: Int, streamPort: Int, navController: NavController) {
    var streaming by remember { mutableStateOf(false) }
    var eng1State by remember { mutableStateOf(false) }
    var eng2State by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var servoValue by remember { mutableStateOf(0) }
    var thrustValue by remember { mutableStateOf(90) }
    var isConnected by remember { mutableStateOf(false) }
    var GoodConnection by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()


    val connectionViewModel: ConnectionViewModel = viewModel()




    LaunchedEffect(streaming) {
        if (streaming) {
                connectionViewModel.sendCommandToEsp32(ip, commandPort, "STREAM_START",isConnected)
                connectionViewModel.streamFramesFromEsp32(ip, streamPort, onFrame = {
                    bitmap = it
                }, stopSignal = { !streaming })
        } else {
            connectionViewModel.sendCommandToEsp32(ip, commandPort, "STREAM_STOP",isConnected)
        }
    }

    LaunchedEffect(eng1State) {
        
            connectionViewModel.sendCommandToEsp32(
                ip,
                commandPort,
                if (eng1State) "ENG1_ON" else "ENG1_OFF",
                isConnected
            )
    }

    LaunchedEffect(eng2State) {
            connectionViewModel.sendCommandToEsp32(
                ip,
                commandPort,
                if (eng2State) "ENG2_ON" else "ENG2_OFF",
                isConnected
            )
    }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            // Synchronizujemy stan przy pierwszym połączeniu
            val state = connectionViewModel.synchronizeState(ip, commandPort)
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

        connectionViewModel.startPingPong(ip, commandPort)


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
                        connectionViewModel.sendCommandToEsp32(ip, commandPort, "SERVO_SET:$currentServo", isConnected)
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
                            connectionViewModel.sendCommandToEsp32(ip, commandPort, "ENG2_SET:$currentThrust", isConnected)
                            lastThrust = currentThrust
                        }
                    }
                }
                // Gdy ENG2 jest wyłączony, nie robimy nic - nie wysyłamy danych.
            }
        }
        // Ping-Pong

    }


    Box(modifier = Modifier.fillMaxSize()) {
        // Tło: obraz z kamery
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Podgląd ESP32",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(0.dp)) // pełny ekran, bez zaokrągleń
            )
        } else if (streaming) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // Overlay: wskaźnik połączenia (góra lewa)
        Box(
            modifier = Modifier
                .padding(12.dp)
                .size(16.dp)
                .align(Alignment.TopStart)
                .background(if (GoodConnection) Color.Green else Color.Red, shape = RoundedCornerShape(50))
        )

        // ** NOWY PRZYCISK USTAWIEŃ (góra prawa) **
        IconButton(
            onClick = { navController.navigate("settings") },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Ustawienia",
                tint = Color.Black
            )
        }




        // Overlay: przyciski sterujące (dół, środek)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                actionName = "Kamera",
                isActive = streaming,
                onClick = { streaming = !streaming }
            )
        }

        // Overlay: suwak serwa (lewy środek)
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Serwo", fontWeight = FontWeight.Bold, color = Color.White)
            ThrottleSlider(
                minValue = 0,
                maxValue = 180,
                initialValue = 90,
                modifier = Modifier.width(100.dp),
                onValueChange = { servoValue = it }
            )
        }

        // Overlay: suwak ciągu (prawy środek)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Ciąg", fontWeight = FontWeight.Bold, color = Color.White)
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

@Composable
fun SettingsScreen(
    navController: NavController
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Ustawienia ESP32")

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { navController.popBackStack() }
        ) {
            Text("Powrót")
        }
    }
}

