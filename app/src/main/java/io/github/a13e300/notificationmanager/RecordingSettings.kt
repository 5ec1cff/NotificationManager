@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.a13e300.notificationmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun PreferencesScreen(vm: ArchiveViewModel) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val blockedChannels by vm.blockedChannels.collectAsStateWithLifecycle()
    val error by vm.rulesError.collectAsStateWithLifecycle()
    val operationError by vm.operationError.collectAsStateWithLifecycle()
    val clearing by vm.clearingRecords.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var cleanup by remember { mutableStateOf<RecordCleanup?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); message = null } }
    var editingText by rememberSaveable { mutableStateOf(false) }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(title = { Text("记录设置") }, navigationIcon = {
            IconButton(onClick = { vm.backStack.removeAt(vm.backStack.lastIndex) }) { Icon(DetailIcons.Back, contentDescription = "返回") }
        })
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            operationError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (clearing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            val current = rules
            if (current == null) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            } else {
                item { Text("开关与屏蔽名单即时生效，规则只影响之后收到的通知。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item {
                    SettingRow(DetailIcons.Channel, "记录新通知", if (current.enabled) "正在记录" else "已暂停记录", Modifier.toggleable(
                        value = current.enabled, role = Role.Switch,
                        onValueChange = { enabled -> vm.updateRules { it.copy(enabled = enabled) } },
                    ), trailing = { Switch(current.enabled, onCheckedChange = null) })
                }
                item {
                    SettingRow(DetailIcons.Time, "包括常驻通知", "记录持续显示在通知栏中的通知", Modifier.toggleable(
                        value = current.includeOngoing, role = Role.Switch,
                        onValueChange = { enabled -> vm.updateRules { it.copy(includeOngoing = enabled) } },
                    ), trailing = { Switch(current.includeOngoing, onCheckedChange = null) })
                }
                item {
                    SettingRow(DetailIcons.App, "应用黑名单", "已排除 ${current.excludedPackageNames().size} 个应用", Modifier.clickable { vm.backStack.add(AppBlacklist) }, trailing = {
                        Icon(DetailIcons.Forward, contentDescription = null)
                    })
                }
                item {
                    SettingRow(DetailIcons.Channel, "通知渠道黑名单", "已屏蔽 ${blockedChannels?.size ?: 0} 个渠道", Modifier.clickable { vm.backStack.add(ChannelBlacklist) }, trailing = {
                        Icon(DetailIcons.Forward, contentDescription = null)
                    })
                }
                item {
                    val summary = listOf(
                        if (current.requiredText.isEmpty()) "包含：不限" else "包含：${current.requiredText}",
                        if (current.excludedText.isEmpty()) "排除：无" else "排除：${current.excludedText}",
                    ).joinToString("\n")
                    SettingRow(DetailIcons.Text, "文本过滤", summary, Modifier.clickable { editingText = true }, trailing = {
                        Icon(DetailIcons.Forward, contentDescription = null)
                    })
                }
                item {
                    SettingRow(DetailIcons.Delete, "清空已屏蔽通知记录", "删除当前应用、渠道黑名单匹配的历史记录", Modifier.clickable(enabled = !clearing) { cleanup = RecordCleanup.BLOCKED }, trailing = {
                        Icon(DetailIcons.Forward, contentDescription = null)
                    })
                }
                item {
                    SettingRow(DetailIcons.DeleteAll, "移除所有记录", "删除全部历史记录及其图像文件", Modifier.clickable(enabled = !clearing) { cleanup = RecordCleanup.ALL }, trailing = {
                        Icon(DetailIcons.Forward, contentDescription = null)
                    })
                }
                item { SettingRow(DetailIcons.Info, "记录范围", "默认保存所有通知及其更新，不自动清理历史，容量受设备存储空间限制。系统脱敏、工作资料和厂商策略可能限制收到的内容。") }
                item { SettingRow(DetailIcons.Code, "特权功能", "普通模式保存 opPkg、UID 和操作令牌的创建者。Shizuku/root 尚未接入。") }
            }
        }
    }
    cleanup?.let { target ->
        AlertDialog(
            onDismissRequest = { cleanup = null },
            icon = { Icon(if (target == RecordCleanup.ALL) DetailIcons.DeleteAll else DetailIcons.Delete, contentDescription = null) },
            title = { Text(if (target == RecordCleanup.ALL) "移除所有记录？" else "清空已屏蔽通知记录？") },
            text = { Text(if (target == RecordCleanup.ALL)
                "将删除全部通知记录及其图像文件，包含筛选范围之外的记录，此操作无法撤销。记录规则和屏蔽名单会保留，系统通知不受影响。"
                else "将删除当前应用或渠道黑名单匹配的历史记录及其图像文件，此操作无法撤销。其他记录、屏蔽名单和系统通知不受影响。") },
            confirmButton = { TextButton(onClick = {
                cleanup = null
                vm.clearRecords(target) { message = it }
            }) { Text("确认清空", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { cleanup = null }) { Text("取消") } },
        )
    }
    if (editingText) rules?.let { TextFilterDialog(vm, it) { editingText = false } }
}

@Composable
private fun TextFilterDialog(vm: ArchiveViewModel, initial: RecordingRules, onDismiss: () -> Unit) {
    var required by rememberSaveable { mutableStateOf(initial.requiredText) }
    var excluded by rememberSaveable { mutableStateOf(initial.excludedText) }
    var saving by remember { mutableStateOf(false) }
    val error by vm.rulesError.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        icon = { Icon(DetailIcons.Text, contentDescription = null) },
        title = { Text("文本过滤") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("匹配标题和正文，不区分大小写。留空表示不限制。点击保存后生效。", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(required, { required = it }, Modifier.fillMaxWidth(), enabled = !saving, label = { Text("只记录包含此文字的通知") })
                OutlinedTextField(excluded, { excluded = it }, Modifier.fillMaxWidth(), enabled = !saving, label = { Text("不记录包含此文字的通知") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                saving = true
                vm.updateRules(onSaved = { success -> saving = false; if (success) onDismiss() }) {
                    it.copy(requiredText = required, excludedText = excluded)
                }
            }) { Text(if (saving) "保存中" else "保存") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun SettingRow(
    icon: ImageVector,
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
    }
}
