package com.unmiss.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unmiss.app.data.ServiceLocator
import com.unmiss.app.reminder.ReminderSyncWorker
import com.unmiss.app.ui.theme.AdaptiveGlassSurface
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings = ServiceLocator.get().settingsDataStore
    var baseUrl by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var captureEnabled by remember { mutableStateOf(true) }
    var liquidGlassEnabled by remember { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteMessage by remember { mutableStateOf<String?>(null) }
    var analysisTimes by remember { mutableStateOf(listOf("22:00")) }
    var scheduleMessage by remember { mutableStateOf<String?>(null) }
    var scheduleSaving by remember { mutableStateOf(false) }

    fun saveAnalysisTimes(updated: List<String>) {
        val normalized = updated.distinct().sorted()
        if (normalized.isEmpty()) return
        analysisTimes = normalized
        scheduleSaving = true
        scheduleMessage = null
        scope.launch {
            settings.setAnalysisTimes(normalized.toSet())
            runCatching {
                ServiceLocator.get().notificationRepository
                    .updateAnalysisSchedule(normalized.toSet())
            }.onSuccess {
                scheduleMessage = "已同步，下一次将在 ${nextAnalysisTime(normalized)} 归纳"
            }.onFailure {
                scheduleMessage = "保存在本机，但同步失败；请检查网络"
            }
            scheduleSaving = false
        }
    }

    fun showTimePicker(current: String? = null) {
        val initial = current?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()
        } ?: LocalTime.now().plusHours(1)
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val value = "%02d:%02d".format(hour, minute)
                saveAnalysisTimes(
                    if (current == null) analysisTimes + value
                    else analysisTimes.map { if (it == current) value else it },
                )
            },
            initial.hour,
            initial.minute,
            true,
        ).show()
    }

    LaunchedEffect(Unit) {
        baseUrl = settings.baseUrlOnce()
        captureEnabled = settings.captureEnabledOnce()
        liquidGlassEnabled = settings.liquidGlassEnabledOnce()
        analysisTimes = settings.analysisTimesOnce().sorted()
        runCatching { ServiceLocator.get().notificationRepository.analysisSchedule() }
            .onSuccess { remote ->
                analysisTimes = remote.times.sorted()
                settings.setAnalysisTimes(remote.times.toSet())
            }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            AdaptiveGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
            ) {
                TopAppBar(
                    title = { Text("设置") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AdaptiveGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.86f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("通知采集", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (captureEnabled) "正在监听已允许应用的通知" else "已暂停，不再采集新通知",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = captureEnabled,
                        onCheckedChange = { enabled ->
                            captureEnabled = enabled
                            scope.launch { settings.setCaptureEnabled(enabled) }
                        },
                    )
                }
            }

            AdaptiveGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.86f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("液态玻璃", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (liquidGlassEnabled) "全局折射、模糊与材质层已开启" else "使用原来的浅色半透明样式",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = liquidGlassEnabled,
                        onCheckedChange = { enabled ->
                            liquidGlassEnabled = enabled
                            scope.launch { settings.setLiquidGlassEnabled(enabled) }
                        },
                    )
                }
            }

            AdaptiveGlassSurface(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.86f),
            ) {
                Column(modifier = Modifier.padding(vertical = 18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "每日归纳",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "每个时刻整理上一时段；点击时间可修改",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(enabled = !scheduleSaving, onClick = { showTimePicker() }) {
                            Icon(Icons.Default.Add, contentDescription = "添加归纳时间")
                        }
                    }
                    analysisTimes.forEachIndexed { index, time ->
                        if (index == 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 14.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 14.dp)
                                    .clickable(enabled = !scheduleSaving) {
                                        showTimePicker(time)
                                    },
                            ) {
                                Text(
                                    time,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    analysisWindowLabel(analysisTimes, index),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                enabled = analysisTimes.size > 1 && !scheduleSaving,
                                onClick = { saveAnalysisTimes(analysisTimes - time) },
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "删除 $time")
                            }
                        }
                    }
                    scheduleMessage?.let {
                        Text(
                            it,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            AdaptiveGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.86f)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("服务端", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it; saved = false },
                        label = { Text("服务端地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        ),
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                val previousUrl = settings.baseUrlOnce()
                                settings.setBaseUrl(baseUrl)
                                if (previousUrl.trimEnd('/') != baseUrl.trim().trimEnd('/')) {
                                    ServiceLocator.get().tokenStore.clear()
                                    ReminderSyncWorker.enqueueNow(ServiceLocator.get().appContext)
                                }
                                saved = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("保存") }
                    if (saved) Text("已保存", color = MaterialTheme.colorScheme.tertiary)
                }
            }

            AdaptiveGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.86f)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("数据与隐私", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "删除服务端保存的通知、提醒和设备身份，同时清空本地队列。此操作不可撤销。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            if (!confirmDelete) {
                                confirmDelete = true
                                deleteMessage = "请再次点击确认删除"
                            } else {
                                scope.launch {
                                    runCatching { ServiceLocator.get().reminderRepository.deleteAllData() }
                                        .onSuccess {
                                            confirmDelete = false
                                            deleteMessage = "全部数据已删除；下次同步会注册新设备"
                                        }
                                        .onFailure { deleteMessage = "删除失败，请检查网络后重试" }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (confirmDelete) "确认永久删除" else "删除所有数据") }
                    deleteMessage?.let {
                        Text(
                            it,
                            color = if (confirmDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

private fun nextAnalysisTime(times: List<String>): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val now = LocalTime.now()
    val parsed = times.mapNotNull { value ->
        runCatching { LocalTime.parse(value, formatter) }.getOrNull()
    }.sorted()
    val today = parsed.firstOrNull { it.isAfter(now) }
    return if (today != null) today.format(formatter) else "明天 ${parsed.first().format(formatter)}"
}

private fun analysisWindowLabel(times: List<String>, index: Int): String {
    if (times.size == 1) return "过去 24 小时"
    val previous = if (index == 0) times.last() else times[index - 1]
    val current = times[index]
    val overnight = if (previous > current) " · 跨夜" else ""
    return "$previous → $current$overnight"
}
