package link.mczihan.androidResourceDownload.data.file

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import link.mczihan.androidResourceDownload.domain.webdav.WebDavUpload

/**
 * Represents a file selected for upload on Windows.
 * Replaces Android's ContentResolverUploadSource which uses ContentResolver + Uri.
 * On Windows we use java.io.File directly.
 */
class UploadDocument(
    val displayName: String,
    val contentLength: Long?,
    val contentType: String?,
    private val openStream: () -> InputStream,
) {
    fun toWebDavUpload(onProgress: (Long) -> Unit = {}): WebDavUpload = WebDavUpload(
        contentLength = contentLength,
        contentType = contentType,
        openStream = openStream,
        onProgress = onProgress,
    )
}

/**
 * Resolves a java.io.File to an UploadDocument for WebDAV upload.
 * Windows equivalent of Android's ContentResolverUploadSource.
 */
class WindowsFileUploadSource {
    suspend fun resolve(file: File): UploadDocument = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) {
            throw IOException("Selected file does not exist: ${file.absolutePath}")
        }
        if (!file.canRead()) {
            throw IOException("Cannot read selected file: ${file.absolutePath}")
        }
        UploadDocument(
            displayName = file.name,
            contentLength = file.length(),
            contentType = guessContentType(file.name),
            openStream = { FileInputStream(file) },
        )
    }

    private fun guessContentType(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "txt", "md", "csv", "json", "xml", "html", "htm", "css", "js", "kt", "java", "py", "c", "cpp", "h", "log" -> "text/plain"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "apk" -> "application/vnd.android.package-archive"
            "exe" -> "application/vnd.microsoft.portable-executable"
            "msi" -> "application/x-msi"
            else -> null
        }
    }
}
