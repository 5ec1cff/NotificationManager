package io.github.a13e300.notificationmanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.room.Room
import androidx.paging.PagingSource
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ArchiveIntegrationTest {
    @Test fun snapshotsPreserveActionsUpdatesRemovalFiltersAndDeletion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, ArchiveDatabase::class.java).build()
        val repo = ArchiveRepository(context, db)
        val channel = "archive-test"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "Test channel", NotificationManager.IMPORTANCE_DEFAULT))
        val pending = PendingIntent.getActivity(context, 3, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val icon = Icon.createWithBitmap(Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888))
        fun sbn(text: String, ongoing: Boolean = false) = StatusBarNotification(context.packageName, context.packageName, 77, "sample", Process.myUid(), Process.myPid(), 0,
            Notification.Builder(context, channel).setSmallIcon(icon).setContentTitle("Title %_").setContentText(text).setOngoing(ongoing).setContentIntent(pending)
                .addAction(Notification.Action.Builder(icon, "Open", pending).build()).build(), Process.myUserHandle(), 123L)
        val first = repo.record(sbn("first", ongoing = true), null, "POSTED", 1000)!!
        val second = repo.record(sbn("second"), null, "POSTED", 2000)!!
        assertEquals("UPDATED", repo.dao.get(first.token)!!.state)
        assertEquals(true, repo.dao.get(first.token)!!.ongoing)
        assertEquals(false, repo.dao.get(second.token)!!.ongoing)
        val json = JSONObject(File(repo.directory(second.token), "details.json").readText())
        assertEquals("Open", json.getJSONObject("notification").getJSONArray("actions").getJSONObject(0).getString("title"))
        assertTrue(File(repo.directory(second.token), second.icon!!).isFile)
        repo.dao.removed(second.key, 3000, 2)
        assertEquals(2, repo.dao.get(second.token)!!.removalReason)
        assertEquals(3000L, repo.dao.get(second.token)!!.removedAt)
        val page = repo.dao.page("%_", context.packageName, 1500, 2500, "REMOVED").load(PagingSource.LoadParams.Refresh(null, 40, false))
        assertEquals(listOf(second.token), (page as PagingSource.LoadResult.Page).data.map { it.token })
        repo.dao.saveRules(RecordingRules(excludedPackages = context.packageName))
        assertNull(repo.record(sbn("filtered"), null, "POSTED", 4000))
        assertEquals(2L, repo.dao.count().first())
        repo.delete(second.token)
        assertNull(repo.dao.get(second.token))
        assertFalse(repo.directory(second.token).exists())
        repo.delete(first.token)
        db.close()
    }

    @Test fun clearAllKeepsRulesAndSerializesWithNewSnapshots() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val files = File(target.cacheDir, "clear-records-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) { override fun getFilesDir() = files }
        val db = Room.inMemoryDatabaseBuilder(context, ArchiveDatabase::class.java).build()
        try {
            val repo = ArchiveRepository(context, db)
            val icon = Icon.createWithBitmap(Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888))
            fun sbn(id: Int) = StatusBarNotification(context.packageName, context.packageName, id, "clear-test", Process.myUid(), Process.myPid(), 0,
                Notification.Builder(context, "clear-test").setSmallIcon(icon).setContentTitle("keep $id").build(), Process.myUserHandle(), 123L)
            val rules = RecordingRules(requiredText = "keep", includeOngoing = false)
            repo.dao.saveRules(rules)
            val first = repo.record(sbn(1), null, "POSTED", 1000)!!
            val second = repo.record(sbn(2), null, "POSTED", 2000)!!
            val orphan = File(files, "notifications/orphan").apply { mkdirs() }
            File(orphan, "details.json").writeText("{}")
            repo.deleteAll()
            assertEquals(0L, repo.dao.count().first())
            assertEquals(rules, repo.dao.readRules())
            assertFalse(repo.directory(first.token).exists())
            assertFalse(repo.directory(second.token).exists())
            assertFalse(orphan.exists())

            coroutineScope {
                val writes = (3..14).map { id -> async(Dispatchers.Default) { repo.record(sbn(id), null, "POSTED", id.toLong()) } }
                val clear = async(Dispatchers.Default) { repo.deleteAll() }
                writes.awaitAll()
                clear.await()
            }
            val afterClear = repo.record(sbn(15), null, "POSTED", 15000)!!
            val page = repo.dao.page("", "", 0, Long.MAX_VALUE, "").load(PagingSource.LoadParams.Refresh(null, 40, false)) as PagingSource.LoadResult.Page
            val records = page.data
            assertTrue(records.any { it.token == afterClear.token })
            records.forEach { notice ->
                assertTrue(File(repo.directory(notice.token), "details.json").isFile)
                assertTrue(File(repo.directory(notice.token), notice.icon!!).isFile)
            }
            assertEquals(records.map { it.token }.toSet(), File(files, "notifications").listFiles()!!.map { it.name }.toSet())
            assertEquals(rules, repo.dao.readRules())
        } finally {
            db.close()
            files.deleteRecursively()
        }
    }

}
