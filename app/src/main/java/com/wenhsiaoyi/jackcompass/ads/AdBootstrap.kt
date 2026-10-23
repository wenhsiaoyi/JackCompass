package com.wenhsiaoyi.jackcompass.ads

import android.app.Activity
import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.wenhsiaoyi.jackcompass.R
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 廣告 SDK 的初始化。Next-Gen 的兩條硬性規定都在這裡：
 * App ID 由程式傳入（不寫進 manifest），而且必須在背景執行緒呼叫。
 */
object AdBootstrap {

    private val started = AtomicBoolean(false)

    /** 冪等：同一個行程只初始化一次。 */
    fun initializeOnce(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val appId = appContext.getString(R.string.admob_app_id)
        Thread({
            MobileAds.initialize(appContext, InitializationConfig.Builder(appId).build()) { }
        }, "AdMobInit").start()
    }

    /** UMP 除錯地區只認清單裡的裝置；未列出的裝置會被當成非規範地區。 */
    fun deviceHash(activity: Activity): String {
        val provider = activity.contentResolver
        val id = android.provider.Settings.Secure.getString(
            provider,
            android.provider.Settings.Secure.ANDROID_ID,
        )
        return android.util.Base64.encodeToString(
            java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray()),
            android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE,
        ).take(64)
    }
}
