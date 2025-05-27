package com.example.kontroler

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.kontroler.ui.theme.MainApp


class MainActivity : ComponentActivity() {
    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ustaw, żeby content rysował się pod systemowymi paskami (status bar, nav bar)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)

        // Ukryj paski systemowe
        windowInsetsController.hide(android.view.WindowInsets.Type.systemBars())

        // Ustaw zachowanie: paski pojawią się przy przesunięciu od krawędzi ekranu
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setContent {

            MainApp()

        }
    }
}



