package link.mczihan.androidResourceDownload.data.download

import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask

data class DownloadTaskEntity(
    val id: String,
    val ownerId: String,
    val fileName: String,
    val remotePath: String,
    val storageName: String,
    val publicUri: String?,
    val mimeType: String?,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val supportRange: Boolean,
    val etag: String?,
    val lastModified: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toDomain(): DownloadTask = DownloadTask(
        id = id,
        ownerId = ownerId,
        fileName = fileName,
        remotePath = remotePath,
        storageName = storageName,
        publicUri = publicUri,
        mimeType = mimeType,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        status = status,
        supportRange = supportRange,
        etag = etag,
        lastModified = lastModified,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(task: DownloadTask): DownloadTaskEntity = DownloadTaskEntity(
            id = task.id,
            ownerId = task.ownerId,
            fileName = task.fileName,
            remotePath = task.remotePath,
            storageName = task.storageName,
            publicUri = task.publicUri,
            mimeType = task.mimeType,
            totalBytes = task.totalBytes,
            downloadedBytes = task.downloadedBytes,
            status = task.status,
            supportRange = task.supportRange,
            etag = task.etag,
            lastModified = task.lastModified,
            errorMessage = task.errorMessage,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
        )
    }
}
