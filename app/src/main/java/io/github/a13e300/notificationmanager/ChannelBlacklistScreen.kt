@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.a13e300.notificationmanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun ChannelBlacklistScreen(vm: ArchiveViewModel) {
    val channels by vm.blockedChannels.collectAsStateWithLifecycle()
    val error by vm.rulesError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); message = null } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(title = { Text("通知渠道黑名单") }, navigationIcon = {
            IconButton(onClick = { vm.backStack.removeAt(vm.backStack.lastIndex) }) { Icon(DetailIcons.Back, contentDescription = "返回") }
        })
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("从通知详情添加渠道。屏蔽只影响之后的记录，已有记录和系统通知不受影响。", style = MaterialTheme.typography.bodyMedium) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            val current = channels
            if (current == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (current.isEmpty()) item { Text("暂无屏蔽的通知渠道", Modifier.padding(vertical = 24.dp)) }
            else items(current, key = { channel ->
                "${channel.packageName}:${channel.userId}:${channel.channelId}"
            }) { channel ->
                var removing by remember { mutableStateOf(false) }
                SettingRow(
                    DetailIcons.Channel,
                    channel.channelName?.takeIf { it.isNotEmpty() } ?: channel.channelId,
                    "${channel.appName}\n${channel.packageName}\n渠道 ID：${channel.channelId}\n用户：${channel.userId}",
                    trailing = {
                        IconButton(enabled = !removing, onClick = {
                            removing = true
                            vm.setChannelBlocked(channel, false) { success ->
                                removing = false
                                if (success) message = "已取消屏蔽该渠道"
                            }
                        }) { Icon(DetailIcons.Delete, contentDescription = "移除渠道屏蔽", tint = MaterialTheme.colorScheme.error) }
                    },
                )
            }
        }
    }
}
