package link.mczihan.androidResourceDownload.feature.files

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import link.mczihan.androidResourceDownload.core.common.formatDate
import link.mczihan.androidResourceDownload.core.common.formatFileSize
import link.mczihan.androidResourceDownload.core.platform.DesktopDragDrop
import link.mczihan.androidResourceDownload.core.ui.EmptyPane
import link.mczihan.androidResourceDownload.core.ui.ErrorPane
import link.mczihan.androidResourceDownload.core.ui.FastScrollbar
import link.mczihan.androidResourceDownload.core.ui.LoadingPane
import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.domain.model.FilePreviewContent
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.domain.model.previewFormat
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JFileChooser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    viewModel: FilesViewModel,
    role: Role,
    onProfile: () -> Unit,
    onDownload: (FileNode, String) -> Unit,
    onUploadFiles: (List<File>, WebDavPath) -> Unit,
    onUploadDirectory: (File, WebDavPath) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val realState by viewModel.state.collectAsState()
    val mutationState by viewModel.mutationState.collectAsState()
    val directoryPickerState by viewModel.directoryPickerState.collectAsState()
    val previewState by viewModel.previewState.collectAsState()
    val multiSelectMode by viewModel.multiSelectMode.collectAsState()
    val selectedPaths by viewModel.selectedPaths.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var selectedFile by remember { mutableStateOf<FileNode?>(null) }
    var showCreateDirectoryDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FileNode?>(null) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var showUploadMenu by remember { mutableStateOf(false) }
    var transferRequest by remember { mutableStateOf<TransferRequest?>(null) }
    var batchTransferRequest by remember { mutableStateOf<BatchTransferRequest?>(null) }
    val isAdmin = role == Role.ADMIN
    val activePath = realState.path
    val displayedPath = activePath.toString()
    val isDragOver = DesktopDragDrop.isDragOver

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            selectedFile = null
            transferRequest = null
            deleteTarget = null
            renameTarget = null
            onMessage(message)
        }
    }

    LaunchedEffect(isAdmin, viewModel, activePath) {
        if (isAdmin) {
            DesktopDragDrop.enabled = true
            DesktopDragDrop.onFilesDrop = { files ->
                val destination = viewModel.state.value.path
                val dirs = files.filter { it.isDirectory }
                val plainFiles = files.filter { it.isFile }
                dirs.forEach { dir -> onUploadDirectory(dir, destination) }
                if (plainFiles.isNotEmpty()) onUploadFiles(plainFiles, destination)
            }
        } else {
            DesktopDragDrop.enabled = false
            DesktopDragDrop.onFilesDrop = null
            DesktopDragDrop.isDragOver = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            DesktopDragDrop.enabled = false
            DesktopDragDrop.onFilesDrop = null
            DesktopDragDrop.isDragOver = false
        }
    }

    Box(modifier = modifier) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (multiSelectMode && isAdmin) {
                TopAppBar(
                    title = { Text("已选择 ${selectedPaths.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitMultiSelect() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "取消选择")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "全选")
                        }
                        IconButton(onClick = {
                            val files = viewModel.getSelectedFiles()
                            if (files.isNotEmpty()) {
                                batchTransferRequest = BatchTransferRequest(files, TransferType.MOVE)
                                viewModel.openDestinationPicker(activePath)
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "批量移动")
                        }
                        IconButton(onClick = {
                            val files = viewModel.getSelectedFiles()
                            if (files.isNotEmpty()) {
                                batchTransferRequest = BatchTransferRequest(files, TransferType.COPY)
                                viewModel.openDestinationPicker(activePath)
                            }
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "批量复制")
                        }
                        IconButton(onClick = {
                            val selected = viewModel.getSelectedFiles()
                            var count = 0
                            selected.forEach { item ->
                                if (item.isDirectory) {
                                    viewModel.downloadFolder(item) { fileNode, relativePath ->
                                        onDownload(fileNode, relativePath)
                                        count++
                                    }
                                } else {
                                    onDownload(item, "")
                                    count++
                                }
                            }
                            if (selected.isNotEmpty()) onMessage("已加入下载任务")
                            viewModel.exitMultiSelect()
                        }) {
                            Icon(Icons.Default.Download, contentDescription = "批量下载")
                        }
                        IconButton(onClick = {
                            val files = viewModel.getSelectedFiles()
                            if (files.isNotEmpty()) viewModel.batchDelete(files)
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "批量删除")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("文件")
                            Text(
                                text = displayedPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        if (!activePath.isRoot) {
                            IconButton(onClick = { viewModel.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上一级")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新文件列表")
                        }
                        IconButton(onClick = onProfile) {
                            Icon(Icons.Default.Person, contentDescription = "个人中心")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (isAdmin && !multiSelectMode) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ExtendedFloatingActionButton(
                        text = { Text("新建文件夹") },
                        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = "新建文件夹") },
                        onClick = { showCreateDirectoryDialog = true },
                    )
                    Box(contentAlignment = Alignment.BottomEnd) {
                        ExtendedFloatingActionButton(
                            text = { Text("上传") },
                            icon = { Icon(Icons.Default.UploadFile, contentDescription = "上传") },
                            onClick = { showUploadMenu = true },
                        )
                        DropdownMenu(
                            expanded = showUploadMenu,
                            onDismissRequest = { showUploadMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("上传文件") },
                                leadingIcon = {
                                    Icon(Icons.Default.UploadFile, contentDescription = "上传文件")
                                },
                                onClick = {
                                    showUploadMenu = false
                                    pickFilesForUpload { files ->
                                        onUploadFiles(files, activePath)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("上传文件夹") },
                                leadingIcon = {
                                    Icon(Icons.Default.Folder, contentDescription = "上传文件夹")
                                },
                                onClick = {
                                    showUploadMenu = false
                                    pickDirectoryForUpload { dir ->
                                        onUploadDirectory(dir, activePath)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        when (val contentState = realState) {
            is FilesUiState.Loading -> LoadingPane(Modifier.padding(innerPadding))
            is FilesUiState.Empty -> EmptyPane(
                message = "此目录为空",
                modifier = Modifier.padding(innerPadding),
            )
            is FilesUiState.Error -> ErrorPane(
                message = contentState.message,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(innerPadding),
            )
            is FilesUiState.Success -> Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                FileList(
                    files = contentState.files.filter { isAdmin || !it.isUploadTemporary },
                    onFileClick = { file ->
                        if (multiSelectMode) {
                            viewModel.toggleSelection(file.path)
                        } else if (file.isDirectory) {
                            viewModel.openDirectory(WebDavPath.parseDecoded(file.path))
                        } else {
                            selectedFile = file
                        }
                    },
                    onFileLongClick = { file ->
                        if (isAdmin && !multiSelectMode) {
                            viewModel.enterMultiSelect()
                            viewModel.toggleSelection(file.path)
                        }
                    },
                    onManage = { if (isAdmin) selectedFile = it },
                    isAdmin = isAdmin,
                    multiSelectMode = multiSelectMode,
                    selectedPaths = selectedPaths,
                    modifier = Modifier.fillMaxSize(),
                )
                // 刷新指示器（与安卓 PullToRefreshBox 效果一致：白色圆底 + 转圈线条）
                AnimatedVisibility(
                    visible = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp,
                        modifier = Modifier.padding(top = 8.dp).size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                            )
                        }
                    }
                }
            }
        }
    }

    if (isDragOver) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .border(4.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "松手上传到此目录",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    displayedPath,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    }

    selectedFile?.let { file ->
        FileDetailsSheet(
            file = file,
            isAdmin = isAdmin,
            onDismiss = { selectedFile = null },
            onPreview = {
                selectedFile = null
                viewModel.preview(file)
            },
            onDownload = {
                onDownload(file, "")
                selectedFile = null
            },
            onDownloadFolder = {
                viewModel.downloadFolder(file) { fileNode, relativePath -> onDownload(fileNode, relativePath) }
                selectedFile = null
            },
            onRename = {
                selectedFile = null
                renameTarget = file
            },
            onMove = {
                selectedFile = null
                transferRequest = TransferRequest(file, TransferType.MOVE)
                viewModel.openDestinationPicker(activePath)
            },
            onCopy = {
                selectedFile = null
                transferRequest = TransferRequest(file, TransferType.COPY)
                viewModel.openDestinationPicker(activePath)
            },
            onDelete = {
                selectedFile = null
                deleteTarget = file
            },
        )
    }

    transferRequest?.takeIf { isAdmin }?.let { request ->
        DestinationDirectoryDialog(
            request = request,
            state = directoryPickerState,
            onOpenDirectory = { viewModel.openDestinationDirectory(it) },
            onNavigateUp = { viewModel.navigateDestinationUp() },
            onRetry = { viewModel.retryDestinationPicker() },
            onDismiss = {
                transferRequest = null
                viewModel.dismissDestinationPicker()
            },
            onConfirm = { directory ->
                val source = WebDavPath.parseDecoded(request.file.path)
                if (request.type == TransferType.MOVE) {
                    viewModel.move(source, request.file.isDirectory, directory, request.file.etag)
                } else {
                    viewModel.copy(source, request.file.isDirectory, directory, request.file.etag)
                }
                transferRequest = null
                viewModel.dismissDestinationPicker()
            },
        )
    }

    batchTransferRequest?.takeIf { isAdmin }?.let { request ->
        DestinationDirectoryDialog(
            request = TransferRequest(request.files.first(), request.type),
            state = directoryPickerState,
            onOpenDirectory = { viewModel.openDestinationDirectory(it) },
            onNavigateUp = { viewModel.navigateDestinationUp() },
            onRetry = { viewModel.retryDestinationPicker() },
            onDismiss = {
                batchTransferRequest = null
                viewModel.dismissDestinationPicker()
            },
            onConfirm = { directory ->
                if (request.type == TransferType.MOVE) {
                    viewModel.batchMove(request.files, directory)
                } else {
                    viewModel.batchCopy(request.files, directory)
                }
                batchTransferRequest = null
                viewModel.dismissDestinationPicker()
            },
        )
    }

    if (showCreateDirectoryDialog && isAdmin) {
        CreateDirectoryDialog(
            directory = activePath,
            onDismiss = { showCreateDirectoryDialog = false },
            onConfirm = { name ->
                viewModel.createDirectory(name)
                showCreateDirectoryDialog = false
            },
        )
    }

    deleteTarget?.takeIf { isAdmin }?.let { file ->
        DeleteConfirmationDialog(
            file = file,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                viewModel.delete(WebDavPath.parseDecoded(file.path), file.isDirectory, file.etag)
                deleteTarget = null
            },
        )
    }

    renameTarget?.takeIf { isAdmin }?.let { file ->
        RenameResourceDialog(
            file = file,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.rename(
                    source = WebDavPath.parseDecoded(file.path),
                    sourceIsDirectory = file.isDirectory,
                    newName = newName,
                    sourceEtag = file.etag,
                )
                renameTarget = null
            },
        )
    }

    when (val mutation = mutationState.takeIf { isAdmin } ?: FileMutationState.Idle) {
        FileMutationState.Idle -> Unit
        FileMutationState.PreparingUpload -> PreparingUploadDialog(
            onCancel = { viewModel.dismissMutation() },
        )
        is FileMutationState.UploadReady -> UploadConfirmationDialog(
            documentName = mutation.document.displayName,
            directory = mutation.directory,
            onDismiss = { viewModel.dismissMutation() },
            onConfirm = { viewModel.upload(it) },
        )
        is FileMutationState.Running -> MutationRunningDialog(
            state = mutation,
            onCancel = if (
                mutation.operation is FileOperation.Upload && !mutation.committing
            ) {
                { viewModel.cancelMutation() }
            } else null,
        )
        is FileMutationState.AwaitingOverwrite -> OverwriteConfirmationDialog(
            operation = mutation.operation,
            onDismiss = { viewModel.dismissMutation() },
            onConfirm = { viewModel.confirmOverwrite() },
        )
        is FileMutationState.Failed -> MutationFailedDialog(
            state = mutation,
            onDismiss = { viewModel.dismissMutation() },
            onRetry = { viewModel.retryMutation() },
        )
    }

    when (val preview = previewState) {
        FilePreviewUiState.Idle -> Unit
        is FilePreviewUiState.Loading -> PreviewLoadingDialog(
            file = preview.file,
            onDismiss = { viewModel.dismissPreview() },
        )
        is FilePreviewUiState.Content -> PreviewContentFullscreen(
            state = preview,
            onDismiss = { viewModel.dismissPreview() },
        )
        is FilePreviewUiState.Editing -> PreviewEditingDialog(
            state = preview,
            onDismiss = { viewModel.dismissPreview() },
            onDraftChange = viewModel::updatePreviewDraft,
            onSave = { viewModel.savePreviewEdit() },
            onCancel = { viewModel.cancelPreviewEdit() },
        )
        is FilePreviewUiState.Error -> PreviewErrorDialog(
            state = preview,
            onDismiss = { viewModel.dismissPreview() },
            onRetry = { viewModel.retryPreview() },
        )
    }
}

private enum class TransferType { MOVE, COPY }
private data class TransferRequest(val file: FileNode, val type: TransferType)
private data class BatchTransferRequest(val files: List<FileNode>, val type: TransferType)

private fun loadAppIconImage(): java.awt.Image? = try {
    val url = object {}.javaClass.getResource("/app_icon.png")
    if (url != null) ImageIcon(url).image else null
} catch (_: Exception) {
    null
}

private fun createIconFileChooser(): JFileChooser = object : JFileChooser() {
    override fun createDialog(parent: java.awt.Component?): javax.swing.JDialog {
        val dialog = super.createDialog(parent)
        loadAppIconImage()?.let { dialog.setIconImage(it) }
        return dialog
    }
}

private fun pickFilesForUpload(
    onFiles: (List<File>) -> Unit,
) {
    val chooser = createIconFileChooser()
    chooser.dialogTitle = "选择要上传的文件"
    chooser.isMultiSelectionEnabled = true
    chooser.fileSelectionMode = JFileChooser.FILES_ONLY
    val result = chooser.showOpenDialog(null)
    if (result == JFileChooser.APPROVE_OPTION) {
        val files = chooser.selectedFiles.filter { it.exists() && it.isFile }
        if (files.isNotEmpty()) onFiles(files)
    }
}

private fun pickDirectoryForUpload(
    onDirectory: (File) -> Unit,
) {
    val chooser = createIconFileChooser()
    chooser.dialogTitle = "选择要上传的文件夹"
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    val result = chooser.showOpenDialog(null)
    if (result == JFileChooser.APPROVE_OPTION) {
        val dir = chooser.selectedFile
        if (dir != null && dir.exists() && dir.isDirectory) onDirectory(dir)
    }
}

@Composable
private fun FileList(
    files: List<FileNode>,
    onFileClick: (FileNode) -> Unit,
    onManage: (FileNode) -> Unit,
    isAdmin: Boolean,
    multiSelectMode: Boolean = false,
    selectedPaths: Set<String> = emptySet(),
    onFileLongClick: (FileNode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    Box(
        modifier = modifier
            .fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 8.dp,
                end = 12.dp,
                bottom = if (isAdmin && !multiSelectMode) 140.dp else 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
        items(files, key = { it.path }) { file ->
            val isSelected = file.path in selectedPaths
            ListItem(
                headlineContent = {
                    Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(
                        text = if (file.isDirectory) {
                            "文件夹 · ${formatDate(file.lastModified)}"
                        } else {
                            "${formatFileSize(file.size)} · ${formatDate(file.lastModified)}"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    if (multiSelectMode) {
                        Checkbox(checked = isSelected, onCheckedChange = { onFileClick(file) })
                    } else {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = if (file.isDirectory) MaterialTheme.shapes.medium else CircleShape,
                            color = if (file.isDirectory) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (file.isDirectory) {
                                        Icons.Default.Folder
                                    } else {
                                        Icons.AutoMirrored.Filled.InsertDriveFile
                                    },
                                    contentDescription = null,
                                    tint = if (file.isDirectory) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                },
                trailingContent = if (multiSelectMode) {
                    null
                } else if (file.isDirectory || isAdmin) {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (file.isDirectory) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isAdmin) {
                                IconButton(onClick = { onManage(file) }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "管理 ${file.name}")
                                }
                            }
                        }
                    }
                } else null,
                colors = ListItemDefaults.colors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onFileClick(file) }
                    .pointerInput(file, multiSelectMode) {
                        awaitEachGesture {
                            val event = awaitPointerEvent()
                            val awt = event.awtEventOrNull
                            if (awt is java.awt.event.MouseEvent &&
                               awt.button == java.awt.event.MouseEvent.BUTTON3
                            ) {
                                event.changes.forEach { it.consume() }
                                if (multiSelectMode) {
                                    onFileClick(file)
                                } else {
                                    onFileLongClick(file)
                                }
                            }
                        }
                    },
            )
        }
        }

        // 快速滚动滑块
        FastScrollbar(
            listState = listState,
            itemCount = files.size,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileDetailsSheet(
    file: FileNode,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onDownloadFolder: () -> Unit = {},
    onRename: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val canPreview = file.previewFormat() != null
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(file.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = if (file.isDirectory) "文件夹" else file.mimeType ?: "未知类型",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!file.isDirectory) DetailLine("大小", formatFileSize(file.size))
            DetailLine("修改时间", formatDate(file.lastModified))
            DetailLine("路径", file.path)
            if (!file.isDirectory) {
                if (canPreview) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = onPreview, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Visibility, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("预览")
                        }
                        Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("下载")
                        }
                    }
                } else {
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("下载")
                    }
                }
            }
            if (file.isDirectory && isAdmin) {
                Button(onClick = onDownloadFolder, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("下载文件夹")
                }
            }
            if (isAdmin) {
                FilledTonalButton(
                    onClick = onRename,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("重命名")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = onMove, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("移动")
                    }
                    FilledTonalButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("复制")
                    }
                }
                FilledTonalButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RenameResourceDialog(
    file: FileNode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val source = remember(file.path) { WebDavPath.parseDecoded(file.path) }
    val currentName = requireNotNull(source.name)
    val parent = remember(source) {
        WebDavPath.fromDecodedSegments(source.decodedSegments.dropLast(1))
    }
    var name by remember(file.path) { mutableStateOf(currentName) }
    val resourceLabel = if (file.isDirectory) "文件夹名称" else "文件名"
    val maxLength = 255
    val validationMessage = when {
        name.isBlank() -> null
        name.length > maxLength -> "${resourceLabel}不能超过 $maxLength 个字符"
        runCatching { parent.child(name) }.isFailure -> "${resourceLabel}包含无效字符"
        else -> null
    }
    val valid = name.isNotBlank() && name != currentName && validationMessage == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (file.isDirectory) "重命名文件夹" else "重命名文件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "位置：$parent",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("新名称") },
                    singleLine = true,
                    isError = validationMessage != null,
                    supportingText = validationMessage?.let { message ->
                        { Text(message) }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(name) }) { Text("重命名") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun UploadConfirmationDialog(
    documentName: String,
    directory: WebDavPath,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var remoteName by remember(documentName) { mutableStateOf(documentName) }
    val valid = runCatching { directory.child(remoteName.trim()) }.isSuccess
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("上传到云端") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("目标目录：$directory")
                OutlinedTextField(
                    value = remoteName,
                    onValueChange = { remoteName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("云端文件名") },
                    singleLine = true,
                    isError = remoteName.isNotBlank() && !valid,
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onConfirm(remoteName.trim()) }) { Text("上传") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PreparingUploadDialog(onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("正在读取文件") },
        text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}

@Composable
private fun CreateDirectoryDialog(
    directory: WebDavPath,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(directory) { mutableStateOf("") }
    val normalizedName = name.trim()
    val validationMessage = when {
        normalizedName.isEmpty() -> null
        normalizedName.length > MAX_DIRECTORY_NAME_LENGTH -> "文件夹名称不能超过 $MAX_DIRECTORY_NAME_LENGTH 个字符"
        runCatching { directory.child(normalizedName) }.isFailure -> "文件夹名称包含无效字符"
        else -> null
    }
    val valid = normalizedName.isNotEmpty() && validationMessage == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文件夹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("位置：$directory", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("文件夹名称") },
                    singleLine = true,
                    isError = validationMessage != null,
                    supportingText = validationMessage?.let { { Text(it) } },
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onConfirm(normalizedName) }) { Text("创建") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DeleteConfirmationDialog(
    file: FileNode,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除 ${file.name}？") },
        text = {
            Text(if (file.isDirectory) "将永久删除 ${file.path} 及其中全部内容，此操作无法撤销。" else "将永久删除 ${file.path}，此操作无法撤销。")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MutationRunningDialog(
    state: FileMutationState.Running,
    onCancel: (() -> Unit)?,
) {
    val total = state.totalBytes
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (state.committing) "正在提交云端文件" else state.operation.actionLabel()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.operation.targetDescription())
                if (state.operation is FileOperation.Upload && total != null && total > 0L) {
                    val progress = (state.uploadedBytes.toFloat() / total).coerceIn(0f, 1f)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${formatFileSize(state.uploadedBytes)} / ${formatFileSize(total)}")
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { if (onCancel != null) TextButton(onClick = onCancel) { Text("取消") } },
    )
}

@Composable
private fun OverwriteConfirmationDialog(
    operation: FileOperation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("目标已存在") },
        text = { Text("${operation.targetDescription()} 已存在，是否覆盖？") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("覆盖") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MutationFailedDialog(
    state: FileMutationState.Failed,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("云端文件操作失败") },
        text = { Text(state.message) },
        confirmButton = {
            if (state.operation != null) TextButton(onClick = onRetry) { Text("重试") }
            else TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = if (state.operation != null) {
            { TextButton(onClick = onDismiss) { Text("关闭") } }
        } else null,
    )
}

@Composable
private fun DestinationDirectoryDialog(
    request: TransferRequest,
    state: DirectoryPickerState,
    onOpenDirectory: (WebDavPath) -> Unit,
    onNavigateUp: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (WebDavPath) -> Unit,
) {
    val source = remember(request.file.path) { WebDavPath.parseDecoded(request.file.path) }
    val currentPath = state.currentPathOrNull()
    val destination = currentPath?.let { path ->
        source.name?.let { remoteName -> runCatching { path.child(remoteName) }.getOrNull() }
    }
    val validDestination = state is DirectoryPickerState.Success &&
        destination != null && destination != source &&
        !destination.isDescendantOf(source) && !source.isDescendantOf(destination)
    val directories = (state as? DirectoryPickerState.Success)?.directories.orEmpty()
        .mapNotNull { directory ->
            val path = runCatching { WebDavPath.parseDecoded(directory.path) }.getOrNull() ?: return@mapNotNull null
            val allowed = !request.file.isDirectory || (path != source && !path.isDescendantOf(source))
            if (allowed) directory to path else null
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (request.type == TransferType.MOVE) "选择移动位置" else "选择复制位置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(request.file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = currentPath?.isRoot == false, onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上一级")
                    }
                    Text(currentPath?.toString() ?: "/", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                when (state) {
                    DirectoryPickerState.Idle, is DirectoryPickerState.Loading -> Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    is DirectoryPickerState.Error -> Column(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text("重试") }
                    }
                    is DirectoryPickerState.Success -> if (directories.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                            Text("没有子文件夹", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(directories, key = { it.second.toString() }) { (directory, path) ->
                                ListItem(
                                    headlineContent = { Text(directory.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingContent = { Icon(Icons.Default.Folder, contentDescription = "打开文件夹 ${directory.name}") },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                                    modifier = Modifier.clickable { onOpenDirectory(path) },
                                )
                            }
                        }
                    }
                }
                if (state is DirectoryPickerState.Success && !validDestination) {
                    Text("当前位置与原位置相同或不可作为目标", color = MaterialTheme.colorScheme.error)
                }
                Text("目标：${destination ?: "-"}", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        confirmButton = { TextButton(enabled = validDestination, onClick = { currentPath?.let(onConfirm) }) { Text(if (request.type == TransferType.MOVE) "移动到此处" else "复制到此处") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun WebDavPath.isDescendantOf(parent: WebDavPath): Boolean =
    decodedSegments.size > parent.decodedSegments.size &&
        decodedSegments.take(parent.decodedSegments.size) == parent.decodedSegments

private fun FileOperation.actionLabel(): String = when (this) {
    is FileOperation.Upload -> "正在上传"
    is FileOperation.CreateDirectory -> "正在新建文件夹"
    is FileOperation.Move -> "正在移动"
    is FileOperation.Copy -> "正在复制"
    is FileOperation.Delete -> "正在删除"
}

private fun FileOperation.targetDescription(): String = when (this) {
    is FileOperation.Upload -> destination.toString()
    is FileOperation.CreateDirectory -> path.toString()
    is FileOperation.Move -> destination.toString()
    is FileOperation.Copy -> destination.toString()
    is FileOperation.Delete -> path.toString()
}

private fun DirectoryPickerState.currentPathOrNull(): WebDavPath? = when (this) {
    DirectoryPickerState.Idle -> null
    is DirectoryPickerState.Loading -> path
    is DirectoryPickerState.Success -> path
    is DirectoryPickerState.Error -> path
}

@Composable
private fun PreviewLoadingDialog(file: FileNode, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("正在预览 ${file.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewContentFullscreen(
    state: FilePreviewUiState.Content,
    onDismiss: () -> Unit,
) {
    val file = state.file
    val preview = state.preview
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = file.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "关闭预览")
                            }
                        },
                    )
                },
            ) { innerPadding ->
                when (preview) {
                    is FilePreviewContent.Text -> {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(24.dp),
                        ) {
                            if (preview.truncated) {
                                Text(
                                    "内容已截断，仅显示前部分",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState),
                            ) {
                                Text(
                                    text = preview.text,
                                    style = if (preview.monospace) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                    fontFamily = if (preview.monospace) androidx.compose.ui.text.font.FontFamily.Monospace else null,
                                )
                            }
                        }
                    }
                    is FilePreviewContent.Image -> {
                        val bitmap = remember(preview.bytes) { decodeImageBitmap(preview.bytes) }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap,
                                    contentDescription = file.name,
                                    modifier = Modifier.fillMaxSize().padding(24.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                )
                            } else {
                                Text("图片解码失败", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewEditingDialog(
    state: FilePreviewUiState.Editing,
    onDismiss: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!state.saving) onDismiss() },
        title = { Text("编辑 ${state.file.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp),
                    enabled = !state.saving,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                )
                if (state.error != null) {
                    Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (state.saving) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.saving && state.draft != state.original.text,
                onClick = onSave,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(enabled = !state.saving, onClick = onCancel) { Text("取消") }
        },
    )
}

@Composable
private fun PreviewErrorDialog(
    state: FilePreviewUiState.Error,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("预览失败") },
        text = { Text(state.message) },
        confirmButton = { TextButton(onClick = onRetry) { Text("重试") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = try {
    org.jetbrains.skia.Image.makeFromEncoded(bytes).asImageBitmap()
} catch (_: Exception) {
    null
}
