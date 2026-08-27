package com.unmiss.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unmiss.app.data.ServiceLocator
import com.unmiss.app.ui.theme.LiquidChip
import com.unmiss.app.ui.theme.LiquidIconButton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private enum class StatisticsPeriod(val label: String) {
    TODAY("今天"), MONTH("本月"), YEAR("今年")
}

@Composable
fun StatisticsScreen(onBack: () -> Unit) {
    val container = ServiceLocator.get()
    val reminders by container.reminderRepository.all.collectAsState(initial = emptyList())
    var period by remember { mutableStateOf(StatisticsPeriod.TODAY) }
    val bounds = remember(period) { statisticsBounds(period) }
    val captured by container.pendingDao.observeHistoryCount(
        fromTime = bounds.first,
        toTime = bounds.second,
        packageName = null,
        uploadState = "all",
        keyword = "",
    ).collectAsState(initial = 0)
    val created = reminders.filter { inPeriod(it.createdAt, bounds) }
    val completed = reminders.count { inPeriod(it.completedAt, bounds) }
    val ignored = reminders.count { it.status == "ignored" && inPeriod(it.updatedAt, bounds) }
    val retained = created.count { it.status != "ignored" }
    val active = reminders.count { it.status == "candidate" || it.status == "pending" }
    val categories = created.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiquidIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column {
                    Text("统计", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("收录与提醒概览", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { PeriodSelector(period = period, onChange = { period = it }) }
        item { StatisticsLead(captured = captured, generated = created.size, period = period) }
        item {
            MetricSection(
                title = "提醒结果",
                metrics = listOf(
                    "生成提醒" to created.size,
                    "已保留" to retained,
                    "已完成" to completed,
                    "已筛除" to ignored,
                ),
            )
        }
        item {
            val rate = if (captured == 0) 0 else (created.size * 100 / captured)
            MetricSection(
                title = "当前状态",
                metrics = listOf("活跃提醒" to active, "提醒产生率" to rate),
                percentLast = true,
            )
        }
        if (categories.isNotEmpty()) {
            item { StatisticsCategories(categories) }
        }
        item {
            Text(
                "统计基于本机已保留的通知收录和已同步提醒；清理过的历史不会计入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun PeriodSelector(period: StatisticsPeriod, onChange: (StatisticsPeriod) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatisticsPeriod.entries.forEach { item ->
            LiquidChip(selected = period == item, onClick = { onChange(item) }) {
                Text(
                    item.label,
                    fontWeight = if (period == item) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun StatisticsLead(captured: Int, generated: Int, period: StatisticsPeriod) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${period.label}收录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedContent(targetState = captured, label = "captured total") { value ->
            Text(value.toString(), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
        }
        Text("条通知 · 生成 $generated 个提醒", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun MetricSection(title: String, metrics: List<Pair<String, Int>>, percentLast: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth()) {
            metrics.forEachIndexed { index, metric ->
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedContent(targetState = metric.second, label = metric.first) { value ->
                        Text(
                            if (percentLast && index == metrics.lastIndex) "$value%" else value.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(metric.first, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}

@Composable
private fun StatisticsCategories(categories: List<Map.Entry<String, Int>>) {
    val maximum = categories.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Text("提醒类别", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        categories.take(6).forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = statisticsCategoryColor(entry.key)) {}
                Text(statisticsCategoryLabel(entry.key), modifier = Modifier.weight(0.28f))
                Box(modifier = Modifier.weight(0.58f).height(7.dp), contentAlignment = Alignment.CenterStart) {
                    Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) {}
                    Surface(
                        modifier = Modifier.fillMaxWidth(entry.value.toFloat() / maximum).height(7.dp),
                        shape = CircleShape,
                        color = statisticsCategoryColor(entry.key),
                    ) {}
                }
                Text(entry.value.toString(), modifier = Modifier.weight(0.14f), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun statisticsBounds(period: StatisticsPeriod): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val start = when (period) {
        StatisticsPeriod.TODAY -> today
        StatisticsPeriod.MONTH -> today.withDayOfMonth(1)
        StatisticsPeriod.YEAR -> today.withDayOfYear(1)
    }.atStartOfDay(zone).toInstant().toEpochMilli()
    return start to System.currentTimeMillis()
}

private fun inPeriod(value: String?, bounds: Pair<Long, Long>): Boolean = value?.let {
    runCatching { Instant.parse(it).toEpochMilli() in bounds.first..bounds.second }.getOrDefault(false)
} ?: false

private fun statisticsCategoryLabel(category: String): String = when (category) {
    "work" -> "工作"
    "life" -> "生活"
    "finance" -> "财务"
    "health" -> "健康"
    "social" -> "社交"
    "entertainment" -> "娱乐"
    else -> "其他"
}

private fun statisticsCategoryColor(category: String): Color = when (category) {
    "work" -> Color(0xFF3D6FA8)
    "life" -> Color(0xFF2F8A65)
    "finance" -> Color(0xFFB97824)
    "health" -> Color(0xFFC94F58)
    "social" -> Color(0xFF7D64A8)
    "entertainment" -> Color(0xFF4E8791)
    else -> Color(0xFF6D7480)
}
