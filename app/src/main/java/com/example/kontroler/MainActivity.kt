package com.example.kontroler

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kontroler.ui.theme.components.CustomSwitch
import com.example.kontroler.ui.theme.components.ThrottleSlider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val httpClient = ControlHttpClient() // Tworzymy instancję klienta HTTP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ControlApp(httpClient) // Przekazujemy klienta do ControlApp
        }
    }
}

@Composable
fun ControlApp(httpClient: ControlHttpClient) {
    var servoAngle by remember { mutableIntStateOf(90) }
    var isActiveENG1 by remember { mutableStateOf(false) }
    var isActiveENG2 by remember { mutableStateOf(false) }

    val lastState = remember { ControlState() } // Domyślny stan, poprzedni stan

    // Nowy stan, który jest aktualizowany
    val newState = ControlState(servoAngle, isActiveENG1, isActiveENG2)

    // Wywołanie funkcji, kiedy stan się zmienia
    onStateChanged(newState, lastState, httpClient)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Start, // Wyrównanie slidera do lewej
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Manetka po lewej stronie
        ThrottleSlider(
            modifier = Modifier
                .width(100.dp) // Stała szerokość dla slidera
                .padding(16.dp),
            minValue = 0f,
            maxValue = 180f,
            onValueChange = { value ->
                servoAngle = value.toInt()
                onStateChanged(newState, lastState, httpClient) // Wysyłanie zmienionych danych na serwer
            }
        )

        Spacer(modifier = Modifier.weight(1f)) // Spacer, który "wypycha" przycisk na środek

        // Przycisk 1
        CustomSwitch(
            engineName = "ENG1",
            isActive = isActiveENG1,
            onClick = {
                isActiveENG1 = !isActiveENG1  // Zmiana stanu po kliknięciu
                onStateChanged(newState, lastState, httpClient) // Wysyłanie zmienionych danych na serwer
            },
            modifier = Modifier.padding(16.dp)
        )

        // Przycisk 2
        CustomSwitch(
            engineName = "ENG2",
            isActive = isActiveENG2,
            onClick = {
                isActiveENG2 = !isActiveENG2  // Zmiana stanu po kliknięciu
                onStateChanged(newState, lastState, httpClient) // Wysyłanie zmienionych danych na serwer
            },
            modifier = Modifier.padding(16.dp)
        )
    }
}

// Funkcja, która zostanie wywołana, gdy stan się zmieni
fun onStateChanged(newState: ControlState, lastState: ControlState, httpClient: ControlHttpClient) {
    val ipAddress = "http://192.168.4.1"
    // Wywołanie funkcji do porównania stanów i wysłania zmian
    CoroutineScope(Dispatchers.Main).launch {
        httpClient.sendData(newState, lastState, ipAddress)
    }
}

