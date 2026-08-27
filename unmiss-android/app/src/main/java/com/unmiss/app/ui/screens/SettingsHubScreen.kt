package com.unmiss.app.ui.screens

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unmiss.app.data.ServiceLocator
import com.unmiss.app.reminder.ReminderSyncWorker
import com.unmiss.app.ui.theme.AdaptiveGlassSurface
import com.unmiss.app.ui.theme.AdaptiveLiquidButton
import com.unmiss.app.ui.theme.AdaptiveLiquidSwitch
import com.unmiss.app.ui.theme.GlassButtonStyle
import com.unmiss.app.ui.theme.LiquidIconButton
import com.unmiss.app.ui.theme.LiquidSlider
import com.unmiss.app.ui.theme.LiquidTextField
import com.unmiss.app.ui.theme.LiquidToggle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.roundToInt

private enum class SettingsPage(val title: String) {
    CAPTURE("采集与归纳"), PREFERENCES("提醒偏好"), APPEARANCE("外观"), DATA("服务与数据")
}

@Composable
fun SettingsHubScreen(onClose: () -> Unit) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }
    AnimatedContent(targetState = page, label = "settings page") { current ->
        when (current) {
            null -> SettingsOverview(onClose = onClose, onOpen = { page = it })
            SettingsPage.CAPTURE -> CaptureSettingsPage { page = null }
            SettingsPage.PREFERENCES -> PreferenceSettingsPage { page = null }
            SettingsPage.APPEARANCE -> AppearanceSettingsPage { page = null }
            SettingsPage.DATA -> DataSettingsPage { page = null }
        }
    }
}

