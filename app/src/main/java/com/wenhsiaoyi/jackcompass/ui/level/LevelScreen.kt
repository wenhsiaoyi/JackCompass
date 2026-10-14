package com.wenhsiaoyi.jackcompass.ui.level

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 水平儀畫面。
 *
 * 預計接上 TYPE_ACCELEROMETER，並提供「水平儀」（只取重力在 XY 平面的分量）
 * 與「傾角儀」（取完整三軸角度）兩種模式。
 */
@Composable
fun LevelScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "水平儀\n尚未實作",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
