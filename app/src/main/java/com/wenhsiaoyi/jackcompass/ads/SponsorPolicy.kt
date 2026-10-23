package com.wenhsiaoyi.jackcompass.ads

/**
 * 贊助入口的規則層：純 JVM、無 Android 相依。
 *
 * 三態對應設計基準圖（spec/sponsor-flow-mock.svg 格 1）的三個膠囊：
 * 可點（Ready）、準備中（Preparing）、暫時沒有可播的廣告（Unavailable）。
 */
enum class SponsorStage {
    /** 使用者拒絕同意或還沒同意：整列不出現。 */
    Hidden,

    /** 可點。 */
    Ready,

    /** 按下後正在跟 SDK 要廣告；重複點擊一律擋掉。 */
    Preparing,

    /** 全螢幕廣告正在播。 */
    Playing,

    /** 這一次拿不到廣告（載入失敗或呈現失敗）；之後仍可再點。 */
    Unavailable,
}

class SponsorPolicy {

    var stage: SponsorStage = SponsorStage.Hidden
        private set

    val isRowVisible: Boolean get() = stage != SponsorStage.Hidden

    val isBusy: Boolean get() = stage == SponsorStage.Preparing || stage == SponsorStage.Playing

    fun onConsentGranted() {
        if (stage == SponsorStage.Hidden) stage = SponsorStage.Ready
    }

    fun onConsentDenied() {
        stage = SponsorStage.Hidden
    }

    /** 回傳 true 代表包裝層這一次**允許**向 SDK 請求並呈現插屏。 */
    fun onClick(): Boolean {
        if (stage != SponsorStage.Ready && stage != SponsorStage.Unavailable) return false
        stage = SponsorStage.Preparing
        return true
    }

    fun onAdShown() {
        if (stage == SponsorStage.Preparing) stage = SponsorStage.Playing
    }

    fun onAdDismissed() {
        if (stage == SponsorStage.Playing) stage = SponsorStage.Ready
    }

    fun onAdUnavailable() {
        if (stage == SponsorStage.Preparing) stage = SponsorStage.Unavailable
    }

    /** 素材與系統版本打架等狀況：走同一句「暫時沒有可播的廣告」，不播感謝動畫。 */
    fun onShowFailed() {
        if (stage == SponsorStage.Playing) stage = SponsorStage.Unavailable
    }
}
