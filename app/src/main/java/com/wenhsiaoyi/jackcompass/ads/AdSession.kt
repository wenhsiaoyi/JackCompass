package com.wenhsiaoyi.jackcompass.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
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
 * UMP 的回呼、以及 AdBootstrap 的初始化完成回呼都在主執行緒，
 * 所以下面的 state 寫入不需要額外同步。
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

    /**
     * 隱私選項表單失敗時要讓使用者看到的字串資源。
     * 做成 State 是因為表單回呼在 UI 之外，Snackbar 得靠重組才拿得到。
     */
    var privacyOptionsSnackbarRes by mutableStateOf<Int?>(null)
        private set

    /** Snackbar 顯示完由 UI 歸零，否則 Activity 重建會再跳一次同一句。 */
    fun clearPrivacyOptionsSnackbar() {
        privacyOptionsSnackbarRes = null
    }

    private var information: ConsentInformation? = null

    val nowMillis: Long get() = SystemClock.elapsedRealtime()

    /**
     * 目前顯示中的橫幅。同一顆 AdView 在顯示期間不重建：重新載入會多算一次
     * impression、也會閃。做成 State 是為了讓收起那一刻能重組。
     */
    var bannerHandle: BannerHandle? by mutableStateOf(null)
        private set

    val bannerVisible: Boolean get() = bannerStage == BannerStage.Showing

    private var bannerRequested = false

    /** 只在政策允許、且本場還沒請求過時發請求——保證一次執行只載入一次。 */
    fun requestBanner(activity: Activity) {
        if (bannerRequested || !banner.mayRequestAd) return
        bannerRequested = true
        AdMobBanner.load(
            activity = activity,
            unitId = activity.getString(R.string.admob_banner_unit_id),
            onLoaded = { handle ->
                banner.onAdLoaded(nowMillis)
                bannerHandle = handle
                sync()
            },
            onFailed = {
                banner.onLoadFailed()
                sync()
            },
        )
    }

    /** UI 每 250 毫秒呼叫一次；收起那一刻由政策層判定，這裡負責銷毀檢視圖。 */
    fun tickBanner() {
        val wasCollapsed = bannerStage == BannerStage.Collapsed
        banner.onTick(nowMillis)
        if (!wasCollapsed && banner.stage == BannerStage.Collapsed) {
            bannerHandle?.destroy()
            bannerHandle = null
        }
        sync()
    }

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
            val debugSettings = ConsentDebugSettings.Builder(context)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            // 除錯地區只認已註冊的裝置，漏了這筆伺服器就回 gdprApplies=0、表單永遠不出現。
            val hashedId = context.getString(R.string.ads_debug_device_hashed_id)
            if (hashedId.isNotBlank()) debugSettings.addTestDeviceHashedId(hashedId)
            builder.setConsentDebugSettings(debugSettings.build())
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
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) {
                // 非規範區會回 INVALID_OPERATION。這裡不靜默：realme 實測按下去沒任何反應，
                // 就是因為表單失敗只有 Google 的 logcat 看得见。
                Log.w(TAG, "privacy options form failed: code=${error.errorCode} ${error.message}")
                privacyOptionsSnackbarRes = privacyOptionsMessageRes(error.errorCode)
            }
            finish(activity.applicationContext, info.canRequestAds())
            onDismissed()
        }
    }

    /** 使用者按下贊助列。回傳 false 代表政策擋掉（準備中或播放中）。 */
    fun requestSponsor(activity: Activity): Boolean {
        if (!sponsor.onClick()) return false
        AdMobInterstitial.show(
            activity = activity,
            unitId = activity.getString(R.string.admob_interstitial_unit_id),
            onUnavailable = { sponsor.onAdUnavailable(); sync() },
            onShown = { sponsor.onAdShown(); sync() },
            onDismissed = {
                sponsor.onAdDismissed()
                sync()
                onSponsorDismissed?.invoke()
            },
            onShowFailed = { sponsor.onShowFailed(); sync() },
        )
        return true
    }

    var onSponsorDismissed: (() -> Unit)? = null

    fun showPrivacyOptions(activity: Activity) = showPrivacyOptionsForm(activity) {}

    private fun canRequestFallback(info: ConsentInformation): Boolean = runCatching {
        info.canRequestAds()
    }.getOrDefault(false)

    private fun sync() {
        bannerStage = banner.stage
        sponsorStage = sponsor.stage
    }

    private fun finish(context: Context, granted: Boolean) {
        if (!granted) {
            banner.onConsentDenied()
            sponsor.onConsentDenied()
            sync()
            return
        }
        // 兩個閘門都在初始化完成的回呼裡放行：initialize 是背景執行緒的異步工作，
        // 先開同意的話，250 毫秒的 tick 會比 SDK 就緒更早碰它（實測當過）。
        AdBootstrap.initializeOnce(context) {
            banner.onSdkReady()
            sponsor.onSdkReady()
            banner.onConsentGranted()
            sponsor.onConsentGranted()
            sync()
        }
    }

    companion object {
        private const val TAG = "AdSession"

        private var instance: AdSession? = null

        /** 行程層級：Activity 重建後回傳同一顆，政策與倒數不會重置。 */
        @Synchronized
        fun ensure(): AdSession = instance ?: AdSession().also { instance = it }
    }
}
