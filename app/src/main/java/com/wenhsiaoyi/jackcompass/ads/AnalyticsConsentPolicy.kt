package com.wenhsiaoyi.jackcompass.ads

/**
 * Firebase Analytics 的同意判定（純函式、JVM 可測）。
 *
 * 只回答一個問題：現在可不可以把 Analytics 的儲存打開？
 * - `consentResolved == false`：UMP 還沒回報結果（例如第一次在 EEA 離線啟動，
 *   `IABTCF_gdprApplies` 還是預設 0，但那不代表非規範地區）→ 一律不放行。
 * - `gdprApplies == 0`：非規範地區，不需要同意 → 放行。
 * - `gdprApplies != 0`：規範地區，只有 `IABTCF_PurposeConsents` 的第一碼
 *   （Purpose 1：在裝置上儲存／存取資訊）為 `1` 才放行。
 *
 * Google 的 UMP 文件把 `analytics_storage` 綁在 Purpose 1；字串是零基索引，
 * 所以 Purpose 1 在 index 0。空字串（表單還沒跑完）一律視為未同意。
 */
object AnalyticsConsentPolicy {

    fun isAnalyticsStorageGranted(
        gdprApplies: Int,
        purposeConsents: String,
        consentResolved: Boolean,
    ): Boolean {
        if (!consentResolved) return false
        if (gdprApplies == 0) return true
        return purposeConsents.getOrNull(0) == '1'
    }
}
