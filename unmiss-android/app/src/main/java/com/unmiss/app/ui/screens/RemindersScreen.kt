package com.unmiss.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.unmiss.app.data.ServiceLocator
import com.unmiss.app.data.db.LocalReminder
import com.unmiss.app.reminder.ReminderDisplayWorker
import com.unmiss.app.ui.theme.AdaptiveGlassSurface
import com.unmiss.app.ui.theme.AdaptiveLiquidButton
import com.unmiss.app.ui.theme.GlassButtonStyle
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen() {
    val repository = ServiceLocator.get().reminderRepository
    val reminders by repository.pending.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var syncing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            error = null
            runCatching { action() }.onFailure { error = "操作失败，请稍后重试" }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            AdaptiveGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    bottomStart = 24.dp,
                    bottomEnd = 24.dp,
                ),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
            ) {
                TopAppBar(
                    title = { Text("遗漏事项") },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("可能遗漏 ${reminders.count { it.status == "candidate" }} 项", style = MaterialTheme.typography.titleMedium)
                AdaptiveLiquidButton(
                    enabled = !syncing,
                    style = GlassButtonStyle.SECONDARY,
                    onClick = {
                        scope.launch {
                            syncing = true
                            error = null
                            runCatching { repository.sync() }
                                .onFailure { error = "同步失败，请检查服务端地址和网络" }
                            syncing = false
                        }
                    },
                ) { Text(if (syncing) "同步中" else "刷新") }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (reminders.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("目前没有可能遗漏的事项", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    QUADRANTS.forEach { quadrant ->
                        val group = reminders.filter { it.quadrant == quadrant.id }
                        if (group.isNotEmpty()) {
                            item(key = "header-${quadrant.id}") {
                                Text(
                                    quadrant.label,
                                    modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = quadrant.color,
                                )
                            }
                            items(group, key = { it.id }) { reminder ->
                                ReminderItem(
                                    reminder = reminder,
                                    quadrant = quadrant,
                                    onDone = { runAction { repository.done(reminder.id) } },
                                    onConfirm = {
                                        runAction {
                                            repository.confirm(reminder.id, Instant.now().plusSeconds(3600))
                                            ReminderDisplayWorker.schedule(context, reminder.id, 3_600_000)
                                        }
                                    },
                                    onSnooze = {
                                        runAction {
                                            repository.snooze(reminder.id, Instant.now().plusSeconds(3600))
                                            ReminderDisplayWorker.schedule(context, reminder.id, 3_600_000)
                                        }
                                    },
                                    onIgnore = { runAction { repository.ignore(reminder.id) } },
                                )
                                HorizontalDivider(color = quadrant.color.copy(alpha = 0.18f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderItem(
    reminder: LocalReminder,
    quadrant: QuadrantStyle,
    onDone: () -> Unit,
    onConfirm: () -> Unit,
    onSnooze: () -> Unit,
    onIgnore: () -> Unit,
) {
    AdaptiveGlassSurface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = quadrant.color.copy(alpha = 0.075f),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                reminder.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                quadrant.shortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = quadrant.color,
            )
        }
        reminder.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (reminder.status == "candidate") {
            Text(
                "归纳发现 · 等待你确认",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            formatTime(reminder.remindAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (reminder.status == "candidate") {
                AdaptiveLiquidButton(onClick = onConfirm) { Text("提醒我") }
                AdaptiveLiquidButton(onClick = onDone, style = GlassButtonStyle.SECONDARY) { Text("已处理") }
            } else {
                AdaptiveLiquidButton(onClick = onDone) { Text("已完成") }
                AdaptiveLiquidButton(onClick = onSnooze, style = GlassButtonStyle.SECONDARY) { Text("一小时后") }
            }
            TextButton(onClick = onIgnore) { Text("忽略") }
        }
    }
    }
}

private fun formatTime(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(value))
}.getOrDefault(value)

private data class QuadrantStyle(
    val id: String,
    val label: String,
    val shortLabel: String,
    val color: Color,
)

private val QUADRANTS = listOf(
    QuadrantStyle("important_urgent", "重要且紧急", "立即关注", Color(0xFFC94F58)),
    QuadrantStyle("important_not_urgent", "重要但不紧急", "重点安排", Color(0xFF3D6FA8)),
    QuadrantStyle("not_important_urgent", "紧急但不重要", "快速处理", Color(0xFFB97824)),
    QuadrantStyle("not_important_not_urgent", "不重要且不紧急", "低优先级", Color(0xFF6D7480)),
)
