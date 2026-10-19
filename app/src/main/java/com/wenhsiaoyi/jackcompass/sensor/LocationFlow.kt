package com.wenhsiaoyi.jackcompass.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * LocationManager 的 Flow 包裝層，與 `SensorFlows.kt` 同一個模式：
 * 進入畫面才向系統註冊、離開自動解除，不在畫面外浪費電。
 *
 * 定位是敏感權限，未核准時一律不呼叫 LocationManager 的更新與最後位置 API
 * （那些呼叫會丟 SecurityException）。權限由 UI 層負責請求，這裡只檢查。
 */

/** 一筆定位讀值。WGS84 十進位度，正負慣例見 `LocationMath.kt`。 */
data class LocationReading(val latitude: Double, val longitude: Double)

/** 定位更新的最小間隔與距離：這欄只是「我在哪裡」的參考，1 秒夠用。 */
private const val MIN_UPDATE_MILLISECONDS = 1_000L
private const val MIN_UPDATE_METERS = 0f

/** 已核准 FINE 或 COARSE 任一權限。minSdk 24 可直接用 Context.checkSelfPermission。 */
fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * 這台裝置有沒有定位能力。回傳 false 時 UI 整列不顯示經緯度。
 * 判定本身在 `hasLocationProvider`（純函式、JVM 可測），這裡只負責讀系統狀態。
 */
fun deviceHasLocation(context: Context): Boolean {
    val manager = context.getSystemService(LocationManager::class.java) ?: return false
    return hasLocationProvider(
        hasLocationFeature = context.packageManager
            .hasSystemFeature(PackageManager.FEATURE_LOCATION),
        providerNames = manager.allProviders,
    )
}

/**
 * 位置更新流。裝置沒有定位硬體時回傳 null（呼叫端據此不顯示）。
 *
 * 訂閱時先把各 provider 的「最後已知位置」推一筆，讓畫面不用等第一次 fix；
 * 之後每個 provider 的更新都會推。尚無權限或尚無定位時，Flow 不會發值，
 * UI 維持佔位顯示。
 *
 * lint 的 MissingPermission 是靜態檢查，看不到 callbackFlow 內部的攔截；
 * 真正的守門是下面 `hasLocationPermission` 那條 early return。
 */
@SuppressLint("MissingPermission")
fun locationReadingFlow(context: Context): Flow<LocationReading>? {
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    if (!deviceHasLocation(context)) return null

    return callbackFlow {
        if (!hasLocationPermission(context)) {
            awaitClose { }
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                trySend(LocationReading(location.latitude, location.longitude))
            }

            // API 24–29 上這三個還是抽象方法，不補齊會在框架回呼時 AbstractMethodError。
            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        manager.getProviders(true).forEach { provider ->
            manager.getLastKnownLocation(provider)?.let {
                send(LocationReading(it.latitude, it.longitude))
            }
            try {
                manager.requestLocationUpdates(
                    provider,
                    MIN_UPDATE_MILLISECONDS,
                    MIN_UPDATE_METERS,
                    listener,
                )
            } catch (_: SecurityException) {
                // 收集期間權限被撤銷：這個 provider 就跳過
            } catch (_: IllegalArgumentException) {
                // provider 在列舉之後、註冊之前被關閉
            }
        }
        awaitClose { manager.removeUpdates(listener) }
    }
}
