package com.unmiss.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.unmiss.app.data.remote.AnalysisRunDto
import com.unmiss.app.data.remote.QualityStatsDto
import com.unmiss.app.reminder.ReminderDisplayWorker
import com.unmiss.app.ui.theme.AdaptiveGlassSurface
import com.unmiss.app.ui.theme.AdaptiveLiquidButton
import com.unmiss.app.ui.theme.GlassButtonStyle
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class ReminderView { ITEMS, DIGESTS, QUALITY }
private enum class ReminderStatusFilter(val label: String) {
    ACTIVE("进行中"), DONE("已完成"), IGNORED("已忽略"), ALL("全部")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen() {
    val repository = ServiceLocator.get().reminderRepository
    val reminders by repository.all.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var syncing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var view by remember { mutableStateOf(ReminderView.ITEMS) }
    var statusFilter by remember { mutableStateOf(ReminderStatusFilter.ACTIVE) }
    var quadrantFilter by remember { mutableStateOf<String?>(null) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var analysisRuns by remember { mutableStateOf<List<AnalysisRunDto>>(emptyList()) }
    var qualityStats by remember { mutableStateOf<QualityStatsDto?>(null) }
    var categoryWeights by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    suspend fun refresh() {
        repository.sync()
        analysisRuns = repository.analysisRuns()
        qualityStats = repository.qualityStats()
        categoryWeights = repository.categoryWeights().asMap()
    }

    LaunchedEffect(Unit) {
        runCatching { refresh() }.onFailure { error = "同步失败，请检查网络" }
    }

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
            ReminderToolbar(
                view = view,
                syncing = syncing,
                onViewChange = { view = it },
                onRefresh = {
                    scope.launch {
                        syncing = true
                        error = null
                        runCatching { refresh() }
                            .onFailure { error = "同步失败，请检查服务端地址和网络" }
                        syncing = false
                    }
                },
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }
            when (view) {
                ReminderView.DIGESTS -> AnalysisRunList(analysisRuns)
                ReminderView.QUALITY -> QualityReport(qualityStats)
                ReminderView.ITEMS -> ReminderHistory(
                    reminders = reminders,
                    query = query,
                    statusFilter = statusFilter,
                    quadrantFilter = quadrantFilter,
                    categoryFilter = categoryFilter,
                    categoryWeights = categoryWeights,
                    onQueryChange = { query = it },
                    onStatusChange = { statusFilter = it },
                    onQuadrantChange = { quadrantFilter = it },
                    onCategoryChange = { categoryFilter = it },
                    onDone = { reminder -> runAction { repository.done(reminder.id) } },
                    onConfirm = { reminder ->
                        runAction {
                            repository.confirm(reminder.id, Instant.now().plusSeconds(3600))
                            ReminderDisplayWorker.schedule(context, reminder.id, 3_600_000)
                        }
                    },
                    onSnooze = { reminder ->
                        runAction {
                            repository.snooze(reminder.id, Instant.now().plusSeconds(3600))
                            ReminderDisplayWorker.schedule(context, reminder.id, 3_600_000)
                        }
                    },
                    onIgnore = { reminder -> runAction { repository.ignore(reminder.id) } },
                )
            }
        }
    }
}

@Composable
private fun ReminderToolbar(
    view: ReminderView,
    syncing: Boolean,
    onViewChange: (ReminderView) -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = view == ReminderView.ITEMS,
                onClick = { onViewChange(ReminderView.ITEMS) },
                label = { Text("事项") },
            )
            FilterChip(
                selected = view == ReminderView.DIGESTS,
                onClick = { onViewChange(ReminderView.DIGESTS) },
                label = { Text("归纳记录") },
            )
            FilterChip(
                selected = view == ReminderView.QUALITY,
                onClick = { onViewChange(ReminderView.QUALITY) },
                label = { Text("统计") },
            )
        }
        Spacer(Modifier.width(8.dp))
        AdaptiveLiquidButton(
            enabled = !syncing,
            style = GlassButtonStyle.SECONDARY,
            onClick = onRefresh,
        ) { Text(if (syncing) "同步中" else "刷新") }
    }
}

