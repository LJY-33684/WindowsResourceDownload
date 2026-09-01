package link.mczihan.androidResourceDownload.feature.uploads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Upload
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.core.common.formatFileSize
import link.mczihan.androidResourceDownload.core.ui.EmptyPane
import link.mczihan.androidResourceDownload.core.ui.FastScrollbar
import link.mczihan.androidResourceDownload.core.ui.FloatingAction
import link.mczihan.androidResourceDownload.core.ui.FloatingActionMenu
import link.mczihan.androidResourceDownload.core.ui.SearchTopAppBar
import link.mczihan.androidResourceDownload.core.ui.SelectionAction
import link.mczihan.androidResourceDownload.core.ui.SelectionBottomBar
import link.mczihan.androidResourceDownload.domain.model.UploadStatus
import link.mczihan.androidResourceDownload.domain.model.UploadTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadsScreen(
    tasks: List<UploadTask>,
    currentSpeeds: Map<String, Long> = emptyMap(),
    preparingSelections: Int = 0,
    onRetry: (taskId: String) -> Unit,
    onCancel: (taskId: String) -> Unit,
    onDelete: (taskId: String) -> Unit,
    onCancelAll: () -> Unit = {},
    onClearTerminal: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedTaskIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var showActionMenu by remember { mutableStateOf(false) }
    var deleteTaskIds by remember { mutableStateOf<List<String>?>(null) }
    var showCancelAllDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    val filteredTasks = filterUploadTasks(tasks, if (searchActive) searchQuery else "")
    val selectedTaskIdSet = selectedTaskIds.toSet()
    val selectedTasks = tasks.filter { it.id in selectedTaskIdSet }
    val allVisibleTasksSelected = filteredTasks.isNotEmpty() &&
        filteredTasks.all { it.id in selectedTaskIdSet }
    val retryOperations = selectedTasks.filter { task ->
        task.status in setOf(UploadStatus.FAILED, UploadStatus.CANCELLED)
    }
    val cancellableSelectedTasks = selectedTasks.filter { task ->
        task.status in setOf(UploadStatus.PENDING, UploadStatus.RUNNING) &&
            !task.isDirectory &&
            !task.committing
    }
    val deletionOperations = selectedTasks.filter { task ->
        task.status in setOf(UploadStatus.SUCCESS, UploadStatus.FAILED, UploadStatus.CANCELLED)
    }
    val hasCancellableTasks = tasks.any { task ->
        task.status in setOf(UploadStatus.PENDING, UploadStatus.RUNNING) &&
            !task.isDirectory &&
            !task.committing
    }
    val hasClearableTasks = tasks.any { task ->
        task.status in setOf(UploadStatus.SUCCESS, UploadStatus.FAILED, UploadStatus.CANCELLED)
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
        val visibleIds = filteredTasks.map(UploadTask::id)
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出上传任务选择")
                        }
                    },
                )
            } else if (searchActive) {
                SearchTopAppBar(
                    query = searchQuery,
                    placeholder = "搜索上传任务",
                    closeContentDescription = "关闭上传搜索",
                    searchContentDescription = "执行上传搜索",
                    onQueryChange = { searchQuery = it },
                    onSearch = {},
                    showSearchAction = false,
                    onClose = {
                        searchActive = false
                        searchQuery = ""
                    },
                    subtitle = when {
                        preparingSelections > 0 -> "正在读取所选内容"
                        searchQuery.trim().isNotEmpty() ->
                            "${filteredTasks.size} / ${tasks.size} 个任务"
                        else -> null
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("上传")
                            Text(
                                text = if (preparingSelections > 0) "正在读取所选内容" else taskCountLabel(tasks.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索上传任务")
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
                        icon = Icons.Default.Refresh,
                        label = "重试",
                        enabled = retryOperations.isNotEmpty(),
                        onClick = {
                            retryOperations.forEach { onRetry(it.id) }
                            exitMultiSelect()
                        },
                    )
                    SelectionAction(
                        icon = Icons.Default.Cancel,
                        label = "取消",
                        enabled = cancellableSelectedTasks.isNotEmpty(),
                        destructive = true,
                        onClick = {
                            cancellableSelectedTasks.forEach { onCancel(it.id) }
                            exitMultiSelect()
                        },
                    )
                    SelectionAction(
                        icon = Icons.Default.Delete,
                        label = "删除",
                        enabled = deletionOperations.isNotEmpty(),
                        destructive = true,
                        onClick = {
                            deleteTaskIds = deletionOperations.map(UploadTask::id)
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
                message = when {
                    preparingSelections > 0 -> "正在创建上传任务"
                    tasks.isEmpty() -> "暂无上传任务"
                    else -> "未找到匹配的上传任务"
                },
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                icon = Icons.Default.Upload,
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
                    items(filteredTasks, key = UploadTask::id) { task ->
                        UploadTaskItem(
                            task = task,
                            currentSpeed = currentSpeeds[task.id] ?: 0L,
                            onRetry = { onRetry(task.id) },
                            onCancel = { onCancel(task.id) },
                            onDelete = { onDelete(task.id) },
                            selectionMode = multiSelectMode,
                            selected = task.id in selectedTaskIdSet,
                            onSelectionToggle = { toggleTaskSelection(task.id) },
                            onLongSelect = {
                                multiSelectMode = true
                                toggleTaskSelection(task.id)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
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

    if (deleteTaskIds != null) {
        val ids = deleteTaskIds ?: emptyList()
        AlertDialog(
            onDismissRequest = { deleteTaskIds = null },
            title = { Text("删除 ${ids.size} 个任务") },
            text = { Text("确定要删除选中的上传任务吗？") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTaskIds = null
                    exitMultiSelect()
                    ids.forEach { id -> onDelete(id) }
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
            text = { Text("确定要取消所有未完成的上传任务吗？") },
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
            text = { Text("确定要清除所有已结束的上传任务吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClearTerminal()
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
private fun UploadTaskItem(
    task: UploadTask,
    currentSpeed: Long,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectionToggle: () -> Unit = {},
    onLongSelect: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val totalBytes = task.totalBytes
    val progress = if (totalBytes != null && totalBytes > 0L) {
        (task.uploadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
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
                            imageVector = statusIcon(task.status, task.isDirectory),
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
                    if (task.relativePath != task.fileName) {
                        Text(
                            text = task.relativePath,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                visible = task.status == UploadStatus.RUNNING && !task.committing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                if (!task.isDirectory && totalBytes != null && totalBytes > 0L) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            StatusBadge(task)
            Box(
                modifier = Modifier.align(Alignment.End),
                contentAlignment = Alignment.CenterEnd,
            ) {
                TaskActions(
                    task = task,
                    onRetry = onRetry,
                    onCancel = onCancel,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(task: UploadTask) {
    Surface(shape = CircleShape, color = statusContainerColor(task.status)) {
        Text(
            text = statusLabel(task),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = statusColor(task.status),
        )
    }
}

@Composable
private fun TaskActions(
    task: UploadTask,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        when (task.status) {
            UploadStatus.PENDING, UploadStatus.RUNNING -> {
                if (!task.isDirectory && !task.committing) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Cancel, contentDescription = "取消上传")
                    }
                }
            }
            UploadStatus.FAILED, UploadStatus.CANCELLED -> {
                FilledTonalIconButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = "重试上传")
                }
                AnimatedDeleteIconButton(
                    onDelete = onDelete,
                    contentDescription = "删除上传任务",
                )
            }
            UploadStatus.SUCCESS -> AnimatedDeleteIconButton(
                onDelete = onDelete,
                contentDescription = "删除上传记录",
            )
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
    IconButton(
        enabled = !deleting,
        onClick = {
            deleting = true
            onDelete()
            scope.launch {
                delay(140L)
                deleting = false
            }
        },
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = contentDescription,
            modifier = Modifier.graphicsLayer {
                scaleX = if (deleting) 0.72f else 1f
                scaleY = if (deleting) 0.72f else 1f
                rotationZ = if (deleting) -14f else 0f
            },
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

private fun taskProgressText(task: UploadTask, progress: Float, currentSpeed: Long): String =
    when (task.status) {
        UploadStatus.SUCCESS -> if (task.isDirectory) "文件夹已创建" else formatFileSize(task.totalBytes)
        UploadStatus.FAILED -> task.errorMessage ?: "上传失败，可重试"
        UploadStatus.CANCELLED -> "任务已取消，重试将从头上传"
        UploadStatus.PENDING -> if (task.isDirectory) {
            "等待创建文件夹"
        } else {
            "${formatFileSize(task.uploadedBytes)} / ${formatFileSize(task.totalBytes)}"
        }
        UploadStatus.RUNNING -> when {
            task.errorMessage != null -> task.errorMessage
            task.committing -> "正在提交到云端"
            task.isDirectory -> "正在创建文件夹"
            else -> buildString {
                append(formatFileSize(task.uploadedBytes))
                append(" / ")
                append(formatFileSize(task.totalBytes))
                if (task.totalBytes != null && task.totalBytes > 0L) {
                    append(" · ")
                    append((progress * 100).toInt())
                    append('%')
                }
                append(" · ")
                append(formatFileSize(currentSpeed))
                append("/s")
            }
        }
    }

private fun statusLabel(task: UploadTask): String = when (task.status) {
    UploadStatus.PENDING -> "等待中"
    UploadStatus.RUNNING -> when {
        task.errorMessage != null -> "等待确认"
        task.committing -> "提交中"
        else -> "上传中"
    }
    UploadStatus.SUCCESS -> "已完成"
    UploadStatus.FAILED -> "失败"
    UploadStatus.CANCELLED -> "已取消"
}

private fun statusIcon(status: UploadStatus, isDirectory: Boolean) = when (status) {
    UploadStatus.PENDING, UploadStatus.RUNNING -> if (isDirectory) Icons.Default.Folder else Icons.Default.Upload
    UploadStatus.SUCCESS -> Icons.Default.CheckCircle
    UploadStatus.FAILED -> Icons.Default.Error
    UploadStatus.CANCELLED -> Icons.Default.Cancel
}

internal fun filterUploadTasks(
    tasks: List<UploadTask>,
    query: String,
): List<UploadTask> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return tasks
    return tasks.filter { task ->
        task.fileName.contains(normalizedQuery, ignoreCase = true) ||
            task.relativePath.contains(normalizedQuery, ignoreCase = true) ||
            statusLabel(task).contains(normalizedQuery, ignoreCase = true)
    }
}

@Composable
private fun statusColor(status: UploadStatus): Color = when (status) {
    UploadStatus.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
    UploadStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
    UploadStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSecondaryContainer
}

@Composable
private fun statusContainerColor(status: UploadStatus): Color = when (status) {
    UploadStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
    UploadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
    UploadStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceContainerHighest
    else -> MaterialTheme.colorScheme.secondaryContainer
}

private fun taskCountLabel(count: Int): String = if (count == 0) "暂无任务" else "$count 个任务"
