package com.unmiss.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unmiss.app.data.AppCatalog
import com.unmiss.app.data.InstalledApp
import com.unmiss.app.data.ServiceLocator
import com.unmiss.app.ui.theme.AdaptiveGlassSurface
import com.unmiss.app.ui.theme.AdaptiveLiquidButton
import com.unmiss.app.ui.theme.AdaptiveLiquidIconButton
import com.unmiss.app.ui.theme.AdaptiveLiquidSwitch
import com.unmiss.app.ui.theme.GlassButtonStyle
import kotlinx.coroutines.launch

private enum class AllowlistLayer { ENABLED, ADD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowlistScreen() {
    val context = LocalContext.current
    val settings = ServiceLocator.get().settingsDataStore
    val scope = rememberCoroutineScope()
    val enabledPackages by settings.enabledPackages.collectAsState(initial = emptySet())
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var layer by rememberSaveable { mutableStateOf(AllowlistLayer.ENABLED) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = AppCatalog(context).loadUserVisibleApps()
        settings.ensureDefaultPackages(apps.mapTo(mutableSetOf()) { it.packageName })
        loaded = true
    }

    val enabledApps = apps.filter { it.packageName in enabledPackages }
    val availableApps = apps.filter { app ->
        app.packageName !in enabledPackages &&
            (query.isBlank() || app.displayName.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true))
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            if (layer == AllowlistLayer.ADD) {
                AdaptiveGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                ) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        title = { Text("添加应用", fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            AdaptiveLiquidIconButton(onClick = { layer = AllowlistLayer.ENABLED }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        if (layer == AllowlistLayer.ENABLED) {
            EnabledAppsLayer(
                apps = enabledApps,
                loaded = loaded,
                modifier = Modifier.padding(padding),
                onAdd = { layer = AllowlistLayer.ADD },
                onRemove = { packageName ->
                    scope.launch { settings.setPackageEnabled(packageName, false) }
                },
            )
        } else {
            AddAppsLayer(
                apps = availableApps,
                query = query,
                loaded = loaded,
                modifier = Modifier.padding(padding),
                onQueryChange = { query = it },
                onAdd = { packageName ->
                    scope.launch { settings.setPackageEnabled(packageName, true) }
                },
            )
        }
    }
}

@Composable
private fun EnabledAppsLayer(
    apps: List<InstalledApp>,
    loaded: Boolean,
    modifier: Modifier,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("应用", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${apps.size} 个应用的通知会被分析",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AdaptiveLiquidButton(onClick = onAdd, style = GlassButtonStyle.TONAL) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("添加")
                }
            }
        }

        if (apps.isEmpty() && loaded) {
            item {
                EmptyState(
                    title = "还没有允许的应用",
                    description = "添加聊天、短信或邮件应用后，Unmiss 才会分析它们的通知。",
                    action = onAdd,
                )
            }
        } else {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                AppGroupRow(index, apps.size) {
                    AppRow(app = app, trailing = {
                        AdaptiveLiquidSwitch(checked = true, onCheckedChange = { onRemove(app.packageName) })
                    })
                }
            }
        }
    }
}

@Composable
private fun AddAppsLayer(
    apps: List<InstalledApp>,
    query: String,
    loaded: Boolean,
    modifier: Modifier,
    onQueryChange: (String) -> Unit,
    onAdd: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        item {
            Text(
                "只显示尚未允许的已安装应用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            AdaptiveGlassSurface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(22.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索应用名称或包名") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                )
            }
        }

        if (apps.isEmpty() && loaded) {
            item {
                Text(
                    if (query.isBlank()) "所有可用应用都已添加" else "没有找到匹配的应用",
                    modifier = Modifier.padding(vertical = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                AppGroupRow(index, apps.size) {
                    AppRow(app = app, trailing = {
                        AdaptiveLiquidButton(onClick = { onAdd(app.packageName) }) { Text("添加") }
                    })
                }
            }
        }
    }
}

@Composable
private fun AppGroupRow(index: Int, total: Int, content: @Composable () -> Unit) {
    val shape = when {
        total == 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        index == total - 1 -> RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(0.dp)
    }
    AdaptiveGlassSurface(shape = shape, color = Color.White.copy(alpha = 0.86f)) {
        Column {
            content()
            if (index < total - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 68.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(42.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(app.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(app.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        trailing()
    }
}

@Composable
private fun EmptyState(title: String, description: String, action: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AdaptiveLiquidButton(onClick = action) { Text("添加应用") }
    }
}