@Composable
private fun ReminderHistory(
    reminders: List<LocalReminder>,
    query: String,
    statusFilter: ReminderStatusFilter,
    quadrantFilter: String?,
    categoryFilter: String?,
    categoryWeights: Map<String, Int>,
    onQueryChange: (String) -> Unit,
    onStatusChange: (ReminderStatusFilter) -> Unit,
    onQuadrantChange: (String?) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onDone: (LocalReminder) -> Unit,
    onConfirm: (LocalReminder) -> Unit,
    onSnooze: (LocalReminder) -> Unit,
    onIgnore: (LocalReminder) -> Unit,
) {
    val filtered = remember(reminders, query, statusFilter, quadrantFilter, categoryFilter, categoryWeights) {
        reminders.filter { reminder ->
            val statusMatches = when (statusFilter) {
                ReminderStatusFilter.ACTIVE -> reminder.status == "candidate" || reminder.status == "pending"
                ReminderStatusFilter.DONE -> reminder.status == "done"
                ReminderStatusFilter.IGNORED -> reminder.status == "ignored"
                ReminderStatusFilter.ALL -> true
            }
            val queryMatches = query.isBlank() || listOf(
                reminder.title,
                reminder.description.orEmpty(),
                reminder.reason.orEmpty(),
            ).any { it.contains(query, ignoreCase = true) }
            statusMatches && queryMatches &&
                (quadrantFilter == null || reminder.quadrant == quadrantFilter) &&
                (categoryFilter == null || reminder.category == categoryFilter)
        }.sortedByDescending { reminder -> categoryWeights[reminder.category] ?: 3 }
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        placeholder = { Text("搜索提醒标题、内容或归纳原因") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReminderStatusFilter.entries.forEach { filter ->
            FilterChip(
                selected = statusFilter == filter,
                onClick = { onStatusChange(filter) },
                label = { Text(filter.label) },
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = categoryFilter == null,
            onClick = { onCategoryChange(null) },
            label = { Text("全部类别") },
        )
        REMINDER_CATEGORIES.forEach { category ->
            FilterChip(
                selected = categoryFilter == category.first,
                onClick = { onCategoryChange(category.first) },
                label = { Text(category.second) },
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = quadrantFilter == null,
            onClick = { onQuadrantChange(null) },
            label = { Text("全部象限") },
        )
        QUADRANTS.forEach { quadrant ->
            FilterChip(
                selected = quadrantFilter == quadrant.id,
                onClick = { onQuadrantChange(quadrant.id) },
                label = { Text(quadrant.shortLabel) },
            )
        }
    }

    if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("没有符合筛选条件的提醒", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 116.dp),
        ) {
            QUADRANTS.forEach { quadrant ->
                val group = filtered.filter { it.quadrant == quadrant.id }
                if (group.isNotEmpty()) {
                    item(key = "header-${quadrant.id}") {
                        Text(
                            quadrant.label,
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = quadrant.color,
                        )
                    }
                    items(group, key = { it.id }) { reminder ->
                        ReminderItem(
                            modifier = Modifier.padding(bottom = 14.dp),
                            reminder = reminder,
                            quadrant = quadrant,
                            onDone = { onDone(reminder) },
                            onConfirm = { onConfirm(reminder) },
                            onSnooze = { onSnooze(reminder) },
                            onIgnore = { onIgnore(reminder) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityReport(stats: QualityStatsDto?) {
    if (stats == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("正在生成质量报告…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val context = LocalContext.current
    val maxQuadrant = stats.quadrants.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "最近 ${stats.periodDays} 天",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Text(
                    if (stats.reminders.evaluated == 0) "还没有已评价样本，不展示任何通知正文"
                    else "基于 ${stats.reminders.evaluated} 个已评价提醒，不展示任何通知正文",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QualityMetric(
                    modifier = Modifier.weight(1f),
                    value = formatRate(stats.reminders.usefulRate),
                    label = "保留率",
                    note = "确认或完成",
                    color = Color(0xFF2F8A65),
                )
                QualityMetric(
                    modifier = Modifier.weight(1f),
                    value = formatRate(stats.reminders.ignoreRate),
                    label = "筛除率",
                    note = "用户最终判断",
                    color = Color(0xFFC06B43),
                )
            }
        }
        item {
            AdaptiveGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.30f),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("提醒结果", style = MaterialTheme.typography.titleMedium)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        CompactCount("生成", stats.reminders.created, Modifier.weight(1f))
                        CompactCount("确认", stats.reminders.confirmed, Modifier.weight(1f))
                        CompactCount("完成", stats.reminders.completed, Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        CompactCount("忽略", stats.reminders.ignored, Modifier.weight(1f))
                        CompactCount("延后", stats.reminders.snoozed, Modifier.weight(1f))
                        CompactCount("待处理", stats.reminders.active, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            AdaptiveGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = 0.30f),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("归纳健康", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "分析 ${stats.analysis.notificationsAnalyzed} 条通知 · " +
                            "成功 ${stats.analysis.successfulRuns} 次 · 失败 ${stats.analysis.failedRuns} 次",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val successRate = if (stats.analysis.runs == 0) null
                    else stats.analysis.successfulRuns.toDouble() / stats.analysis.runs
                    Text(
                        "运行成功率 ${formatRate(successRate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (stats.quadrants.isNotEmpty()) {
            item {
                AdaptiveGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = 0.30f),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("优先级分布", style = MaterialTheme.typography.titleMedium)
                        stats.quadrants.forEach { item ->
                            val style = QUADRANTS.firstOrNull { it.id == item.quadrant }
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(style?.label ?: item.quadrant, style = MaterialTheme.typography.bodySmall)
                                    Text(item.count.toString(), style = MaterialTheme.typography.labelMedium)
                                }
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(7.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(item.count.toFloat() / maxQuadrant)
                                            .height(7.dp)
                                            .background(style?.color ?: MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (stats.packages.isNotEmpty()) {
            item {
                AdaptiveGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = 0.30f),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("来源应用", style = MaterialTheme.typography.titleMedium)
                        stats.packages.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(appLabel(context, item.packageName), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        item.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    "${item.created} 项 · ${item.completed} 完成 · ${item.ignored} 忽略",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityMetric(
    modifier: Modifier,
    value: String,
    label: String,
    note: String,
    color: Color,
) {
    AdaptiveGlassSurface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.30f),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompactCount(label: String, value: Int, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatRate(rate: Double?): String = rate?.let { "%.0f%%".format(it * 100) } ?: "—"

private fun appLabel(context: android.content.Context, packageName: String): String = runCatching {
    val info = context.packageManager.getApplicationInfo(packageName, 0)
    context.packageManager.getApplicationLabel(info).toString()
}.getOrDefault(packageName.substringAfterLast('.'))

@Composable
private fun AnalysisRunList(runs: List<AnalysisRunDto>) {
    if (runs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("还没有归纳记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(runs, key = { it.id }) { run ->
            val statusColor = when (run.status) {
                "success" -> Color(0xFF2F8A65)
                "failed" -> MaterialTheme.colorScheme.error
                else -> Color(0xFFB97824)
            }
            val statusLabel = when (run.status) {
                "success" -> "归纳成功"
                "failed" -> "归纳失败"
                else -> "归纳中"
            }
            AdaptiveGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.30f),
            ) {
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp)
                            .background(statusColor, androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
                    )
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(statusLabel, style = MaterialTheme.typography.titleMedium, color = statusColor)
                            Text(formatTime(run.startedAt), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "分析 ${run.notificationCount} 条通知 · 新建 ${run.reminderCount} 项 · 更新 ${run.updateCount} 项",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        run.result?.reminders?.take(3)?.forEach { reminder ->
                            Text(
                                "新增 · ${reminder.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        run.result?.updates?.take(3)?.forEach { update ->
                            val action = when (update.action) {
                                "complete" -> "完成"
                                "ignore" -> "忽略"
                                else -> "变更"
                            }
                            Text(
                                "$action · ${update.title ?: update.reason.orEmpty()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        run.error?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderItem(
    modifier: Modifier = Modifier,
    reminder: LocalReminder,
    quadrant: QuadrantStyle,
    onDone: () -> Unit,
    onConfirm: () -> Unit,
    onSnooze: () -> Unit,
    onIgnore: () -> Unit,
) {
    AdaptiveGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.30f),
    ) {
      Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .background(quadrant.color, androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
        )
        Column(
          modifier = Modifier.padding(start = 14.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
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
                "${categoryLabel(reminder.category)} · ${quadrant.shortLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = quadrant.color,
            )
        }
        reminder.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        reminder.reason?.takeIf { it.isNotBlank() }?.let {
            Text(
                "判断依据 · $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            when (reminder.status) {
                "candidate" -> "归纳发现 · 等待你确认"
                "pending" -> "已确认 · 等待提醒"
                "done" -> "已完成"
                "ignored" -> "已忽略"
                else -> reminder.status
            },
            style = MaterialTheme.typography.labelMedium,
            color = when (reminder.status) {
                "done" -> Color(0xFF2F8A65)
                "ignored" -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.primary
            },
        )
        Text(
            formatTime(reminder.remindAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (reminder.status == "candidate" || reminder.status == "pending") {
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
    }
}

private fun formatTime(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(value))
}.getOrDefault(value)

private fun categoryLabel(category: String): String =
    REMINDER_CATEGORIES.firstOrNull { it.first == category }?.second ?: "其他"

private val REMINDER_CATEGORIES = listOf(
    "work" to "工作",
    "life" to "生活",
    "finance" to "财务",
    "health" to "健康",
    "social" to "社交",
    "entertainment" to "娱乐",
    "other" to "其他",
)

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
