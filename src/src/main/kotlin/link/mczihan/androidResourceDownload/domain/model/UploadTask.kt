package link.mczihan.androidResourceDownload.domain.model

import java.io.File

data class UploadTask(
    val id: String,
    val ownerId: String,
    val fileName: String,
    val relativePath: String,
    val destinationRoot: String,
    val remotePath: String,
    val sourceFile: File?,
    val isDirectory: Boolean,
    val mimeType: String?,
    val totalBytes: Long?,
    val uploadedBytes: Long = 0L,
    val status: UploadStatus = UploadStatus.PENDING,
    val committing: Boolean = false,
    val errorMessage: String? = null,
    val queueOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class UploadStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
}
