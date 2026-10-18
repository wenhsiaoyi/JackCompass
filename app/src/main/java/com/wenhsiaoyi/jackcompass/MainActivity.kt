package com.wenhsiaoyi.jackcompass

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.wenhsiaoyi.jackcompass.ui.JackCompassApp
import com.wenhsiaoyi.jackcompass.ui.theme.JackCompassTheme

/**
 * 必須是 AppCompatActivity：AppCompatDelegate 的語系切換只套用在現活的
 * AppCompatDelegate 上（見 appcompat 原始碼 getLocaleManagerForApplication()）。
 * 用 ComponentActivity 時 setApplicationLocales() 會靜默不做事。
 */
class MainActivity : AppCompatActivity() {
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
