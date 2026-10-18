package com.wenhsiaoyi.jackcompass.sensor

import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 指南針方位角換算的接縫測試。
 *
 * 座標系採 Android 感測器座標系：+X 向右、+Y 朝裝置頂端、+Z 出螢幕正面；
 * 感測器讀值約為「世界向上方向在裝置座標系的投影 × g」。
 * rotation vector 為 [x·sin(θ/2), y·sin(θ/2), z·sin(θ/2), cos(θ/2)]，
 * 代表裝置座標系對世界座標系（X=東、Y=北、Z=天頂）的旋轉。
 */
class RotationVectorAzimuthTest {

    @Test
    fun `頂端朝北、平放時方位角為零度`() {
        val azimuth = rotationVectorToAzimuthDegrees(floatArrayOf(0f, 0f, 0f, 1f))
        assertEquals(0f, azimuth, 0.1f)
    }

    @Test
    fun `頂端向東旋轉90度時方位角為90度`() {
        // 頂端從北轉向東 = 順時針90度 = 繞世界 Z 軸 -90 度
        val s = sin(Math.toRadians(-45.0)).toFloat()
        val c = kotlin.math.cos(Math.toRadians(-45.0)).toFloat()
        val azimuth = rotationVectorToAzimuthDegrees(floatArrayOf(0f, 0f, s, c))
        assertEquals(90f, azimuth, 0.1f)
    }

    @Test
    fun `頂端逆時針轉90度朝西時方位角為270度`() {
        val s = sin(Math.toRadians(45.0)).toFloat()
        val c = kotlin.math.cos(Math.toRadians(45.0)).toFloat()
        val azimuth = rotationVectorToAzimuthDegrees(floatArrayOf(0f, 0f, s, c))
        assertEquals(270f, azimuth, 0.1f)
    }

    @Test
    fun `螢幕朝下翻轉時方位角不變`() {
        // 繞裝置 Y 軸（頂端軸）旋轉180度：頂端仍朝北，只是螢幕朝下
        val azimuth = rotationVectorToAzimuthDegrees(floatArrayOf(0f, 1f, 0f, 0f))
        assertEquals(0f, azimuth, 0.1f)
    }

    @Test
    fun `只有三元素的 rotation vector 也能換算`() {
        // 部分裝置只回傳軸角向量（w 由實作端補算）
        val s = sin(Math.toRadians(-45.0)).toFloat()
        val azimuth = rotationVectorToAzimuthDegrees(floatArrayOf(0f, 0f, s))
        assertEquals(90f, azimuth, 0.1f)
    }

    @Test
    fun `五元素的 rotation vector 忽略多出來的航向欄位`() {
        // 部分裝置（實測模擬器）在 w 之後還附帶 heading accuracy／rate 欄位
        val s = sin(Math.toRadians(-45.0)).toFloat()
        val c = kotlin.math.cos(Math.toRadians(-45.0)).toFloat()
        val azimuth = rotationVectorToAzimuthDegrees(floatArrayOf(0f, 0f, s, c, 0.05f))
        assertEquals(90f, azimuth, 0.1f)
    }

    @Test
    fun `方位角一律歸一化到0到360度`() {
        // 頂端逆時針轉45度朝西北偏北，負角應歸一化成315
        val s = sin(Math.toRadians(22.5)).toFloat()
        val c = kotlin.math.cos(Math.toRadians(22.5)).toFloat()
        val azimuth = rotationVectorToAzimuthDegrees(floatArrayOf(0f, 0f, s, c))
        assertEquals(315f, azimuth, 0.1f)
    }
}

/** 水平儀傾角計算的接縫測試。 */
class AccelerometerTiltTest {

    @Test
    fun `完全水平時 roll 為零度`() {
        assertEquals(0f, flatRollDegrees(0f, 0f, 9.81f), 0.01f)
    }

    @Test
    fun `右緣抬高30度時 roll 為正30度`() {
        val g = 9.81f
        val roll = flatRollDegrees(g * sin(Math.toRadians(30.0)).toFloat(), 0f, g * kotlin.math.cos(Math.toRadians(30.0)).toFloat())
        assertEquals(30f, roll, 0.1f)
    }

    @Test
    fun `左緣抬高45度時 roll 為負45度`() {
        val g = 9.81f
        val roll = flatRollDegrees(-g * sin(Math.toRadians(45.0)).toFloat(), 0f, g * kotlin.math.cos(Math.toRadians(45.0)).toFloat())
        assertEquals(-45f, roll, 0.1f)
    }

    @Test
    fun `roll 計算不受前後傾干擾`() {
        val g = 9.81f
        // 右緣抬30度且頂端同時抬高20度
        val roll = flatRollDegrees(4.905f, 3.353f, 7.872f)
        assertEquals(30f, roll, 1f)
    }

    @Test
    fun `螢幕直立面向使用者時垂直傾角為零度`() {
        assertEquals(0f, verticalTiltDegrees(0f, 9.81f, 0f), 0.01f)
    }

