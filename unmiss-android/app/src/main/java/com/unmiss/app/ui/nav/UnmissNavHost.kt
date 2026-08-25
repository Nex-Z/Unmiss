package com.unmiss.app.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.unmiss.app.data.ServiceLocator
import com.unmiss.app.ui.screens.AllowlistScreen
import com.unmiss.app.ui.screens.HomeScreen
import com.unmiss.app.ui.screens.NotificationHistoryScreen
import com.unmiss.app.ui.screens.RemindersScreen
import com.unmiss.app.ui.screens.SettingsScreen
import com.unmiss.app.ui.theme.LiquidGlass
import com.unmiss.app.ui.theme.LiquidGlassCanvas
import com.unmiss.app.upload.UploadWorker

object Routes {
    const val HOME = "home"
    const val ALLOWLIST = "allowlist"
    const val HISTORY = "history"
    const val REMINDERS = "reminders"
    const val SETTINGS = "settings"
}

private data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun UnmissNavHost() {
    val liquidGlassEnabled by ServiceLocator.get().settingsDataStore.liquidGlassEnabled
        .collectAsState(initial = true)
    val navController = rememberNavController()
    val items = listOf(
        BottomNavItem(Routes.HOME, "首页", Icons.Filled.Home),
        BottomNavItem(Routes.HISTORY, "收录", Icons.Filled.History),
        BottomNavItem(Routes.REMINDERS, "提醒", Icons.Filled.Notifications),
        BottomNavItem(Routes.ALLOWLIST, "应用", Icons.Filled.Apps),
    )
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    LaunchedEffect(Unit) {
        runCatching { ServiceLocator.get().notificationRepository.ensureRegistered() }
        UploadWorker.enqueueNow(navController.context)
    }

    LiquidGlassCanvas(enabled = liquidGlassEnabled) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            bottomBar = {
                if (currentRoute != Routes.SETTINGS) {
                    LiquidBottomBar(
                        items = items,
                        currentRoute = currentRoute,
                        onSelect = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(padding),
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenAllowlist = { navController.navigate(Routes.ALLOWLIST) },
                        onOpenReminders = { navController.navigate(Routes.REMINDERS) },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    )
                }
                composable(Routes.ALLOWLIST) { AllowlistScreen() }
                composable(Routes.HISTORY) { NotificationHistoryScreen() }
                composable(Routes.REMINDERS) { RemindersScreen() }
                composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }
}

@Composable
private fun LiquidBottomBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp)) {
        LiquidGlass(modifier = Modifier.fillMaxWidth(), cornerRadius = 32) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEach { item ->
                    val selected = currentRoute == item.route
                    val color by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tab color",
                    )
                    val iconScale by animateFloatAsState(if (selected) 1.08f else 1f, label = "tab scale")
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clickable { onSelect(item.route) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = color,
                            modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                        )
                        Text(item.label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
