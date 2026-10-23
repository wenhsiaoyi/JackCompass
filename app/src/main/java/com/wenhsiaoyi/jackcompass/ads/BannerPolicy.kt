package com.wenhsiaoyi.jackcompass.ads

/**
 * 橫幅廣告的規則層：純 JVM、無 Android 相依，時間由外部注入。
 *
 * 兩套廣告 SDK 都沒有內建的「顯示 N 秒後停止」開關，所以計時一定由 App 自己擁有。
 * 本類別只決定狀態怎麼變，不碰任何 SDK 型別。
 */

/** 橫幅顯示滿這個毫秒數就收起，本次執行不再出現。 */
const val BANNER_VISIBLE_DURATION_MS = 30_000L

enum class BannerStage {
    /** 還沒問完同意，不允許請求廣告。 */
    AwaitingConsent,

    /** 已通過同意閘門，正在等第一筆（也是本場唯一一筆）廣告。 */
    Loading,

    /** 廣告已顯示，倒數中。 */
    Showing,

    /** 倒數結束，本次執行收幕。 */
    Collapsed,

    /** 使用者拒絕同意，或載入失敗：本次執行不出現。 */
    Suppressed,
}

class BannerPolicy(private val durationMillis: Long = BANNER_VISIBLE_DURATION_MS) {

    var stage: BannerStage = BannerStage.AwaitingConsent
        private set

    private var shownAtMillis: Long? = null

    val isVisible: Boolean get() = stage == BannerStage.Showing

    /** 包裝層只在此值為 true 時向 SDK 要廣告，因此一次執行只會要一次。 */
    val mayRequestAd: Boolean get() = stage == BannerStage.Loading

    fun onConsentGranted() {
        if (stage == BannerStage.AwaitingConsent) stage = BannerStage.Loading
    }

    fun onConsentDenied() {
        if (stage == BannerStage.AwaitingConsent) stage = BannerStage.Suppressed
    }

    fun onAdLoaded(nowMillis: Long) {
        if (stage != BannerStage.Loading) return
        stage = BannerStage.Showing
        shownAtMillis = nowMillis
    }

    fun onLoadFailed() {
        if (stage == BannerStage.Loading) stage = BannerStage.Suppressed
    }

    fun onTick(nowMillis: Long) {
        if (stage != BannerStage.Showing) return
        val started = shownAtMillis ?: return
        if (nowMillis - started >= durationMillis) {
            stage = BannerStage.Collapsed
            shownAtMillis = null
        }
    }
}
