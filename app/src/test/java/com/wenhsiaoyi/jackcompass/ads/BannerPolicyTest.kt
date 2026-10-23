package com.wenhsiaoyi.jackcompass.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 橫幅生命週期的接縫測試。
 *
 * 時間一律由外部注入（nowMillis 參數），政策層不碰 SystemClock，
 * 所以 30 秒邊界可以精確打到 29_999 與 30_000 兩側。
 */
class BannerPolicyTest {

    @Test
    fun `開始時等同意，不得請求廣告`() {
        val policy = BannerPolicy()
        assertEquals(BannerStage.AwaitingConsent, policy.stage)
        assertFalse("同意前不得產生請求", policy.mayRequestAd)
        assertFalse(policy.isVisible)
    }

    @Test
    fun `同意後進入載入中，且只允許請求一次`() {
        val policy = BannerPolicy()
        policy.onConsentGranted()
        assertEquals(BannerStage.Loading, policy.stage)
        assertTrue(policy.mayRequestAd)
        policy.onAdLoaded(nowMillis = 1_000L)
        assertFalse("載入中結束後不再是 Loading，包裝層不會再發第二次請求", policy.mayRequestAd)
    }

    @Test
    fun `拒絕同意後整場壓抑，之後的載入事件無效`() {
        val policy = BannerPolicy()
        policy.onConsentDenied()
        assertEquals(BannerStage.Suppressed, policy.stage)
        policy.onAdLoaded(nowMillis = 1_000L)
        assertFalse(policy.isVisible)
        assertEquals(BannerStage.Suppressed, policy.stage)
    }

    @Test
    fun `計時起點是廣告真的顯示出來那一刻`() {
        val policy = BannerPolicy()
        policy.onConsentGranted()
        policy.onTick(nowMillis = 99_000L)
        assertFalse(policy.isVisible)
        policy.onAdLoaded(nowMillis = 100_000L)
        assertTrue(policy.isVisible)
        // 啟動後 99 秒才出現：再 29.999 秒仍在顯示，總時數才剛滿
        policy.onTick(nowMillis = 129_999L)
        assertTrue(policy.isVisible)
    }

    @Test
    fun `顯示滿 30 秒收起，邊界在 30_000 毫秒`() {
        val policy = BannerPolicy()
        policy.onConsentGranted()
        policy.onAdLoaded(nowMillis = 0L)
        policy.onTick(nowMillis = 29_999L)
        assertTrue(policy.isVisible)
        policy.onTick(nowMillis = 30_000L)
        assertFalse(policy.isVisible)
        assertEquals(BannerStage.Collapsed, policy.stage)
    }

    @Test
    fun `收起後任何事件都不讓它再出現`() {
        val policy = BannerPolicy()
        policy.onConsentGranted()
        policy.onAdLoaded(nowMillis = 0L)
        policy.onTick(nowMillis = 30_000L)
        policy.onAdLoaded(nowMillis = 40_000L)
        policy.onConsentGranted()
        assertEquals(BannerStage.Collapsed, policy.stage)
        assertFalse(policy.isVisible)
        assertFalse(policy.mayRequestAd)
    }

    @Test
    fun `載入失敗不重試，本次執行徹底沒有橫幅`() {
        val policy = BannerPolicy()
        policy.onConsentGranted()
        policy.onLoadFailed()
        assertEquals(BannerStage.Suppressed, policy.stage)
        assertFalse(policy.mayRequestAd)
        policy.onConsentGranted()
        assertEquals(BannerStage.Suppressed, policy.stage)
    }

    @Test
    fun `未同意時 onTick 與 onLoadFailed 都是空操作`() {
        val policy = BannerPolicy()
        policy.onTick(nowMillis = 10_000L)
        policy.onLoadFailed()
        assertEquals(BannerStage.AwaitingConsent, policy.stage)
    }

    @Test
    fun `時數可注入，方便測試其他秒數`() {
        val policy = BannerPolicy(durationMillis = 5_000L)
        policy.onConsentGranted()
        policy.onAdLoaded(nowMillis = 1_000L)
        policy.onTick(nowMillis = 5_999L)
        assertTrue(policy.isVisible)
        policy.onTick(nowMillis = 6_000L)
        assertFalse(policy.isVisible)
    }

    @Test
    fun `預設時長就是規格定的 30 秒`() {
        assertEquals(30_000L, BANNER_VISIBLE_DURATION_MS)
    }
}
