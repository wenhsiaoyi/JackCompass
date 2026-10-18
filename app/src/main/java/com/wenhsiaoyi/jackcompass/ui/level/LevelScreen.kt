package com.wenhsiaoyi.jackcompass.ui.level

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenhsiaoyi.jackcompass.R
import com.wenhsiaoyi.jackcompass.sensor.LEVEL_FULL_SCALE_DEGREES
import com.wenhsiaoyi.jackcompass.sensor.LevelMode
import com.wenhsiaoyi.jackcompass.sensor.levelTiltFlow
import com.wenhsiaoyi.jackcompass.ui.theme.DialFace
import com.wenhsiaoyi.jackcompass.ui.theme.DialRing
import com.wenhsiaoyi.jackcompass.ui.theme.DialScreen
import com.wenhsiaoyi.jackcompass.ui.theme.DialText
import com.wenhsiaoyi.jackcompass.ui.theme.DialTick
import com.wenhsiaoyi.jackcompass.ui.theme.DialTickMajor
import com.wenhsiaoyi.jackcompass.ui.theme.LevelGreen
import com.wenhsiaoyi.jackcompass.ui.theme.LevelGreenBright
import com.wenhsiaoyi.jackcompass.ui.theme.LevelTube
import java.util.Locale
import kotlin.math.abs

/** 傾角在此範圍內視為「已水平」，氣泡鎖定亮綠（度）。 */
private const val LEVEL_LOCK_THRESHOLD_DEGREES = 0.5f

/**
 * 水平儀畫面：VERTICAL／HORIZONTAL 切換，綠色氣泡依傾角左右移動，
 * 接近水平時鎖定提示。資料來自 TYPE_ACCELEROMETER。
 */
