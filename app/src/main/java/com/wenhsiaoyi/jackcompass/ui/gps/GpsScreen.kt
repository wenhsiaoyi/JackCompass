package com.wenhsiaoyi.jackcompass.ui.gps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wenhsiaoyi.jackcompass.R

/**
 * GPS 畫面。
 *
 * 只顯示數值（緯度、經度、海拔、速度、定位精度），不含地圖。
 * 預計使用 FusedLocationProviderClient，並在進入畫面時索取定位權限。
 */
@Composable
fun GpsScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.gps_not_implemented),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
