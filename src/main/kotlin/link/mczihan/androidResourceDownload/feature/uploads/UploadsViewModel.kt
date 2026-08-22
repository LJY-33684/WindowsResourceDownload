package link.mczihan.androidResourceDownload.feature.uploads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import link.mczihan.androidResourceDownload.core.common.formatFileSize
import link.mczihan.androidResourceDownload.data.file.UploadDocument
import link.mczihan.androidResourceDownload.data.file.WebDavFileRepository
import link.mczihan.androidResourceDownload.data.file.WindowsFileUploadSource
import link.mczihan.androidResourceDownload.domain.model.UploadStatus
import link.mczihan.androidResourceDownload.domain.model.UploadTask
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath

class UploadsViewModel(
    private val repository: WebDavFileRepository,
    private val uploadSource: WindowsFileUploadSource,
) : ViewModel() {
    private val ownerId = MutableStateFlow<String?>(null)
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    private val _tasks = MutableStateFlow<List<UploadTask>>(emptyList())
    val tasks = _tasks.asStateFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val _preparingSelections = MutableStateFlow(0)
    val preparingSelections = _preparingSelections.asStateFlow()

    private val speedEstimator = UploadSpeedEstimator()
    private val _currentSpeeds = MutableStateFlow<Map<String, Long>>(emptyMap())
    val currentSpeeds = _currentSpeeds.asStateFlow()

    private var speedTrackingJob: Job? = null
    private val executionSemaphore = Semaphore(MAX_CONCURRENT_UPLOADS)
    private val activeJobs = mutableMapOf<String, Job>()
    private var queueOrderCounter = 0

    fun bindOwner(value: String) {
        if (ownerId.value == value) return
        ownerId.value = value
    }

    fun enqueueFiles(files: List<File>, destination: WebDavPath) {
        val owner = ownerId.value ?: return
        if (files.isEmpty()) return
        viewModelScope.launch {
            _preparingSelections.update { it + 1 }
            var added = 0
            var skipped = 0
            var failed = 0
            try {
                files.distinctBy { it.absolutePath }.forEach { file ->
                    try {
                        if (!file.exists() || !file.isFile) {
                            failed++
                            return@forEach
                        }
                        val (remotePath, uniqueFileName) = resolveUniqueFilePath(destination, file.name)
                        val doc = uploadSource.resolve(file)
                        addTask(
                            owner = owner,
                            fileName = uniqueFileName,
                            relativePath = uniqueFileName,
                            destinationRoot = destination.toString(),
                            remotePath = remotePath.toString(),
                            sourceFile = file,
                            isDirectory = false,
                            mimeType = doc.contentType,
                            totalBytes = doc.contentLength,
                        )
                        added++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        failed++
                    }
                }
                messageChannel.send(buildEnqueueMessage(added, skipped, failed))
                startPending()
            } finally {
                _preparingSelections.update { (it - 1).coerceAtLeast(0) }
            }
        }
    }

    fun enqueueDirectory(dir: File, destination: WebDavPath) {
        val owner = ownerId.value ?: return
        if (!dir.exists() || !dir.isDirectory) return
        viewModelScope.launch {
            _preparingSelections.update { it + 1 }
            var addedFiles = 0
            var addedDirs = 0
            var failed = 0
            try {
                val originalRootName = dir.name
                var rootName = originalRootName
                var rootRemote = runCatching { destination.child(rootName) }.getOrNull()
                if (rootRemote == null) {
                    messageChannel.send("文件夹名称无效")
                    return@launch
                }
                // Auto-rename root folder if it already exists
                var renameCounter = 1
                while ((isPathInQueue(rootRemote.toString()) || repository.resourceExists(rootRemote!!)) && renameCounter < 1000) {
                    rootName = "$originalRootName($renameCounter)"
                    val next = runCatching { destination.child(rootName) }.getOrNull()
                    if (next != null) {
                        rootRemote = next
                    }
                    renameCounter++
                }
                // Walk the tree: directories first (sorted by depth), then files
                val allFiles = mutableListOf<File>()
                val allDirs = mutableListOf<Pair<File, Int>>()
                dir.walkTopDown().forEach { f ->
                    val relPath = f.absolutePath.removePrefix(dir.absolutePath).trimStart('\\', '/')
                    val depth = if (relPath.isEmpty()) 0 else relPath.count { it == '\\' || it == '/' } + 1
                    if (f.isDirectory) {
                        allDirs.add(f to depth)
                    } else {
                        allFiles.add(f)
                    }
                }
                // Sort directories by depth so parents are created first
                allDirs.sortBy { it.second }
                // Create directory tasks
                allDirs.forEach { (d, depth) ->
                    val relPath = if (d == dir) {
                        rootName
                    } else {
                        rootName + "/" + d.absolutePath.removePrefix(dir.absolutePath).trimStart('\\', '/').replace('\\', '/')
                    }
                    val segments = relPath.split('/')
                    var current = destination
                    var valid = true
                    for (seg in segments) {
                        val next = runCatching { current.child(seg) }.getOrNull()
                        if (next == null) { valid = false; break }
                        current = next
                    }
                    if (!valid) { failed++; return@forEach }
                    if (isPathInQueue(current.toString())) return@forEach
                    if (repository.resourceExists(current)) return@forEach
                    addTask(
                        owner = owner,
                        fileName = segments.last(),
                        relativePath = relPath,
                        destinationRoot = destination.toString(),
                        remotePath = current.toString(),
                        sourceFile = null,
                        isDirectory = true,
                        mimeType = null,
                        totalBytes = null,
                        pathDepth = depth,
                    )
                    addedDirs++
                }
                // Create file tasks
                allFiles.forEach { f ->
                    val relPath = rootName + "/" + f.absolutePath.removePrefix(dir.absolutePath).trimStart('\\', '/').replace('\\', '/')
                    val segments = relPath.split('/')
                    val fileName = segments.last()
                    val parentSegments = segments.dropLast(1)
                    var parent = destination
                    var valid = true
                    for (seg in parentSegments) {
                        val next = runCatching { parent.child(seg) }.getOrNull()
                        if (next == null) { valid = false; break }
                        parent = next
                    }
                    if (!valid) { failed++; return@forEach }
                    val (remotePath, uniqueFileName) = resolveUniqueFilePath(parent, fileName)
                    val doc = runCatching { uploadSource.resolve(f) }.getOrNull()
                    if (doc == null) { failed++; return@forEach }
                    val uniqueRelPath = if (parentSegments.isNotEmpty()) {
                        parentSegments.joinToString("/") + "/" + uniqueFileName
                    } else {
                        uniqueFileName
                    }
                    addTask(
                        owner = owner,
                        fileName = uniqueFileName,
                        relativePath = uniqueRelPath,
                        destinationRoot = destination.toString(),
                        remotePath = remotePath.toString(),
                        sourceFile = f,
                        isDirectory = false,
                        mimeType = doc.contentType,
                        totalBytes = doc.contentLength,
                        pathDepth = segments.size,
                    )
                    addedFiles++
                }
                val total = addedFiles + addedDirs
                messageChannel.send(
                    if (total > 0 && failed == 0) {
                        "已加入上传队列：$addedFiles 个文件，$addedDirs 个文件夹"
                    } else if (total > 0) {
                        "已加入 $total 个任务，$failed 个失败"
                    } else {
                        "无法读取所选文件夹"
                    },
                )
                startPending()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                messageChannel.send("无法创建上传任务，请重试")
            } finally {
                _preparingSelections.update { (it - 1).coerceAtLeast(0) }
            }
        }
    }

    fun retry(taskId: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        if (task.status != UploadStatus.FAILED && task.status != UploadStatus.CANCELLED) return
        updateTask(taskId) { it.copy(status = UploadStatus.PENDING, errorMessage = null, uploadedBytes = 0L) }
        startPending()
    }

    fun cancel(taskId: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        if (task.status != UploadStatus.PENDING && task.status != UploadStatus.RUNNING) return
        if (task.committing) {
            messageChannel.trySend("任务已进入提交阶段，无法取消")
            return
        }
        activeJobs[taskId]?.cancel()
        updateTask(taskId) { it.copy(status = UploadStatus.CANCELLED) }
    }

    fun delete(taskId: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        if (task.status == UploadStatus.RUNNING || task.status == UploadStatus.PENDING) {
            activeJobs[taskId]?.cancel()
        }
        _tasks.update { it.filterNot { t -> t.id == taskId } }
        activeJobs.remove(taskId)
    }

    fun cancelAll() {
        val toCancel = _tasks.value.filter {
            (it.status == UploadStatus.PENDING || it.status == UploadStatus.RUNNING) && !it.committing
        }
        toCancel.forEach { task ->
            activeJobs[task.id]?.cancel()
            updateTask(task.id) { it.copy(status = UploadStatus.CANCELLED) }
        }
        viewModelScope.launch {
            messageChannel.send(if (toCancel.isNotEmpty()) "已取消 ${toCancel.size} 个任务" else "没有可取消的任务")
        }
    }

    fun clearTerminal() {
        val terminal = _tasks.value.filter { it.status == UploadStatus.FAILED || it.status == UploadStatus.CANCELLED }
        _tasks.update { it.filterNot { t -> t.status == UploadStatus.FAILED || t.status == UploadStatus.CANCELLED } }
        viewModelScope.launch {
            messageChannel.send(if (terminal.isNotEmpty()) "已清除 ${terminal.size} 个已结束任务" else "没有可清除的任务")
        }
    }

    fun startPending() {
        val owner = ownerId.value ?: return
        val pending = _tasks.value
            .filter { it.status == UploadStatus.PENDING }
            .sortedBy { it.queueOrder }
        pending.forEach { task ->
            if (activeJobs.containsKey(task.id)) return@forEach
            val job = viewModelScope.launch {
                executionSemaphore.withPermit {
                    executeTask(task.id, owner)
                }
            }
            activeJobs[task.id] = job
        }
    }

    fun startSpeedTracking() {
        if (speedTrackingJob?.isActive == true) return
        speedTrackingJob = viewModelScope.launch {
            coroutineScope {
                launch {
                    tasks.collect { currentTasks ->
                        _currentSpeeds.value = speedEstimator.update(currentTasks, System.currentTimeMillis())
                    }
                }
                launch {
                    while (isActive) {
                        delay(SPEED_REFRESH_INTERVAL_MILLIS)
                        _currentSpeeds.value = speedEstimator.snapshot(System.currentTimeMillis())
                    }
                }
            }
        }
    }

    fun stopSpeedTracking() {
        speedTrackingJob?.cancel()
        speedTrackingJob = null
        speedEstimator.clear()
        _currentSpeeds.value = emptyMap()
    }

    private suspend fun executeTask(taskId: String, owner: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        if (task.ownerId != owner) return
        updateTask(taskId) { it.copy(status = UploadStatus.RUNNING) }
        try {
            if (task.isDirectory) {
                val path = WebDavPath.parseDecoded(task.remotePath)
                repository.createDirectory(path)
                // Auto-remove on success (matches Android completeAndDelete behavior)
                _tasks.update { it.filterNot { t -> t.id == taskId } }
            } else {
                val file = task.sourceFile ?: run {
                    updateTask(taskId) { it.copy(status = UploadStatus.FAILED, errorMessage = "源文件不存在") }
                    return
                }
                if (!file.exists()) {
                    updateTask(taskId) { it.copy(status = UploadStatus.FAILED, errorMessage = "源文件已被移动或删除") }
                    return
                }
                val doc = uploadSource.resolve(file)
                val path = WebDavPath.parseDecoded(task.remotePath)
                val totalBytes = doc.contentLength
                var lastUpdateAt = 0L
                repository.upload(
                    path = path,
                    upload = doc.toWebDavUpload { uploaded ->
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateAt >= PROGRESS_UPDATE_INTERVAL_MILLIS || uploaded == totalBytes) {
                            lastUpdateAt = now
                            updateTask(taskId) { it.copy(uploadedBytes = uploaded) }
                        }
                    },
                    overwrite = false,
                    onCommitting = {
                        updateTask(taskId) { it.copy(committing = true, uploadedBytes = totalBytes ?: it.uploadedBytes) }
                    },
                )
                // Auto-remove on success (matches Android completeAndDelete behavior)
                _tasks.update { it.filterNot { t -> t.id == taskId } }
            }
        } catch (e: CancellationException) {
            updateTask(taskId) { it.copy(status = UploadStatus.CANCELLED, committing = false) }
            throw e
        } catch (e: Exception) {
            updateTask(taskId) {
                it.copy(
                    status = UploadStatus.FAILED,
                    errorMessage = e.message ?: "上传失败",
                    committing = false,
                )
            }
        } finally {
            activeJobs.remove(taskId)
            // Start next pending task
            startPending()
        }
    }

    private fun addTask(
        owner: String,
        fileName: String,
        relativePath: String,
        destinationRoot: String,
        remotePath: String,
        sourceFile: File?,
        isDirectory: Boolean,
        mimeType: String?,
        totalBytes: Long?,
        pathDepth: Int = 0,
    ) {
        val now = System.currentTimeMillis()
        val task = UploadTask(
            id = UUID.randomUUID().toString(),
            ownerId = owner,
            fileName = fileName,
            relativePath = relativePath,
            destinationRoot = destinationRoot,
            remotePath = remotePath,
            sourceFile = sourceFile,
            isDirectory = isDirectory,
            mimeType = mimeType,
            totalBytes = totalBytes,
            status = UploadStatus.PENDING,
            queueOrder = queueOrderCounter++,
            createdAt = now,
            updatedAt = now,
        )
        _tasks.update { it + task }
    }

    private fun isPathInQueue(remotePath: String): Boolean =
        _tasks.value.any { it.remotePath == remotePath && it.status != UploadStatus.SUCCESS && it.status != UploadStatus.FAILED }

    private fun appendSuffixToFileName(fileName: String, suffix: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex > 0) {
            fileName.substring(0, dotIndex) + suffix + fileName.substring(dotIndex)
        } else {
            fileName + suffix
        }
    }

    private suspend fun resolveUniqueFilePath(
        destination: WebDavPath,
        fileName: String,
    ): Pair<WebDavPath, String> {
        val originalPath = runCatching { destination.child(fileName) }.getOrNull()
        if (originalPath != null) {
            val pathStr = originalPath.toString()
            if (!isPathInQueue(pathStr) && !repository.resourceExists(originalPath)) {
                return originalPath to fileName
            }
        }
        var counter = 1
        while (counter < 1000) {
            val candidateName = appendSuffixToFileName(fileName, "($counter)")
            val path = runCatching { destination.child(candidateName) }.getOrNull()
            if (path != null) {
                val pathStr = path.toString()
                if (!isPathInQueue(pathStr) && !repository.resourceExists(path)) {
                    return path to candidateName
                }
            }
            counter++
        }
        return (originalPath ?: destination.child(fileName)) to fileName
    }

    private fun updateTask(taskId: String, transform: (UploadTask) -> UploadTask) {
        _tasks.update { list ->
            list.map { task ->
                if (task.id == taskId) transform(task).copy(updatedAt = System.currentTimeMillis()) else task
            }
        }
    }

    private fun buildEnqueueMessage(added: Int, skipped: Int, failed: Int): String = when {
        added > 0 && failed == 0 && skipped == 0 -> "$added 个文件已加入上传队列"
        added > 0 -> "已加入 $added 个文件，跳过 ${failed + skipped} 个"
        failed > 0 -> "无法读取所选文件，请重新选择"
        else -> "所选文件已在上传队列中"
    }

    private companion object {
        const val MAX_CONCURRENT_UPLOADS = 2
        const val SPEED_REFRESH_INTERVAL_MILLIS = 1_000L
        const val PROGRESS_UPDATE_INTERVAL_MILLIS = 500L
    }
}
