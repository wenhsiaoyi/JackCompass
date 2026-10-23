package com.wenhsiaoyi.jackcompass.ads

import androidx.annotation.StringRes
import com.google.android.ump.FormError
import com.wenhsiaoyi.jackcompass.R

/**
 * UMP 隱私選項表單的錯誤碼 → 給使用者看的一句話。
 *
 * 非規範區（`IABTCF_gdprApplies=0`）Google 根本沒有表單可顯示，這時回的是
 * `INVALID_OPERATION`；對使用者來說這不是「失敗」，而是「這裡沒有東西可改」，
 * 所以單獨一句，不要和載入失敗混在一起。其餘錯誤碼一律請使用者稍後再試。
 */
@StringRes
fun privacyOptionsMessageRes(errorCode: Int?): Int? = when (errorCode) {
    null -> null
    FormError.ErrorCode.INVALID_OPERATION -> R.string.settings_ads_form_unavailable
    else -> R.string.settings_ads_form_failed
}
