package com.example.kontroler.ui.theme


import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.kontroler.ConnectionViewModel
import com.example.kontroler.R
import com.example.kontroler.ui.theme.components.Autorzy
import com.example.kontroler.ui.theme.components.CustomSwitch
import com.example.kontroler.ui.theme.components.ThrottleSlider
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch

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

        composable("sekretnyWidok") {
            SekretnyWidok(navController)
        }
    }
}



@Composable
fun Esp32StreamViewer(ip: String, commandPort: Int, streamPort: Int, navController: NavController) {

    val viewModel: ConnectionViewModel = viewModel(LocalContext.current as ComponentActivity)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var streaming = viewModel.streaming.value
    var eng1State = viewModel.eng1State.value
    var eng2State = viewModel.eng2State.value
    var bitmap = viewModel.bitmap.value
    var servoValue = viewModel.servoValue.value
    var thrustValue = viewModel.thrustValue.value
    var isConnected = viewModel.isConnected.value




    val isRecording by viewModel.isRecording.collectAsState()


//    DisposableEffect(Unit) {
//        viewModel.startSensorListening()
//        onDispose {
//            viewModel.stopSensorListening()
//        }
//    }


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

    LaunchedEffect(viewModel.ograniczenie.value) {
        if (viewModel.ograniczenie.value && viewModel.thrustValue.value > 50) {
            viewModel.thrustValue.value = 50
        }
    }

    LaunchedEffect(Unit) {

        viewModel.startPingPong(ip, commandPort)


        // serwo


        launch {
            val ticker = ticker(delayMillis = 20, initialDelayMillis = 0)
            var lastServo = viewModel.servoValue.value

            for (event in ticker) {
                val currentServo = viewModel.getServoControlValue()

                if (currentServo != lastServo) {
                    viewModel.sendCommandToEsp32(ip, commandPort, "SERVO_SET:$currentServo", isConnected)
                    lastServo = currentServo
                }
            }
        }

//        launch {
//            val ticker = ticker(delayMillis = 10, initialDelayMillis = 0)
//            var lastServo = viewModel.servoValue.value
//            var currentServo = viewModel.servoValue.value
//
//            for (event in ticker) {
//                val targetServo = viewModel.getServoControlValue()
//
//                if (currentServo != targetServo) {
//                    currentServo += when {
//                        currentServo < targetServo -> 1
//                        currentServo > targetServo -> -1
//                        else -> 0
//                    }
//
//                    if (currentServo != lastServo) {
//                        viewModel.sendCommandToEsp32(ip, commandPort, "SERVO_SET:$currentServo", isConnected)
//                        lastServo = currentServo
//                    }
//                }
//            }
//        }


        launch {
            val ticker = ticker(delayMillis = 20, initialDelayMillis = 0)
            var lastThrust = viewModel.thrustValue.value

            for (event in ticker) {
                val currentThrust = viewModel.thrustValue.value
                val eng2 = viewModel.eng2State.value

                if (eng2 && currentThrust != lastThrust) {
                    viewModel.sendCommandToEsp32(ip, commandPort, "ENG2_SET:$currentThrust", isConnected)
                    lastThrust = currentThrust
                }
            }
        }






//        launch {
//            val ticker = ticker(delayMillis = 10, initialDelayMillis = 0)
//            var lastThrust = viewModel.thrustValue.value
//            var currentThrust = viewModel.thrustValue.value
//
//            for (event in ticker) {
//                val targetThrust = viewModel.thrustValue.value
//                val eng2 = viewModel.eng2State.value
//
//                if (eng2) {
//                    if (currentThrust != targetThrust) {
//                        currentThrust += when {
//                            currentThrust < targetThrust -> 1
//                            currentThrust > targetThrust -> -1
//                            else -> 0
//                        }
//
//                        if (currentThrust != lastThrust) {
//                            viewModel.sendCommandToEsp32(ip, commandPort, "ENG2_SET:$currentThrust", isConnected)
//                            lastThrust = currentThrust
//                        }
//                    }
//                }
//
//            }
//        }


    }


    Box(modifier = Modifier.fillMaxSize()) {
        // Tło: obraz z kamery
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Podgląd ESP32",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = 180f
                    }
                    .clip(RoundedCornerShape(0.dp))
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
                .fillMaxSize()
        ) {
            // Ikona Wi-Fi w lewym górnym rogu
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .size(24.dp)
                    .align(Alignment.TopStart)
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = if (isConnected) "Connected" else "Disconnected",
                    tint = if (isConnected) Color.Green else Color.Red,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Rząd ikon w prawym górnym rogu
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                val isRecording by viewModel.isRecording.collectAsState()
                val bitmapProvider = { bitmap ?: Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888) }

                IconButton(onClick = {
                    if (isRecording) {
                        viewModel.stopRecording()
                    } else {
                        val flow = viewModel.bitmapToFlow { bitmapProvider() }
                        viewModel.startRecording(context, flow)
                    }
                }) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = if (isRecording) "Zatrzymaj nagrywanie" else "Rozpocznij nagrywanie",
                        tint = if (isRecording) Color.Red else Color.Black
                    )
                }

                // Przycisk robienia zdjęcia
                IconButton(onClick = {
                    scope.launch {
                        if (bitmap != null) {
                            viewModel.saveSnapshot(context, bitmap) { uri ->
                                viewModel.setSavedImageUri(uri)
                            }
                        }
                    }
                }) {
                    Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "Zrób zdjęcie")
                }

                // Przycisk ustawień
                IconButton(onClick = {
                    navController.navigate("settings")
                }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ustawienia",
                        tint = Color.Black
                    )
                }
            }
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
                onClick = { viewModel.eng1State.value = !eng1State },
                enabled = isConnected
            )
            CustomSwitch(
                actionName = "ENG2",
                isActive = eng2State,
                onClick = { viewModel.eng2State.value = !eng2State },
                enabled = isConnected
            )
            CustomSwitch(
                actionName = "Kamera",
                isActive = streaming,
                onClick = { viewModel.streaming.value = !streaming },
                enabled = isConnected
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
                minValue = 10,
                maxValue = 170,
                initialValue = 90,
                modifier = Modifier.width(100.dp),
                onValueChange = { viewModel.servoValue.value = it },
                enabled = isConnected
            )
        }

        if (!isConnected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000)) // Półprzezroczyste tło
                    .zIndex(1f), // Nakłada się na inne elementy
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Brak połączenia z Poduszkowcem",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
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
                maxLimit = if (viewModel.ograniczenie.value) 50 else 100,
                initialValue = 0,
                modifier = Modifier.width(100.dp),
                onValueChange = { viewModel.thrustValue.value = it },
                enabled = isConnected
            )
        }
    }

}


