package com.example.kontroler

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.*

class ControlHttpClient {
    private val client = OkHttpClient()

    // Funkcja porównująca stany i wysyłająca dane tylko w przypadku zmian
    suspend fun sendData(newState: ControlState, lastState: ControlState, ipAddress: String) {
        val jsonData = JSONObject()

        // Porównaj tylko zmienione parametry
        newState.apply {
            if (servoAngle != lastState.servoAngle) {
                jsonData.put("servoAngle", servoAngle)
            }
            if (isActiveENG1 != lastState.isActiveENG1) {
                jsonData.put("isActive", isActiveENG1)
            }
            if (isActiveENG2 != lastState.isActiveENG2) {
                jsonData.put("isSecondActive", isActiveENG2)
            }
        }

        // Jeśli są zmiany, wyślij dane
        if (jsonData.length() > 0) {
            delay(1000)
            sendHttpRequest(jsonData, ipAddress)
        }
    }

    // Funkcja wysyłająca zapytanie HTTP POST z danymi JSON
    private fun sendHttpRequest(jsonData: JSONObject, ipAddress: String) {
        val requestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            jsonData.toString()
        )

        val request = Request.Builder()
            .url(ipAddress)
            .post(requestBody)
            .build()

        Log.d("HTTP_REQUEST", "Wysyłanie zapytania: $ipAddress")

        // Asynchroniczne wykonanie zapytania
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("HTTP_REQUEST", "Błąd: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("HTTP_REQUEST", "Odpowiedź: ${response.body?.string()}")
            }
        })
    }
}