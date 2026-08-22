package link.mczihan.androidResourceDownload.service

import java.util.concurrent.ConcurrentHashMap
import link.mczihan.androidResourceDownload.data.download.DownloadRepository

/**
 * Desktop DownloadQueueController. Controls the download queue using the
 * DesktopDownloadTransferEngine instead of Android foreground services.
 */
class DesktopDownloadQueueController(
    private val repository: DownloadRepository,
    private val executionRegistry: DesktopDownloadExecutionRegistry,
    private val transferEngine: DesktopDownloadTransferEngine,
) {
    private val blockedOwners = ConcurrentHashMap.newKeySet<String>()

    fun activate(ownerId: String) {
        blockedOwners.remove(ownerId)
    }

    fun isBlocked(ownerId: String): Boolean = ownerId in blockedOwners

    fun block(ownerId: String) {
        blockedOwners += ownerId
        executionRegistry.cancelOwner(ownerId)
    }

    fun start(ownerId: String): Boolean {
        if (ownerId in blockedOwners || !hasPublicDownloadAccess()) return false
        transferEngine.start(ownerId)
        return true
    }

    suspend fun startIfNeeded(ownerId: String): Boolean =
        !repository.hasRunnable(ownerId) || start(ownerId)

    suspend fun pause(ownerId: String, taskId: String): Boolean {
        val changed = repository.pause(ownerId, taskId)
        if (changed) executionRegistry.cancelTask(taskId)
        return changed
    }

    suspend fun retry(ownerId: String, taskId: String): Boolean {
        if (ownerId in blockedOwners || !hasPublicDownloadAccess()) return false
        executionRegistry.cancelTaskAndJoin(taskId)
        val changed = repository.retry(ownerId, taskId)
        return changed && start(ownerId)
    }

    suspend fun cancel(ownerId: String, taskId: String): Boolean {
        val changed = repository.cancel(ownerId, taskId)
        if (changed) {
            executionRegistry.cancelTaskAndJoin(taskId)
            repository.cleanupCancelled(ownerId, taskId)
        }
        return changed
    }

    suspend fun deleteTerminal(ownerId: String, taskId: String): Boolean {
        executionRegistry.cancelTaskAndJoin(taskId)
        return repository.deleteTerminal(ownerId, taskId)
    }

    suspend fun deleteTerminal(ownerId: String, taskId: String, deleteLocalFile: Boolean): Boolean {
        executionRegistry.cancelTaskAndJoin(taskId)
        return repository.deleteTerminal(ownerId, taskId, deleteLocalFile)
    }

    suspend fun cancelAll(ownerId: String): Int {
        executionRegistry.cancelOwnerAndJoin(ownerId)
        return repository.cancelAll(ownerId)
    }

    suspend fun clearTerminal(ownerId: String, deleteLocalFiles: Boolean): Int =
        repository.clearTerminal(ownerId, deleteLocalFiles)

    suspend fun stop(ownerId: String) {
        block(ownerId)
        repository.pauseRunning(ownerId)
        executionRegistry.cancelOwnerAndJoin(ownerId)
        transferEngine.stop(ownerId)
    }

    fun hasPublicDownloadAccess(): Boolean = true
}
