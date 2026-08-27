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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.unmiss.app.data.ServiceLocator
import com.unmiss.app.data.db.LocalReminder
import com.unmiss.app.ui.theme.AdaptiveLiquidButton
import com.unmiss.app.ui.theme.GlassButtonStyle
import com.unmiss.app.ui.theme.LiquidGlass
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
    onOpenStatistics: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val container = ServiceLocator.get()
    val reminders by container.reminderRepository.pending.collectAsState(initial = emptyList())
    val allReminders by container.reminderRepository.all.collectAsState(initial = emptyList())
    val enabledPackages by container.settingsDataStore.enabledPackages.collectAsState(initial = emptySet())
    val captureEnabled by container.settingsDataStore.captureEnabled.collectAsState(initial = true)
    val capturedCount by container.pendingDao.observeTotalCount().collectAsState(initial = 0)
    val pendingUploadCount by container.pendingDao.observePendingCount().collectAsState(initial = 0)
    val todayStart = remember {
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val capturedToday by container.pendingDao.observeCountSince(todayStart).collectAsState(initial = 0)
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
    val candidates = reminders.filter { it.status == "candidate" }
    val confirmed = reminders.filter { it.status == "pending" }
    val completedToday = allReminders.count { it.status == "done" && isToday(it.completedAt, todayStart) }
    val ignoredToday = allReminders.count { it.status == "ignored" && isToday(it.updatedAt, todayStart) }
    val categoryCounts = reminders.groupingBy { it.category }.eachCount().entries
        .sortedByDescending { it.value }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item { HomeHeader(onOpenSettings) }
        item {
            HomeStatus(
                healthy = healthy,
                candidates = candidates.size,
                confirmed = confirmed.size,
                pendingUploads = pendingUploadCount,
            )
        }
        if (!healthy) {
            item {
                SetupPanel(
                    listenerGranted = listenerGranted,
                    postGranted = postGranted,
                    hasApps = enabledPackages.isNotEmpty(),
                    onListenerPermission = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onPostPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenAllowlist = onOpenAllowlist,
                )
            }
        }
        item {
            TodayProgress(
                captured = capturedToday,
                completed = completedToday,
                ignored = ignoredToday,
                onOpen = onOpenStatistics,
            )
        }
        item {
            ReminderPreviewSection(
                title = "待你判断",
                emptyText = "新发现的重要事项会出现在这里",
                reminders = candidates.take(3),
                onOpen = onOpenReminders,
            )
        }
        if (categoryCounts.isNotEmpty()) {
            item { CategoryDistribution(categoryCounts) }
        }
        item {
            ReminderPreviewSection(
                title = "接下来",
                emptyText = "目前没有已确认的提醒",
                reminders = confirmed.take(3),
                onOpen = onOpenReminders,
            )
        }
        item {
            CaptureFootnote(
                capturedCount = capturedCount,
                appCount = enabledPackages.size,
                onOpenHistory = onOpenHistory,
            )
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
        LiquidGlass(modifier = Modifier.size(46.dp), cornerRadius = 23, onClick = onOpenSettings) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Settings, contentDescription = "设置", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun HomeStatus(healthy: Boolean, candidates: Int, confirmed: Int, pendingUploads: Int) {
    Column(modifier = Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Surface(
                shape = CircleShape,
                color = if (healthy) Color(0xFF34C759) else Color(0xFFFF9F0A),
                modifier = Modifier.size(9.dp),
            ) {}
            Text(if (healthy) "通知守护正常" else "需要完成设置", fontWeight = FontWeight.SemiBold)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            HomeMetric("待判断", candidates, Modifier.weight(1f))
            HomeMetric("已确认", confirmed, Modifier.weight(1f))
            HomeMetric("待同步", pendingUploads, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeMetric(label: String, value: Int, modifier: Modifier) {
    Column(modifier) {
        AnimatedContent(targetState = value, label = label) { count ->
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TodayProgress(captured: Int, completed: Int, ignored: Int, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("今日进展", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "查看统计",
                modifier = Modifier.clickable(onClick = onOpen),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            HomeMetric("新收录", captured, Modifier.weight(1f))
            HomeMetric("已完成", completed, Modifier.weight(1f))
            HomeMetric("已筛除", ignored, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CategoryDistribution(categories: List<Map.Entry<String, Int>>) {
    val maximum = categories.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("当前关注", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        categories.take(5).forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    homeCategoryLabel(entry.key),
                    modifier = Modifier.weight(0.28f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier.weight(0.58f).height(7.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    ) {}
                    Surface(
                        modifier = Modifier.fillMaxWidth(entry.value.toFloat() / maximum).height(7.dp),
                        shape = CircleShape,
                        color = categoryColor(entry.key),
                    ) {}
                }
                Text(
                    entry.value.toString(),
                    modifier = Modifier.weight(0.14f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ReminderPreviewSection(
    title: String,
    emptyText: String,
    reminders: List<LocalReminder>,
    onOpen: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (reminders.isNotEmpty()) {
                Text(
                    "查看全部",
                    modifier = Modifier.clickable(onClick = onOpen),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (reminders.isEmpty()) {
            Text(
                emptyText,
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.42f),
            ) {
                Column {
                    reminders.forEachIndexed { index, reminder ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(8.dp),
                                shape = CircleShape,
                                color = categoryColor(reminder.category),
                            ) {}
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    reminder.title,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${homeCategoryLabel(reminder.category)} · ${formatHomeTime(reminder.remindAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                        if (index < reminders.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 36.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupPanel(
    listenerGranted: Boolean,
    postGranted: Boolean,
    hasApps: Boolean,
    onListenerPermission: () -> Unit,
    onPostPermission: () -> Unit,
    onOpenAllowlist: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.55f),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("完成设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SetupLine("通知使用权", listenerGranted)
            SetupLine("发送提醒", postGranted)
            SetupLine("监听应用", hasApps)
            when {
                !listenerGranted -> AdaptiveLiquidButton(onClick = onListenerPermission) { Text("开启通知使用权") }
                !postGranted -> AdaptiveLiquidButton(onClick = onPostPermission, style = GlassButtonStyle.SECONDARY) { Text("允许发送提醒") }
                !hasApps -> AdaptiveLiquidButton(onClick = onOpenAllowlist, style = GlassButtonStyle.SECONDARY) { Text("选择应用") }
            }
        }
    }
}

@Composable
private fun SetupLine(label: String, complete: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Icon(
            if (complete) Icons.Filled.Check else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (complete) Color(0xFF34C759) else Color(0xFFFF9F0A),
            modifier = Modifier.size(17.dp),
        )
        Text(label, modifier = Modifier.weight(1f))
        Text(
            if (complete) "已完成" else "待设置",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CaptureFootnote(capturedCount: Int, appCount: Int, onOpenHistory: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenHistory).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("已收录 $capturedCount 条通知", fontWeight = FontWeight.Medium)
            Text(
                "来自 $appCount 个应用 · 点击回查",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

private fun formatHomeTime(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("M月d日 HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value))
}.getOrDefault(value)

private fun isToday(value: String?, todayStart: Long): Boolean = value?.let {
    runCatching { Instant.parse(it).toEpochMilli() >= todayStart }.getOrDefault(false)
} ?: false

private fun categoryColor(category: String): Color = when (category) {
    "work" -> Color(0xFF3D6FA8)
    "life" -> Color(0xFF2F8A65)
    "finance" -> Color(0xFFB97824)
    "health" -> Color(0xFFC94F58)
    "social" -> Color(0xFF7D64A8)
    "entertainment" -> Color(0xFF4E8791)
    else -> Color(0xFF6D7480)
}

private fun homeCategoryLabel(category: String): String = when (category) {
    "work" -> "工作"
    "life" -> "生活"
    "finance" -> "财务"
    "health" -> "健康"
    "social" -> "社交"
    "entertainment" -> "娱乐"
    else -> "其他"
}

private fun isListenerAccessGranted(context: Context): Boolean = runCatching {
    Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        ?.contains(context.packageName) == true
}.getOrDefault(false)

private fun isPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
