package com.unmiss.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.unmiss.app.ui.components.InstalledAppIcon
import com.unmiss.app.ui.theme.AdaptiveGlassSurface
import com.unmiss.app.ui.theme.AdaptiveLiquidSwitch
import com.unmiss.app.ui.theme.LiquidTextField
import com.unmiss.app.ui.theme.LiquidToggle
import kotlinx.coroutines.launch

@Composable
fun AllowlistScreen() {
    val context = LocalContext.current
    val settings = ServiceLocator.get().settingsDataStore
    val enabledPackages by settings.enabledPackages.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = AppCatalog(context).loadUserVisibleApps()
        settings.ensureDefaultPackages(apps.mapTo(mutableSetOf()) { it.packageName })
        loaded = true
    }

    val visibleApps = remember(apps, enabledPackages, query) {
        val matching = apps.filter { app ->
            query.isBlank() || app.displayName.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
        }
        matching.sortedWith(
            compareByDescending<InstalledApp> { it.packageName in enabledPackages }
                .thenBy { it.displayName.lowercase() },
        )
    }
    val selectedCount = visibleApps.count { it.packageName in enabledPackages }

    Scaffold(containerColor = Color.Transparent) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 116.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("应用", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        "已选择 ${enabledPackages.size} 个 · 开关后立即生效",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                LiquidTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                    placeholder = "搜索应用",
                    leading = { Icon(Icons.Filled.Search, contentDescription = null) },
                )
            }

            if (!loaded) {
                item { EmptyAppsMessage("正在读取已安装应用…") }
            } else if (visibleApps.isEmpty()) {
                item { EmptyAppsMessage("没有找到匹配的应用") }
            } else {
                var previousSelected: Boolean? = null
                visibleApps.forEach { app ->
                    val selected = app.packageName in enabledPackages
                    if (previousSelected != selected) {
                        item(key = "section-$selected") {
                            Text(
                                if (selected) "已选择 · $selectedCount" else "更多应用",
                                modifier = Modifier.padding(top = if (previousSelected == null) 0.dp else 22.dp, bottom = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        previousSelected = selected
                    }
                    item(key = app.packageName) {
                        AppSelectionRow(
                            app = app,
                            selected = selected,
                            onToggle = { enabled ->
                                scope.launch { settings.setPackageEnabled(app.packageName, enabled) }
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 58.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSelectionRow(
    app: InstalledApp,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InstalledAppIcon(app.packageName, app.displayName, Modifier.size(44.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.displayName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LiquidToggle(checked = selected, onCheckedChange = onToggle)
    }
}

@Composable
private fun EmptyAppsMessage(message: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
