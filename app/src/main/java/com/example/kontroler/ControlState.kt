package com.example.kontroler

// Klasa danych ControlState
data class ControlState(
    val servoAngle: Int = 90,
    val isActiveENG1: Boolean = false,
    val isActiveENG2: Boolean = false
)