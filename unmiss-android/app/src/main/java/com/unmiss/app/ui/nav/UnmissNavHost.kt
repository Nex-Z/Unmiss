package com.unmiss.app.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.unmiss.app.data.ServiceLocator
import com.unmiss.app.data.AppCatalog
import com.unmiss.app.ui.screens.AllowlistScreen
import com.unmiss.app.ui.screens.HomeScreen
import com.unmiss.app.ui.screens.NotificationHistoryScreen
import com.unmiss.app.ui.screens.RemindersScreen
import com.unmiss.app.ui.screens.SettingsHubScreen
import com.unmiss.app.ui.screens.StatisticsScreen
import com.unmiss.app.ui.theme.LiquidGlass
import com.unmiss.app.ui.theme.LiquidGlassCanvas
import com.unmiss.app.ui.theme.LocalLiquidGlassEnabled
import com.unmiss.app.ui.theme.LocalLiquidGlassIntensity
import com.unmiss.app.ui.theme.LocalLiquidBackdrop
import com.unmiss.app.ui.theme.liquidNavigationIndicator
import com.unmiss.app.ui.theme.LiquidOverlayHost
import com.unmiss.app.upload.UploadWorker
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.abs

object Routes {
    const val HOME = "home"
    const val ALLOWLIST = "allowlist"
    const val HISTORY = "history"
    const val REMINDERS = "reminders"
    const val SETTINGS = "settings"
    const val STATISTICS = "statistics"
}

private data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun UnmissNavHost() {
    val liquidGlassEnabled by ServiceLocator.get().settingsDataStore.liquidGlassEnabled
        .collectAsState(initial = true)
    val liquidGlassIntensity by ServiceLocator.get().settingsDataStore.liquidGlassIntensity
        .collectAsState(initial = 1f)
    val navController = rememberNavController()
    val navigationBackdrop = rememberLayerBackdrop()
    val items = listOf(
        BottomNavItem(Routes.HOME, "首页", Icons.Filled.Home),
        BottomNavItem(Routes.REMINDERS, "提醒", Icons.Filled.Notifications),
        BottomNavItem(Routes.HISTORY, "收录", Icons.Filled.History),
        BottomNavItem(Routes.ALLOWLIST, "应用", Icons.Filled.Apps),
    )
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    fun navigateTopLevel(route: String) {
        if (currentRoute == route) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(Unit) {
        launch { runCatching { AppCatalog(navController.context).loadUserVisibleApps() } }
        launch { runCatching { ServiceLocator.get().notificationRepository.ensureRegistered() } }
        UploadWorker.enqueueNow(navController.context)
    }

    LiquidGlassCanvas(
        enabled = liquidGlassEnabled,
        intensity = liquidGlassIntensity,
    ) {
        LiquidOverlayHost(backdrop = navigationBackdrop) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize().layerBackdrop(navigationBackdrop),
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenAllowlist = { navigateTopLevel(Routes.ALLOWLIST) },
                        onOpenReminders = { navigateTopLevel(Routes.REMINDERS) },
                        onOpenHistory = { navigateTopLevel(Routes.HISTORY) },
                        onOpenStatistics = { navController.navigate(Routes.STATISTICS) },
                    )
                }
                composable(Routes.ALLOWLIST) { AllowlistScreen() }
                composable(Routes.HISTORY) { NotificationHistoryScreen() }
                composable(Routes.REMINDERS) { RemindersScreen() }
                composable(Routes.SETTINGS) { SettingsHubScreen(onClose = { navController.popBackStack() }) }
                composable(Routes.STATISTICS) { StatisticsScreen(onBack = { navController.popBackStack() }) }
            }
            if (currentRoute != Routes.SETTINGS && currentRoute != Routes.STATISTICS) {
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    CompositionLocalProvider(LocalLiquidBackdrop provides navigationBackdrop) {
                        LiquidBottomBar(
                            items = items,
                            currentRoute = currentRoute,
                            onSelect = ::navigateTopLevel,
                        )
                    }
                }
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
    if (LocalLiquidGlassEnabled.current && LocalLiquidGlassIntensity.current > 0f) {
        DraggableLiquidBottomBar(items, currentRoute, onSelect)
    } else {
        ClassicBottomBar(items, currentRoute, onSelect)
    }
}

