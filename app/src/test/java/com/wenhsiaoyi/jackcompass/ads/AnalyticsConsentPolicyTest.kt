package com.wenhsiaoyi.jackcompass.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Firebase Analytics 同意閘門的接縫測試。
 *
 * 輸入只有兩個：UMP 寫進 SharedPreferences 的 `IABTCF_gdprApplies` 與
 * `IABTCF_PurposeConsents`。政策層不碰 SharedPreferences、不碰 Firebase，
 * 所以「哪個字串算同意」可以在 JVM 上精確驗。
 *
 * 規則來源：Google UMP 文件把 analytics_storage 綁在 Purpose 1（裝置上的儲存／存取）；
 * gdprApplies=0（非規範地區）不需要同意，gdprApplies=1 且 Purpose 1 為 1 才放行。
 */
class AnalyticsConsentPolicyTest {

    @Test
    fun `非規範地區不需要同意就放行`() {
        assertTrue(
            AnalyticsConsentPolicy.isAnalyticsStorageGranted(
                gdprApplies = 0,
                purposeConsents = "",
                consentResolved = true,
            ),
        )
    }

    @Test
    fun `規範地區且同意 Purpose 1 才放行`() {
        assertTrue(
            AnalyticsConsentPolicy.isAnalyticsStorageGranted(
                gdprApplies = 1,
                purposeConsents = "11100000000",
                consentResolved = true,
            ),
        )
    }

    @Test
    fun `規範地區拒絕 Purpose 1 就不放行`() {
        assertFalse(
            AnalyticsConsentPolicy.isAnalyticsStorageGranted(
                gdprApplies = 1,
                purposeConsents = "01111111111",
                consentResolved = true,
            ),
        )
    }

    @Test
    fun `規範地區還沒有同意字串時不放行`() {
        assertFalse(
            AnalyticsConsentPolicy.isAnalyticsStorageGranted(
                gdprApplies = 1,
                purposeConsents = "",
                consentResolved = true,
            ),
        )
    }

    @Test
    fun `只有其他目的同意、沒有 Purpose 1 時不放行`() {
        assertFalse(
            AnalyticsConsentPolicy.isAnalyticsStorageGranted(
                gdprApplies = 1,
                purposeConsents = "0111",
                consentResolved = true,
            ),
        )
    }

    @Test
    fun `gdprApplies 是任何非零值都視為規範地區`() {
        assertFalse(
            AnalyticsConsentPolicy.isAnalyticsStorageGranted(
                gdprApplies = 2,
                purposeConsents = "0",
                consentResolved = true,
            ),
        )
    }

    @Test
    fun `同意狀態還沒解析出來時一律不放行`() {
        // 第一次在 EEA 離線啟動：gdprApplies 還是預設 0，但這不代表非規範地區。
        assertFalse(
            AnalyticsConsentPolicy.isAnalyticsStorageGranted(
                gdprApplies = 0,
                purposeConsents = "",
                consentResolved = false,
            ),
        )
        assertFalse(
            AnalyticsConsentPolicy.isAnalyticsStorageGranted(
                gdprApplies = 1,
                purposeConsents = "11100000000",
                consentResolved = false,
            ),
        )
    }
}