@Composable
fun SnapshotButton(lastFrame: Bitmap?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    val viewModel: ConnectionViewModel = viewModel(LocalContext.current as ComponentActivity)

    Column {
        Button(onClick = {
            scope.launch {
                if (lastFrame != null) {
                    viewModel.saveSnapshot(context, lastFrame) { uri ->
                        savedUri = uri
                    }
                }
            }
        }) {
            Text("Zrób zdjęcie")
        }

        if (savedUri != null) {
            Text("Zapisano: ${savedUri.toString()}", fontSize = 12.sp)
        }
    }
}


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

    var clickCount by remember { mutableStateOf(0) }
    var lastClickTime by remember { mutableStateOf(0L) }
    val isConnected by viewModel.isConnected

    LaunchedEffect(Unit) {
        viewModel.startPingPong(ip, commandPort)
    }
    LaunchedEffect(startSync) {
        if (startSync) {
            // Wywołaj synchronizację
            val state = viewModel.synchronizeState(ip, commandPort)
            if (state != null) {
                viewModel.eng1State.value = state["ENG1"] == "ON"
                viewModel.eng2State.value = state["ENG2"] == "ON"
                viewModel.thrustValue.value = state["ENG2_VAL"]?.toIntOrNull() ?: 0
                viewModel.servoValue.value = state["SERVO"]?.toIntOrNull() ?: 90
                viewModel.streaming.value = state["STREAM"] == "ON"

                viewModel.isConnected.value = true
            } else {
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
                .padding(top = 64.dp),
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

            // Checkbox z trybem nauki
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Checkbox(
                    checked = viewModel.ograniczenie.value,
                    onCheckedChange = {
                        viewModel.ograniczenie.value = it
                    }
                )

                Text(
                    text = "Ograniczenie",
                    color = Color.Black, // Żaden niebieski, żadna podpowiedź
                    modifier = Modifier.clickable {
                        val currentTime = System.currentTimeMillis()

                        if (currentTime - lastClickTime > 2000) {
                            clickCount = 0
                        }

                        clickCount++
                        lastClickTime = currentTime

                        if (clickCount >= 5) {
                            clickCount = 0
                            navController.navigate("sekretnyWidok")
                        }
                    }
                )
            }
            }
        }
    }

@Composable
fun SekretnyWidok(navController: NavController) {

    val twórcy = listOf(
        Autorzy(1, "dr inż. Tomasz Sobieraj", "Promotor", R.drawable.tomasz_sobieraj),
        Autorzy(2, "Tomasz Szulc", "Lider konstruktorów", R.drawable.tomasz_szulc),
        Autorzy(3, "Patryk Sołomachin", "Lider programistów", R.drawable.patryk_solomachin),
        Autorzy(4,"Damian Rosiak","Lider elektroników", R.drawable.damian_rosiak),
        Autorzy(5,"Ita Anioł","Elektroniczka", R.drawable.ita_aniol),
        Autorzy(6,"Michał Karpiak","Konstruktor", R.drawable.michal_karpiak)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Twórcy poduszkowca", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(24.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)  // odstępy poziome
        ) {
            items(
                items = twórcy,
                key = { it.id }
            ) { member ->
                TeamMemberItem(member)
            }
        }
    }
}

@Composable
fun TeamMemberItem(member: Autorzy) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(id = member.photoResId),
            contentDescription = "Zdjęcie członka zespołu",
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(member.name, style = MaterialTheme.typography.headlineSmall)
        Text(member.role, style = MaterialTheme.typography.bodyMedium)
    }
}

