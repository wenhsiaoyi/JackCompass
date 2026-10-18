package com.wenhsiaoyi.jackcompass.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.wenhsiaoyi.jackcompass.R
import com.wenhsiaoyi.jackcompass.ui.theme.DialFace
import com.wenhsiaoyi.jackcompass.ui.theme.DialScreen
import com.wenhsiaoyi.jackcompass.ui.theme.DialText
import com.wenhsiaoyi.jackcompass.ui.theme.LevelGreenBright
import com.wenhsiaoyi.jackcompass.ui.theme.LevelTube

/** 可選語系；標籤一律以該語言本身書寫（語言名不翻譯原則）。 */
private enum class AppLanguage(val tag: String, val label: String) {
    English("en", "English"),
    ChineseSimplified("zh-CN", "简体中文"),
    ChineseTraditional("zh-TW", "繁体中文"),
}

/**
 * 設定畫面：目前僅有語系切換。
 * 經 AppCompatDelegate 套用——API 33+ 走系統 per-app locale，
 * 以下由 appcompat 保存（見 AndroidManifest 的 autoStoreLocales 服務）。
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val locales = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (locales.isEmpty) "" else locales.get(0)?.toLanguageTag().orEmpty()
    val selected = AppLanguage.entries.firstOrNull { it.tag == currentTag }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DialScreen)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = DialText,
        )
        Spacer(Modifier.height(40.dp))
        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.titleMedium,
            color = DialText,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.height(12.dp))
        LanguageSelector(selected = selected)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelector(selected: AppLanguage?) {
    val options = AppLanguage.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(option.tag)) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = LevelTube,
                    activeContentColor = LevelGreenBright,
                    inactiveContainerColor = DialFace,
                    inactiveContentColor = DialText,
                ),
                label = {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }
    }
}
