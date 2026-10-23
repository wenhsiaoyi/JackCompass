package com.wenhsiaoyi.jackcompass.ads

import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * 把 UMP 的同意結果套到 Firebase Analytics（唯一碰 Firebase SDK 的一截）。
 *
 * 判定本身在 [AnalyticsConsentPolicy]（純函式、JVM 可測）；這裡只負責
 * 讀 UMP 寫進 SharedPreferences 的 `IABTCF_*`，再呼叫 SDK。
 *
 * 兩個地方一起設，缺一不可：
 * - `setConsent(ANALYTICS_STORAGE)`：Google 的同意模式，拒絕時不寫分析用的儲存。
 * - `setAnalyticsCollectionEnabled`：整個關掉收集。
 *
 * Manifest 另有 `firebase_analytics_collection_enabled=false` 當預設值，
 * 所以同意鏈跑完之前（包含第一次啟動）不會有任何收集。
 */
object AnalyticsConsent {

    fun apply(context: Context) {
        val prefs = context.getSharedPreferences(
            "${context.packageName}_preferences",
            Context.MODE_PRIVATE,
        )
        // IABTCF 規格是 Integer；少數 SDK 版本可能寫成字串，兩種都收。
        val gdprApplies = when (val raw = prefs.all[KEY_GDPR_APPLIES]) {
            is Int -> raw
            is String -> raw.toIntOrNull() ?: 0
            else -> 0
        }
        val purposeConsents = prefs.getString(KEY_PURPOSE_CONSENTS, "").orEmpty()
        val status = UserMessagingPlatform.getConsentInformation(context).consentStatus
        val resolved = status == ConsentInformation.ConsentStatus.NOT_REQUIRED ||
            status == ConsentInformation.ConsentStatus.OBTAINED
        val granted = AnalyticsConsentPolicy.isAnalyticsStorageGranted(
            gdprApplies = gdprApplies,
            purposeConsents = purposeConsents,
            consentResolved = resolved,
        )

        val analytics = FirebaseAnalytics.getInstance(context)
        analytics.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to
                    if (granted) {
                        FirebaseAnalytics.ConsentStatus.GRANTED
                    } else {
                        FirebaseAnalytics.ConsentStatus.DENIED
                    },
            ),
        )
        analytics.setAnalyticsCollectionEnabled(granted)
    }

    private const val KEY_GDPR_APPLIES = "IABTCF_gdprApplies"
    private const val KEY_PURPOSE_CONSENTS = "IABTCF_PurposeConsents"
}
