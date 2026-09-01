package link.mczihan.androidResourceDownload.feature.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.core.common.formatFileSize
import link.mczihan.androidResourceDownload.core.ui.EmptyPane
import link.mczihan.androidResourceDownload.core.ui.FastScrollbar
import link.mczihan.androidResourceDownload.core.ui.FloatingAction
import link.mczihan.androidResourceDownload.core.ui.FloatingActionMenu
import link.mczihan.androidResourceDownload.core.ui.SearchTopAppBar
import link.mczihan.androidResourceDownload.core.ui.SelectionAction
import link.mczihan.androidResourceDownload.core.ui.SelectionBottomBar
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    tasks: List<DownloadTask>,
    currentSpeeds: Map<String, Long> = emptyMap(),
    onStatusChange: (taskId: String, status: DownloadStatus) -> Unit,
    onOpen: (DownloadTask) -> Unit,
    onDelete: (taskId: String) -> Unit,
    onDeleteWithOption: (taskId: String, deleteLocalFile: Boolean) -> Unit = { _, _ -> },
    onCancelAll: () -> Unit = {},
    onClearTerminal: (deleteLocalFiles: Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedTaskIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var showActionMenu by remember { mutableStateOf(false) }
    var deleteTaskId by remember { mutableStateOf<String?>(null) }
    var deleteTaskIds by remember { mutableStateOf<List<String>?>(null) }
    var deleteLocalFile by remember { mutableStateOf(true) }
    var showCancelAllDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var clearLocalFiles by remember { mutableStateOf(true) }

    val filteredTasks = filterDownloadTasks(tasks, if (searchActive) searchQuery else "")
    val selectedTaskIdSet = selectedTaskIds.toSet()
    val selectedTasks = tasks.filter { it.id in selectedTaskIdSet }
    val allVisibleTasksSelected = filteredTasks.isNotEmpty() &&
        filteredTasks.all { it.id in selectedTaskIdSet }
    val resumableTasks = selectedTasks.filter { task ->
        task.status in setOf(
            DownloadStatus.PAUSED,
            DownloadStatus.FAILED,
            DownloadStatus.CANCELLED,
        )
    }
    val pausableTasks = selectedTasks.filter { it.status == DownloadStatus.RUNNING }
    val cancellableSelectedTasks = selectedTasks.filter { task ->
        task.status in setOf(
            DownloadStatus.PENDING,
            DownloadStatus.RUNNING,
            DownloadStatus.PAUSED,
        )
    }
    val deletableSelectedTasks = selectedTasks.filter { task ->
        task.status in setOf(
            DownloadStatus.SUCCESS,
            DownloadStatus.FAILED,
            DownloadStatus.CANCELLED,
        )
    }
    val hasCancellableTasks = tasks.any { task ->
        task.status in setOf(
            DownloadStatus.PENDING,
            DownloadStatus.RUNNING,
            DownloadStatus.PAUSED,
        )
    }
    val hasClearableTasks = tasks.any { task ->
        task.status in setOf(
            DownloadStatus.SUCCESS,
            DownloadStatus.FAILED,
            DownloadStatus.CANCELLED,
        )
    }

    fun exitMultiSelect() {
        multiSelectMode = false
        selectedTaskIds = emptyList()
    }

    fun toggleTaskSelection(taskId: String) {
        selectedTaskIds = selectedTaskIds.toMutableList().apply {
            if (contains(taskId)) remove(taskId) else add(taskId)
        }
    }

    fun toggleAllVisibleTasks() {
        val visibleIds = filteredTasks.map(DownloadTask::id)
        selectedTaskIds = if (visibleIds.isNotEmpty() && visibleIds.all(selectedTaskIdSet::contains)) {
            selectedTaskIds.filterNot(visibleIds::contains)
        } else {
            (selectedTaskIds + visibleIds).distinct()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (multiSelectMode) {
                TopAppBar(
                    title = { Text("已选择 ${selectedTasks.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = { exitMultiSelect() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出下载任务选择")
                        }
                    },
                )
            } else if (searchActive) {
                SearchTopAppBar(
                    query = searchQuery,
                    placeholder = "搜索下载任务",
                    closeContentDescription = "关闭下载搜索",
                    searchContentDescription = "执行下载搜索",
                    onQueryChange = { searchQuery = it },
                    onSearch = {},
                    showSearchAction = false,
                    onClose = {
                        searchActive = false
                        searchQuery = ""
                    },
                    subtitle = searchQuery.trim().takeIf(String::isNotEmpty)?.let {
                        "${filteredTasks.size} / ${tasks.size} 个任务"
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("下载")
                            Text(
                                text = if (tasks.isEmpty()) "暂无任务" else "${tasks.size} 个任务",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索下载任务")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (multiSelectMode) {
                SelectionBottomBar {
                    SelectionAction(
                        icon = if (allVisibleTasksSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                        label = if (allVisibleTasksSelected) "取消全选" else "全选",
                        onClick = ::toggleAllVisibleTasks,
                        enabled = filteredTasks.isNotEmpty(),
                    )
                    SelectionAction(
                        icon = Icons.Default.PlayArrow,
                        label = "继续",
                        enabled = resumableTasks.isNotEmpty(),
                        onClick = {
                            resumableTasks.forEach { onStatusChange(it.id, DownloadStatus.RUNNING) }
                            exitMultiSelect()
                        },
                    )
                    SelectionAction(
                        icon = Icons.Default.Pause,
                        label = "暂停",
                        enabled = pausableTasks.isNotEmpty(),
                        onClick = {
                            pausableTasks.forEach { onStatusChange(it.id, DownloadStatus.PAUSED) }
                            exitMultiSelect()
                        },
                    )
                    SelectionAction(
                        icon = Icons.Default.Cancel,
                        label = "取消",
                        enabled = cancellableSelectedTasks.isNotEmpty(),
                        destructive = true,
                        onClick = {
                            cancellableSelectedTasks.forEach {
                                onStatusChange(it.id, DownloadStatus.CANCELLED)
                            }
                            exitMultiSelect()
                        },
                    )
                    SelectionAction(
                        icon = Icons.Default.Delete,
                        label = "删除",
                        enabled = deletableSelectedTasks.isNotEmpty(),
                        destructive = true,
                        onClick = {
                            deleteLocalFile = true
                            deleteTaskIds = deletableSelectedTasks.map(DownloadTask::id)
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            if (!multiSelectMode && (hasCancellableTasks || hasClearableTasks)) {
                FloatingActionMenu(
                    expanded = showActionMenu,
                    onExpandedChange = { showActionMenu = it },
                ) {
                    if (hasCancellableTasks) {
                        FloatingAction(
                            icon = Icons.Default.Cancel,
                            label = "全部取消",
                            destructive = true,
                            onClick = {
                                showActionMenu = false
                                showCancelAllDialog = true
                            },
                        )
                    }
                    if (hasClearableTasks) {
                        FloatingAction(
                            icon = Icons.Default.DeleteSweep,
                            label = "全部清除",
                            destructive = true,
                            onClick = {
                                showActionMenu = false
                                clearLocalFiles = true
                                showClearDialog = true
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        if (filteredTasks.isEmpty()) {
            EmptyPane(
                message = if (tasks.isEmpty()) "暂无下载任务" else "未找到匹配的下载任务",
                modifier = Modifier.padding(innerPadding),
                icon = Icons.Default.Download,
            )
        } else {
            val listState = rememberLazyListState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                ) {
                    items(filteredTasks, key = DownloadTask::id) { task ->
                        DownloadTaskItem(
                            task = task,
                            currentSpeed = currentSpeeds[task.id] ?: 0L,
                            onStatusChange = { status -> onStatusChange(task.id, status) },
                            onOpen = { onOpen(task) },
                            onDelete = {
                                deleteTaskId = task.id
                                deleteLocalFile = true
                            },
                            selectionMode = multiSelectMode,
                            selected = task.id in selectedTaskIdSet,
                            onSelectionToggle = { toggleTaskSelection(task.id) },
                            onLongSelect = {
                                multiSelectMode = true
                                toggleTaskSelection(task.id)
                            },
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                FastScrollbar(
                    listState = listState,
                    itemCount = filteredTasks.size,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }

    if (deleteTaskId != null) {
        AlertDialog(
            onDismissRequest = { deleteTaskId = null },
            title = { Text("删除任务") },
            text = {
                Column {
                    Text("确定要删除此下载任务吗？")
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = deleteLocalFile,
                            onCheckedChange = { deleteLocalFile = it },
                        )
                        Text("同时删除本地文件")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = deleteTaskId
                    deleteTaskId = null
                    if (id != null) onDeleteWithOption(id, deleteLocalFile)
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTaskId = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (deleteTaskIds != null) {
        val ids = deleteTaskIds ?: emptyList()
        AlertDialog(
            onDismissRequest = { deleteTaskIds = null },
            title = { Text("删除 ${ids.size} 个任务") },
            text = {
                Column {
                    Text("确定要删除选中的下载任务吗？")
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = deleteLocalFile,
                            onCheckedChange = { deleteLocalFile = it },
                        )
                        Text("同时删除本地文件")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTaskIds = null
                    exitMultiSelect()
                    ids.forEach { id -> onDeleteWithOption(id, deleteLocalFile) }
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTaskIds = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (showCancelAllDialog) {
        AlertDialog(
            onDismissRequest = { showCancelAllDialog = false },
            title = { Text("全部取消") },
            text = { Text("确定要取消所有未完成的下载任务吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelAllDialog = false
                    onCancelAll()
                }) {
                    Text("取消全部")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelAllDialog = false }) {
                    Text("返回")
                }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("全部清除") },
            text = {
                Column {
                    Text("确定要清除所有已结束的下载任务吗？")
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = clearLocalFiles,
                            onCheckedChange = { clearLocalFiles = it },
                        )
                        Text("同时删除本地文件")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClearTerminal(clearLocalFiles)
                }) {
                    Text("清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun DownloadTaskItem(
    task: DownloadTask,
    currentSpeed: Long,
    onStatusChange: (DownloadStatus) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectionToggle: () -> Unit = {},
    onLongSelect: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val totalBytes = task.totalBytes
    val progress = if (totalBytes != null && totalBytes > 0L) {
        (task.downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        label = "downloadProgress",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (selectionMode) {
                    Modifier.clickable(onClick = onSelectionToggle)
                } else {
                    Modifier.pointerInput(task.id) {
                        awaitEachGesture {
                            val event = awaitPointerEvent()
                            val awt = event.awtEventOrNull
                            if (awt is java.awt.event.MouseEvent &&
                                awt.button == java.awt.event.MouseEvent.BUTTON3
                            ) {
                                event.changes.forEach { it.consume() }
                                onLongSelect()
                            }
                        }
                    }
                },
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onSelectionToggle() },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = statusContainerColor(task.status),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = statusIcon(task.status),
                            contentDescription = null,
                            tint = statusColor(task.status),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = taskProgressText(task, progress, currentSpeed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            AnimatedVisibility(
                visible = task.status == DownloadStatus.RUNNING,
            ) {
                if (totalBytes != null && totalBytes > 0L) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(task.status)
            }
            Box(
                modifier = Modifier.align(Alignment.End),
                contentAlignment = Alignment.CenterEnd,
            ) {
                TaskActions(
                    status = task.status,
                    onStatusChange = onStatusChange,
                    onOpen = onOpen,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: DownloadStatus) {
    Surface(
        shape = CircleShape,
        color = statusContainerColor(status),
    ) {
        Text(
            text = statusLabel(status),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = statusColor(status),
        )
    }
}

@Composable
private fun TaskActions(
    status: DownloadStatus,
    onStatusChange: (DownloadStatus) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        when (status) {
            DownloadStatus.RUNNING -> {
                FilledTonalIconButton(
                    onClick = { onStatusChange(DownloadStatus.PAUSED) },
                ) {
                    Icon(Icons.Default.Pause, contentDescription = "暂停下载")
                }
                IconButton(
                    onClick = { onStatusChange(DownloadStatus.CANCELLED) },
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = "取消下载")
                }
            }
            DownloadStatus.PENDING -> IconButton(
                onClick = { onStatusChange(DownloadStatus.CANCELLED) },
            ) {
                Icon(Icons.Default.Cancel, contentDescription = "取消下载")
            }
            DownloadStatus.PAUSED -> {
                FilledTonalIconButton(
                    onClick = { onStatusChange(DownloadStatus.RUNNING) },
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "继续下载")
                }
                IconButton(
                    onClick = { onStatusChange(DownloadStatus.CANCELLED) },
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = "取消下载")
                }
            }
            DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                FilledTonalIconButton(
                    onClick = { onStatusChange(DownloadStatus.RUNNING) },
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "重试下载")
                }
                AnimatedDeleteIconButton(
                    onDelete = onDelete,
                    contentDescription = "删除下载任务",
                )
            }
            DownloadStatus.SUCCESS -> {
                FilledTonalIconButton(onClick = onOpen) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "打开文件所在文件夹")
                }
                AnimatedDeleteIconButton(
                    onDelete = onDelete,
                    contentDescription = "删除下载任务和本地文件",
                )
            }
        }
    }
}

@Composable
private fun AnimatedDeleteIconButton(
    onDelete: () -> Unit,
    contentDescription: String,
) {
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scale by animateFloatAsState(
        targetValue = if (deleting) 0.72f else 1f,
        label = "deleteScale",
    )
    val rotation by animateFloatAsState(
        targetValue = if (deleting) -14f else 0f,
        label = "deleteRotation",
    )

    IconButton(
        enabled = !deleting,
        onClick = {
            deleting = true
            onDelete()
            scope.launch {
                kotlinx.coroutines.delay(140L)
                deleting = false
            }
        },
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = contentDescription,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            },
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

private fun taskProgressText(
    task: DownloadTask,
    progress: Float,
    currentSpeed: Long,
): String = when (task.status) {
    DownloadStatus.SUCCESS -> formatFileSize(task.totalBytes)
    DownloadStatus.FAILED -> task.errorMessage ?: "下载中断，可从断点重试"
    DownloadStatus.CANCELLED -> "任务已取消"
    else -> buildString {
        append(formatFileSize(task.downloadedBytes))
        append(" / ")
        append(formatFileSize(task.totalBytes))
        if (task.totalBytes != null && task.totalBytes > 0L) {
            append(" · ")
            append((progress * 100).toInt())
            append('%')
        }
        if (task.status == DownloadStatus.RUNNING) {
            append(" · ")
            append(formatFileSize(currentSpeed))
            append("/s")
        }
    }
}

internal fun filterDownloadTasks(
    tasks: List<DownloadTask>,
    query: String,
): List<DownloadTask> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return tasks
    return tasks.filter { task ->
        task.fileName.contains(normalizedQuery, ignoreCase = true) ||
            task.remotePath.contains(normalizedQuery, ignoreCase = true) ||
            task.relativePath.contains(normalizedQuery, ignoreCase = true) ||
            statusLabel(task.status).contains(normalizedQuery, ignoreCase = true)
    }
}

private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.PENDING -> "等待中"
    DownloadStatus.RUNNING -> "下载中"
    DownloadStatus.PAUSED -> "已暂停"
    DownloadStatus.SUCCESS -> "已完成"
    DownloadStatus.FAILED -> "失败"
    DownloadStatus.CANCELLED -> "已取消"
}

private fun statusIcon(status: DownloadStatus) = when (status) {
    DownloadStatus.PENDING, DownloadStatus.RUNNING -> Icons.Default.Download
    DownloadStatus.PAUSED -> Icons.Default.Pause
    DownloadStatus.SUCCESS -> Icons.Default.CheckCircle
    DownloadStatus.FAILED -> Icons.Default.Error
    DownloadStatus.CANCELLED -> Icons.Default.Cancel
}

@Composable
private fun statusColor(status: DownloadStatus): Color = when (status) {
    DownloadStatus.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
    DownloadStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
    DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSecondaryContainer
}

@Composable
private fun statusContainerColor(status: DownloadStatus): Color = when (status) {
    DownloadStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
    DownloadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
    DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceContainerHighest
    else -> MaterialTheme.colorScheme.secondaryContainer
}
