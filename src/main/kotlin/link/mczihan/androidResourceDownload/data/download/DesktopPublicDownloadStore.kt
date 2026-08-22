package link.mczihan.androidResourceDownload.data.download

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import link.mczihan.androidResourceDownload.domain.model.DownloadTask

/**
 * Desktop PublicDownloadStore. Writes completed downloads directly to the
 * user's Downloads folder. Uses file:// URIs as "publicUri" strings.
 */
class DesktopPublicDownloadStore(
    private val downloadsDir: File = defaultDownloadsDir(),
) {
    init {
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
    }

    suspend fun create(task: DownloadTask, mimeType: String?): String {
        val stage = File(downloadsDir, ".ard-${task.id}-${UUID.randomUUID()}.part")
        if (!stage.createNewFile()) throw java.io.IOException("Unable to reserve download stage")
        return stage.toURI().toString()
    }

    suspend fun write(
        task: DownloadTask,
        publicUri: String,
        source: File,
        mimeType: String?,
        onPublished: (String) -> Unit = {},
    ): String {
        if (!source.isFile) throw java.io.IOException("Downloaded file is missing")
        val stage = File(URI(publicUri))
        if (!stage.isFile) throw java.io.IOException("Reserved download stage is invalid")
        FileOutputStream(stage, false).use { output ->
            FileInputStream(source).use { input -> input.copyTo(output) }
            output.fd.sync()
        }
        val destination = moveToAvailableFile(stage, task.storageName)
        val resultUri = destination.toURI().toString()
        onPublished(resultUri)
        return resultUri
    }

    suspend fun exists(publicUri: String?): Boolean {
        if (publicUri == null) return false
        return runCatching { File(URI(publicUri)).isFile }.getOrDefault(false)
    }

    suspend fun delete(publicUri: String?): Boolean {
        if (publicUri == null) return true
        return runCatching {
            val file = File(URI(publicUri))
            if (Files.notExists(file.toPath())) true else file.delete()
        }.getOrDefault(false)
    }

    fun fileFor(publicUri: String?): File? {
        if (publicUri == null) return null
        return runCatching { File(URI(publicUri)) }.getOrNull()?.takeIf { it.isFile }
    }

    private fun moveToAvailableFile(stage: File, requestedName: String): File {
        val directory = stage.parentFile ?: throw java.io.IOException("Download stage has no parent")
        val extensionStart = requestedName.lastIndexOf('.').takeIf { it > 0 } ?: requestedName.length
        val base = requestedName.substring(0, extensionStart)
        val extension = requestedName.substring(extensionStart)
        for (index in 0..10000) {
            val name = if (index == 0) requestedName else "$base ($index)$extension"
            val candidate = File(directory, name).absoluteFile
            try {
                Files.move(stage.toPath(), candidate.toPath(), StandardCopyOption.REPLACE_EXISTING)
                return candidate
            } catch (_: Exception) {
                continue
            }
        }
        throw java.io.IOException("Unable to allocate a download name")
    }

    companion object {
        fun defaultDownloadsDir(): File {
            val userHome = System.getProperty("user.home")
            return File(userHome, "Downloads")
        }
    }
}
