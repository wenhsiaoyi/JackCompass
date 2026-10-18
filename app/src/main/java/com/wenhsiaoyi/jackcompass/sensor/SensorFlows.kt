package com.wenhsiaoyi.jackcompass.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * SensorManager 的 Flow 包裝層。指南針、水平儀與之後的 GPS 功能共用。
 *
 * 每個 Flow 在被收集（畫面進入組合）時才向系統註冊 listener，
 * 收集結束（畫面離開）自動 unregister；濾波器在 Flow 內部建立，
 * 每個收集者各別平滑，不會跨畫面互相污染狀態。
 */

/** 低通濾波係數：越小越平滑、延遲越高。SENSOR_DELAY_GAME 約 50Hz，0.12 ≈ 時間常數 170ms。 */
private const val BEARING_ALPHA = 0.12f
private const val TILT_ALPHA = 0.2f

/** 水平儀全量程：氣泡跑到管端代表的傾角（度）。 */
const val LEVEL_FULL_SCALE_DEGREES = 8f

/** 水平儀的兩種模式。Horizontal＝手機平放看左右；Vertical＝手機直立看前後。 */
enum class LevelMode {
    Horizontal,
    Vertical,
}

private fun sensorValuesFlow(context: Context, sensorType: Int): Flow<FloatArray>? {
    val manager = context.getSystemService(SensorManager::class.java) ?: return null
    val sensor = manager.getDefaultSensor(sensorType) ?: return null
    return callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == sensorType) {
                    // values 是系統端重用陣列，一定要複製
                    trySend(event.values.clone())
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { manager.unregisterListener(listener) }
    }
}

/**
 * 裝置頂端指向的方位角（0–360 度，磁北），已低通濾波。
 * 裝置沒有 TYPE_ROTATION_VECTOR 時回傳 null。
 */
fun compassBearingFlow(context: Context): Flow<Float>? =
    sensorValuesFlow(context, Sensor.TYPE_ROTATION_VECTOR)?.let { values ->
        flow {
            val filter = CircularLowPassFilter(BEARING_ALPHA)
            emitAll(values.map { filter.next(rotationVectorToAzimuthDegrees(it)) })
        }
    }

/**
 * 水平儀傾角（度），已低通濾波。符號慣例見 `flatRollDegrees`／`verticalTiltDegrees`。
 * 裝置沒有加速計時回傳 null。
 */
fun levelTiltFlow(context: Context, mode: LevelMode): Flow<Float>? =
    sensorValuesFlow(context, Sensor.TYPE_ACCELEROMETER)?.let { values ->
        flow {
            val filter = LowPassFilter(TILT_ALPHA)
            emitAll(
                values.map { (x, y, z) ->
                    filter.next(
                        when (mode) {
                            LevelMode.Horizontal -> flatRollDegrees(x, y, z)
                            LevelMode.Vertical -> verticalTiltDegrees(x, y, z)
                        },
                    )
                },
            )
        }
    }
