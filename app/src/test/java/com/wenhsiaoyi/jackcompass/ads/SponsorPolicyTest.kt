package com.wenhsiaoyi.jackcompass.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 設定頁贊助入口的規則層測試。
 *
 * 插屏絕不自動播：只有使用者按下、且回傳 true 時，包裝層才允許向 SDK 要廣告。
 * 「準備中」與「正在播」期間重複點擊回傳 false——不排隊、不重疊。
 */
class SponsorPolicyTest {

    @Test
    fun `同意前整列隱藏`() {
        val policy = SponsorPolicy()
        assertEquals(SponsorStage.Hidden, policy.stage)
        assertFalse(policy.isRowVisible)
        assertFalse("隱藏時點了也不允許請求", policy.onClick())
    }

    @Test
    fun `通過同意後進入可點狀態`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        assertEquals(SponsorStage.Ready, policy.stage)
        assertTrue(policy.isRowVisible)
        assertFalse(policy.isBusy)
    }

    @Test
    fun `拒絕同意時整列藏起來`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        policy.onConsentDenied()
        assertEquals(SponsorStage.Hidden, policy.stage)
        assertFalse(policy.isRowVisible)
    }

    @Test
    fun `按下進入準備中並回傳允許請求`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        assertTrue(policy.onClick())
        assertEquals(SponsorStage.Preparing, policy.stage)
        assertTrue("準備中算忙碌", policy.isBusy)
    }

    @Test
    fun `準備中重複點擊不被允許，也不會把請求排成隊`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        policy.onClick()
        assertFalse(policy.onClick())
        assertFalse(policy.onClick())
        assertEquals(SponsorStage.Preparing, policy.stage)
    }

    @Test
    fun `廣告呈現期間重複點擊不被允許`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        policy.onClick()
        policy.onAdShown()
        assertEquals(SponsorStage.Playing, policy.stage)
        assertTrue(policy.isBusy)
        assertFalse(policy.onClick())
    }

    @Test
    fun `關掉廣告後回到可點，不設次數上限`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        repeat(3) {
            assertTrue("第 ${it + 1} 次都該允許", policy.onClick())
            policy.onAdShown()
            policy.onAdDismissed()
            assertEquals(SponsorStage.Ready, policy.stage)
        }
    }

    @Test
    fun `載入不到廣告時暫時沒有，之後還能再點`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        policy.onClick()
        policy.onAdUnavailable()
        assertEquals(SponsorStage.Unavailable, policy.stage)
        assertFalse(policy.isBusy)
        assertTrue(policy.isRowVisible)
        assertTrue(policy.onClick())
        assertEquals(SponsorStage.Preparing, policy.stage)
    }

    @Test
    fun `呈現失敗走暫時沒有，且不會進到播放中`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        policy.onClick()
        policy.onAdShown()
        policy.onShowFailed()
        assertEquals(SponsorStage.Unavailable, policy.stage)
    }

    @Test
    fun `非準備中的 onAdShown 是空操作`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        policy.onAdShown()
        assertEquals(SponsorStage.Ready, policy.stage)
    }

    @Test
    fun `暫時沒有時收到 onAdDismissed 不會跳到可點以外`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        policy.onClick()
        policy.onAdUnavailable()
        policy.onAdDismissed()
        assertEquals(SponsorStage.Unavailable, policy.stage)
    }
}