    @Test
    fun `頂端往遠離使用者方向傾30度時為正30度`() {
        val g = 9.81f
        val tilt = verticalTiltDegrees(0f, g * kotlin.math.cos(Math.toRadians(30.0)).toFloat(), g * sin(Math.toRadians(30.0)).toFloat())
        assertEquals(30f, tilt, 0.1f)
    }

    @Test
    fun `頂端往使用者方向傾20度時為負20度`() {
        val g = 9.81f
        val tilt = verticalTiltDegrees(0f, g * kotlin.math.cos(Math.toRadians(20.0)).toFloat(), -g * sin(Math.toRadians(20.0)).toFloat())
        assertEquals(-20f, tilt, 0.1f)
    }
}

/** 方位名換算的接縫測試：回傳 0–7 索引，由 UI 層依語系取字串。 */
class CardinalDirectionTest {

    @Test
    fun `正北在零度與359度都是索引0`() {
        assertEquals(0, cardinalDirectionIndex(0f))
        assertEquals(0, cardinalDirectionIndex(359f))
    }

    @Test
    fun `每45度一個方位 邊界在22度5`() {
        assertEquals(0, cardinalDirectionIndex(22.4f))
        assertEquals(1, cardinalDirectionIndex(22.6f))
        assertEquals(2, cardinalDirectionIndex(68f))
        assertEquals(3, cardinalDirectionIndex(135f))
        assertEquals(4, cardinalDirectionIndex(180f))
        assertEquals(5, cardinalDirectionIndex(232f))
        assertEquals(6, cardinalDirectionIndex(270f))
        assertEquals(7, cardinalDirectionIndex(310f))
    }
}

/** 方位角顯示換算的接縫測試。 */
class DisplayedHeadingTest {

    @Test
    fun `四捨五入後恰為360度時顯示0度`() {
        assertEquals(0, displayedHeadingDegrees(359.6f))
    }

    @Test
    fun `一般角度四捨五入到最接近的整數度`() {
        assertEquals(232, displayedHeadingDegrees(231.7f))
        assertEquals(0, displayedHeadingDegrees(0f))
        assertEquals(180, displayedHeadingDegrees(179.5f))
    }
}

/** 轉盤刻度震動的接縫測試：顯示度數每變一次，震動一格。 */
class HeadingTickTest {

    @Test
    fun `尚無前次讀值時不震動`() {
        assertFalse(headingTickChanged(previousAzimuth = null, azimuth = 123f))
    }

    @Test
    fun `顯示度數未變時不震動`() {
        assertFalse(headingTickChanged(100.2f, 100.4f))
    }

    @Test
    fun `顯示度數跨一度時震動`() {
        assertTrue(headingTickChanged(100.2f, 101.3f))
    }

    @Test
    fun `顯示度數經360回繞到0度時震動`() {
        // 359.5 起顯示即歸 0 度（360 不顯示），回繞邊界兩側顯示度數不同
        assertTrue(headingTickChanged(359.4f, 359.6f))
    }

    @Test
    fun `跨過零度但顯示度數未變時不震動`() {
        // 359.7 與 0.2 都顯示 0°：指針沒有跨格
        assertFalse(headingTickChanged(359.7f, 0.2f))
    }
}

/** 低通濾波的接縫測試。 */
class LowPassFilterTest {

    @Test
    fun `線性濾波器首筆直接通過 之後依 alpha 平滑`() {
        val filter = LowPassFilter(alpha = 0.5f)
        assertEquals(10f, filter.next(10f), 0.001f)
        assertEquals(5f, filter.next(0f), 0.001f)
        assertEquals(2.5f, filter.next(0f), 0.001f)
    }

    @Test
    fun `重置後下一筆又直接通過`() {
        val filter = LowPassFilter(alpha = 0.5f)
        filter.next(10f)
        filter.reset()
        assertEquals(3f, filter.next(3f), 0.001f)
    }

    @Test
    fun `圓形濾波器跨零點走短路徑`() {
        val filter = CircularLowPassFilter(alpha = 0.5f)
        assertEquals(350f, filter.next(350f), 0.001f)
        // 350 → 10 的短路徑是 +20 度，中點應為 0（而非 180）
        assertEquals(0f, filter.next(10f), 0.001f)
    }

    @Test
    fun `圓形濾波器跨零點反方向也走短路徑`() {
        val filter = CircularLowPassFilter(alpha = 0.5f)
        filter.next(0f)
        // 0 → 350 的短路徑是 -10 度，中點應為 355
        assertEquals(355f, filter.next(350f), 0.001f)
    }

    @Test
    fun `圓形濾波器輸出恆在0到360度`() {
        val filter = CircularLowPassFilter(alpha = 0.5f)
        filter.next(350f)
        filter.next(10f)
        // 目前停在0度，往5度插值一半應為2.5度，且不產生負值
        val result = filter.next(5f)
        assertEquals(2.5f, result, 0.001f)
    }
}
