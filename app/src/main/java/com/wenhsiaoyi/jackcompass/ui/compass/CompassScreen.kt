package com.wenhsiaoyi.jackcompass.ui.compass

import android.graphics.Paint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenhsiaoyi.jackcompass.R
import com.wenhsiaoyi.jackcompass.sensor.cardinalDirectionIndex
import com.wenhsiaoyi.jackcompass.sensor.compassBearingFlow
import com.wenhsiaoyi.jackcompass.sensor.displayedHeadingDegrees
import com.wenhsiaoyi.jackcompass.sensor.headingTickChanged
import com.wenhsiaoyi.jackcompass.ui.theme.CompassOrange
import com.wenhsiaoyi.jackcompass.ui.theme.DialFace
import com.wenhsiaoyi.jackcompass.ui.theme.DialRing
import com.wenhsiaoyi.jackcompass.ui.theme.DialScreen
import com.wenhsiaoyi.jackcompass.ui.theme.DialText
import com.wenhsiaoyi.jackcompass.ui.theme.DialTick
import com.wenhsiaoyi.jackcompass.ui.theme.DialTickMajor
import com.wenhsiaoyi.jackcompass.ui.theme.LevelGreenBright
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 指南針畫面：大字方位角＋中文方位名，下方為隨方位角反向旋轉的羅盤盤面，
 * 橘色指針固定朝上、指向正北時轉綠。資料來自 TYPE_ROTATION_VECTOR（磁北，未做磁偏角修正）。
 */
