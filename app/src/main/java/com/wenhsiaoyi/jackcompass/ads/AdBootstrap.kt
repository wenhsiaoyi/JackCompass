package com.wenhsiaoyi.jackcompass.ads

import android.app.Activity
import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.wenhsiaoyi.jackcompass.R
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 廣告 SDK 的初始化。Next-Gen 的兩條硬性規定都在這裡：
 * App ID 由 `InitializationConfig` 傳入，而且必須在背景執行緒呼叫。
 * （UMP 是另一回事：它只從 manifest 的 `com.google.android.gms.ads.APPLICATION_ID` 讀 App ID。）
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
}
