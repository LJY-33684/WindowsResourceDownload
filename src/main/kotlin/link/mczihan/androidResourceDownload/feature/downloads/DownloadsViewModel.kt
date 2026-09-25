package link.mczihan.androidResourceDownload.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.data.download.DesktopDownloadFileOpener
import link.mczihan.androidResourceDownload.data.download.DesktopPublicDownloadStore
import link.mczihan.androidResourceDownload.data.download.DownloadRepository
import link.mczihan.androidResourceDownload.data.download.EnqueueResult
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.service.DesktopDownloadQueueController

/** Download task enriched with desktop-specific UI state. */
data class DownloadTaskUi(
    val task: DownloadTask,
    val fileMissing: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModel(
    private val repository: DownloadRepository,
    private val queueController: DesktopDownloadQueueController,
    private val fileOpener: DesktopDownloadFileOpener,
    private val publicDownloadStore: DesktopPublicDownloadStore,
) : ViewModel() {
    private val ownerId = MutableStateFlow<String?>(null)
    private val messageChannel = Channel<String>(Channel.BUFFERED)

    // Periodically re-emits so file-missing state refreshes without waiting for a task mutation.
    private val fileCheckTicker = flow {
        emit(Unit)
        while (true) {
            delay(FILE_CHECK_INTERVAL_MILLIS)
            emit(Unit)
        }
    }

    val tasks = ownerId
        .filterNotNull()
        .flatMapLatest(repository::observe)
        .combine(fileCheckTicker) { list, _ -> list }
        .map { list ->
            list.map { task ->
                DownloadTaskUi(
                    task = task,
                    fileMissing = task.status == DownloadStatus.SUCCESS &&
                        publicDownloadStore.fileFor(task.publicUri) == null,
                )
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())
    private val speedEstimator = DownloadSpeedEstimator()
    private val _currentSpeeds = MutableStateFlow<Map<String, Long>>(emptyMap())
    val currentSpeeds = _currentSpeeds.asStateFlow()
    val messages = messageChannel.receiveAsFlow()
    private var speedTrackingJob: Job? = null

    fun startSpeedTracking() {
        if (speedTrackingJob?.isActive == true) return
        speedTrackingJob = viewModelScope.launch {
            while (isActive) {
                delay(SPEED_REFRESH_INTERVAL_MILLIS)
                _currentSpeeds.value = speedEstimator.update(
                    tasks.value.map { it.task },
                    System.currentTimeMillis(),
                )
            }
        }
    }

    fun stopSpeedTracking() {
        speedTrackingJob?.cancel()
        speedTrackingJob = null
        speedEstimator.clear()
        _currentSpeeds.value = emptyMap()
    }

    fun bindOwner(value: String) {
        queueController.activate(value)
        if (ownerId.value == value) return
        ownerId.value = value
        viewModelScope.launch {
            if (queueController.hasPublicDownloadAccess()) {
                repository.reconcileUncommittedPublications(value)
            }
            queueController.startIfNeeded(value)
        }
    }

    fun enqueue(file: FileNode, relativePath: String = "") {
        val currentOwner = ownerId.value ?: return
        viewModelScope.launch {
            val result = try {
                repository.enqueue(currentOwner, file, relativePath)
            } catch (_: IllegalArgumentException) {
                messageChannel.send("文件路径无效，无法下载")
                return@launch
            }
            val message = when (result) {
                EnqueueResult.ADDED -> "已加入下载队列"
                EnqueueResult.RESTARTED -> "已重新加入下载队列"
                EnqueueResult.ALREADY_QUEUED -> "该文件已在下载队列中"
                EnqueueResult.ALREADY_DOWNLOADED -> "该文件已经下载完成"
            }
            if ((result == EnqueueResult.ADDED || result == EnqueueResult.RESTARTED) &&
                !queueController.start(currentOwner)
            ) {
                messageChannel.send("任务已保存，系统暂未启动下载")
            } else {
                messageChannel.send(message)
            }
        }
    }

    fun pause(taskId: String) = withOwner { owner ->
        if (!queueController.pause(owner, taskId)) messageChannel.send("任务状态已发生变化")
    }

    fun retry(taskId: String) = withOwner { owner ->
        if (!queueController.retry(owner, taskId)) messageChannel.send("无法重新启动该任务")
    }

    fun startPending() = withOwner { owner ->
        repository.reconcileUncommittedPublications(owner)
        if (!queueController.startIfNeeded(owner)) messageChannel.send("无法启动下载队列")
    }

    fun cancel(taskId: String) = withOwner { owner ->
        if (!queueController.cancel(owner, taskId)) messageChannel.send("任务状态已发生变化")
    }

    fun delete(taskId: String) = withOwner { owner ->
        if (!queueController.deleteTerminal(owner, taskId)) {
            messageChannel.send("任务状态已变化，或本地文件无法删除")
        }
    }

    fun delete(taskId: String, deleteLocalFile: Boolean) = withOwner { owner ->
        if (!queueController.deleteTerminal(owner, taskId, deleteLocalFile)) {
            messageChannel.send("任务状态已变化，无法删除")
        }
    }

    fun cancelAll() = withOwner { owner ->
        val count = queueController.cancelAll(owner)
        messageChannel.send(if (count > 0) "已取消 $count 个任务" else "没有可取消的任务")
    }

    fun clearTerminal(deleteLocalFiles: Boolean) = withOwner { owner ->
        val count = queueController.clearTerminal(owner, deleteLocalFiles)
        messageChannel.send(if (count > 0) "已清除 $count 个已结束任务" else "没有可清除的任务")
    }

    fun open(task: DownloadTask) {
        if (!fileOpener.open(task)) {
            viewModelScope.launch { messageChannel.send("无法打开文件所在文件夹，或文件已被移除") }
        }
    }

    private fun withOwner(block: suspend (String) -> Unit) {
        val currentOwner = ownerId.value ?: return
        viewModelScope.launch { block(currentOwner) }
    }

    private companion object {
        const val SPEED_REFRESH_INTERVAL_MILLIS = 1_000L
        const val FILE_CHECK_INTERVAL_MILLIS = 1_000L
    }
}
