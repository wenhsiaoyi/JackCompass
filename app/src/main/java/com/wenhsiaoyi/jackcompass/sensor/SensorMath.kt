package com.wenhsiaoyi.jackcompass.sensor

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 感測器數值的純數學換算，不含任何 Android 相依，可在 JVM 單元測試。
 *
 * 座標系為 Android 感測器座標系（+X 向右、+Y 朝裝置頂端、+Z 出螢幕正面）；
 * 世界座標系 X=東、Y=北、Z=天頂。方位角以磁北為 0 度、順時針遞增，
 * 未做磁偏角修正（顯示的是磁北方位）。
 */

private const val RAD_TO_DEG = 180.0 / Math.PI

/**
 * rotation vector（軸角向量 [x, y, z, cos(θ/2)]，θ=0 時 w=1）→ 裝置頂端指向的方位角。
 *
 * 支援 3–5 元素的回傳：只有前三元素時 w 由 sqrt(1-x²-y²-z²) 補算；
 * 第四元素之後的 heading accuracy／rate（實測模擬器會給 5 個元素）一律忽略。
 * 換算方式同 `SensorManager.getRotationMatrixFromVector` + `getOrientation` 的
 * azimuth = atan2(R01, R11)，但不依賴 Android framework。
 */
fun rotationVectorToAzimuthDegrees(values: FloatArray): Float {
    require(values.size in 3..5) { "rotation vector 需要 3 到 5 個元素，得到 ${values.size}" }
    val x = values[0]
    val y = values[1]
    val z = values[2]
    val w = if (values.size >= 4) {
        values[3]
    } else {
        sqrt((1f - x * x - y * y - z * z).coerceAtLeast(0f))
    }

    // 裝置→世界旋轉矩阵（列主序）中 azimuth 所需的兩個元素
    val r01 = 2f * (x * y - w * z)
    val r11 = 1f - 2f * (x * x + z * z)

    return normalizeDegrees((atan2(r01, r11) * RAD_TO_DEG).toFloat())
}

/** 水平模式：右緣抬高為正、左緣抬高為負，單位為度。不受前後傾影響。 */
fun flatRollDegrees(x: Float, y: Float, z: Float): Float =
    (atan2(x, hypot(y, z)) * RAD_TO_DEG).toFloat()

/** 直立模式：完全垂直為 0，頂端往遠離使用者方向傾為正、往使用者方向傾為負。 */
fun verticalTiltDegrees(x: Float, y: Float, z: Float): Float =
    (atan2(z, y) * RAD_TO_DEG).toFloat()

/**
 * 方位角 → 八方位索引（0=北、1=東北、2=東…順時針），
 * 邊界在每 45 度的中點（即 22.5 度起跳）。字串由 UI 層依語系取。
 */
fun cardinalDirectionIndex(azimuthDegrees: Float): Int =
    normalizeDegrees(azimuthDegrees).let { (it / 45f).roundToInt() % 8 }

/** 大字顯示用的整數方位角：四捨五入後 360 歸 0，範圍恆為 0–359。 */
fun displayedHeadingDegrees(azimuthDegrees: Float): Int =
    Math.floorMod(normalizeDegrees(azimuthDegrees).roundToInt(), 360)

/**
 * 轉盤刻度震動判斷：前次與目前的「顯示度數」不同即回傳 true，
 * 每跨一度震動一格；經 0/360 回繞同樣會觸發。
 */
fun headingTickChanged(previousAzimuth: Float?, azimuth: Float): Boolean {
    if (previousAzimuth == null) return false
    return displayedHeadingDegrees(previousAzimuth) != displayedHeadingDegrees(azimuth)
}

private fun normalizeDegrees(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

/** 指數移動平均濾波器，消除感測器抖動。alpha 越小越平滑、延遲越高。 */
class LowPassFilter(private val alpha: Float) {

    private var previous: Float? = null

    fun next(value: Float): Float {
        val prev = previous
        val result = if (prev == null) value else prev + alpha * (value - prev)
        previous = result
        return result
    }

    fun reset() {
        previous = null
    }
}

/** 角度（0–360 度）用的低通濾波器，跨 0/360 邊界時走短路徑，不會從 180 度繞道。 */
class CircularLowPassFilter(private val alpha: Float) {

    private var previous: Float? = null

    fun next(value: Float): Float {
        val prev = previous
        if (prev == null) {
            previous = normalizeDegrees(value)
            return previous!!
        }
        val shortestDelta = ((value - prev + 540f) % 360f) - 180f
        val result = normalizeDegrees(prev + alpha * shortestDelta)
        previous = result
        return result
    }

    fun reset() {
        previous = null
    }
}
