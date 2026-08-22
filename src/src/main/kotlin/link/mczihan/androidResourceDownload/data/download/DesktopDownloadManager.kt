package link.mczihan.androidResourceDownload.data.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.core.platform.AppLogger
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
import link.mczihan.androidResourceDownload.domain.webdav.WebDavClient
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import java.io.File
import java.io.FileOutputStream

/**
 * Desktop download manager. Uses OkHttp (via WebDavClient) directly,
 * writes files to the user's Downloads folder. No Android Service,
 * MediaStore, or SAF required.
 */
class DesktopDownloadManager(
    private val webDavClient: WebDavClient,
    private val downloadDir: File,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()

    init {
        if (!downloadDir.exists()) downloadDir.mkdirs()
    }

    fun enqueue(remotePath: WebDavPath, fileName: String, mimeType: String? = null) {
        val taskId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val task = DownloadTask(
            id = taskId,
            ownerId = "desktop-user",
            fileName = fileName,
            remotePath = remotePath.toString(),
            mimeType = mimeType,
            totalBytes = null,
            downloadedBytes = 0L,
            status = DownloadStatus.PENDING,
            createdAt = now,
            updatedAt = now,
        )
        _tasks.value = _tasks.value + task
        AppLogger.info("加入下载队列: $fileName ($remotePath)")
        startDownload(task)
    }

    private fun startDownload(task: DownloadTask) {
        val job = scope.launch {
            updateTask(task.id) { it.copy(status = DownloadStatus.RUNNING, updatedAt = System.currentTimeMillis()) }
            try {
                val path = WebDavPath.parseDecoded(task.remotePath)
                val response = webDavClient.get(path)
                val totalBytes = response.metadata.contentLength
                    ?: response.contentRange?.totalLength
                val targetFile = File(downloadDir, task.fileName)
                var downloaded = 0L

                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = response.stream.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        downloaded += read
                        updateTask(task.id) {
                            it.copy(
                                downloadedBytes = downloaded,
                                totalBytes = totalBytes,
                                status = DownloadStatus.RUNNING,
                                updatedAt = System.currentTimeMillis(),
                            )
                        }
                    }
                    output.flush()
                }
                response.close()
                updateTask(task.id) {
                    it.copy(
                        status = DownloadStatus.SUCCESS,
                        downloadedBytes = downloaded,
                        totalBytes = totalBytes ?: downloaded,
                        updatedAt = System.currentTimeMillis(),
                    )
                }
                AppLogger.info("下载完成: ${task.fileName} -> ${targetFile.absolutePath}")
            } catch (error: Exception) {
                AppLogger.error("下载失败: ${task.fileName}", error)
                updateTask(task.id) {
                    it.copy(status = DownloadStatus.FAILED, errorMessage = error.message, updatedAt = System.currentTimeMillis())
                }
            }
        }
        activeJobs[task.id] = job
    }

    fun retry(taskId: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        AppLogger.info("重试下载: ${task.fileName}")
        startDownload(task)
    }

    fun cancel(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        updateTask(taskId) { it.copy(status = DownloadStatus.CANCELLED, updatedAt = System.currentTimeMillis()) }
        AppLogger.info("取消下载: taskId=$taskId")
    }

    fun openDownloadFolder() {
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("win") -> Runtime.getRuntime().exec("explorer.exe \"${downloadDir.absolutePath}\"")
            os.contains("mac") -> Runtime.getRuntime().exec("open \"${downloadDir.absolutePath}\"")
            else -> Runtime.getRuntime().exec("xdg-open \"${downloadDir.absolutePath}\"")
        }
    }

    fun openFile(task: DownloadTask) {
        val file = File(downloadDir, task.fileName)
        if (!file.exists()) return
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("win") -> Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler \"${file.absolutePath}\"")
            os.contains("mac") -> Runtime.getRuntime().exec("open \"${file.absolutePath}\"")
            else -> Runtime.getRuntime().exec("xdg-open \"${file.absolutePath}\"")
        }
    }

    private fun updateTask(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) transform(task) else task
        }
    }

    companion object {
        fun defaultDownloadDir(): File {
            val userHome = System.getProperty("user.home")
            return File(userHome, "Downloads")
        }
    }
}
