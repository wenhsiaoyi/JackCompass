package com.wenhsiaoyi.jackcompass.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wenhsiaoyi.jackcompass.ui.compass.CompassScreen
import com.wenhsiaoyi.jackcompass.ui.gps.GpsScreen
import com.wenhsiaoyi.jackcompass.ui.level.LevelScreen

/** 底部導覽列的三個工具分頁。新增工具時在這裡加一筆即可。 */
private enum class ToolDestination(
    val route: String,
    val label: String,
) {
    Compass("compass", "指南針"),
    Level("level", "水平儀"),
    Gps("gps", "GPS"),
}

/*
 * 尚未放入 icon：androidx.compose.material:material-icons-core / -extended
 * 都停在 1.7.8 後停止更新，也不再隨 Compose BOM 帶入。等盤面設計定案後，
 * 建議自行用 ImageVector 或 drawable 畫，而不是重新依賴已停更的 icon pack。
 */

@Composable
fun JackCompassApp() {
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
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {},
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ToolDestination.Compass.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ToolDestination.Compass.route) { CompassScreen() }
            composable(ToolDestination.Level.route) { LevelScreen() }
            composable(ToolDestination.Gps.route) { GpsScreen() }
        }
    }
}
