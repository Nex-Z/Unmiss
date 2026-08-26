package com.unmiss.app.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.unmiss.app.data.ServiceLocator
import com.unmiss.app.data.db.LocalReminder
import com.unmiss.app.ui.theme.LiquidGlass
import com.unmiss.app.ui.theme.AdaptiveGlassSurface
import com.unmiss.app.ui.theme.AdaptiveLiquidButton
import com.unmiss.app.ui.theme.GlassButtonStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenAllowlist: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val container = ServiceLocator.get()
    val reminders by container.reminderRepository.pending.collectAsState(initial = emptyList())
    val enabledPackages by container.settingsDataStore.enabledPackages.collectAsState(initial = emptySet())
    val captureEnabled by container.settingsDataStore.captureEnabled.collectAsState(initial = true)
    val capturedCount by container.pendingDao.observeTotalCount().collectAsState(initial = 0)
    val pendingUploadCount by container.pendingDao.observePendingCount().collectAsState(initial = 0)
    var listenerGranted by remember { mutableStateOf(isListenerAccessGranted(context)) }
    var postGranted by remember { mutableStateOf(isPermissionGranted(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        postGranted = it
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerGranted = isListenerAccessGranted(context)
                postGranted = isPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val healthy = listenerGranted && postGranted && captureEnabled && enabledPackages.isNotEmpty()
    Scaffold(containerColor = Color.Transparent) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item { HomeHeader(onOpenSettings) }
            item {
                CaptureOverview(
                    healthy = healthy,
                    capturedCount = capturedCount,
                    pendingUploadCount = pendingUploadCount,
                    enabledApps = enabledPackages.size,
                )
            }
            if (!healthy) {
                item {
                    SetupGroup(
                        listenerGranted = listenerGranted,
                        postGranted = postGranted,
                        captureEnabled = captureEnabled,
                        hasApps = enabledPackages.isNotEmpty(),
                        onListenerPermission = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        onPostPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onOpenAllowlist = onOpenAllowlist,
                    )
                }
            }
            item { NextReminder(reminders.firstOrNull(), onOpenReminders) }
            item {
                HomeDestinations(
                    capturedCount = capturedCount,
                    remindersCount = reminders.size,
                    appsCount = enabledPackages.size,
                    onOpenHistory = onOpenHistory,
                    onOpenReminders = onOpenReminders,
                    onOpenAllowlist = onOpenAllowlist,
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Unmiss", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE).format(LocalDate.now()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LiquidGlass(modifier = Modifier.size(48.dp), cornerRadius = 24, onClick = onOpenSettings) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Settings, contentDescription = "设置", modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable
private fun CaptureOverview(healthy: Boolean, capturedCount: Int, pendingUploadCount: Int, enabledApps: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().animateContentSize().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Surface(shape = CircleShape, color = if (healthy) Color(0xFF34C759) else Color(0xFFFF9F0A), modifier = Modifier.size(9.dp)) {}
            Text(if (healthy) "正在守护通知" else "采集尚未就绪", fontWeight = FontWeight.SemiBold)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedContent(targetState = capturedCount, label = "capture count") { count ->
                Text(count.toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            }
            Text("条已收录", modifier = Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        Row(modifier = Modifier.fillMaxWidth()) {
            SmallMetric("待上传", pendingUploadCount.toString(), Modifier.weight(1f))
            SmallMetric("监听应用", enabledApps.toString(), Modifier.weight(1f))
            SmallMetric("保留", "90 天", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SmallMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SetupGroup(
    listenerGranted: Boolean,
    postGranted: Boolean,
    captureEnabled: Boolean,
    hasApps: Boolean,
    onListenerPermission: () -> Unit,
    onPostPermission: () -> Unit,
    onOpenAllowlist: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("完成设置")
        AdaptiveGlassSurface(shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.86f)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SetupLine("通知使用权", listenerGranted)
                SetupLine("发送提醒", postGranted)
                SetupLine("监听应用", hasApps)
                if (!captureEnabled) SetupLine("通知采集", false)
                if (!listenerGranted) AdaptiveLiquidButton(onClick = onListenerPermission) { Text("开启通知使用权") }
                if (!postGranted) AdaptiveLiquidButton(onClick = onPostPermission, style = GlassButtonStyle.SECONDARY) { Text("允许发送提醒") }
                if (!hasApps) AdaptiveLiquidButton(onClick = onOpenAllowlist, style = GlassButtonStyle.SECONDARY) { Text("选择监听应用") }
            }
        }
    }
}

@Composable
private fun SetupLine(label: String, complete: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            if (complete) Icons.Filled.Check else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (complete) Color(0xFF34C759) else Color(0xFFFF9F0A),
            modifier = Modifier.size(18.dp),
        )
        Text(label, modifier = Modifier.weight(1f))
        Text(if (complete) "已完成" else "待设置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NextReminder(reminder: LocalReminder?, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("下一条提醒")
        AdaptiveGlassSurface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = 0.86f),
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(Icons.Filled.Notifications)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(reminder?.title ?: "暂无待处理提醒", fontWeight = FontWeight.SemiBold)
                    Text(
                        reminder?.let { formatDashboardTime(it.remindAt) } ?: "重要通知生成的提醒会出现在这里",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun HomeDestinations(
    capturedCount: Int,
    remindersCount: Int,
    appsCount: Int,
    onOpenHistory: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenAllowlist: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("浏览")
        AdaptiveGlassSurface(shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.86f)) {
            Column {
                DestinationRow(Icons.Filled.Notifications, "提醒事项", "$remindersCount 项待处理", onOpenReminders)
                GroupDivider()
                DestinationRow(Icons.Filled.History, "通知收录", "$capturedCount 条历史通知", onOpenHistory)
                GroupDivider()
                DestinationRow(Icons.Filled.Apps, "监听应用", "已选择 $appsCount 个应用", onOpenAllowlist)
            }
        }
    }
}

@Composable
private fun DestinationRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        IconTile(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun IconTile(icon: ImageVector) {
    Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 67.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
}

private fun formatDashboardTime(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("M月d日 HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(value))
}.getOrDefault(value)

private fun isListenerAccessGranted(context: Context): Boolean = runCatching {
    Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        ?.contains(context.packageName) == true
}.getOrDefault(false)

private fun isPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
