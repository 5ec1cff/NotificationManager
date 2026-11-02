package io.github.a13e300.notificationmanager

import android.app.Notification
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Process
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ChannelBlacklistIntegrationTest {
    @Test fun blocksOnlyMatchingAppUserAndChannelAndSurvivesHistoryClear() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val files = File(target.cacheDir, "channels-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) { override fun getFilesDir() = files }
        val db = Room.inMemoryDatabaseBuilder(context, ArchiveDatabase::class.java).build()
        try {
            val repo = ArchiveRepository(context, db)
            val user = Process.myUserHandle()
            val otherUser = UserHandle.getUserHandleForUid(Process.myUid() + 1000000)
            val icon = Icon.createWithBitmap(Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888))
            fun sbn(pkg: String = "example.one", channelId: String? = "shared", profile: UserHandle = user): StatusBarNotification {
                val builder = if (channelId != null) Notification.Builder(context, channelId) else Notification.Builder(context)
                val notification = builder.setSmallIcon(icon).setContentTitle("Channel test").build()
                return StatusBarNotification(pkg, pkg, 1, "test", Process.myUid(), Process.myPid(), 0, notification, profile, 123L)
            }
            val original = repo.record(sbn(), null, "POSTED", 1)!!
            val blocked = BlockedChannel("example.one", user.hashCode(), "shared", "One", "Shared")
            repo.dao.blockChannel(blocked)
            repo.dao.blockChannel(blocked.copy(channelName = "New name"))
            assertEquals(1, repo.dao.blockedChannels().first().size)
            assertEquals("New name", repo.dao.blockedChannels().first().single().channelName)
            assertNull(repo.record(sbn(), null, "POSTED", 2))
            assertEquals("UPDATED", repo.dao.get(original.token)!!.state)
            assertNotNull(repo.record(sbn(channelId = "other"), null, "POSTED", 3))
            assertNotNull(repo.record(sbn(pkg = "example.two"), null, "POSTED", 4))
            assertNotNull(repo.record(sbn(profile = otherUser), null, "POSTED", 5))
            assertNotNull(repo.record(sbn(channelId = null), null, "POSTED", 6))
            assertEquals(5L, repo.dao.count().first())
            repo.deleteAll()
            assertTrue(repo.dao.isChannelBlocked(blocked.packageName, blocked.userId, blocked.channelId))
            assertNull(repo.record(sbn(), null, "POSTED", 7))
            repo.dao.unblockChannel(blocked.packageName, blocked.userId, blocked.channelId)
            assertNotNull(repo.record(sbn(), null, "POSTED", 8))
        } finally {
            db.close()
            files.deleteRecursively()
        }
    }

    @Test fun blockedCleanupUsesBothBlacklistsAndKeepsOtherRecordsAndRules() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val files = File(target.cacheDir, "cleanup-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) { override fun getFilesDir() = files }
        val db = Room.inMemoryDatabaseBuilder(context, ArchiveDatabase::class.java).build()
        try {
            val repo = ArchiveRepository(context, db)
            fun notice(token: String, pkg: String, channel: String?, user: Int = 0) = Notice(
                token = token, key = token, packageName = pkg, appName = pkg, opPackage = pkg,
                userId = user, uid = 10000, notificationId = 1, tag = null, channelId = channel,
                channelName = null, title = "Old", text = "Old content", postTime = 1, receivedAt = 2,
                source = "POSTED", state = "REMOVED", icon = "icon.png", ongoing = false,
            )
            val blocked = (0..259).map { notice("app-$it", "blocked.app", null) } + listOf(
                notice("channel", "channel.app", "target"),
                notice("overlap", "blocked.app", "overlap"),
            )
            val kept = listOf(
                notice("other-channel", "channel.app", "other").copy(ongoing = true),
                notice("other-user", "channel.app", "target", 10),
                notice("other-app", "other.app", "target"),
                notice("prefix", "blocked.app.extra", "target"),
                notice("no-channel", "channel.app", null),
            )
            val rules = RecordingRules(enabled = false, excludedPackages = "  blocked.app  \n\n", requiredText = "not in old content", includeOngoing = false)
            repo.dao.saveRules(rules)
            val channels = listOf(
                BlockedChannel("channel.app", 0, "target", "Channel app", "Target"),
                BlockedChannel("blocked.app", 0, "overlap", "Blocked app", "Overlap"),
            )
            channels.forEach { repo.dao.blockChannel(it) }
            db.withTransaction { (blocked + kept).forEach { repo.dao.insert(it) } }
            (blocked + kept).forEach { item ->
                val directory = repo.directory(item.token).apply { mkdirs() }
                File(directory, "details.json").writeText("{}")
                File(directory, "icon.png").writeBytes(byteArrayOf(1, 2, 3))
            }
            assertEquals(262L, repo.deleteBlocked())
            assertEquals(5L, repo.dao.count().first())
            blocked.forEach { assertNull(repo.dao.get(it.token)); assertFalse(repo.directory(it.token).exists()) }
            kept.forEach {
                assertEquals(it, repo.dao.get(it.token))
                assertTrue(File(repo.directory(it.token), "details.json").isFile)
                assertTrue(File(repo.directory(it.token), "icon.png").isFile)
            }
            assertEquals(rules, repo.dao.readRules())
            assertEquals(channels.toSet(), repo.dao.blockedChannels().first().toSet())
            assertEquals(0L, repo.deleteBlocked())
        } finally {
            db.close()
            files.deleteRecursively()
        }
    }

}
