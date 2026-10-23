package com.wenhsiaoyi.jackcompass.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 冪等：同一個行程只初始化一次。
     *
     * `onReady` 一定在主執行緒呼叫（政策要把就緒寫進 Compose state），而且
     * **只在 SDK 真的初始化完成後**才呼叫。初始化是背景執行緒上的異步工作，
     * 呼叫出去不等於就緒——實測過沒等它就發橫幅請求，SDK 直接丟
     * `IllegalStateException: MobileAds.initialize must be called before using the Google Mobile Ads SDK`。
     */
    fun initializeOnce(context: Context, onReady: () -> Unit) {
        val appContext = context.applicationContext
        if (!started.compareAndSet(false, true)) {
            mainHandler.post(onReady)
            return
        }
        val appId = appContext.getString(R.string.admob_app_id)
        Thread({
            MobileAds.initialize(appContext, InitializationConfig.Builder(appId).build()) {
                mainHandler.post(onReady)
            }
        }, "AdMobInit").start()
    }
}
