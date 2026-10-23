package com.wenhsiaoyi.jackcompass.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.wenhsiaoyi.jackcompass.R

/**
 * 一次行程（從啟動到結束）的廣告會話。
 *
 * 刻意做成行程層級單例：切換語系會重建 Activity，若政策跟著重建，
 * 橫幅就會在倒數未滿時重新進入 Loading、多算一次 impression。
 * 放在行程層，30 秒與「只播一次」才撐得過重建。
 *
 * UMP 的回呼都在主執行緒，所以下面的 state 寫入不需要額外同步。
 */
class AdSession(
    val banner: BannerPolicy = BannerPolicy(),
    val sponsor: SponsorPolicy = SponsorPolicy(),
) {
    /** 政策層 stage 的 Compose 鏡像；每個政策事件後由 sync() 更新，UI 只讀這兩個。 */
    var bannerStage by mutableStateOf(BannerStage.AwaitingConsent)
        private set

    var sponsorStage by mutableStateOf(SponsorStage.Hidden)
        private set

    private var information: ConsentInformation? = null

    val nowMillis: Long get() = SystemClock.elapsedRealtime()

    /**
     * 同意鏈，順序不可調（spec「同意鏈」）：
     * 請求更新 → 需要時顯示表單 → 才初始化 SDK（背景執行緒）→ 檢查可否請求。
     */
    fun runConsentChain(activity: Activity) {
        val context = activity.applicationContext
        val info = UserMessagingPlatform.getConsentInformation(context)
        information = info

        val builder = ConsentRequestParameters.Builder()
        if (context.resources.getBoolean(R.bool.ads_force_eea_debug)) {
            builder.setConsentDebugSettings(
                ConsentDebugSettings.Builder(context)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .addTestDeviceHashedId(AdBootstrap.deviceHash(activity))
                    .build(),
            )
        }

        info.requestConsentInfoUpdate(
            activity,
            builder.build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    finish(context, info.canRequestAds())
                }
            },
            { _ -> finish(context, canRequestFallback(info)) },
        )
    }

    /** 規範區使用者事後改同意的唯一入口；Google 政策要求一定要有。 */
    fun showPrivacyOptionsForm(activity: Activity, onDismissed: () -> Unit) {
        val info = information ?: return
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ ->
            finish(activity.applicationContext, info.canRequestAds())
            onDismissed()
        }
    }

    private fun canRequestFallback(info: ConsentInformation): Boolean = runCatching {
        info.canRequestAds()
    }.getOrDefault(false)

    private fun sync() {
        bannerStage = banner.stage
        sponsorStage = sponsor.stage
    }

    private fun finish(context: Context, granted: Boolean) {
        if (granted) {
            AdBootstrap.initializeOnce(context)
            banner.onConsentGranted()
            sponsor.onConsentGranted()
        } else {
            banner.onConsentDenied()
            sponsor.onConsentDenied()
        }
        sync()
    }

    companion object {
        private var instance: AdSession? = null

        /** 行程層級：Activity 重建後回傳同一顆，政策與倒數不會重置。 */
        @Synchronized
        fun ensure(): AdSession = instance ?: AdSession().also { instance = it }
    }
}

/**
 * 本專案的 Compose 沒有 LocalActivity（見 Global Constraints），只能沿 Context 鏈往上找。
 * AppCompat 會包一層 ContextWrapper，所以必須走 baseContext，不能直接 as Activity。
 */
fun Context.asActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
