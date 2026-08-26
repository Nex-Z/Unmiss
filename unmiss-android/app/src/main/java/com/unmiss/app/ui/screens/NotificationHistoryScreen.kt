package com.unmiss.app.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import com.unmiss.app.ui.theme.AdaptiveGlassSurface
import com.unmiss.app.ui.theme.AdaptiveLiquidIconButton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

private enum class HistoryRange { ALL, TODAY, WEEK, CUSTOM }
private enum class UploadState(val value: String, val label: String) {
    ALL("all", "全部"), UPLOADED("uploaded", "已上传"), PENDING("pending", "待上传")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen() {
    val context = LocalContext.current
    val dao = ServiceLocator.get().pendingDao
    var keyword by rememberSaveable { mutableStateOf("") }
    var range by rememberSaveable { mutableStateOf(HistoryRange.ALL) }
    var uploadState by rememberSaveable { mutableStateOf(UploadState.ALL) }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var customFrom by rememberSaveable { mutableStateOf<Long?>(null) }
    var customTo by rememberSaveable { mutableStateOf<Long?>(null) }
    var showPreciseTime by rememberSaveable { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }

    LaunchedEffect(Unit) { apps = AppCatalog(context).loadUserVisibleApps() }
    val capturedPackages by dao.observePackages().collectAsState(initial = emptyList())
    val capturedApps = remember(apps, capturedPackages) {
        capturedPackages.map { packageName ->
            apps.firstOrNull { it.packageName == packageName }
                ?: InstalledApp(packageName, packageName)
        }.sortedBy { it.displayName.lowercase() }
    }
    val bounds = remember(range, customFrom, customTo) {
        val zone = ZoneId.systemDefault()
        when (range) {
            HistoryRange.ALL -> null to null
            HistoryRange.TODAY -> {
                val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
                start to start + 24L * 60 * 60 * 1000 - 1
            }
            HistoryRange.WEEK -> System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000 to null
            HistoryRange.CUSTOM -> customFrom to customTo
        }
    }
    val historyFlow = remember(bounds, selectedPackage, uploadState, keyword) {
        dao.observeHistory(bounds.first, bounds.second, selectedPackage, uploadState.value, keyword.trim())
    }
    val entries by historyFlow.collectAsState(initial = emptyList())
    val appNames = remember(capturedApps) { capturedApps.associate { it.packageName to it.displayName } }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text("收录", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("所有被 Unmiss 捕获的通知 · 本机保留 90 天", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            stickyHeader {
                HistoryFilters(
                    keyword = keyword,
                    onKeywordChange = { keyword = it },
                    range = range,
                    onRangeChange = { range = it },
                    uploadState = uploadState,
                    onUploadStateChange = { uploadState = it },
                    selectedPackage = selectedPackage,
                    apps = capturedApps,
                    onPackageChange = { selectedPackage = it },
                    showPreciseTime = showPreciseTime,
                    onTogglePreciseTime = { showPreciseTime = !showPreciseTime },
                    customFrom = customFrom,
                    customTo = customTo,
                    onPickFrom = {
                        pickDateTime(context, customFrom ?: System.currentTimeMillis()) {
                            customFrom = it; range = HistoryRange.CUSTOM
                        }
                    },
                    onPickTo = {
                        pickDateTime(context, customTo ?: System.currentTimeMillis(), endOfMinute = true) {
                            customTo = it; range = HistoryRange.CUSTOM
                        }
                    },
                )
            }
            item {
                Text(
                    if (entries.isEmpty()) "没有匹配的通知" else "${entries.size} 条通知",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(entries, key = { it.id }) { entry ->
                NotificationHistoryRow(
                    entry = entry,
                    appName = appNames[entry.packageName] ?: entry.packageName,
                )
                HorizontalDivider(modifier = Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun HistoryFilters(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    range: HistoryRange,
    onRangeChange: (HistoryRange) -> Unit,
    uploadState: UploadState,
    onUploadStateChange: (UploadState) -> Unit,
    selectedPackage: String?,
    apps: List<InstalledApp>,
    onPackageChange: (String?) -> Unit,
    showPreciseTime: Boolean,
    onTogglePreciseTime: () -> Unit,
    customFrom: Long?,
    customTo: Long?,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
) {
    AdaptiveGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.91f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdaptiveGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            OutlinedTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索标题或通知内容") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    AdaptiveLiquidIconButton(onClick = onTogglePreciseTime) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = "精确时间筛选",
                            tint = if (showPreciseTime || range == HistoryRange.CUSTOM) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                HistoryRange.ALL to "全部时间",
                HistoryRange.TODAY to "今天",
                HistoryRange.WEEK to "近 7 天",
            ).forEach { (item, label) ->
                FilterChip(selected = range == item, onClick = { onRangeChange(item) }, label = { Text(label) })
            }
            AppFilter(selectedPackage, apps, onPackageChange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UploadState.entries.forEach { state ->
                FilterChip(
                    selected = uploadState == state,
                    onClick = { onUploadStateChange(state) },
                    label = { Text(state.label) },
                )
            }
        }
            AnimatedVisibility(visible = showPreciseTime) {
                AdaptiveGlassSurface(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), shape = RoundedCornerShape(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onPickFrom, modifier = Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("起始", style = MaterialTheme.typography.labelSmall)
                            Text(formatFilterTime(customFrom), maxLines = 1)
                        }
                    }
                    Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onPickTo, modifier = Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("结束", style = MaterialTheme.typography.labelSmall)
                            Text(formatFilterTime(customTo), maxLines = 1)
                        }
                    }
                }
            }
            }
        }
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
    Box {
        FilterChip(
            selected = selectedPackage != null,
            onClick = { expanded = true },
            trailingIcon = { Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp)) },
            label = { Text(selectedName ?: "全部 App") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("全部 App") },
                onClick = { onPackageChange(null); expanded = false },
            )
            apps.forEach { app ->
                DropdownMenuItem(
                    text = { Text(app.displayName) },
                    onClick = { onPackageChange(app.packageName); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun NotificationHistoryRow(entry: PendingNotificationUpload, appName: String) {
    val uploaded = entry.uploadedAt != null
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        InstalledAppIcon(
            packageName = entry.packageName,
            fallbackLabel = appName,
            modifier = Modifier.size(42.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(appName, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(
                    if (uploaded) Icons.Filled.CheckCircle else Icons.Filled.CloudOff,
                    contentDescription = if (uploaded) "已上传" else "待上传",
                    modifier = Modifier.size(15.dp),
                    tint = if (uploaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (uploaded) "已上传" else "待上传",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (uploaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!entry.title.isNullOrBlank()) {
                Text(entry.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    if (!uploaded && entry.retryCount > 0) append(" · 已重试 ${entry.retryCount} 次")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
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

private val entryFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE)
    .withZone(ZoneId.systemDefault())

private fun formatEntryTime(value: Long): String = entryFormatter.format(Instant.ofEpochMilli(value))

private fun formatFilterTime(value: Long?): String = value?.let(::formatEntryTime) ?: "选择时间"
