package com.wenhsiaoyi.jackcompass.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wenhsiaoyi.jackcompass.ads.AdSession
import com.wenhsiaoyi.jackcompass.R
import com.wenhsiaoyi.jackcompass.ui.compass.CompassScreen
import com.wenhsiaoyi.jackcompass.ui.level.LevelScreen
import com.wenhsiaoyi.jackcompass.ui.settings.SettingsScreen

/** 底部導覽列的兩個工具分頁。新增工具時在這裡加一筆即可。 */
private enum class ToolDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
) {
    Compass("compass", R.string.tab_compass),
    Level("level", R.string.tab_level),
}

/** 不在導覽列上的頁面路由。 */
private const val SETTINGS_ROUTE = "settings"

/** 導覽列的第一個分頁同時是 NavHost 的起始路由。 */
private val startRoute = ToolDestination.Compass.route

/**
 * 點分頁時的跳轉。
 *
 * 起點分頁要單獨處理：一般的 `popUpTo(起點){saveState}` + `restoreState`
 * 會先把被彈掉的頁面（例如設定頁）存進起點 entry 的保存棧，緊接著
 * restoreState 又把它還原回來，結果點了指南針卻回到設定頁。
 * 對起點分頁改用 popBackStack，只往上彈、不重新入棧。
 */
private fun NavHostController.selectTab(route: String) {
    if (route == startRoute) {
        popBackStack(route, inclusive = false)
        return
    }
    navigate(route) {
        popUpTo(startRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/*
 * 尚未放入 icon：androidx.compose.material:material-icons-core / -extended
 * 都停在 1.7.8 後停止更新，也不再隨 Compose BOM 帶入。等盤面設計定案後，
 * 建議自行用 ImageVector 或 drawable 畫，而不是重新依賴已停更的 icon pack。
 */

@Composable
fun JackCompassApp(session: AdSession) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                ToolDestination.entries.forEach { destination ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == destination.route } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.selectTab(destination.route) },
                        icon = {},
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val onSettings = currentDestination?.hierarchy?.any { it.route == SETTINGS_ROUTE } == true

        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = ToolDestination.Compass.route,
            ) {
                composable(ToolDestination.Compass.route) { CompassScreen() }
                composable(ToolDestination.Level.route) { LevelScreen() }
                composable(SETTINGS_ROUTE) { SettingsScreen() }
            }

            // 右上齒輪：任何分頁都能進設定；設定頁本身不重複顯示
            if (!onSettings) {
                IconButton(
                    onClick = { navController.navigate(SETTINGS_ROUTE) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.settings_open),
                    )
                }
            }
        }
    }
}