@Composable
private fun SettingsOverview(onClose: () -> Unit, onOpen: (SettingsPage) -> Unit) {
    SettingsPageFrame(title = "设置", onBack = onClose) {
        item {
            Text(
                "按需进入一项设置",
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.42f),
            ) {
                Column {
                    SettingsDestination(Icons.Filled.Schedule, "采集与归纳", "通知采集、每日归纳时间") {
                        onOpen(SettingsPage.CAPTURE)
                    }
                    SettingsDivider()
                    SettingsDestination(Icons.Filled.Star, "提醒偏好", "分类权重与 0 星过滤") {
                        onOpen(SettingsPage.PREFERENCES)
                    }
                    SettingsDivider()
                    SettingsDestination(Icons.Filled.Palette, "外观", "液态玻璃与显示强度") {
                        onOpen(SettingsPage.APPEARANCE)
                    }
                    SettingsDivider()
                    SettingsDestination(Icons.Filled.Security, "服务与数据", "服务地址、隐私与删除") {
                        onOpen(SettingsPage.DATA)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDestination(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 54.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun SettingsPageFrame(
    title: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AdaptiveGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
            ) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        LiquidIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun CaptureSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = ServiceLocator.get()
    val settings = container.settingsDataStore
    var captureEnabled by remember { mutableStateOf(true) }
    var times by remember { mutableStateOf(listOf("22:00")) }
    var message by remember { mutableStateOf<String?>(null) }

    fun saveTimes(updated: List<String>) {
        val normalized = updated.distinct().sorted()
        if (normalized.isEmpty()) return
        times = normalized
        scope.launch {
            settings.setAnalysisTimes(normalized.toSet())
            runCatching { container.notificationRepository.updateAnalysisSchedule(normalized.toSet()) }
                .onSuccess { message = "归纳时间已同步" }
                .onFailure { message = "已保存在本机，服务端同步失败" }
        }
    }

    fun pickTime(current: String? = null) {
        val initial = current?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: LocalTime.now().plusHours(1)
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val value = "%02d:%02d".format(hour, minute)
                saveTimes(if (current == null) times + value else times.map { if (it == current) value else it })
            },
            initial.hour,
            initial.minute,
            true,
        ).show()
    }

    LaunchedEffect(Unit) {
        captureEnabled = settings.captureEnabledOnce()
        times = settings.analysisTimesOnce().sorted()
        runCatching { container.notificationRepository.analysisSchedule() }.onSuccess { remote ->
            times = remote.times.sorted()
            settings.setAnalysisTimes(remote.times.toSet())
        }
    }

    SettingsPageFrame(title = "采集与归纳", onBack = onBack) {
        item {
            SettingsLine(
                title = "通知采集",
                detail = if (captureEnabled) "正在监听已选择应用" else "已暂停采集",
                trailing = {
                    LiquidToggle(
                        checked = captureEnabled,
                        onCheckedChange = {
                            captureEnabled = it
                            scope.launch { settings.setCaptureEnabled(it) }
                        },
                    )
                },
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("每日归纳", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("点击时间修改", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AdaptiveLiquidButton(onClick = { pickTime() }, style = GlassButtonStyle.SECONDARY) { Text("添加") }
            }
        }
        times.forEach { time ->
            item(key = time) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { pickTime(time) }.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(time, modifier = Modifier.weight(1f).padding(start = 14.dp), style = MaterialTheme.typography.headlineSmall)
                    LiquidIconButton(
                        enabled = times.size > 1,
                        onClick = { saveTimes(times - time) },
                    ) { Icon(Icons.Filled.DeleteOutline, contentDescription = "删除 $time") }
                }
            }
        }
        message?.let { value ->
            item { Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun AppearanceSettingsPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings = ServiceLocator.get().settingsDataStore
    var enabled by remember { mutableStateOf(true) }
    var intensity by remember { mutableFloatStateOf(1f) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        enabled = settings.liquidGlassEnabledOnce()
        intensity = settings.liquidGlassIntensityOnce()
        loaded = true
    }
    LaunchedEffect(intensity, loaded) {
        if (loaded) {
            delay(120)
            settings.setLiquidGlassIntensity(intensity)
        }
    }

    SettingsPageFrame(title = "外观", onBack = onBack) {
        item {
            SettingsLine(
                title = "液态玻璃",
                detail = if (enabled) "全局玻璃材质已启用" else "使用浅色半透明样式",
                trailing = {
                    LiquidToggle(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            scope.launch { settings.setLiquidGlassEnabled(it) }
                        },
                    )
                },
            )
        }
        if (enabled) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("玻璃强度", fontWeight = FontWeight.Medium)
                        Text("${(intensity * 100).roundToInt()}%", color = MaterialTheme.colorScheme.primary)
                    }
                    LiquidSlider(value = intensity, onValueChange = { intensity = it })
                    Text(
                        "向右增加染色、模糊与边缘高光。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsLine(
    title: String,
    detail: String,
    trailing: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun PreferenceSettingsPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val container = ServiceLocator.get()
    val settings = container.settingsDataStore
    var weights by remember { mutableStateOf(HUB_DEFAULT_WEIGHTS) }
    var message by remember { mutableStateOf<String?>(null) }
    var saveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun save(category: String, weight: Int) {
        val updated = weights + (category to weight.coerceIn(0, 5))
        weights = updated
        saveJob?.cancel()
        saveJob = scope.launch {
            settings.setCategoryWeights(updated)
            delay(350)
            runCatching { container.reminderRepository.updateCategoryWeights(updated) }
                .onSuccess { remote ->
                    weights = remote.asMap()
                    settings.setCategoryWeights(weights)
                    message = "已同步，只影响未来候选"
                }
                .onFailure { message = "已保存在本机，服务端同步失败" }
        }
    }

    LaunchedEffect(Unit) {
        weights = settings.categoryWeightsOnce()
        runCatching { container.reminderRepository.categoryWeights() }.onSuccess { remote ->
            weights = remote.asMap()
            settings.setCategoryWeights(weights)
        }
    }

    SettingsPageFrame(title = "提醒偏好", onBack = onBack) {
        item {
            Text(
                "统一规则先判断是否为明确事项，星级只控制候选排序；0 星不生成该类别的新候选。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HUB_CATEGORY_OPTIONS.forEach { option ->
            item(key = option.first) {
                val weight = weights[option.first] ?: 3
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(option.second, fontWeight = FontWeight.Medium)
                        Text(
                            if (weight == 0) "已关闭" else "$weight 星",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (weight == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    (1..5).forEach { star ->
                        LiquidIconButton(
                            modifier = Modifier.size(36.dp),
                            onClick = { save(option.first, if (star == 1 && weight == 1) 0 else star) },
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "${option.second} $star 星",
                                modifier = Modifier.size(22.dp),
                                tint = if (star <= weight) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
        message?.let { value ->
            item { Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun DataSettingsPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val container = ServiceLocator.get()
    val settings = container.settingsDataStore
    var baseUrl by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { baseUrl = settings.baseUrlOnce() }

    SettingsPageFrame(title = "服务与数据", onBack = onBack) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("服务端", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                LiquidTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; message = null },
                    placeholder = "服务端地址",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AdaptiveLiquidButton(
                    onClick = {
                        scope.launch {
                            val previous = settings.baseUrlOnce()
                            settings.setBaseUrl(baseUrl)
                            if (previous.trimEnd('/') != baseUrl.trim().trimEnd('/')) {
                                container.tokenStore.clear()
                                ReminderSyncWorker.enqueueNow(container.appContext)
                            }
                            message = "已保存"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存地址") }
            }
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("删除数据", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "删除服务端通知、提醒和设备身份，同时清空本地队列。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AdaptiveLiquidButton(
                    onClick = {
                        if (!confirmDelete) {
                            confirmDelete = true
                            message = "请再次点击确认"
                        } else {
                            scope.launch {
                                runCatching { container.reminderRepository.deleteAllData() }
                                    .onSuccess {
                                        confirmDelete = false
                                        message = "全部数据已删除"
                                    }
                                    .onFailure { message = "删除失败，请检查网络" }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = GlassButtonStyle.DANGER,
                ) { Text(if (confirmDelete) "确认永久删除" else "删除所有数据") }
            }
        }
        message?.let { value ->
            item { Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private val HUB_CATEGORY_OPTIONS = listOf(
    "work" to "工作",
    "life" to "生活",
    "finance" to "财务",
    "health" to "健康",
    "social" to "社交",
    "entertainment" to "娱乐",
    "other" to "其他",
)

private val HUB_DEFAULT_WEIGHTS = HUB_CATEGORY_OPTIONS.associate { it.first to 3 }
