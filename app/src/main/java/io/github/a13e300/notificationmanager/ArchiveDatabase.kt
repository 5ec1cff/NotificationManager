package io.github.a13e300.notificationmanager

import android.app.Application
import androidx.room.*
import androidx.paging.PagingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notifications", indices = [Index("key"), Index("receivedAt"), Index(value = ["packageName", "receivedAt"]), Index(value = ["key", "state"])])
data class Notice(
    @PrimaryKey val token: String,
    val key: String,
    val packageName: String,
    val appName: String,
    val opPackage: String,
    val userId: Int,
    val uid: Int,
    val notificationId: Int,
    val tag: String?,
    val channelId: String?,
    val channelName: String?,
    val title: String,
    val text: String,
    val postTime: Long,
    val receivedAt: Long,
    val source: String,
    val state: String = "ACTIVE",
    val removedAt: Long? = null,
    val removalReason: Int? = null,
    val icon: String? = null,
    val ongoing: Boolean? = null,
)

@Entity(tableName = "blocked_channels", primaryKeys = ["packageName", "userId", "channelId"])
data class BlockedChannel(
    val packageName: String,
    val userId: Int,
    val channelId: String,
    val appName: String,
    val channelName: String?,
)

@Entity(tableName = "recording_rules")
data class RecordingRules(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = true,
    val excludedPackages: String = "",
    val requiredText: String = "",
    val excludedText: String = "",
    val includeOngoing: Boolean = true,
) {
    fun excludedPackageNames(): Set<String> = excludedPackages.lineSequence()
        .map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun accepts(packageName: String, content: String, ongoing: Boolean): Boolean = enabled &&
        packageName !in excludedPackageNames() &&
        (includeOngoing || !ongoing) &&
        (requiredText.isBlank() || content.contains(requiredText, ignoreCase = true)) &&
        (excludedText.isBlank() || !content.contains(excludedText, ignoreCase = true))
}

@Dao
interface ArchiveDao {
    @Insert suspend fun insert(notice: Notice)
    @Query("SELECT * FROM notifications WHERE (:pkg = '' OR packageName = :pkg) AND (:query = '' OR instr(lower(title || char(10) || text || char(10) || appName || char(10) || packageName), lower(:query)) > 0) AND receivedAt >= :start AND receivedAt <= :end AND (:state = '' OR state = :state) ORDER BY receivedAt DESC, token DESC")
    fun page(query: String, pkg: String, start: Long, end: Long, state: String): PagingSource<Int, Notice>
    @Query("SELECT * FROM notifications WHERE token = :token") fun observe(token: String): Flow<Notice?>
    @Query("SELECT * FROM notifications WHERE token = :token") suspend fun get(token: String): Notice?
    @Query("UPDATE notifications SET ongoing = :ongoing WHERE token = :token AND ongoing IS NULL")
    suspend fun setOngoing(token: String, ongoing: Boolean)
    @Query("SELECT COUNT(*) FROM notifications") fun count(): Flow<Long>
    @Query("SELECT DISTINCT packageName FROM notifications ORDER BY packageName") fun packages(): Flow<List<String>>
    @Query("UPDATE notifications SET state = 'REMOVED', removedAt = :time, removalReason = :reason WHERE key = :key AND state = 'ACTIVE'")
    suspend fun removed(key: String, time: Long, reason: Int)
    @Query("UPDATE notifications SET state = 'UPDATED' WHERE key = :key AND state = 'ACTIVE'") suspend fun supersede(key: String)
    @Query("UPDATE notifications SET state = 'UNKNOWN' WHERE state = 'ACTIVE'") suspend fun disconnected()
    @Query("""
        SELECT n.token FROM notifications AS n
        WHERE n.token > :after AND (
            instr(:packages, char(10) || n.packageName || char(10)) > 0
            OR EXISTS (
                SELECT 1 FROM blocked_channels AS b
                WHERE b.packageName = n.packageName AND b.userId = n.userId AND b.channelId = n.channelId
            )
        )
        ORDER BY n.token LIMIT :limit
    """)
    suspend fun blockedRecordTokens(packages: String, after: String, limit: Int): List<String>
    @Query("DELETE FROM notifications WHERE token IN (:tokens)") suspend fun deleteTokens(tokens: List<String>)
    @Query("DELETE FROM notifications WHERE token = :token") suspend fun delete(token: String)
    @Query("DELETE FROM notifications") suspend fun deleteAll()
    @Query("SELECT * FROM blocked_channels ORDER BY appName COLLATE NOCASE, packageName, userId, channelId")
    fun blockedChannels(): Flow<List<BlockedChannel>>
    @Query("SELECT EXISTS(SELECT 1 FROM blocked_channels WHERE packageName = :pkg AND userId = :userId AND channelId = :channelId)")
    suspend fun isChannelBlocked(pkg: String, userId: Int, channelId: String): Boolean
    @Upsert suspend fun blockChannel(channel: BlockedChannel)
    @Query("DELETE FROM blocked_channels WHERE packageName = :pkg AND userId = :userId AND channelId = :channelId")
    suspend fun unblockChannel(pkg: String, userId: Int, channelId: String)
    @Query("SELECT * FROM recording_rules WHERE id = 1") fun rules(): Flow<RecordingRules?>
    @Query("SELECT * FROM recording_rules WHERE id = 1") suspend fun readRules(): RecordingRules?
    @Upsert suspend fun saveRules(rules: RecordingRules)
}

@Database(entities = [Notice::class, RecordingRules::class, BlockedChannel::class], version = 3, exportSchema = true)
abstract class ArchiveDatabase : RoomDatabase() {
    abstract fun archive(): ArchiveDao
}

class ArchiveApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { Room.databaseBuilder(this, ArchiveDatabase::class.java, "archive.db").build() }
    val repository by lazy { ArchiveRepository(this, database) }
}
