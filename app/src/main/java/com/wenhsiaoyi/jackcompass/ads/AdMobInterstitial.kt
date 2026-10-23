package com.wenhsiaoyi.jackcompass.ads

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback

/**
 * 插屏的 SDK 接觸面。全專案只有這裡 import 插屏型別。
 *
 * 全部簽名已用 javap 查證（見計畫〈已驗證 SDK 表面〉）：
 * `InterstitialAd.load(AdRequest, AdLoadCallback<InterstitialAd>)` 是 static，
 * **沒有** activity 參數、**沒有** `InterstitialAdRequest`、**沒有**
 * `InterstitialAdLoadCallback` 這種專屬回呼型別；事件掛點是 `setAdEventCallback`，
 * 呈現失敗的參數型別是 `FullScreenContentError`（不是 `LoadAdError`）。
 */
object AdMobInterstitial {

    /**
     * 載入並呈現。四個出口都要走到：
     * 拿不到素材 → onUnavailable；呈現失敗 → onShowFailed（不播感謝動畫）；
     * 出現 → onShown；使用者關掉 → onDismissed。
     */
    fun show(
        activity: Activity,
        unitId: String,
        onUnavailable: () -> Unit,
        onShown: () -> Unit,
        onDismissed: () -> Unit,
        onShowFailed: () -> Unit,
    ) {
        InterstitialAd.load(
            AdRequest.Builder(unitId).build(),
            object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(ad: InterstitialAd) {
                    activity.runOnUiThread {
                        ad.adEventCallback = object : InterstitialAdEventCallback {
                            override fun onAdShowedFullScreenContent() = onShown()

                            override fun onAdDismissedFullScreenContent() {
                                ad.destroy()
                                onDismissed()
                            }

                            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                                ad.destroy()
                                onShowFailed()
                            }
                        }
                        ad.show(activity)
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) = onUnavailable()
            },
        )
    }
}
