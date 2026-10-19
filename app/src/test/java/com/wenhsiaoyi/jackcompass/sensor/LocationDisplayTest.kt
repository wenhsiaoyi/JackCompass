package com.wenhsiaoyi.jackcompass.sensor

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 經緯度顯示的接縫測試。
 *
 * 座標採 WGS84 十進位度：緯度正為北、負為南；經度正為東、負為西。
 * 數字與方位字的搭配是刻意拆成兩個純函式的——方位字（N/E/S/W 或 北/東/南/西）
 * 由 UI 層從語系檔取，這裡只決定「取哪個」與「數字怎麼印」，
 * 所以兩個函式都不碰 Android。
 */
class CoordinateFormatTest {

    @Test
    fun `固定印四位小數，避免數值更新時寬度跳動`() {
        assertEquals("24.8781", formatCoordinateValue(24.8781, Locale.ENGLISH))
    }

    @Test
    fun `小數位四捨五入`() {
        assertEquals("121.0544", formatCoordinateValue(121.05440001, Locale.ENGLISH))
        assertEquals("24.8782", formatCoordinateValue(24.87816, Locale.ENGLISH))
    }

    @Test
    fun `整數也補滿四位小數`() {
        assertEquals("24.0000", formatCoordinateValue(24.0, Locale.ENGLISH))
    }

    @Test
    fun `符號不印在數字上，方位由字母表達`() {
        assertEquals("33.8688", formatCoordinateValue(-33.8688, Locale.ENGLISH))
    }

    @Test
    fun `負零不會印出負號`() {
        assertEquals("0.0000", formatCoordinateValue(-0.0, Locale.ENGLISH))
    }

    @Test
    fun `十進位符號跟隨語系`() {
        // 繁中／簡中／英文都用句點；這裡用德語驗證機制真的走語系而不是寫死
        assertEquals("33.8688", formatCoordinateValue(-33.8688, Locale.forLanguageTag("zh-TW")))
        assertEquals("33,8688", formatCoordinateValue(-33.8688, Locale.forLanguageTag("de-DE")))
    }
}

/** 經緯度數值 → 方位字索引（對應 `dial_cardinals` 的 0=北、1=東、2=南、3=西）。 */
class CoordinateCardinalTest {

    @Test
    fun `北半球緯度取北，南半球取南`() {
        assertEquals(0, coordinateCardinalIndex(24.8781, isLatitude = true))
        assertEquals(2, coordinateCardinalIndex(-33.8688, isLatitude = true))
    }

    @Test
    fun `東經取東，西經取西`() {
        assertEquals(1, coordinateCardinalIndex(121.0544, isLatitude = false))
        assertEquals(3, coordinateCardinalIndex(-73.9877, isLatitude = false))
    }

    @Test
    fun `零度經緯線算北與東`() {
        assertEquals(0, coordinateCardinalIndex(0.0, isLatitude = true))
        assertEquals(1, coordinateCardinalIndex(0.0, isLatitude = false))
    }

    @Test
    fun `負零也不算南與西`() {
        assertEquals(0, coordinateCardinalIndex(-0.0, isLatitude = true))
        assertEquals(1, coordinateCardinalIndex(-0.0, isLatitude = false))
    }
}

/** 「這台裝置該不該顯示經緯度」的判定。輸入是兩個 already-read 的狀態，不碰 Android API。 */
class LocationAvailabilityTest {

    @Test
    fun `有定位硬體且至少有一個 provider 可用時才顯示`() {
        assertTrue(hasLocationProvider(hasLocationFeature = true, providerNames = listOf("gps")))
        assertTrue(
            hasLocationProvider(
                hasLocationFeature = true,
                providerNames = listOf("gps", "network", "passive"),
            )
        )
    }

    @Test
    fun `沒有定位硬體時隱藏，即使 provider 清單不為空`() {
        assertFalse(hasLocationProvider(hasLocationFeature = false, providerNames = listOf("gps")))
    }

    @Test
    fun `有定位硬體但沒有任何 provider 時隱藏`() {
        assertFalse(hasLocationProvider(hasLocationFeature = true, providerNames = emptyList()))
    }
}
