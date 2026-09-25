package link.mczihan.androidResourceDownload.data.download

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import link.mczihan.androidResourceDownload.core.platform.AppLogger
import java.io.File

/**
 * Persists download tasks to a JSON file in the app directory,
 * so completed/history tasks survive app restarts.
 */
class DownloadTaskStore(private val appDirectory: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val taskFile: File = File(appDirectory, "download_tasks.json")

    fun load(): List<DownloadTaskEntity> {
        if (!taskFile.exists()) return emptyList()
        return try {
            val decoded = json.decodeFromString(PersistedDownloadTasks.serializer(), taskFile.readText(Charsets.UTF_8))
            AppLogger.debug("DownloadTaskStore: 已加载 ${decoded.tasks.size} 个任务 (${taskFile.absolutePath})")
            decoded.tasks
        } catch (error: Exception) {
            AppLogger.error("DownloadTaskStore: 任务文件读取/解码失败，按空列表处理", error)
            emptyList()
        }
    }

    fun save(tasks: List<DownloadTaskEntity>) {
        try {
            taskFile.parentFile?.mkdirs()
            taskFile.writeText(
                json.encodeToString(PersistedDownloadTasks.serializer(), PersistedDownloadTasks(tasks)),
                Charsets.UTF_8,
            )
        } catch (error: Exception) {
            AppLogger.error("DownloadTaskStore: 任务保存失败 ${taskFile.absolutePath}", error)
        }
    }
}

@Serializable
private data class PersistedDownloadTasks(
    val tasks: List<DownloadTaskEntity>,
)
