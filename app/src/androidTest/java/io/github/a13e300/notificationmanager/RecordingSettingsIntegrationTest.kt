package io.github.a13e300.notificationmanager

import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class RecordingSettingsIntegrationTest {
    @Test fun fieldUpdatesKeepConcurrentChangesAndOldBlacklistEntries() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val files = File(target.cacheDir, "settings-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) { override fun getFilesDir() = files }
        val db = Room.inMemoryDatabaseBuilder(context, ArchiveDatabase::class.java).build()
        try {
            val repo = ArchiveRepository(context, db)
            val oldPackage = "removed.old.app"
            repo.dao.saveRules(RecordingRules(excludedPackages = oldPackage))
            coroutineScope {
                val changes = (1..16).map { id -> async(Dispatchers.Default) {
                    repo.updateRules { current -> current.copy(excludedPackages =
                        (current.excludedPackageNames() + "example.app$id").sorted().joinToString("\n")) }
                } } + listOf(
                    async(Dispatchers.Default) { repo.updateRules { it.copy(enabled = false) } },
                    async(Dispatchers.Default) { repo.updateRules { it.copy(includeOngoing = false) } },
                    async(Dispatchers.Default) { repo.updateRules { it.copy(requiredText = "keep", excludedText = "skip") } },
                )
                changes.awaitAll()
            }
            val current = repo.dao.readRules()!!
            assertEquals((1..16).map { "example.app$it" }.toSet() + oldPackage, current.excludedPackageNames())
            assertFalse(current.enabled)
            assertFalse(current.includeOngoing)
            assertEquals("keep", current.requiredText)
            assertEquals("skip", current.excludedText)
            repo.updateRules { it.copy(excludedPackages = (it.excludedPackageNames() - oldPackage).sorted().joinToString("\n")) }
            val updated = repo.dao.readRules()!!
            assertFalse(oldPackage in updated.excludedPackageNames())
            assertEquals(16, updated.excludedPackageNames().size)
            assertEquals(current.copy(excludedPackages = updated.excludedPackages), updated)
        } finally {
            db.close()
            files.deleteRecursively()
        }
    }
}
