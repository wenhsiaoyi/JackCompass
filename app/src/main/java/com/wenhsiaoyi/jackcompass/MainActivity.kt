package com.wenhsiaoyi.jackcompass

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.wenhsiaoyi.jackcompass.ads.AdSession
import com.wenhsiaoyi.jackcompass.ui.JackCompassApp
import com.wenhsiaoyi.jackcompass.ui.theme.JackCompassTheme

/**
 * 必須是 AppCompatActivity：AppCompatDelegate 的語系切換只套用在現活的
 * AppCompatDelegate 上（見 appcompat 原始碼 getLocaleManagerForApplication()）。
 * 用 ComponentActivity 時 setApplicationLocales() 會靜默不做事。
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 三頁內容都是固定深色盤面，狀態列圖示固定用淺色。
        // 預設的 auto 會跟隨系統深淺色：淺色模式下把圖示設成深色，壓在深色盤面上等於看不見。
        // 導覽列仍交給預設處理，因為底部 NavigationBar 自己會畫 Material 表面。
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val session = AdSession.ensure()
        session.runConsentChain(this)
        setContent {
            JackCompassTheme {
                JackCompassApp(session = session)
            }
        }
    }
}
