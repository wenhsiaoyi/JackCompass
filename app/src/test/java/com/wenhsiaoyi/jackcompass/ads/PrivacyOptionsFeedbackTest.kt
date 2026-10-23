package com.wenhsiaoyi.jackcompass.ads

import com.google.android.ump.FormError
import com.wenhsiaoyi.jackcompass.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「調整廣告同意選項」失敗時要講什麼。
 *
 * 非規範區（實測 logcat：`Privacy options form is not required.`）Google 端根本沒有
 * 隱私選項表單，`showPrivacyOptionsForm` 會帶著 FormError 回來。那一列依裁決永遠可見，
 * 所以失敗一定要讓使用者看見，不能再像之前那樣把錯誤丟掉。
 */
class PrivacyOptionsFeedbackTest {

    @Test
    fun `表單正常關閉時不提示`() {
        assertNull(privacyOptionsMessageRes(null))
    }

    @Test
    fun `非規範區沒有表單可顯示`() {
        assertEquals(
            R.string.settings_ads_form_unavailable,
            privacyOptionsMessageRes(FormError.ErrorCode.INVALID_OPERATION),
        )
    }

    @Test
    fun `網路逾時與內部錯誤請使用者稍後再試`() {
        listOf(
            FormError.ErrorCode.INTERNET_ERROR,
            FormError.ErrorCode.TIME_OUT,
            FormError.ErrorCode.INTERNAL_ERROR,
        ).forEach { code ->
            assertEquals(
                "code $code 應該提示稍後再試",
                R.string.settings_ads_form_failed,
                privacyOptionsMessageRes(code),
            )
        }
    }

    @Test
    fun `沒見過的錯誤碼一律走稍後再試`() {
        assertEquals(
            R.string.settings_ads_form_failed,
            privacyOptionsMessageRes(99),
        )
    }
}