@Composable
private fun ClassicBottomBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = Color.White.copy(alpha = 0.38f),
            shadowElevation = 5.dp,
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEach { item ->
                    val selected = currentRoute == item.route
                    val interactionSource = remember { MutableInteractionSource() }
                    val color by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tab color",
                    )
                    val iconScale by animateFloatAsState(if (selected) 1.08f else 1f, label = "tab scale")
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSelect(item.route) },
                            ),
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

@Composable
private fun DraggableLiquidBottomBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
) {
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val position = remember { Animatable(selectedIndex.toFloat()) }
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(LocalLiquidBackdrop.current, tabsBackdrop)
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var velocityStretch by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(selectedIndex) {
        if (!dragging && position.targetValue != selectedIndex.toFloat()) {
            dragPosition = selectedIndex.toFloat()
            position.animateTo(
                selectedIndex.toFloat(),
                spring(dampingRatio = 0.72f, stiffness = 420f),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        LiquidGlass(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 34,
            navigation = true,
            surfaceAlpha = 0.28f,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(66.dp)) {
                val tabWidthPx = constraints.maxWidth.toFloat() / items.size
                val tabWidth = with(LocalDensity.current) { tabWidthPx.toDp() }
                val activeIndex = position.value.roundToInt().coerceIn(items.indices)
                val stretchX by animateFloatAsState(
                    targetValue = if (dragging) 1.14f else 1f,
                    animationSpec = spring(dampingRatio = 0.58f, stiffness = 520f),
                    label = "liquid tab stretch x",
                )
                val stretchY by animateFloatAsState(
                    targetValue = if (dragging) 0.91f else 1f,
                    animationSpec = spring(dampingRatio = 0.62f, stiffness = 560f),
                    label = "liquid tab stretch y",
                )

                fun settle() {
                    val target = dragPosition.roundToInt().coerceIn(items.indices)
                    dragging = false
                    velocityStretch = 0f
                    dragPosition = target.toFloat()
                    if (items[target].route != currentRoute) onSelect(items[target].route)
                    scope.launch {
                        position.animateTo(
                            target.toFloat(),
                            spring(dampingRatio = 0.66f, stiffness = 460f),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(66.dp)
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer(colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)),
                ) {
                    items.forEach { item ->
                        Column(
                            modifier = Modifier.weight(1f).height(66.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(item.icon, contentDescription = null)
                            Text(item.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .width(tabWidth - 8.dp)
                        .height(54.dp)
                        .graphicsLayer {
                            translationX = position.value * tabWidthPx + 4.dp.toPx()
                            translationY = 6.dp.toPx()
                            scaleX = stretchX + abs(velocityStretch) * 0.16f
                            scaleY = stretchY - abs(velocityStretch) * 0.05f
                        }
                        .liquidNavigationIndicator(
                            tint = MaterialTheme.colorScheme.primary,
                            dragging = dragging,
                            backdrop = combinedBackdrop,
                        ),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(66.dp)
                        .pointerInput(tabWidthPx, items.size) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    dragging = true
                                    dragPosition = position.value
                                    scope.launch { position.stop() }
                                },
                                onDragCancel = { settle() },
                                onDragEnd = { settle() },
                            ) { change, dragAmount ->
                                change.consume()
                                velocityStretch = (dragAmount / tabWidthPx).coerceIn(-1f, 1f)
                                dragPosition = (dragPosition + dragAmount / tabWidthPx)
                                    .coerceIn(0f, items.lastIndex.toFloat())
                                scope.launch { position.snapTo(dragPosition) }
                            }
                        },
                ) {
                    items.forEachIndexed { index, item ->
                        val selected = activeIndex == index
                        val interactionSource = remember { MutableInteractionSource() }
                        val color by animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "drag tab color",
                        )
                        val scale by animateFloatAsState(
                            if (selected) 1.1f else 0.96f,
                            spring(dampingRatio = 0.72f, stiffness = 520f),
                            label = "drag tab scale",
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(66.dp)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                ) {
                                    dragging = false
                                    dragPosition = index.toFloat()
                                    scope.launch {
                                        position.animateTo(
                                            index.toFloat(),
                                            spring(dampingRatio = 0.66f, stiffness = 460f),
                                        )
                                    }
                                    if (item.route != currentRoute) onSelect(item.route)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = color,
                                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
                            )
                            Text(
                                item.label,
                                color = color,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}
