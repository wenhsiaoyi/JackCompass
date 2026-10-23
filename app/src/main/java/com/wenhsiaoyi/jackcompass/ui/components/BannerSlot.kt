package com.wenhsiaoyi.jackcompass.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.wenhsiaoyi.jackcompass.ads.AdSession

/**
 * 橫幅槽：掛在導覽列正上方，三個分頁與設定頁都看得到。
 * 收起時整個 composable 不輸出任何東西，導覽列自己往下補位。
 *
 * 盤面直徑由寬度決定（CompassScreen 的 minOf(maxWidth, maxHeight)），
 * 所以收起橫幅不會讓盤面變大，只有中心下移——設計基準圖格 4 就是這樣畫的。
 *
 * 高度不寫死：`AdSize.BANNER` 是 320×50 dp，View 自己量得出來。
 *
 * 刻意不寫 AndroidView 的 onRelease 來銷毀：語系切換重建組合層時，
 * 那張橫幅還在倒數中、依然有效，onRelease 會把它當掉。
 * 銷毀只在 AdSession.tickBanner() 判定收起的那一刻做。
 */
@Composable
fun BannerSlot(session: AdSession, modifier: Modifier = Modifier) {
    val handle = if (session.bannerVisible) session.bannerHandle else null
    if (handle == null) return
    AndroidView(
        factory = { handle.view },
        modifier = modifier.fillMaxWidth(),
    )
}
