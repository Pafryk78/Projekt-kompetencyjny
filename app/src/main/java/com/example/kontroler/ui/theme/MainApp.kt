package com.example.kontroler.ui.theme

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
            SettingsScreen(
                ip = "192.168.4.1",
                commandPort = 80,
                navController = navController)
        }
    }
}

@SuppressLint("UnrememberedGetBackStackEntry")
@OptIn(ObsoleteCoroutinesApi::class)
@Composable
fun Esp32StreamViewer(ip: String, commandPort: Int, streamPort: Int, navController: NavController) {

    val viewModel: ConnectionViewModel = viewModel(LocalContext.current as ComponentActivity)


    val scope = rememberCoroutineScope()

    var streaming = viewModel.streaming.value
    var eng1State = viewModel.eng1State.value
    var eng2State = viewModel.eng2State.value
    var bitmap = viewModel.bitmap.value
    var servoValue = viewModel.servoValue.value
    var thrustValue = viewModel.thrustValue.value
    var isConnected = viewModel.isConnected.value



    LaunchedEffect(streaming) {
        if (streaming) {
            viewModel.sendCommandToEsp32(ip, commandPort, "STREAM_START",isConnected)
            viewModel.streamFramesFromEsp32(ip, streamPort, onFrame = {
                    viewModel.bitmap.value = it
                }, stopSignal = { !streaming })
        } else {
            viewModel.sendCommandToEsp32(ip, commandPort, "STREAM_STOP",isConnected)
        }
    }

    LaunchedEffect(eng1State) {

        viewModel.sendCommandToEsp32(
                ip,
                commandPort,
                if (eng1State) "ENG1_ON" else "ENG1_OFF",
                isConnected
            )
    }

    LaunchedEffect(eng2State) {
        viewModel.sendCommandToEsp32(
                ip,
                commandPort,
                if (eng2State) "ENG2_ON" else "ENG2_OFF",
                isConnected
            )
    }



    LaunchedEffect(Unit) {

        viewModel.startPingPong(ip, commandPort)


        // serwo
        launch {
            val ticker = ticker(delayMillis = 10, initialDelayMillis = 0)
            var lastServo = viewModel.servoValue.value
            var currentServo = viewModel.servoValue.value

            for (event in ticker) {
                val targetServo = viewModel.servoValue.value

                if (currentServo != targetServo) {
                    currentServo += when {
                        currentServo < targetServo -> 1
                        currentServo > targetServo -> -1
                        else -> 0
                    }

                    if (currentServo != lastServo) {
                        viewModel.sendCommandToEsp32(ip, commandPort, "SERVO_SET:$currentServo", isConnected)
                        lastServo = currentServo
                    }
                }
            }
        }

        launch {
            val ticker = ticker(delayMillis = 10, initialDelayMillis = 0)
            var lastThrust = viewModel.thrustValue.value
            var currentThrust = viewModel.thrustValue.value

            for (event in ticker) {
                val targetThrust = viewModel.thrustValue.value
                val eng2 = viewModel.eng2State.value

                if (eng2) {
                    if (currentThrust != targetThrust) {
                        currentThrust += when {
                            currentThrust < targetThrust -> 1
                            currentThrust > targetThrust -> -1
                            else -> 0
                        }

                        if (currentThrust != lastThrust) {
                            viewModel.sendCommandToEsp32(ip, commandPort, "ENG2_SET:$currentThrust", isConnected)
                            lastThrust = currentThrust
                        }
                    }
                }
                // Jeśli ENG2 jest wyłączony – nic nie robimy
            }
        }


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
                .background(if (isConnected) Color.Green else Color.Red, shape = RoundedCornerShape(50))
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
                onClick = { viewModel.eng1State.value = !eng1State }
            )
            CustomSwitch(
                actionName = "ENG2",
                isActive = eng2State,
                onClick = { viewModel.eng2State.value = !eng2State }
            )
            CustomSwitch(
                actionName = "Kamera",
                isActive = streaming,
                onClick = { viewModel.streaming.value = !streaming }
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
                onValueChange = { viewModel.servoValue.value = it }
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
                onValueChange = { viewModel.thrustValue.value = it }
            )
        }
    }

}

@SuppressLint("UnrememberedGetBackStackEntry")
@Composable
fun SettingsScreen(
    ip: String,
    commandPort: Int,
    navController: NavController
) {
    val viewModel: ConnectionViewModel = viewModel(LocalContext.current as ComponentActivity)

    var startSync by remember { mutableStateOf(false) }
    val context = LocalContext.current

    var streaming = viewModel.streaming.value
    var eng1State = viewModel.eng1State.value
    var eng2State = viewModel.eng2State.value
    var servoValue = viewModel.servoValue.value
    var thrustValue = viewModel.thrustValue.value


    val isConnected by viewModel.isConnected

    LaunchedEffect(Unit) {
        viewModel.startPingPong(ip, commandPort)
    }
    LaunchedEffect(startSync) {
        if (startSync) {
            // Wywołaj synchronizację
            val state = viewModel.synchronizeState(ip, commandPort)
            if (state != null) {
                eng1State = state["ENG1"] == "ON"
                eng2State = state["ENG2"] == "ON"
                thrustValue = state["ENG2_VAL"]?.toIntOrNull() ?: 0
                servoValue = state["SERVO"]?.toIntOrNull() ?: 90
                streaming = state["STREAM"] == "ON"

                viewModel.isConnected.value = true
                Log.e("SYNCH", "Elo")
            } else {
                Log.e("SYNCH", "Gówno")
                viewModel.isConnected.value = false
            }
            // Resetuj startSync, żeby można było wywołać ponownie po kliknięciu
            startSync = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Ikona X w prawym górnym rogu
        IconButton(
            onClick = {
                Log.d(
                    "SettingsScreen",
                    "Powrót kliknięty, isConnected = ${viewModel.isConnected.value}"
                )
                navController.popBackStack()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Zamknij",
                tint = Color.Black
            )
        }

        // Reszta zawartości
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp), // Aby nie zasłonić X-a
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { startSync = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) Color.Green else Color.Red
                )
            ) {
                Text("Połącz")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

}