package link.mczihan.androidResourceDownload.data.download

import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask

/**
 * Desktop file opener. Opens downloaded files with the OS default application.
 */
class DesktopDownloadFileOpener(
    private val publicDownloadStore: DesktopPublicDownloadStore,
    private val fileStore: DownloadFileStore,
) {
    fun open(task: DownloadTask): Boolean {
        if (task.status != DownloadStatus.SUCCESS) return false
        val file = publicDownloadStore.fileFor(task.publicUri)
            ?: fileStore.finalFile(task).takeIf { it.isFile }
            ?: return false
        return try {
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("win") -> {
                    // Open Explorer with the file selected (not launched)
                    val cmd = "explorer.exe /select,\"${file.absolutePath}\""
                    Runtime.getRuntime().exec(arrayOf("cmd.exe", "/c", cmd))
                }
                os.contains("mac") -> Runtime.getRuntime().exec(arrayOf("open", "-R", file.absolutePath))
                else -> {
                    val parent = file.parentFile?.absolutePath ?: file.absolutePath
                    Runtime.getRuntime().exec(arrayOf("xdg-open", parent))
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
