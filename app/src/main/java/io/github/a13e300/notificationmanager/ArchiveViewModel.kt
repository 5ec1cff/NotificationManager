package io.github.a13e300.notificationmanager

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data object Timeline : NavKey
data object Preferences : NavKey
data object AppBlacklist : NavKey
data object ChannelBlacklist : NavKey
data class Detail(val token: String) : NavKey
enum class RecordCleanup { ALL, BLOCKED }

data class SearchFilter(val query: String = "", val pkg: String = "", val start: Long = 0, val end: Long = Long.MAX_VALUE, val state: String = "")

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class ArchiveViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as ArchiveApp).repository
    val dao = repository.dao
    val backStack = mutableStateListOf<NavKey>(Timeline)
    val filter = MutableStateFlow(SearchFilter())
    val pages = filter.debounce(200).flatMapLatest { f ->
        Pager(PagingConfig(pageSize = 40, maxSize = 200, enablePlaceholders = false)) {
            dao.page(f.query, f.pkg, f.start, f.end, f.state)
        }.flow
    }.cachedIn(viewModelScope)
    val count = dao.count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val packages = dao.packages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rules: StateFlow<RecordingRules?> = dao.rules().map { it ?: RecordingRules() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val blockedChannels: StateFlow<List<BlockedChannel>?> = dao.blockedChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val rulesMutex = Mutex()
    val rulesError = MutableStateFlow<String?>(null)
    val operationError = MutableStateFlow<String?>(null)
    val clearingRecords = MutableStateFlow(false)
    fun clearRecords(target: RecordCleanup, done: (String) -> Unit) {
        if (clearingRecords.value) return
        clearingRecords.value = true
        operationError.value = null
        viewModelScope.launch {
            try {
                val message = rulesMutex.withLock {
                    when (target) {
                        RecordCleanup.ALL -> { repository.deleteAll(); "所有记录已移除" }
                        RecordCleanup.BLOCKED -> "已清空 ${repository.deleteBlocked()} 条已屏蔽通知记录"
                    }
                }
                done(message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                operationError.value = "清理记录失败：${e.message}"
            } finally {
                clearingRecords.value = false
            }
        }
    }
    fun updateRules(onSaved: (Boolean) -> Unit = {}, change: (RecordingRules) -> RecordingRules) = viewModelScope.launch {
        rulesMutex.withLock {
            rulesError.value = null
            try {
                repository.updateRules(change)
                onSaved(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rulesError.value = "更新设置失败：${e.message}"
                onSaved(false)
            }
        }
    }
    fun excludePackage(packageName: String, excluded: Boolean, onSaved: (Boolean) -> Unit = {}) = updateRules(onSaved) { current ->
        val packages = current.excludedPackageNames().toMutableSet()
        if (excluded) packages.add(packageName) else packages.remove(packageName)
        current.copy(excludedPackages = packages.sorted().joinToString("\n"))
    }
    fun setChannelBlocked(channel: BlockedChannel, blocked: Boolean, onSaved: (Boolean) -> Unit = {}) = viewModelScope.launch {
        rulesMutex.withLock {
            rulesError.value = null
            try {
                if (blocked) dao.blockChannel(channel)
                else dao.unblockChannel(channel.packageName, channel.userId, channel.channelId)
                onSaved(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rulesError.value = "更新渠道屏蔽规则失败：${e.message}"
                onSaved(false)
            }
        }
    }
    fun delete(token: String, done: () -> Unit) = viewModelScope.launch {
        runCatching { repository.delete(token) }.onSuccess { done() }.onFailure { operationError.value = "删除失败：${it.message}" }
    }
}
