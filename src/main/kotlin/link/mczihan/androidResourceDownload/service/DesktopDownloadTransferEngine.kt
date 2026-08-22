package link.mczihan.androidResourceDownload.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import link.mczihan.androidResourceDownload.data.download.DesktopPublicDownloadStore
import link.mczihan.androidResourceDownload.data.download.DownloadFileStore
import link.mczihan.androidResourceDownload.data.download.DownloadPreparation
import link.mczihan.androidResourceDownload.data.download.DownloadRepository
import link.mczihan.androidResourceDownload.data.download.DownloadTransferResult
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
import link.mczihan.androidResourceDownload.domain.webdav.WebDavByteRange
import link.mczihan.androidResourceDownload.domain.webdav.WebDavClient
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import java.io.FileOutputStream

/**
 * Desktop download transfer engine. Runs a coroutine loop per owner that
 * claims pending tasks and downloads them via WebDavClient.
 */
class DesktopDownloadTransferEngine(
    private val repository: DownloadRepository,
    private val webDavClient: WebDavClient,
    private val fileStore: DownloadFileStore,
    private val publicDownloadStore: DesktopPublicDownloadStore,
    private val executionRegistry: DesktopDownloadExecutionRegistry,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ownerLoops = mutableMapOf<String, Job>()

    fun start(ownerId: String) {
        if (ownerLoops[ownerId]?.isActive == true) return
        val job = scope.launch { runLoop(ownerId) }
        ownerLoops[ownerId] = job
    }

    suspend fun stop(ownerId: String) {
        ownerLoops[ownerId]?.let { it.cancel(); it.join() }
        ownerLoops.remove(ownerId)
    }

    fun isRunning(ownerId: String): Boolean = ownerLoops[ownerId]?.isActive == true

    private suspend fun runLoop(ownerId: String) {
        repository.recoverRunning(ownerId)
        while (coroutineContext.isActive) {
            val task = repository.claimNext(ownerId) ?: break
            executeTask(ownerId, task)
        }
    }

    private suspend fun executeTask(ownerId: String, task: DownloadTask) {
        val job = scope.launch {
            executionRegistry.register(task.id, ownerId, this.coroutineContext[Job]!!)
            try {
                val path = WebDavPath.parseDecoded(task.remotePath)

                // Check for an existing partial file to support resume (123pan WebDAV supports Range)
                fileStore.ensureTaskDirectory(task)
                val partialFile = fileStore.partialFile(task)
                val existingBytes = if (partialFile.isFile) partialFile.length() else 0L
                val range = if (existingBytes > 0L) {
                    WebDavByteRange(start = existingBytes, endInclusive = null)
                } else null

                val response = webDavClient.get(path, range = range)
                val isPartial = response.statusCode == 206
                val totalBytes = if (isPartial) {
                    response.contentRange?.totalLength ?: response.metadata.contentLength
                } else {
                    response.metadata.contentLength ?: response.contentRange?.totalLength
                }
                val mimeType = response.metadata.contentType ?: task.mimeType
                val etag = response.metadata.etag
                val lastModified = response.metadata.lastModified
                val supportRange = response.metadata.acceptsByteRanges || isPartial

                val startBytes = if (isPartial) existingBytes else 0L
                if (!isPartial && existingBytes > 0L) {
                    // Server ignored Range (returned 200); discard stale partial data
                    fileStore.truncatePartial(task)
                }

                repository.updatePreparation(
                    taskId = task.id,
                    preparation = DownloadPreparation(
                        totalBytes = totalBytes,
                        downloadedBytes = startBytes,
                        supportRange = supportRange,
                        etag = etag,
                        lastModified = lastModified,
                        mimeType = mimeType,
                    ),
                )

                var downloaded = startBytes
                FileOutputStream(partialFile, isPartial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (isActive) {
                        val read = response.stream.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded % (256 * 1024) < read) {
                            repository.updateProgress(task.id, downloaded)
                        }
                    }
                    output.flush()
                }
                response.close()

                if (!isActive) {
                    repository.requeueIfRunning(task.id)
                    return@launch
                }

                fileStore.finalize(task)
                repository.updateProgress(task.id, downloaded)

                val stageUri = publicDownloadStore.create(task, mimeType)
                repository.stagePublicUri(task.id, stageUri)
                val finalUri = publicDownloadStore.write(
                    task = task,
                    publicUri = stageUri,
                    source = fileStore.finalFile(task),
                    mimeType = mimeType,
                )
                fileStore.deleteAll(task)

                repository.complete(
                    taskId = task.id,
                    result = DownloadTransferResult(
                        totalBytes = totalBytes ?: downloaded,
                        downloadedBytes = downloaded,
                        supportRange = supportRange,
                        etag = etag,
                        lastModified = lastModified,
                        mimeType = mimeType,
                    ),
                    publicUri = finalUri,
                )
            } catch (error: Exception) {
                if (repository.status(task.id) == DownloadStatus.RUNNING) {
                    repository.fail(task.id, error.message ?: "下载失败")
                }
            } finally {
                executionRegistry.unregister(task.id, ownerId)
            }
        }
        job.join()
    }
}
