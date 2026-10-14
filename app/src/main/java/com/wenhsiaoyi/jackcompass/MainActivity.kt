package com.wenhsiaoyi.jackcompass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wenhsiaoyi.jackcompass.ui.JackCompassApp
import com.wenhsiaoyi.jackcompass.ui.theme.JackCompassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            JackCompassTheme {
                JackCompassApp()
            }
        }
    }
}
