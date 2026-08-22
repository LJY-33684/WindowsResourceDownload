package link.mczihan.androidResourceDownload.feature.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.core.common.formatFileSize
import link.mczihan.androidResourceDownload.core.ui.EmptyPane
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
    var showMenu by remember { mutableStateOf(false) }
    var deleteTaskId by remember { mutableStateOf<String?>(null) }
    var deleteLocalFile by remember { mutableStateOf(true) }
    var showClearDialog by remember { mutableStateOf(false) }
    var clearLocalFiles by remember { mutableStateOf(true) }
    Scaffold(
        modifier = modifier,
        topBar = {
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
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部取消") },
                            onClick = {
                                showMenu = false
                                onCancelAll()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("全部清除") },
                            onClick = {
                                showMenu = false
                                clearLocalFiles = true
                                showClearDialog = true
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            EmptyPane(
                message = "暂无下载任务",
                modifier = Modifier.padding(innerPadding),
                icon = Icons.Default.Download,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            ) {
                items(tasks, key = DownloadTask::id) { task ->
                    DownloadTaskItem(
                        task = task,
                        currentSpeed = currentSpeeds[task.id] ?: 0L,
                        onStatusChange = { status -> onStatusChange(task.id, status) },
                        onOpen = { onOpen(task) },
                        onDelete = {
                            deleteTaskId = task.id
                            deleteLocalFile = true
                        },
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
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
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
