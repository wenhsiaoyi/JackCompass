package com.wenhsiaoyi.jackcompass.ui.compass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 指南針畫面。
 *
 * 預計接上 TYPE_ROTATION_VECTOR（或 TYPE_MAGNETIC_FIELD + TYPE_ACCELEROMETER），
 * 低通濾波後換算成方位角，再以旋轉的羅盤盤面呈現。
 */
@Composable
fun CompassScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "指南針\n尚未實作",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