@Composable
fun CompassScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bearingFlow = remember(context) { compassBearingFlow(context) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DialScreen),
    ) {
        if (bearingFlow == null) {
            Text(
                text = stringResource(R.string.compass_no_sensor),
                color = DialText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }

        val azimuth by bearingFlow.collectAsStateWithLifecycle(initialValue = null)

        // 顯示度數每跨一度，震動一格，模擬轉盤刻度手感
        val vibrator = remember(context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                context.getSystemService(Vibrator::class.java)
            else
                @Suppress("DEPRECATION")
                context.getSystemService(android.os.Vibrator::class.java)
        }
        var lastAzimuth by remember { mutableFloatStateOf(Float.NaN) }
        LaunchedEffect(azimuth) {
            val current = azimuth ?: return@LaunchedEffect
            val previous = if (lastAzimuth.isNaN()) null else lastAzimuth
            if (headingTickChanged(previous, current)) {
                vibrator?.vibrate(VibrationEffect.createOneShot(8L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            lastAzimuth = current
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Heading(azimuthDegrees = azimuth)
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CompassDial(azimuthDegrees = azimuth ?: 0f, hasReading = azimuth != null)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Heading(azimuthDegrees: Float?) {
    val directions = stringArrayResource(R.array.cardinal_directions)
    Text(
        text = azimuthDegrees?.let { "${displayedHeadingDegrees(it)}°" } ?: "--°",
        style = MaterialTheme.typography.headlineLarge,
        color = DialTickMajor,
    )
    Text(
        text = azimuthDegrees?.let { directions[cardinalDirectionIndex(it)] } ?: " ",
        style = MaterialTheme.typography.titleMedium,
        color = DialText,
    )
}

@Composable
private fun CompassDial(azimuthDegrees: Float, hasReading: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val paints = remember(density) { DialPaints(density) }
    val cardinals = stringArrayResource(R.array.dial_cardinals)

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Canvas(modifier = Modifier.size(side)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f

            fun point(angleDegrees: Float, atRadius: Float): Offset {
                val rad = Math.toRadians(angleDegrees.toDouble())
                return Offset(
                    center.x + atRadius * sin(rad).toFloat(),
                    center.y - atRadius * cos(rad).toFloat(),
                )
            }

            // 盤面外圈光暈：整顆表像浮在暗背景上
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(DialTick.copy(alpha = 0.28f), Color.Transparent),
                    center = center,
                    radius = radius * 1.06f,
                ),
                radius = radius * 1.06f,
                center = center,
            )
            // 盤面：中心微亮、邊緣變暗的球面感
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1C232B), DialFace, Color(0xFF0C1015)),
                    center = Offset(center.x - radius * 0.18f, center.y - radius * 0.22f),
                    radius = radius * 1.15f,
                ),
                radius = radius,
            )
            // 金屬表圈：四段明暗交錯，像斜射光下的不鏽鋼環
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF6B7683), Color(0xFF262E37), Color(0xFF5A6470),
                        Color(0xFF1E252C), Color(0xFF6B7683),
                    ),
                    center = center,
                ),
                radius = radius * 0.975f,
                center = center,
                style = Stroke(width = radius * 0.05f),
            )
            // 表圈內緣壓線，做出凹陷深度
            drawCircle(
                color = Color.Black.copy(alpha = 0.5f),
                radius = radius * 0.945f,
                center = center,
                style = Stroke(width = radius * 0.012f),
            )

            // 刻度環：隨方位角反向旋轉
            rotate(degrees = -azimuthDegrees, pivot = center) {
                // 細密經緯線網格：像刻在玻璃下的球面坐標紙，隨盤面一起轉
                val grid = DialTick.copy(alpha = 0.09f)
                val gridWidth = radius * 0.93f
                clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(center.x - gridWidth, center.y - gridWidth, center.x + gridWidth, center.y + gridWidth)) }) {
                    // 緯線：略彎的水平線，每 0.15r 一條
                    var k = -6
                    while (k <= 6) {
                        val y = center.y + k * radius * 0.15f
                        val half = sqrt((gridWidth * gridWidth - (k * radius * 0.15f) * (k * radius * 0.15f)).coerceAtLeast(0f))
                        drawLine(grid, Offset(center.x - half, y), Offset(center.x + half, y), strokeWidth = radius * 0.004f)
                        k++
                    }
                    // 經線：向兩側鼓出的弧線，模擬球面投影
                    var m = -6
                    while (m <= 6) {
                        if (m != 0) {
                            val dx = m * radius * 0.155f
                            val bulge = dx * 1.28f
                            drawPath(
                                Path().apply {
                                    moveTo(center.x + dx, center.y - gridWidth)
                                    quadraticTo(center.x + bulge, center.y, center.x + dx, center.y + gridWidth)
                                },
                                grid,
                                style = Stroke(width = radius * 0.004f),
                            )
                        }
                        m++
                    }
                }
                for (deg in 0 until 360 step 2) {
                    val major = deg % 30 == 0
                    val medium = !major && deg % 10 == 0
                    drawLine(
                        color = when {
                            major -> DialTickMajor
                            medium -> DialTickMajor.copy(alpha = 0.7f)
                            else -> DialTick.copy(alpha = 0.55f)
                        },
                        start = point(
                            deg.toFloat(),
                            when {
                                major -> radius * 0.855f
                                medium -> radius * 0.885f
                                else -> radius * 0.905f
                            },
                        ),
                        end = point(deg.toFloat(), radius * 0.945f),
                        strokeWidth = when {
                            major -> radius * 0.012f
                            medium -> radius * 0.009f
                            else -> radius * 0.006f
                        },
                        cap = StrokeCap.Round,
                    )
                }
                // 每 30 度標數字，正交的四個位置讓給字母
                val canvas = drawContext.canvas.nativeCanvas

                fun drawCentered(text: String, at: Offset, paint: Paint) {
                    canvas.drawText(text, at.x, at.y - (paint.ascent() + paint.descent()) / 2f, paint)
                }

                for (deg in 30 until 360 step 30) {
                    if (deg % 90 == 0) continue
                    drawCentered(deg.toString(), point(deg.toFloat(), radius * 0.775f), paints.tickNumber)
                }
                drawCentered(cardinals[1], point(90f, radius * 0.76f), paints.cardinal)
                drawCentered(cardinals[2], point(180f, radius * 0.76f), paints.cardinal)
                drawCentered(cardinals[3], point(270f, radius * 0.76f), paints.cardinal)
                drawCentered(cardinals[0], point(0f, radius * 0.76f), paints.north)
            }

            // 指針：固定朝上，指向正北時轉綠。左右兩面深淺不同，做出稜線立體感。
            val pointingNorth = hasReading && (azimuthDegrees >= 357f || azimuthDegrees <= 3f)
            val (needleLight, needleDark) = when {
                !hasReading -> DialTick.copy(alpha = 0.5f) to DialTick.copy(alpha = 0.32f)
                pointingNorth -> LevelGreenBright to Color(0xFF1B8A44)
                else -> CompassOrange to Color(0xFF9E5200)
            }
            val halfWidth = radius * 0.065f
            val tipY = center.y - radius * 0.60f
            // 指針發光：針尖下一團柔光，像玻璃下打了燈
            if (hasReading) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(needleLight.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(center.x, center.y - radius * 0.30f),
                        radius = radius * 0.52f,
                    ),
                    radius = radius * 0.52f,
                    center = Offset(center.x, center.y - radius * 0.30f),
                )
            }
            // 左面（受光）：針尖亮、針身飽和、近軸處壓暗的金屬漸層
            drawPath(
                Path().apply {
                    moveTo(center.x, tipY)
                    lineTo(center.x - halfWidth, center.y)
                    lineTo(center.x, center.y)
                    close()
                },
                Brush.linearGradient(
                    colors = listOf(needleLight, needleLight, needleDark),
                    start = Offset(center.x, tipY),
                    end = Offset(center.x, center.y),
                ),
            )
            // 右面（背光）：整體再壓暗，中段留一絲反光
            drawPath(
                Path().apply {
                    moveTo(center.x, tipY)
                    lineTo(center.x + halfWidth, center.y)
                    lineTo(center.x, center.y)
                    close()
                },
                Brush.linearGradient(
                    colors = listOf(needleDark, needleLight.copy(alpha = 0.55f), needleDark),
                    start = Offset(center.x + halfWidth * 0.5f, tipY),
                    end = Offset(center.x, center.y),
                ),
            )
            // 尾針（指向南）同樣分左右面
            val tailY = center.y + radius * 0.60f
            drawPath(
                Path().apply {
                    moveTo(center.x, tailY)
                    lineTo(center.x - halfWidth, center.y)
                    lineTo(center.x, center.y)
                    close()
                },
                Brush.linearGradient(
                    colors = listOf(DialTick.copy(alpha = 0.75f), DialTick.copy(alpha = 0.55f), DialRing),
                    start = Offset(center.x, tailY),
                    end = Offset(center.x, center.y),
                ),
            )
            drawPath(
                Path().apply {
                    moveTo(center.x, tailY)
                    lineTo(center.x + halfWidth, center.y)
                    lineTo(center.x, center.y)
                    close()
                },
                DialRing,
            )
            // 中央軸承：金屬圓帽＋中心點
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFF2F5F8), DialTickMajor, Color(0xFF5C6670)),
                    center = Offset(center.x - radius * 0.015f, center.y - radius * 0.015f),
                    radius = radius * 0.05f,
                ),
                radius = radius * 0.045f,
                center = center,
            )
            drawCircle(DialFace, radius * 0.02f, center)

            // 玻璃高光：左上角一道斜射反光，蓋在整個盤面上
            drawArc(
                color = Color.White.copy(alpha = 0.11f),
                startAngle = 198f,
                sweepAngle = 76f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.88f, center.y - radius * 0.88f),
                size = androidx.compose.ui.geometry.Size(radius * 1.76f, radius * 1.76f),
                style = Stroke(width = radius * 0.22f, cap = StrokeCap.Round),
            )
            // 高光內側再補一道窄而亮的反光帶
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = 205f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.70f, center.y - radius * 0.70f),
                size = androidx.compose.ui.geometry.Size(radius * 1.4f, radius * 1.4f),
                style = Stroke(width = radius * 0.07f, cap = StrokeCap.Round),
            )
        }
    }
}

/** 盤面文字用舊式 Paint（經 nativeCanvas 繪製），依 density 快取。 */
private class DialPaints(density: Density) {

    val tickNumber: Paint = Paint().apply {
        color = DialText.toArgb()
        textSize = with(density) { 11.sp.toPx() }
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    val cardinal: Paint = Paint().apply {
        color = DialTickMajor.toArgb()
        textSize = with(density) { 17.sp.toPx() }
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    val north: Paint = Paint().apply {
        color = CompassOrange.toArgb()
        textSize = with(density) { 19.sp.toPx() }
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }
}
