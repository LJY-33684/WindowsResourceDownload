package link.mczihan.androidResourceDownload.data.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus

/**
 * In-memory replacement for Room DownloadTaskDao.
 * Stores tasks in a MutableStateFlow list.
 * When a [DownloadTaskStore] and [scope] are provided, every task mutation is
 * persisted (debounced) to disk so history survives app restarts.
 */
class InMemoryDownloadTaskDao(
    private val store: DownloadTaskStore? = null,
    scope: CoroutineScope? = null,
) {
    private val tasks = MutableStateFlow<List<DownloadTaskEntity>>(emptyList())
    private var saveJob: Job? = null

    init {
        if (store != null) {
            tasks.value = store.load()
        }
        if (store != null && scope != null) {
            scope.launch {
                tasks.collect { snapshot ->
                    saveJob?.cancel()
                    saveJob = scope.launch {
                        delay(SAVE_DEBOUNCE_MILLIS)
                        withContext(Dispatchers.IO) {
                            store.save(snapshot)
                        }
                    }
                }
            }
        }
    }

    fun observeForOwner(ownerId: String): Flow<List<DownloadTaskEntity>> =
        tasks.map { list ->
            list.filter { it.ownerId == ownerId }
                .sortedWith(
                    compareByDescending<DownloadTaskEntity> { statusOrder(it.status) }
                        .thenByDescending { it.createdAt }
                )
        }

    private fun statusOrder(status: DownloadStatus): Int = when (status) {
        DownloadStatus.RUNNING -> 0
        DownloadStatus.PENDING -> 1
        DownloadStatus.PAUSED -> 2
        DownloadStatus.FAILED -> 3
        DownloadStatus.SUCCESS -> 4
        DownloadStatus.CANCELLED -> 5
    }

    suspend fun findLatest(ownerId: String, remotePath: String): DownloadTaskEntity? =
        tasks.value.filter { it.ownerId == ownerId && it.remotePath == remotePath }
            .maxByOrNull { it.createdAt }

    suspend fun findById(ownerId: String, taskId: String): DownloadTaskEntity? =
        tasks.value.find { it.id == taskId && it.ownerId == ownerId }

    suspend fun status(taskId: String): DownloadStatus? =
        tasks.value.find { it.id == taskId }?.status

    suspend fun findUncommittedPublications(ownerId: String): List<DownloadTaskEntity> =
        tasks.value.filter {
            it.ownerId == ownerId &&
                it.status in setOf(DownloadStatus.PENDING, DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.CANCELLED) &&
                it.publicUri != null
        }

    suspend fun attachPublicUri(taskId: String, publicUri: String, now: Long): Int {
        var updated = 0
        tasks.value = tasks.value.map { task ->
            if (task.id == taskId && task.status == DownloadStatus.SUCCESS && task.publicUri == null) {
                updated = 1
                task.copy(publicUri = publicUri, updatedAt = now)
            } else task
        }
        return updated
    }

    suspend fun clearCompletedPublicUri(taskId: String, publicUri: String, now: Long): Int {
        var updated = 0
        tasks.value = tasks.value.map { task ->
            if (task.id == taskId && task.publicUri == publicUri && task.status == DownloadStatus.SUCCESS) {
                updated = 1
                task.copy(publicUri = null, updatedAt = now)
            } else task
        }
        return updated
    }

    suspend fun stagePublicUri(taskId: String, publicUri: String, now: Long): Int {
        var updated = 0
        tasks.value = tasks.value.map { task ->
            if (task.id == taskId && task.status == DownloadStatus.RUNNING && task.publicUri == null) {
                updated = 1
                task.copy(publicUri = publicUri, updatedAt = now)
            } else task
        }
        return updated
    }

    suspend fun clearPublicUri(taskId: String, publicUri: String, now: Long): Int {
        var updated = 0
        tasks.value = tasks.value.map { task ->
            if (task.id == taskId && task.publicUri == publicUri && task.status != DownloadStatus.SUCCESS) {
                updated = 1
                task.copy(publicUri = null, updatedAt = now)
            } else task
        }
        return updated
    }

    suspend fun insert(task: DownloadTaskEntity) {
        tasks.value = tasks.value + task
    }

    suspend fun claimNext(ownerId: String, now: Long): DownloadTaskEntity? {
        val candidate = tasks.value
            .filter { it.ownerId == ownerId && it.status == DownloadStatus.PENDING }
            .minByOrNull { it.createdAt } ?: return null
        var claimed: DownloadTaskEntity? = null
        tasks.value = tasks.value.map { task ->
            if (task.id == candidate.id && task.status == DownloadStatus.PENDING) {
                claimed = task.copy(status = DownloadStatus.RUNNING, errorMessage = null, updatedAt = now)
                claimed!!
            } else task
        }
        return claimed
    }

    suspend fun retry(ownerId: String, taskId: String, now: Long): Int {
        var updated = 0
        tasks.value = tasks.value.map { task ->
            if (task.id == taskId && task.ownerId == ownerId &&
                task.status in setOf(DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.CANCELLED)
            ) {
                updated = 1
                task.copy(status = DownloadStatus.PENDING, errorMessage = null, updatedAt = now)
            } else task
        }
        return updated
    }

    suspend fun pause(ownerId: String, taskId: String, now: Long): Int {
        var updated = 0
        tasks.value = tasks.value.map { task ->
            if (task.id == taskId && task.ownerId == ownerId && task.status == DownloadStatus.RUNNING) {
                updated = 1
                task.copy(status = DownloadStatus.PAUSED, updatedAt = now)
            } else task
        }
        return updated
    }

    suspend fun pauseRunning(ownerId: String, now: Long) {
        tasks.value = tasks.value.map { task ->
            if (task.ownerId == ownerId && task.status == DownloadStatus.RUNNING) {
                task.copy(status = DownloadStatus.PAUSED, updatedAt = now)
            } else task
        }
    }

    suspend fun cancel(ownerId: String, taskId: String, now: Long): Int {
        var updated = 0
        tasks.value = tasks.value.map { task ->
            if (task.id == taskId && task.ownerId == ownerId &&
                task.status in setOf(DownloadStatus.PENDING, DownloadStatus.RUNNING, DownloadStatus.PAUSED, DownloadStatus.FAILED)
            ) {
                updated = 1
                task.copy(status = DownloadStatus.CANCELLED, errorMessage = null, updatedAt = now)
            } else task
        }
        return updated
    }

    suspend fun deleteTerminal(ownerId: String, taskId: String): Int {
        var deleted = 0
        tasks.value = tasks.value.filterNot { task ->
            if (task.id == taskId && task.ownerId == ownerId &&
                task.status in setOf(DownloadStatus.SUCCESS, DownloadStatus.FAILED, DownloadStatus.CANCELLED)
            ) {
                deleted = 1
                true
            } else false
        }
        return deleted
    }

    suspend fun recoverRunning(ownerId: String, now: Long) {
        tasks.value = tasks.value.map { task ->
            if (task.ownerId == ownerId && task.status == DownloadStatus.RUNNING) {
                task.copy(status = DownloadStatus.PENDING, updatedAt = now)
            } else task
        }
    }

    suspend fun updatePreparation(
        taskId: String, totalBytes: Long?, downloadedBytes: Long, supportRange: Boolean,
        etag: String?, lastModified: String?, mimeType: String?, now: Long,
    ): Int {
        var updated = 0
        tasks.value = tasks.value.map { task ->
            if (task.id == taskId && task.status == DownloadStatus.RUNNING) {
                updated = 1
                task.copy(
                    totalBytes = totalBytes,
                    downloadedBytes = downloadedBytes,
                    supportRange = supportRange,
                    etag = etag,
                    lastModified = lastModified,
                    mimeType = mimeType ?: task.mimeType,
                    updatedAt = now,
                )
            } else task
        }
        return updated
    }

    suspend fun updateProgress(taskId: String, downloadedBytes: Long, now: Long): Int {
        var updated = 0
        tasks.value = tasks.value.map { task ->
            if (task.id == taskId && task.status == DownloadStatus.RUNNING) {
                updated = 1
                task.copy(downloadedBytes = downloadedBytes, updatedAt = now)
            } else task
        }
        return updated
    }

    suspend fun complete(
        taskId: String, totalBytes: Long?, downloadedBytes: Long, supportRange: Boolean,
        etag: String?, lastModified: String?, publicUri: String, mimeType: String?, now: Long,
    ): Int {
        var updated = 0
        tasks.value = tasks.value.map { task ->
            if (task.id == taskId && task.status == DownloadStatus.RUNNING) {
                updated = 1
                task.copy(
                    status = DownloadStatus.SUCCESS,
                    totalBytes = totalBytes,
                    downloadedBytes = downloadedBytes,
                    supportRange = supportRange,
                    etag = etag,
                    lastModified = lastModified,
                    publicUri = publicUri,
                    mimeType = mimeType ?: task.mimeType,
                    errorMessage = null,
                    updatedAt = now,
                )
            } else task
        }
        return updated
    }

    suspend fun fail(taskId: String, message: String, now: Long) {
        tasks.value = tasks.value.map { task ->
            if (task.id == taskId && task.status == DownloadStatus.RUNNING) {
                task.copy(status = DownloadStatus.FAILED, errorMessage = message, updatedAt = now)
            } else task
        }
    }

    suspend fun requeueIfRunning(taskId: String, now: Long) {
        tasks.value = tasks.value.map { task ->
            if (task.id == taskId && task.status == DownloadStatus.RUNNING) {
                task.copy(status = DownloadStatus.PENDING, updatedAt = now)
            } else task
        }
    }

    suspend fun hasRunnable(ownerId: String): Boolean =
        tasks.value.any { it.ownerId == ownerId && it.status in setOf(DownloadStatus.PENDING, DownloadStatus.RUNNING) }

    suspend fun cancelAllPending(ownerId: String, now: Long): Int {
        var updated = 0
        tasks.value = tasks.value.map { task ->
            if (task.ownerId == ownerId &&
                task.status in setOf(DownloadStatus.PENDING, DownloadStatus.RUNNING, DownloadStatus.PAUSED)
            ) {
                updated++
                task.copy(status = DownloadStatus.CANCELLED, errorMessage = null, updatedAt = now)
            } else task
        }
        return updated
    }

    suspend fun deleteTerminalAll(ownerId: String): Int {
        var deleted = 0
        tasks.value = tasks.value.filterNot { task ->
            if (task.ownerId == ownerId &&
                task.status in setOf(DownloadStatus.SUCCESS, DownloadStatus.FAILED, DownloadStatus.CANCELLED)
            ) {
                deleted++
                true
            } else false
        }
        return deleted
    }

    suspend fun tasksForOwnerTerminal(ownerId: String): List<DownloadTaskEntity> =
        tasks.value.filter {
            it.ownerId == ownerId &&
                it.status in setOf(DownloadStatus.SUCCESS, DownloadStatus.FAILED, DownloadStatus.CANCELLED)
        }

    private companion object {
        const val SAVE_DEBOUNCE_MILLIS = 500L
    }
}
