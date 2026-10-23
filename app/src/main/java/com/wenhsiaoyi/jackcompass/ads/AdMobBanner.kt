package com.wenhsiaoyi.jackcompass.ads

import android.app.Activity
import android.view.View
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

/**
 * 一張橫幅檢視圖和它的銷毀方式。只有本檔知道實質型別，
 * 這樣 AdSession 與 UI 都不用 import 廣告 SDK。
 */
class BannerHandle(val view: View, private val release: () -> Unit) {
    fun destroy() = release()
}

/**
 * 橫幅的 SDK 接觸面。全專案只有這裡 import 橫幅型別。
 *
 * 走 `BannerAd.load(...)` 再取 `ad.getView(activity)`，**不用 `AdView` 容器**：
 * `AdView` 假設 imperative 的 XML 容器與 `registerBannerAd`，交給 Compose
 * `AndroidView` 的 factory 會在每次重組時搶生命週期。這裡把 View 交給
 * `BannerHandle` 持有，銷毀只呼 `ad.destroy()`（`common.Ad` 繼承來的）。
 */
object AdMobBanner {

    fun load(
        activity: Activity,
        unitId: String,
        onLoaded: (BannerHandle) -> Unit,
        onFailed: () -> Unit,
    ) {
        activity.runOnUiThread {
            val request = BannerAdRequest.Builder(unitId, AdSize.BANNER).build()
            BannerAd.load(
                request,
                object : AdLoadCallback<BannerAd> {
                    override fun onAdLoaded(ad: BannerAd) {
                        onLoaded(BannerHandle(ad.getView(activity)) { ad.destroy() })
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        onFailed()
                    }
                },
            )
        }
    }
}
