package com.wenhsiaoyi.jackcompass.sensor

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * 經緯度顯示的純換算，不含 Android 相依，可在 JVM 單元測試。
 *
 * 座標為 WGS84 十進位度：緯度正為北、負為南；經度正為東、負為西。
 * 數字與方位字拆成兩個函式，是因為方位字（N/E/S/W 或 北/東/南/西）
 * 要由 UI 層從語系檔取——這裡只決定小數怎麼印、以及該取哪一個字。
 */

/** 四位小數約等於 11 公尺，對「我在哪裡」夠用；固定位數可避免數值更新時寬度跳動。 */
private const val COORDINATE_PATTERN = "0.0000"

/**
 * 座標數值 → 依語系格式化後的字串，不含符號與方位字。
 * 負號拿掉：南北、東西由 `coordinateCardinalIndex` 挑出的方位字表達。
 */
fun formatCoordinateValue(value: Double, locale: Locale): String =
    DecimalFormat(COORDINATE_PATTERN, DecimalFormatSymbols(locale)).format(abs(value))

/**
 * 座標數值 → 方位字索引，對應 `R.array.dial_cardinals` 的 0=北、1=東、2=南、3=西。
 * 零度經緯線與負零都歸到北／東：`-0.0 < 0.0` 不成立，所以不會冒出「南 0.0000°」。
 */
fun coordinateCardinalIndex(value: Double, isLatitude: Boolean): Int = when {
    isLatitude -> if (value < 0.0) 2 else 0
    else -> if (value < 0.0) 3 else 1
}

/**
 * 這台裝置該不該顯示經緯度。
 *
 * 兩個輸入由呼叫端先讀好（`PackageManager.FEATURE_LOCATION` 與
 * `LocationManager.allProviders`），這裡只做判斷，才測得動。
 * 有定位硬體但 provider 清單是空的（例如定位服務被關到一個不剩）同樣不顯示。
 */
fun hasLocationProvider(hasLocationFeature: Boolean, providerNames: List<String>): Boolean =
    hasLocationFeature && providerNames.isNotEmpty()
