package io.github.a13e300.notificationmanager

import android.app.ActivityOptions
import android.app.PendingIntent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class ArchiveListener : NotificationListenerService() {
    private val app get() = application as ArchiveApp
    private val events = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    override fun onCreate() {
        super.onCreate()
        app.scope.launch {
            for (event in events) runCatching { event() }.onFailure {
                error.value = "记录失败：${it.javaClass.simpleName} ${it.message.orEmpty()}"
                android.util.Log.e("NotificationArchive", "Listener event failed", it)
            }
        }
    }
    override fun onListenerConnected() {
        instance = this
        connected.value = true
        val current = activeNotifications.orEmpty()
        val ranking = currentRanking
        events.trySend {
            app.repository.dao.disconnected()
            current.forEach { capture(it, ranking, "RECONNECT", System.currentTimeMillis()) }
        }
    }
    private suspend fun capture(sbn: StatusBarNotification, map: RankingMap, source: String, time: Long) {
        val rank = Ranking().takeIf { map.getRanking(sbn.key, it) }
        live.remove(sbn.key)
        liveRevision.update { it + 1 }
        val notice = app.repository.record(sbn, rank, source, time) ?: return
        live[sbn.key] = Live(notice.token, sbn)
        liveRevision.update { it + 1 }
    }
    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        val time = System.currentTimeMillis()
        events.trySend { capture(sbn, rankingMap, "POSTED", time) }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        val time = System.currentTimeMillis()
        events.trySend {
            live.remove(sbn.key)
            liveRevision.update { it + 1 }
            app.repository.dao.removed(sbn.key, time, reason)
        }
    }
    override fun onListenerDisconnected() { disconnect(); super.onListenerDisconnected() }
    private fun disconnect() {
        if (instance === this) {
            instance = null; connected.value = false; live.clear()
            events.trySend { app.repository.dao.disconnected() }
        }
    }
    override fun onDestroy() { disconnect(); events.close(); super.onDestroy() }
    data class Live(val token: String, val sbn: StatusBarNotification)
    companion object {
        private var instance: ArchiveListener? = null
        private val live = ConcurrentHashMap<String, Live>()
        val connected = MutableStateFlow(false)
        val liveRevision = MutableStateFlow(0L)
        val error = MutableStateFlow<String?>(null)
        fun current(notice: Notice): StatusBarNotification? = live[notice.key]?.takeIf { connected.value && it.token == notice.token }?.sbn
        fun dismiss(notice: Notice): String = runCatching {
            check(current(notice) != null) { "通知已更新、移除或监听已断开" }
            checkNotNull(instance).cancelNotification(notice.key)
            "已请求从通知栏移除，结果以系统回调为准"
        }.getOrElse { it.message ?: "移除失败" }
        fun send(context: android.content.Context, pending: PendingIntent?): String = try {
            checkNotNull(pending) { "此通知没有该操作" }
            val options = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= 36) options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
            else if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION")
                options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
            pending.send(context, 0, null, null, null, null, options.toBundle())
            "已发送操作；最终行为由发送方和系统决定"
        } catch (_: PendingIntent.CanceledException) { "PendingIntent 已失效" }
        catch (e: Exception) { "操作失败：${e.message}" }
    }
}
