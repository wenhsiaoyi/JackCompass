package com.wenhsiaoyi.jackcompass.ui.components

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wenhsiaoyi.jackcompass.R
import com.wenhsiaoyi.jackcompass.ui.theme.CompassOrange
import com.wenhsiaoyi.jackcompass.ui.theme.DialFace
import com.wenhsiaoyi.jackcompass.ui.theme.DialRing
import com.wenhsiaoyi.jackcompass.ui.theme.DialScreen
import com.wenhsiaoyi.jackcompass.ui.theme.DialText
import com.wenhsiaoyi.jackcompass.ui.theme.DialTickMajor
import kotlinx.coroutines.delay

/*
 * 時序對應設計基準圖格 2：0.0s 淡入 → 0.5s 縮放到 1.0 → 1.6s 後淡出。
 * 這段動畫不解鎖任何功能、不寫入任何「已贊助」狀態。
 */
private const val FADE_IN_MS = 300
private const val SCALE_MS = 500
private const val HOLD_MS = 1_600L
private const val FADE_OUT_MS = 300

/**
 * 「降低動態效果」的偵測。
 *
 * 計畫原本假定的 `AccessibilityManager.isReducedMotionSettingEnabled()` **不存在**：
 * 已用 javap 查證 android-33／36／37.0／37.2 的 `android.jar`，`AccessibilityManager`
 * 沒有任何 reduce／motion 相關方法。系統那個開關（設定 → 無障礙 → 視覺效果 →
 * 移除動畫）對 App 可見的訊號是 `Settings.Global` 的動畫縮放係數歸零，
 * 所以這裡讀它；兩個係數在 API 17 起就是公開常數，minSdk 24 全覆蓋。
 */
@Composable
private fun isReduceMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
            Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
}

@Composable
fun SponsorThanksOverlay(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val reduceMotion = isReduceMotionEnabled()
    var entered by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else FADE_IN_MS),
        label = "thanksAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (entered && !reduceMotion) 1f else 0.8f,
        animationSpec = tween(if (reduceMotion) 0 else SCALE_MS),
        label = "thanksScale",
    )

    LaunchedEffect(Unit) {
        entered = true
        delay(HOLD_MS)
        entered = false
        delay(if (reduceMotion) 0L else FADE_OUT_MS.toLong())
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DialScreen.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // 外圈橘色光暈：降低動態效果時不出現
                if (!reduceMotion) {
                    Canvas(modifier = Modifier.size(232.dp)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(CompassOrange.copy(alpha = 0.35f), Color.Transparent),
                                radius = size.minDimension / 2f,
                            ),
                            radius = size.minDimension / 2f,
                        )
                    }
                }
                Canvas(modifier = Modifier.size(208.dp).scale(scale)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f
                    drawCircle(DialFace, radius = r * 0.98f, center = center)
                    drawCircle(DialRing, radius = r * 0.90f, center = center, style = Stroke(width = r * 0.03f))
                    drawRose(center, r * 0.80f)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.sponsor_thanks_title),
                style = MaterialTheme.typography.headlineSmall,
                color = DialTickMajor,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.sponsor_thanks_body),
                style = MaterialTheme.typography.bodyMedium,
                color = DialText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 羅盤玫瑰：與指南針盤面同一個造型，N 指針橘色、其餘壓暗。 */
private fun DrawScope.drawRose(center: Offset, reach: Float) {
    val half = reach * 0.16f
    fun diamond(tip: Offset, side: Offset) = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(center.x + side.x * half, center.y + side.y * half)
        lineTo(center.x - side.x * half, center.y - side.y * half)
        close()
    }
    drawPath(diamond(Offset(center.x, center.y - reach), Offset(1f, 1f)), CompassOrange)
    drawPath(diamond(Offset(center.x, center.y - reach), Offset(-1f, -1f)), CompassOrange.copy(alpha = 0.72f))
    drawPath(diamond(Offset(center.x, center.y + reach), Offset(1f, 1f)), DialRing)
    drawPath(diamond(Offset(center.x, center.y + reach), Offset(-1f, -1f)), DialRing.copy(alpha = 0.7f))
    drawPath(diamond(Offset(center.x + reach, center.y), Offset(1f, -1f)), Color(0xFF2A313C))
    drawPath(diamond(Offset(center.x + reach, center.y), Offset(-1f, 1f)), Color(0xFF2A313C).copy(alpha = 0.75f))
    drawPath(diamond(Offset(center.x - reach, center.y), Offset(1f, -1f)), Color(0xFF2A313C))
    drawPath(diamond(Offset(center.x - reach, center.y), Offset(-1f, 1f)), Color(0xFF2A313C).copy(alpha = 0.75f))
    drawCircle(DialTickMajor, radius = reach * 0.10f, center = center)
    drawCircle(DialScreen, radius = reach * 0.04f, center = center)
}