@Composable
fun LevelScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(LevelMode.Horizontal) }
    val tiltFlow = remember(context, mode) { levelTiltFlow(context, mode) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DialScreen),
    ) {
        if (tiltFlow == null) {
            Text(
                text = stringResource(R.string.level_no_sensor),
                color = DialText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        val tilt by tiltFlow.collectAsStateWithLifecycle(initialValue = null)
        val locked = tilt != null && abs(tilt!!) <= LEVEL_LOCK_THRESHOLD_DEGREES

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            ModeSelector(mode = mode, onModeChange = { mode = it })
            Spacer(Modifier.height(56.dp))
            Text(
                text = stringResource(
                    if (mode == LevelMode.Horizontal) R.string.level_mode_horizontal
                    else R.string.level_mode_vertical,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = DialText,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(12.dp))
            val verticalMode = mode == LevelMode.Vertical
            BubbleLevel(
                tiltDegrees = tilt,
                locked = locked,
                vertical = verticalMode,
                modifier = if (verticalMode) {
                    Modifier.size(width = 160.dp, height = 400.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                },
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = tilt?.let { String.format(Locale.US, "%.1f°", it) } ?: "--°",
                style = MaterialTheme.typography.headlineMedium,
                color = if (locked) LevelGreenBright else DialTickMajor,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (locked) stringResource(R.string.level_locked) else " ",
                style = MaterialTheme.typography.bodyMedium,
                color = LevelGreenBright,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(mode: LevelMode, onModeChange: (LevelMode) -> Unit) {
    val options = LevelMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onModeChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = LevelTube,
                    activeContentColor = LevelGreenBright,
                    inactiveContainerColor = DialFace,
                    inactiveContentColor = DialText,
                ),
                label = {
                    Text(
                        text = stringResource(
                            if (option == LevelMode.Horizontal) R.string.level_mode_horizontal
                            else R.string.level_mode_vertical,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                },
            )
        }
    }
}

/**
 * 綠色氣泡水平儀：玻璃管質感（上缘高光、內層陰影、底部反彈光）＋圓形氣泡，
 * 氣泡位置 = 傾角／全量程。
 *
 * vertical=true 時整個組件以同套橫向繪製邏輯旋轉 -90 度呈現，
 * 正傾角（頂端往遠離使用者方向傾）在直立模式下氣泡往上。
 */
@Composable
private fun BubbleLevel(tiltDegrees: Float?, locked: Boolean, vertical: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        // 管長沿畫布長邊：橫向取寬、直立取高
        val longSide = if (vertical) size.height else size.width
        val shortSide = if (vertical) size.width else size.height

        fun drawTube() {
            // 直立模式管短一些，留空間給上下端弧，不與標籤重疊
            val tubeWidth = longSide * (if (vertical) 0.60f else 0.74f)
            val tubeHeight = shortSide * 0.34f
            val tubeLeft = cx - tubeWidth / 2f
            val tubeTop = cy - tubeHeight / 2f
            val tubeSize = Size(tubeWidth, tubeHeight)
            val corner = CornerRadius(tubeHeight / 2f)

            // 玻璃管本體：上缘亮、中段深、底部一抹反彈光
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF5C6D4E),
                        Color(0xFF2B3423),
                        Color(0xFF161D11),
                        Color(0xFF324129),
                    ),
                    start = Offset(0f, tubeTop),
                    end = Offset(0f, tubeTop + tubeHeight),
                ),
                topLeft = Offset(tubeLeft, tubeTop),
                size = tubeSize,
                cornerRadius = corner,
            )
            // 貼著管壁的內層陰影
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.35f),
                topLeft = Offset(tubeLeft, tubeTop),
                size = tubeSize,
                cornerRadius = corner,
                style = Stroke(width = tubeHeight * 0.07f),
            )

            // 氣泡：圓形，徑向漸層＋左上反光點，外圍柔光
            val fraction = ((tiltDegrees ?: 0f) / LEVEL_FULL_SCALE_DEGREES).coerceIn(-1f, 1f)
            val bubbleR = tubeHeight * 0.34f
            val travel = tubeWidth / 2f - bubbleR - tubeHeight * 0.32f
            val bx = cx + fraction * travel
            val base = when {
                tiltDegrees == null -> LevelGreen.copy(alpha = 0.35f)
                locked -> LevelGreenBright
                else -> LevelGreen
            }
            drawCircle(color = base.copy(alpha = 0.18f), radius = bubbleR * 1.5f, center = Offset(bx, cy))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE8FFE9),
                        base,
                        base.copy(alpha = 0.9f),
                        Color(0xFF1E4620),
                    ),
                    center = Offset(bx - bubbleR * 0.25f, cy - bubbleR * 0.25f),
                    radius = bubbleR * 1.35f,
                ),
                radius = bubbleR,
                center = Offset(bx, cy),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = bubbleR * 0.22f,
                center = Offset(bx - bubbleR * 0.38f, cy - bubbleR * 0.42f),
            )

            // 中央水平判定刻線：刻在玻璃上，畫在氣泡之上
            val tickColor = DialTickMajor.copy(alpha = 0.8f)
            drawLine(tickColor, Offset(cx - tubeHeight * 0.16f, tubeTop + 4f), Offset(cx - tubeHeight * 0.16f, tubeTop + tubeHeight - 4f), strokeWidth = 2.5f)
            drawLine(tickColor, Offset(cx + tubeHeight * 0.16f, tubeTop + 4f), Offset(cx + tubeHeight * 0.16f, tubeTop + tubeHeight - 4f), strokeWidth = 2.5f)

            // 玻璃上缘高光（覆蓋在一切之上）
            drawLine(
                color = Color.White.copy(alpha = 0.13f),
                start = Offset(tubeLeft + tubeWidth * 0.10f, tubeTop + tubeHeight * 0.16f),
                end = Offset(tubeLeft + tubeWidth * 0.90f, tubeTop + tubeHeight * 0.16f),
                strokeWidth = tubeHeight * 0.08f,
                cap = StrokeCap.Round,
            )

            // 外框
            drawRoundRect(
                color = DialRing,
                topLeft = Offset(tubeLeft, tubeTop),
                size = tubeSize,
                cornerRadius = corner,
                style = Stroke(width = 2f),
            )

            // 刻度尺
            val rulerY = tubeTop + tubeHeight + 10f
            for (i in -5..5) {
                val x = cx + i * (tubeWidth / 20f)
                val long = i % 5 == 0
                drawLine(
                    color = if (long) DialTick else DialTick.copy(alpha = 0.4f),
                    start = Offset(x, rulerY),
                    end = Offset(x, rulerY + if (long) 12f else 7f),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
            }

            // 端帽弧線（原型圖樣式）
            val arcColor = if (locked) LevelGreenBright.copy(alpha = 0.8f) else LevelGreen.copy(alpha = 0.55f)
            val arcRadius = tubeHeight * 1.15f
            drawArc(
                color = arcColor,
                startAngle = 120f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(tubeLeft - arcRadius * 1.5f, cy - arcRadius),
                size = Size(arcRadius * 2f, arcRadius * 2f),
                style = Stroke(width = 7f, cap = StrokeCap.Round),
            )
            drawArc(
                color = arcColor,
                startAngle = -60f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(tubeLeft + tubeWidth - arcRadius * 0.5f, cy - arcRadius),
                size = Size(arcRadius * 2f, arcRadius * 2f),
                style = Stroke(width = 7f, cap = StrokeCap.Round),
            )
        }

        if (vertical) {
            // 旋轉 -90 度：繪製座標的 +x（正傾角方向）對應螢幕上方
            rotate(degrees = -90f, pivot = Offset(cx, cy)) { drawTube() }
        } else {
            drawTube()
        }
    }
}
