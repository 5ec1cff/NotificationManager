@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.a13e300.notificationmanager

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val dark = isSystemInDarkTheme()
            val colors = if (android.os.Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else if (dark) darkColorScheme(primary = Color(0xFFBCD092), secondaryContainer = Color(0xFF414A32))
            else lightColorScheme(primary = Color(0xFF526431), primaryContainer = Color(0xFFD5E9AC), secondaryContainer = Color(0xFFE2E8D3))
            MaterialExpressiveTheme(colorScheme = colors) {
                val vm: ArchiveViewModel = viewModel()
                Surface(Modifier.fillMaxSize()) {
                    NavDisplay(backStack = vm.backStack, onBack = { if (vm.backStack.size > 1) vm.backStack.removeAt(vm.backStack.lastIndex) }, entryProvider = entryProvider {
                        entry<Timeline> { TimelineScreen(vm) }
                        entry<Preferences> { PreferencesScreen(vm) }
                        entry<AppBlacklist> { AppBlacklistScreen(vm) }
                        entry<ChannelBlacklist> { ChannelBlacklistScreen(vm) }
                        entry<Detail> { DetailsScreen(vm, it.token) }
                    })
                }
            }
        }
    }
}

internal fun timestamp(time: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(java.util.Date(time))
internal fun status(state: String) = when (state) { "ACTIVE" -> "通知栏中"; "REMOVED" -> "已移除"; "UPDATED" -> "旧版本"; else -> "状态未知" }
internal fun openSettings(context: android.content.Context, intent: Intent): String? = runCatching { context.startActivity(intent); null }.getOrElse { "无法打开系统设置：${it.message}" }

@Composable
private fun TimelineScreen(vm: ArchiveViewModel) {
    val pages = vm.pages.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val filtersScroll = rememberFiltersScrollBehavior(listState, headerIndex = 1)
    val count by vm.count.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val packages by vm.packages.collectAsStateWithLifecycle()
    val connected by ArchiveListener.connected.collectAsStateWithLifecycle()
    val error by ArchiveListener.error.collectAsStateWithLifecycle()
    val operationError by vm.operationError.collectAsStateWithLifecycle()
    val clearing by vm.clearingRecords.collectAsStateWithLifecycle()
    var packageMenu by remember { mutableStateOf(false) }
    var dates by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(title = { Text("通知档案", fontWeight = FontWeight.Bold) }, actions = {
            IconButton(onClick = { vm.backStack.add(Preferences) }) {
                Icon(DetailIcons.Settings, contentDescription = "记录设置")
            }
        })
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .imePadding().nestedScroll(filtersScroll.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "summary") {
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(22.dp)) {
                        Text("留住每一条消息", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("$count 条快照 · ${if (connected) "监听已连接" else "监听未连接"}", style = MaterialTheme.typography.labelLarge)
                        if (!connected) TextButton(onClick = { openSettings(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("开启通知使用权") }
                    }
                }
            }
            stickyHeader(key = "search") { _ ->
                CollapsingSearchHeader(
                    scrollBehavior = filtersScroll,
                    search = {
                        OutlinedTextField(value = filter.query, onValueChange = { vm.filter.value = filter.copy(query = it) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), placeholder = { Text("搜索内容、应用或包名") }, singleLine = true)
                    },
                    filters = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box {
                                FilterChip(selected = filter.pkg.isNotEmpty(), onClick = { packageMenu = true }, label = { Text(if (filter.pkg.isEmpty()) "所有应用" else filter.pkg.substringAfterLast('.'), maxLines = 1) })
                                DropdownMenu(expanded = packageMenu, onDismissRequest = { packageMenu = false }) {
                                    DropdownMenuItem(text = { Text("所有应用") }, onClick = { vm.filter.value = filter.copy(pkg = ""); packageMenu = false })
                                    packages.forEach { pkg -> DropdownMenuItem(text = { Text(pkg) }, onClick = { vm.filter.value = filter.copy(pkg = pkg); packageMenu = false }) }
                                }
                            }
                            FilterChip(selected = filter.start != 0L || filter.end != Long.MAX_VALUE, onClick = { dates = true }, label = { Text("时间范围") })
                            FilterChip(selected = filter.state == "REMOVED", onClick = { vm.filter.value = filter.copy(state = if (filter.state.isEmpty()) "REMOVED" else "") }, label = { Text("已移除") })
                            if (filter != SearchFilter()) TextButton(onClick = { vm.filter.value = SearchFilter() }) { Text("重置") }
                        }
                    },
                )
            }
            operationError?.let { item(key = "operationError") { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
            if (clearing) item(key = "clearing") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item(key = "listenerError") { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
            when {
                pages.loadState.refresh is LoadState.Error -> item(key = "loadError") {
                    Column {
                        Text("读取失败，请重试", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { pages.retry() }) { Text("重试") }
                    }
                }
                pages.loadState.refresh is LoadState.Loading -> item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                pages.itemCount == 0 -> item(key = "empty") {
                    Box(Modifier.fillParentMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("暂时没有通知", style = MaterialTheme.typography.headlineSmall)
                            Text("开启使用权后，新通知会出现在这里", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            items(pages.itemCount, key = pages.itemKey { it.token }) { index -> pages[index]?.let { n ->
                val ongoing by rememberOngoing(n, vm.repository)
                NoticeCard(n, vm.repository.directory(n.token), ongoing) { vm.backStack.add(Detail(n.token)) }
            } }
            if (pages.loadState.append is LoadState.Loading) item(key = "appendLoading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (pages.loadState.append is LoadState.Error) item(key = "appendError") { TextButton(onClick = { pages.retry() }) { Text("继续加载") } }
        }
    }
    if (dates) {
        val picker = rememberDateRangePickerState()
        DatePickerDialog(onDismissRequest = { dates = false }, confirmButton = {
            TextButton(onClick = {
                fun localDate(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                val zone = ZoneId.systemDefault()
                vm.filter.value = filter.copy(start = picker.selectedStartDateMillis?.let { localDate(it).atStartOfDay(zone).toInstant().toEpochMilli() } ?: 0,
                    end = picker.selectedEndDateMillis?.let { localDate(it).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1 } ?: Long.MAX_VALUE)
                dates = false
            }) { Text("应用") }
        }, dismissButton = { TextButton(onClick = { vm.filter.value = filter.copy(start = 0, end = Long.MAX_VALUE); dates = false }) { Text("全部时间") } }) {
            DateRangePicker(state = picker, modifier = Modifier.heightIn(max = 520.dp))
        }
    }
}

@Composable
private fun NoticeCard(n: Notice, directory: File, ongoing: Boolean?, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SnapshotImage(n.icon?.let { File(directory, it) }, Modifier.size(32.dp))
                Text(n.appName, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status(n.state), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (n.title.isNotEmpty()) Text(n.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (n.text.isNotEmpty()) Text(n.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ongoing == true) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(DetailIcons.Time, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("常驻", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text(timestamp(n.receivedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun SnapshotImage(file: File?, modifier: Modifier = Modifier) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { file?.let { runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(it.path, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (bounds.outWidth / inSampleSize > 1024 || bounds.outHeight / inSampleSize > 1024) inSampleSize *= 2
            }
            BitmapFactory.decodeFile(it.path, options)?.asImageBitmap()
        }.getOrNull() } }
    }
    if (bitmap != null) Image(bitmap!!, contentDescription = "通知图像", modifier = modifier.clip(RoundedCornerShape(8.dp)))
    else Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) { Text("·") }
}

fun removalReason(reason: Int): String = when (reason) {
    1 -> "点击通知"; 2 -> "用户移除"; 3 -> "用户全部清除"; 4 -> "通知错误"
    5 -> "应用发生变化"; 6 -> "用户停止"; 7 -> "应用通知被禁用"; 8 -> "应用取消"; 9 -> "应用取消全部"
    10 -> "监听器取消"; 11 -> "监听器取消全部"; 12 -> "分组摘要取消"; 13 -> "分组优化"
    14 -> "应用被暂停"; 15 -> "资料关闭"; 16 -> "自动分组解除"; 17 -> "渠道禁用"
    18 -> "稍后提醒"; 19 -> "超时"; 20 -> "渠道删除"; 21 -> "清除数据"; 22 -> "通知助理取消"; 23 -> "锁定模式"
    else -> "未知系统原因"
}
