package io.github.a13e300.notificationmanager

import android.content.Context
import android.service.notification.StatusBarNotification
import android.service.notification.NotificationListenerService.Ranking
import androidx.room.withTransaction
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.json.JSONObject

class ArchiveRepository(private val context: Context, private val database: ArchiveDatabase) {
    val dao = database.archive()
    private val initialized = (context.applicationContext as ArchiveApp).scope.async { dao.disconnected() }
    private val storageMutex = Mutex()
    private val root = File(context.filesDir, "notifications").apply { mkdirs() }
    fun directory(token: String) = File(root, token)

    suspend fun record(sbn: StatusBarNotification, ranking: Ranking?, source: String, receivedAt: Long): Notice? = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            initialized.await()
            val n = sbn.notification
            val title = n.extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = listOfNotNull(
                n.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString(),
                n.extras?.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString(),
                n.extras?.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)?.joinToString("\n"),
                n.extras?.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString(),
            ).distinct().joinToString("\n")
            // A filtered update still ends the previous snapshot's live state.
            dao.supersede(sbn.key)
            if (!(dao.readRules() ?: RecordingRules()).accepts(sbn.packageName, "$title\n$text", sbn.isOngoing)) return@withLock null
            val channelId = n.channelId
            if (channelId != null && dao.isChannelBlocked(sbn.packageName, sbn.user.hashCode(), channelId)) return@withLock null
            val token = UUID.randomUUID().toString()
            val directory = directory(token).apply { mkdirs() }
            try {
                val snapshot = NotificationSnapshot(context, directory)
                val payload = snapshot.capture(sbn, ranking)
                File(directory, "details.json").writeText(payload.toString(2))
                val icon = snapshot.saveIcon(n.smallIcon, "smallIcon")
                val name = runCatching {
                    context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
                }.getOrDefault(sbn.packageName)
                val notice = Notice(token, sbn.key, sbn.packageName, name, sbn.opPkg, sbn.user.hashCode(), sbn.uid,
                    sbn.id, sbn.tag, n.channelId, ranking?.channel?.name?.toString(), title, text, sbn.postTime, receivedAt, source, icon = icon, ongoing = sbn.isOngoing)
                database.withTransaction { dao.insert(notice) }
                notice
            } catch (e: Exception) {
                directory.deleteRecursively()
                throw e
            }
        }
    }

    suspend fun resolveOngoing(notice: Notice): Boolean? = withContext(Dispatchers.IO) {
        if (notice.ongoing != null) return@withContext notice.ongoing
        val ongoing = runCatching {
            JSONObject(File(directory(notice.token), "details.json").readText())
                .optJSONObject("statusBar")?.opt("ongoing") as? Boolean
        }.getOrNull()
        if (ongoing != null) dao.setOngoing(notice.token, ongoing)
        ongoing
    }

    suspend fun updateRules(change: (RecordingRules) -> RecordingRules) = database.withTransaction {
        val current = dao.readRules() ?: RecordingRules()
        dao.saveRules(change(current))
    }

    suspend fun delete(token: String) = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            dao.delete(token)
            check(directory(token).deleteRecursively()) { "记录已移除，但图像文件删除失败" }
        }
    }

    suspend fun deleteBlocked(): Long = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            val packages = (dao.readRules() ?: RecordingRules()).excludedPackageNames()
                .joinToString("\n", prefix = "\n", postfix = "\n")
            var after = ""
            var removed = 0L
            while (true) {
                val tokens = dao.blockedRecordTokens(packages, after, 256)
                if (tokens.isEmpty()) break
                dao.deleteTokens(tokens)
                tokens.forEach { token ->
                    check(directory(token).deleteRecursively()) { "记录已移除，但部分图像文件删除失败" }
                }
                after = tokens.last()
                removed += tokens.size
            }
            removed
        }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        // Share the lock with record(), so new snapshots keep their files.
        storageMutex.withLock {
            dao.deleteAll()
            Files.newDirectoryStream(root.toPath()).use { files ->
                files.forEach { path ->
                    check(path.toFile().deleteRecursively()) { "记录已移除，但部分图像文件删除失败" }
                }
            }
        }
    }
}
