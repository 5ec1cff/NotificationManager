package io.github.a13e300.notificationmanager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.CancellationException

@Composable
internal fun rememberOngoing(notice: Notice?, repository: ArchiveRepository): State<Boolean?> =
    produceState(notice?.ongoing, notice?.token, notice?.ongoing) {
        value = try {
            notice?.ongoing ?: notice?.let { repository.resolveOngoing(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("NotificationArchive", "Failed to load ongoing flag", e)
            null
        }
    }
