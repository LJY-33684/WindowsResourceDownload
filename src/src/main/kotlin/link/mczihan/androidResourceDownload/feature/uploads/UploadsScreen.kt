package link.mczihan.androidResourceDownload.feature.uploads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.core.common.formatFileSize
import link.mczihan.androidResourceDownload.core.ui.EmptyPane
import link.mczihan.androidResourceDownload.core.ui.FastScrollbar
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
    var showMenu by remember { mutableStateOf(false) }
    val subtitle = if (preparingSelections > 0) "正在读取所选内容" else taskCountLabel(tasks.size)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("上传")
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
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
                                onClearTerminal()
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            EmptyPane(
                message = if (preparingSelections > 0) "正在创建上传任务" else "暂无上传任务",
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
                    items(tasks, key = UploadTask::id) { task ->
                        UploadTaskItem(
                            task = task,
                            currentSpeed = currentSpeeds[task.id] ?: 0L,
                            onRetry = { onRetry(task.id) },
                            onCancel = { onCancel(task.id) },
                            onDelete = { onDelete(task.id) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                FastScrollbar(
                    listState = listState,
                    itemCount = tasks.size,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

@Composable
private fun UploadTaskItem(
    task: UploadTask,
    currentSpeed: Long,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalBytes = task.totalBytes
    val progress = if (totalBytes != null && totalBytes > 0L) {
        (task.uploadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
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
