@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.a13e300.notificationmanager

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

private data class BlacklistApp(val packageName: String, val label: String, val installed: Boolean = true)

@Composable
internal fun AppBlacklistScreen(vm: ArchiveViewModel) {
    val listState = rememberLazyListState()
    val filtersScroll = rememberFiltersScrollBehavior(listState, headerIndex = 1)
    val rules by vm.rules.collectAsStateWithLifecycle()
    val history by vm.packages.collectAsStateWithLifecycle()
    val error by vm.rulesError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var selectedOnly by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val installed by produceState<Result<List<BlacklistApp>>?>(null, revision) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS).map { info ->
                    BlacklistApp(info.packageName, runCatching { info.loadLabel(pm).toString() }.getOrDefault(info.packageName))
                }
            }
        }
    }
    val excluded = remember(rules) { rules?.excludedPackageNames().orEmpty() }
    val apps = remember(installed, history, excluded) {
        val known = installed?.getOrNull().orEmpty().associateBy { it.packageName }.toMutableMap()
        // Keep old blacklist entries visible even if their app has been removed.
        (history + excluded).forEach { pkg -> known.putIfAbsent(pkg, BlacklistApp(pkg, pkg, installed = false)) }
        val collator = Collator.getInstance()
        known.values.sortedWith { a, b ->
            collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.packageName.compareTo(b.packageName)
        }
    }
    val visible = remember(apps, query, selectedOnly, excluded) {
        apps.filter { app ->
            (!selectedOnly || app.packageName in excluded) &&
                (app.label.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true))
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("应用黑名单") }, navigationIcon = {
            IconButton(onClick = { vm.backStack.removeAt(vm.backStack.lastIndex) }) { Icon(DetailIcons.Back, contentDescription = "返回") }
        }, actions = {
            IconButton(onClick = { revision++ }) { Icon(DetailIcons.Refresh, contentDescription = "刷新应用列表") }
        })
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .imePadding().nestedScroll(filtersScroll.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "intro") {
                Text("勾选后不再记录该应用的新通知，即时生效。", style = MaterialTheme.typography.bodyMedium)
            }
            stickyHeader(key = "search") { _ ->
                CollapsingSearchHeader(
                    scrollBehavior = filtersScroll,
                    search = {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(28.dp),
                            placeholder = { Text("搜索应用名称或包名") }, leadingIcon = { Icon(DetailIcons.Search, contentDescription = null) })
                    },
                    filters = {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("已排除 ${excluded.size} 个应用", style = MaterialTheme.typography.labelLarge)
                            FilterChip(selectedOnly, onClick = { selectedOnly = !selectedOnly }, label = { Text("仅看已排除") })
                        }
                    },
                )
            }
            error?.let { item(key = "rulesError") { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
            installed?.exceptionOrNull()?.let { item(key = "appsError") { Text("读取应用列表失败：${it.message}，可点击工具栏刷新。", color = MaterialTheme.colorScheme.error) } }
            if (installed == null || rules == null) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (visible.isEmpty() && installed != null && rules != null) item(key = "empty") {
                Box(Modifier.fillParentMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) { Text("没有匹配的应用") }
            }
            items(visible, key = { "app:${it.packageName}" }) { app ->
                val checked = app.packageName in excluded
                Surface(
                    Modifier.fillMaxWidth().toggleable(value = checked, enabled = rules != null, role = Role.Checkbox,
                        onValueChange = { vm.excludePackage(app.packageName, it) }),
                    shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        BlacklistAppIcon(app.packageName, revision)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(app.label, style = MaterialTheme.typography.titleMedium)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!app.installed) Text("当前用户未安装或不可见", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(checked, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun BlacklistAppIcon(packageName: String, revision: Int) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, packageName, revision) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    val modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
    bitmap?.let { Image(it, contentDescription = null, modifier) }
        ?: Icon(DetailIcons.App, contentDescription = null, modifier, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
