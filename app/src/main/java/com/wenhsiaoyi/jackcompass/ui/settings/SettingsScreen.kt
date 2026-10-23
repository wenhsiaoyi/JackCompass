package com.wenhsiaoyi.jackcompass.ui.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.wenhsiaoyi.jackcompass.ads.AdSession
import com.wenhsiaoyi.jackcompass.ads.SponsorStage
import com.wenhsiaoyi.jackcompass.ads.asActivity
import com.wenhsiaoyi.jackcompass.R
import com.wenhsiaoyi.jackcompass.ui.theme.DialFace
import com.wenhsiaoyi.jackcompass.ui.theme.DialScreen
import com.wenhsiaoyi.jackcompass.ui.theme.DialText
import com.wenhsiaoyi.jackcompass.ui.theme.DialTick
import com.wenhsiaoyi.jackcompass.ui.theme.LevelGreenBright
import com.wenhsiaoyi.jackcompass.ui.theme.LevelTube

/** 可選語系；標籤一律以該語言本身書寫（語言名不翻譯原則）。 */
private enum class AppLanguage(val tag: String, val label: String) {
    English("en", "English"),
    ChineseSimplified("zh-CN", "简体中文"),
    ChineseTraditional("zh-TW", "繁體中文"),
}

/**
 * 設定畫面：目前僅有語系切換。
 * 經 AppCompatDelegate 套用——API 33+ 走系統 per-app locale，
 * 以下由 appcompat 保存（見 AndroidManifest 的 autoStoreLocales 服務）。
 */
@Composable
fun SettingsScreen(session: AdSession, modifier: Modifier = Modifier) {
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
        Spacer(Modifier.height(36.dp))
        SponsorSection(session = session)
        Spacer(Modifier.height(36.dp))
        AdSettingsSection(session = session)
    }
}

@Composable
private fun SectionLabel(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleMedium,
        color = DialText,
        letterSpacing = 3.sp,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RowCard(
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DialFace)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = DialText,
            )
            if (subtitleRes != null) {
                Text(
                    text = stringResource(subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = DialTick,
                )
            }
        }
    }
}

@Composable
private fun SponsorSection(session: AdSession) {
    val stage = session.sponsorStage          // Compose State：政策一變在這裡重組
    if (stage == SponsorStage.Hidden) return
    val activity = LocalContext.current.asActivity() ?: return
    SectionLabel(R.string.settings_sponsor_section)
    Spacer(Modifier.height(12.dp))
    RowCard(
        titleRes = R.string.settings_sponsor_title,
        subtitleRes = R.string.settings_sponsor_subtitle,
        onClick = { session.requestSponsor(activity) },
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(
            when (stage) {
                SponsorStage.Preparing -> R.string.settings_sponsor_preparing
                SponsorStage.Unavailable -> R.string.settings_sponsor_unavailable
                else -> R.string.settings_sponsor_ready
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = DialTick,
    )
}

@Composable
private fun AdSettingsSection(session: AdSession) {
    val activity = LocalContext.current.asActivity() ?: return
    SectionLabel(R.string.settings_ads_section)
    Spacer(Modifier.height(12.dp))
    // 即使使用者拒絕同意也要留著：這是規範區事後改同意的唯一入口。
    RowCard(
        titleRes = R.string.settings_ads_privacy,
        subtitleRes = null,
        onClick = { session.showPrivacyOptions(activity) },
    )
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
