package com.unmiss.app.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unmiss.app.data.AppCatalog
import com.unmiss.app.data.InstalledApp
import com.unmiss.app.data.ServiceLocator
import com.unmiss.app.data.db.PendingNotificationUpload
import com.unmiss.app.ui.components.InstalledAppIcon
import com.unmiss.app.ui.components.LiquidFilterSheet
import com.unmiss.app.ui.theme.LiquidButton
import com.unmiss.app.ui.theme.GlassButtonStyle
import com.unmiss.app.ui.theme.LiquidChip
import com.unmiss.app.ui.theme.LiquidIconButton
import com.unmiss.app.ui.theme.LiquidPopover
import com.unmiss.app.ui.theme.LiquidTextField
import com.unmiss.app.ui.theme.AdaptiveGlassSurface
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

private const val HISTORY_PAGE_SIZE = 50

private enum class HistoryRange(val label: String) {
    ALL("全部时间"), TODAY("今天"), WEEK("近 7 天"), CUSTOM("自定义")
}

private enum class UploadState(val value: String, val label: String) {
    ALL("all", "全部状态"), UPLOADED("uploaded", "已上传"), PENDING("pending", "待上传")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen() {
    val context = LocalContext.current
    val dao = ServiceLocator.get().pendingDao
    var keyword by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var range by rememberSaveable { mutableStateOf(HistoryRange.ALL) }
    var uploadState by rememberSaveable { mutableStateOf(UploadState.ALL) }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var customFrom by rememberSaveable { mutableStateOf<Long?>(null) }
    var customTo by rememberSaveable { mutableStateOf<Long?>(null) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var visibleLimit by rememberSaveable { mutableIntStateOf(HISTORY_PAGE_SIZE) }

    LaunchedEffect(Unit) { apps = AppCatalog(context).loadUserVisibleApps() }
    val capturedPackages by dao.observePackages().collectAsState(initial = emptyList())
    val capturedApps = remember(apps, capturedPackages) {
        capturedPackages.map { packageName ->
            apps.firstOrNull { it.packageName == packageName } ?: InstalledApp(packageName, packageName)
        }.sortedBy { it.displayName.lowercase() }
    }
    val bounds = remember(range, customFrom, customTo) { historyBounds(range, customFrom, customTo) }
    val cleanKeyword = keyword.trim()

    LaunchedEffect(bounds, selectedPackage, uploadState, cleanKeyword) {
        visibleLimit = HISTORY_PAGE_SIZE
    }
    val historyFlow = remember(bounds, selectedPackage, uploadState, cleanKeyword, visibleLimit) {
        dao.observeHistory(
            bounds.first, bounds.second, selectedPackage, uploadState.value, cleanKeyword, visibleLimit,
        )
    }
    val countFlow = remember(bounds, selectedPackage, uploadState, cleanKeyword) {
        dao.observeHistoryCount(bounds.first, bounds.second, selectedPackage, uploadState.value, cleanKeyword)
    }
    val entries by historyFlow.collectAsState(initial = emptyList())
    val total by countFlow.collectAsState(initial = 0)
    val appNames = remember(capturedApps) { capturedApps.associate { it.packageName to it.displayName } }
    val activeFilterCount = listOf(
        range != HistoryRange.ALL,
        uploadState != UploadState.ALL,
        selectedPackage != null,
    ).count { it }

    Scaffold(containerColor = Color.Transparent) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 116.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("收录", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text("本机通知档案", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("$total 条", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            stickyHeader {
                HistoryCommandBar(
                    keyword = keyword,
                    searching = searching,
                    activeFilterCount = activeFilterCount,
                    summary = filterSummary(range, uploadState, selectedPackage, capturedApps),
                    onKeywordChange = { keyword = it },
                    onToggleSearch = { searching = !searching; if (!searching) keyword = "" },
                    onOpenFilters = { showFilters = true },
                )
            }
            if (entries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("没有匹配的通知", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(entries, key = { it.id }) { entry ->
                    NotificationHistoryRow(entry, appNames[entry.packageName] ?: entry.packageName)
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                }
                if (entries.size < total) {
                    item {
                        LiquidButton(
                            onClick = { visibleLimit += HISTORY_PAGE_SIZE },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            style = GlassButtonStyle.SECONDARY,
                        ) { Text("继续加载 · ${total - entries.size} 条") }
                    }
                }
            }
        }
    }

    if (showFilters) {
        HistoryFilterSheet(
            context = context,
            range = range,
            uploadState = uploadState,
            selectedPackage = selectedPackage,
            apps = capturedApps,
            customFrom = customFrom,
            customTo = customTo,
            onRangeChange = { range = it },
            onUploadStateChange = { uploadState = it },
            onPackageChange = { selectedPackage = it },
            onCustomFromChange = { customFrom = it; range = HistoryRange.CUSTOM },
            onCustomToChange = { customTo = it; range = HistoryRange.CUSTOM },
            onClear = {
                range = HistoryRange.ALL
                uploadState = UploadState.ALL
                selectedPackage = null
                customFrom = null
                customTo = null
            },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun HistoryCommandBar(
    keyword: String,
    searching: Boolean,
    activeFilterCount: Int,
    summary: String,
    onKeywordChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onOpenFilters: () -> Unit,
) {
    AdaptiveGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
    ) {
        AnimatedContent(targetState = searching, label = "history search") { isSearching ->
            if (isSearching) {
                LiquidTextField(
                    value = keyword,
                    onValueChange = onKeywordChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    placeholder = "搜索标题或正文",
                    leading = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailing = {
                        LiquidIconButton(onClick = onToggleSearch) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭搜索")
                        }
                    },
                    singleLine = true,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(summary, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    LiquidIconButton(onClick = onToggleSearch) { Icon(Icons.Filled.Search, contentDescription = "搜索") }
                    Box {
                        LiquidIconButton(onClick = onOpenFilters) { Icon(Icons.Filled.FilterList, contentDescription = "筛选") }
                        if (activeFilterCount > 0) {
                            Text(
                                activeFilterCount.toString(),
                                modifier = Modifier.align(Alignment.TopEnd)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(9.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryFilterSheet(
    context: Context,
    range: HistoryRange,
    uploadState: UploadState,
    selectedPackage: String?,
    apps: List<InstalledApp>,
    customFrom: Long?,
    customTo: Long?,
    onRangeChange: (HistoryRange) -> Unit,
    onUploadStateChange: (UploadState) -> Unit,
    onPackageChange: (String?) -> Unit,
    onCustomFromChange: (Long) -> Unit,
    onCustomToChange: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    LiquidFilterSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("筛选收录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                LiquidButton(onClick = onClear, style = GlassButtonStyle.SECONDARY) { Text("重置") }
            }
            FilterSection("时间") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HistoryRange.entries.take(3).forEach { item ->
                        LiquidChip(
                            selected = range == item,
                            onClick = { onRangeChange(item) },
                            label = { Text(item.label) },
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    LiquidButton(
                        onClick = {
                            pickDateTime(context, customFrom ?: System.currentTimeMillis(), onPicked = onCustomFromChange)
                        },
                        modifier = Modifier.weight(1f),
                        style = GlassButtonStyle.SECONDARY,
                    ) { Text(customFrom?.let(::formatEntryTime) ?: "起始时间") }
                    LiquidButton(
                        onClick = {
                            pickDateTime(
                                context,
                                customTo ?: System.currentTimeMillis(),
                                endOfMinute = true,
                                onPicked = onCustomToChange,
                            )
                        },
                        modifier = Modifier.weight(1f),
                        style = GlassButtonStyle.SECONDARY,
                    ) { Text(customTo?.let(::formatEntryTime) ?: "结束时间") }
                }
            }
            FilterSection("上传状态") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UploadState.entries.forEach { item ->
                        LiquidChip(
                            selected = uploadState == item,
                            onClick = { onUploadStateChange(item) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
            FilterSection("来源应用") {
                AppFilter(selectedPackage, apps, onPackageChange)
            }
            LiquidButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun AppFilter(
    selectedPackage: String?,
    apps: List<InstalledApp>,
    onPackageChange: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = apps.firstOrNull { it.packageName == selectedPackage }?.displayName
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LiquidChip(
            selected = selectedPackage != null,
            onClick = { expanded = !expanded },
            label = {
                Text(selectedName ?: "全部应用")
                Icon(Icons.Filled.ExpandMore, contentDescription = null, Modifier.size(18.dp))
            },
        )
        LiquidPopover(
            visible = expanded,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LiquidChip(selected = selectedPackage == null, onClick = { onPackageChange(null); expanded = false }) {
                    Text("全部应用")
                }
                apps.forEach { app ->
                    LiquidChip(
                        selected = selectedPackage == app.packageName,
                        onClick = { onPackageChange(app.packageName); expanded = false },
                    ) { Text(app.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun NotificationHistoryRow(entry: PendingNotificationUpload, appName: String) {
    val uploaded = entry.uploadedAt != null
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        InstalledAppIcon(entry.packageName, appName, Modifier.size(42.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(appName, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(
                    if (uploaded) Icons.Filled.CheckCircle else Icons.Filled.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (uploaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            entry.title?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                entry.body.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(formatEntryTime(entry.postedAt))
                    append(if (uploaded) " · 已上传" else " · 待上传")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private fun historyBounds(
    range: HistoryRange,
    customFrom: Long?,
    customTo: Long?,
): Pair<Long?, Long?> {
    val zone = ZoneId.systemDefault()
    return when (range) {
        HistoryRange.ALL -> null to null
        HistoryRange.TODAY -> {
            val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
            start to start + 24L * 60 * 60 * 1000 - 1
        }
        HistoryRange.WEEK -> System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000 to null
        HistoryRange.CUSTOM -> customFrom to customTo
    }
}

private fun filterSummary(
    range: HistoryRange,
    uploadState: UploadState,
    selectedPackage: String?,
    apps: List<InstalledApp>,
): String {
    val parts = mutableListOf<String>()
    if (range != HistoryRange.ALL) parts += range.label
    if (uploadState != UploadState.ALL) parts += uploadState.label
    selectedPackage?.let { packageName ->
        parts += apps.firstOrNull { it.packageName == packageName }?.displayName ?: packageName
    }
    return parts.joinToString(" · ").ifBlank { "全部通知" }
}

private fun pickDateTime(
    context: Context,
    initial: Long,
    endOfMinute: Boolean = false,
    onPicked: (Long) -> Unit,
) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    calendar.set(year, month, day, hour, minute, if (endOfMinute) 59 else 0)
                    calendar.set(Calendar.MILLISECOND, if (endOfMinute) 999 else 0)
                    onPicked(calendar.timeInMillis)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true,
            ).show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH),
    ).show()
}

private val entryFormatter = DateTimeFormatter
    .ofPattern("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE)
    .withZone(ZoneId.systemDefault())

private fun formatEntryTime(value: Long): String = entryFormatter.format(Instant.ofEpochMilli(value))
