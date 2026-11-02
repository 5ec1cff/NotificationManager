@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.a13e300.notificationmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DetailsScreen(vm: ArchiveViewModel, token: String) {
    val notice by remember(token) { vm.dao.observe(token) }.collectAsStateWithLifecycle(initialValue = null)
    val operationError by vm.operationError.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val blockedChannels by vm.blockedChannels.collectAsStateWithLifecycle()
    var blockingApp by remember(token) { mutableStateOf(false) }
    var blockingChannel by remember(token) { mutableStateOf(false) }
    val context = LocalContext.current
    val ongoing by rememberOngoing(notice, vm.repository)
    var message by remember(token) { mutableStateOf<String?>(null) }
    var deleting by remember(token) { mutableStateOf(false) }
    var action by remember(token) { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var showJson by remember(token) { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); message = null }
    }
    val directory = remember(token) { vm.repository.directory(token) }
    val snapshot by produceState<DetailSnapshot?>(null, token) {
        value = withContext(Dispatchers.IO) { loadDetailSnapshot(directory) }
    }
    val connected by ArchiveListener.connected.collectAsStateWithLifecycle()
    val liveRevision by ArchiveListener.liveRevision.collectAsStateWithLifecycle()
    val liveNotice = remember(notice, connected, liveRevision) {
        notice?.takeIf { connected && it.state == "ACTIVE" }?.let { ArchiveListener.current(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(
            title = { Text("通知详情") },
            navigationIcon = { IconButton(onClick = { vm.backStack.removeAt(vm.backStack.lastIndex) }) { Icon(DetailIcons.Back, contentDescription = "返回") } },
            actions = { IconButton(onClick = { deleting = true }, enabled = notice != null) { Icon(DetailIcons.Delete, contentDescription = "删除记录", tint = MaterialTheme.colorScheme.error) } },
        )
    }) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            operationError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            notice?.let { n ->
                item { Text("点击信息卡片即可复制", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { DetailCard("标题", n.title.ifEmpty { "无标题" }, DetailIcons.Title, copyValue = n.title) }
                item { DetailCard("正文", n.text.ifEmpty { "无正文" }, DetailIcons.Text, copyValue = n.text) }
                item {
                    val type = when (ongoing) {
                        true -> "常驻通知"
                        false -> "普通通知"
                        null -> "未知"
                    }
                    DetailCard("通知类型", type, DetailIcons.Time)
                }
                item {
                    DetailCard("应用", "${n.appName}\n${n.packageName}", DetailIcons.App, copyValue = n.packageName) {
                        FilledTonalButton(onClick = {
                            message = openSettings(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${n.packageName}".toUri()))
                        }) { Text("应用详情设置") }
                        val appBlocked = n.packageName in rules?.excludedPackageNames().orEmpty()
                        FilledTonalButton(enabled = rules != null && !appBlocked && !blockingApp, onClick = {
                            blockingApp = true
                            vm.excludePackage(n.packageName, true) { success ->
                                blockingApp = false
                                message = if (success) "已加入应用黑名单，之后不再记录该应用的新通知" else vm.rulesError.value
                            }
                        }) { Text(if (appBlocked) "已加入应用黑名单" else "加入应用黑名单") }
                        if (n.userId >= 0 && n.userId != android.os.Process.myUserHandle().hashCode()) {
                            Text("此通知来自其他用户，系统设置可能只显示当前用户。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    DetailCard("渠道", listOfNotNull(n.channelName, n.channelId).joinToString("\n").ifEmpty { "未提供" }, DetailIcons.Channel, copyValue = n.channelId.orEmpty()) {
                        FilledTonalButton(enabled = n.channelId != null, onClick = {
                            message = openSettings(context, Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, n.packageName)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, n.channelId))
                        }) { Text("系统通知渠道设置") }
                        val channel = n.channelId?.let { BlockedChannel(n.packageName, n.userId, it, n.appName, n.channelName) }
                        val channelBlocked = blockedChannels?.any {
                            it.packageName == n.packageName && it.userId == n.userId && it.channelId == n.channelId
                        } == true
                        FilledTonalButton(enabled = channel != null && blockedChannels != null && !channelBlocked && !blockingChannel, onClick = {
                            channel?.let {
                                blockingChannel = true
                                vm.setChannelBlocked(it, true) { success ->
                                    blockingChannel = false
                                    message = if (success) "已加入渠道黑名单，之后不再记录该渠道的新通知" else vm.rulesError.value
                                }
                            }
                        }) { Text(if (channelBlocked) "已加入渠道黑名单" else "加入渠道黑名单") }
                    }
                }
                item { DetailCard("通知操作", if (liveNotice == null) "当前通知不可操作" else "当前通知可操作", DetailIcons.Action) {
                    Text("仅当前连接中仍匹配的通知可操作，历史记录中的操作可能已失效。", style = MaterialTheme.typography.bodySmall)
                    Button(enabled = liveNotice?.notification?.contentIntent != null, onClick = {
                        action = "点击通知" to { message = ArchiveListener.send(context, ArchiveListener.current(n)?.notification?.contentIntent) }
                    }) { Text("点击通知") }
                    liveNotice?.notification?.actions?.forEachIndexed { index, button ->
                        FilledTonalButton(onClick = {
                            action = "执行操作：${button.title}" to {
                                message = ArchiveListener.send(context, ArchiveListener.current(n)?.notification?.actions?.getOrNull(index)?.actionIntent)
                            }
                        }) { Text(button.title?.toString().orEmpty().ifEmpty { "操作 ${index + 1}" }) }
                    }
                    OutlinedButton(enabled = liveNotice != null, onClick = {
                        action = "从系统通知栏移除" to { message = ArchiveListener.dismiss(n) }
                    }) { Text("从通知栏移除") }
                } }
                item { DetailCard("状态", status(n.state), DetailIcons.Info) }
                item { DetailCard("发布时间", timestamp(n.postTime), DetailIcons.Time) }
                item { DetailCard("记录时间", timestamp(n.receivedAt), DetailIcons.Time) }
                n.removedAt?.let { time -> item { DetailCard("移除时间", timestamp(time), DetailIcons.Time) } }
                n.removalReason?.let { reason -> item { DetailCard("移除原因", "${removalReason(reason)} ($reason)", DetailIcons.Info) } }
                item { DetailCard("发送包名 · opPkg", n.opPackage, DetailIcons.App) }
                item { DetailCard("应用 UID", n.uid.toString(), DetailIcons.Identity) }
                item { DetailCard("用户", if (n.userId == -1) "所有用户 (-1)" else n.userId.toString(), DetailIcons.Person, copyValue = n.userId.toString()) }
                item { DetailCard("通知 ID", n.notificationId.toString(), DetailIcons.Identity) }
                item { DetailCard("通知标签 · tag", n.tag ?: "未提供", DetailIcons.Identity, copyValue = n.tag.orEmpty()) }
                item { DetailCard("通知标识 · key", n.key, DetailIcons.Identity) }
                item { DetailCard("记录来源", if (n.source == "RECONNECT") "监听连接时的已有通知" else "通知发布或更新", DetailIcons.Source) }
                snapshot?.let { saved ->
                    items(saved.images, key = { it.file.name }) { image ->
                        DetailCard(image.title, image.description, DetailIcons.Image) {
                            SnapshotImage(image.file, Modifier.fillMaxWidth().height(160.dp))
                        }
                    }
                    item {
                        if (saved.error != null) {
                            DetailCard("原始 JSON", saved.error, DetailIcons.Code)
                        } else {
                            DetailCard("原始 JSON", "查看完整通知快照", DetailIcons.Code, onClick = { showJson = true }, clickLabel = "查看原始 JSON", trailingIcon = DetailIcons.Source) {
                                Text("通知字段、附加信息和操作定义", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } ?: item { Text("正在读取记录，或记录已被删除") }
        }
    }

    if (showJson) snapshot?.let { saved -> JsonDialog(saved.json) { showJson = false } }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("删除这条记录？") },
        text = { Text("会删除快照及图像文件。系统通知栏中的通知不受影响。") },
        confirmButton = { TextButton(onClick = { deleting = false; vm.delete(token) { vm.backStack.removeAt(vm.backStack.lastIndex) } }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } },
    )
    action?.let { selected -> AlertDialog(
        onDismissRequest = { action = null },
        title = { Text(selected.first) },
        text = { Text("这会实际执行发送者提供的操作，可能打开应用或改变其状态。") },
        confirmButton = { TextButton(onClick = { action = null; selected.second() }) { Text("执行") } },
        dismissButton = { TextButton(onClick = { action = null }) { Text("取消") } },
    ) }
}

@Composable
private fun DetailCard(
    title: String,
    value: String,
    icon: ImageVector,
    copyValue: String = value,
    onClick: (() -> Unit)? = null,
    clickLabel: String = "复制$title",
    trailingIcon: ImageVector = DetailIcons.Copy,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = clickLabel) {
            if (onClick != null) onClick() else copyText(context, title, copyValue)
        },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Icon(trailingIcon, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(value, style = MaterialTheme.typography.bodyLarge)
                content()
            }
        }
    }
}

@Composable
private fun JsonDialog(json: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lines = remember(json) { json.lines() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(DetailIcons.Code, contentDescription = null) },
        title = { Text("原始 JSON") },
        text = {
            SelectionContainer {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(lines.size) { index -> Text(lines[index], fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { copyText(context, "原始 JSON", json) }) { Text("复制全文") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun copyText(context: Context, label: String, text: String) {
    runCatching {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    }.onSuccess {
        // Android 13 and later show their own clipboard preview.
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "已复制$label", Toast.LENGTH_SHORT).show()
    }.onFailure { Toast.makeText(context, "复制失败：${it.message}", Toast.LENGTH_SHORT).show() }
}
