package link.mczihan.androidResourceDownload.feature.files

import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import link.mczihan.androidResourceDownload.core.common.RolePreview
import link.mczihan.androidResourceDownload.core.platform.AppLogger
import link.mczihan.androidResourceDownload.core.platform.NativeFileDialog
import link.mczihan.androidResourceDownload.core.common.formatDate
import link.mczihan.androidResourceDownload.core.common.formatFileSize
import link.mczihan.androidResourceDownload.core.platform.DesktopDragDrop
import link.mczihan.androidResourceDownload.core.ui.EmptyPane
import link.mczihan.androidResourceDownload.core.ui.ErrorPane
import link.mczihan.androidResourceDownload.core.ui.FastScrollbar
import link.mczihan.androidResourceDownload.core.ui.LoadingPane
import link.mczihan.androidResourceDownload.core.ui.SearchTopAppBar
import link.mczihan.androidResourceDownload.core.ui.FloatingActionMenu
import link.mczihan.androidResourceDownload.core.ui.FloatingActionSubmenu
import link.mczihan.androidResourceDownload.core.ui.FloatingAction
import link.mczihan.androidResourceDownload.domain.model.FileNode
import link.mczihan.androidResourceDownload.domain.model.FilePreviewContent
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.domain.model.previewFormat
import link.mczihan.androidResourceDownload.domain.webdav.WebDavPath
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    viewModel: FilesViewModel,
    role: Role,
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
    val previewPaneState by viewModel.previewPaneState.collectAsState()
    val multiSelectMode by viewModel.multiSelectMode.collectAsState()
    val selectedPaths by viewModel.selectedPaths.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var selectedFile by remember { mutableStateOf<FileNode?>(null) }
    var showCreateDirectoryDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FileNode?>(null) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var showUploadMenu by remember { mutableStateOf(false) }
    var showActionMenu by remember { mutableStateOf(false) }
    var transferRequest by remember { mutableStateOf<TransferRequest?>(null) }
    var batchTransferRequest by remember { mutableStateOf<BatchTransferRequest?>(null) }
    val isAdmin = role == Role.ADMIN && !RolePreview.asUser
    val allSelected = (realState as? FilesUiState.Success)?.let { s ->
        val visible = s.files.filter { isAdmin || !it.isUploadTemporary }
        visible.isNotEmpty() && visible.all { it.path in selectedPaths }
    } ?: false
    val activePath = realState.path
    val displayedPath = activePath.toString()
    val isDragOver = DesktopDragDrop.isDragOver
    val searchState by viewModel.searchState.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    val previewPaneVisible by viewModel.previewPaneOpen.collectAsState()
    var lastClickedFile by remember { mutableStateOf<FileNode?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSearchScope by remember { mutableStateOf(FileSearchScope.CURRENT_DIRECTORY) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            selectedFile = null
            transferRequest = null
            deleteTarget = null
            renameTarget = null
            onMessage(message)
        }
    }

    // 选中变化时同步驱动预览窗格（Windows 资源管理器行为）：
    // 单选且支持预览 -> 显示预览；多选 -> 显示没有预览；空 -> 清除
    LaunchedEffect(selectedPaths, previewPaneVisible, isAdmin) {
        if (!previewPaneVisible) return@LaunchedEffect
        val selected = viewModel.getSelectedFiles()
        when {
            selected.isEmpty() -> viewModel.clearPreviewPane()
            selected.size == 1 -> viewModel.previewPane(selected.first())
            else -> viewModel.previewPaneMultiSelected()
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
            if (searchActive) {
                Column {
                    SearchTopAppBar(
                        query = searchQuery,
                        placeholder = "搜索文件和文件夹",
                        closeContentDescription = "关闭文件搜索",
                        searchContentDescription = "执行文件搜索",
                        onQueryChange = { query ->
                            searchQuery = query
                            viewModel.cancelSearch()
                        },
                        onSearch = {
                            viewModel.search(searchQuery, selectedSearchScope)
                        },
                        onClose = {
                            searchActive = false
                            searchQuery = ""
                            viewModel.cancelSearch()
                        },
                        subtitle = (searchState as? FileSearchUiState.Loading)?.let { loading ->
                            if (loading.scannedDirectories == 0) {
                                "正在准备搜索"
                            } else {
                                "已扫描 ${loading.scannedDirectories} 个目录"
                            }
                        },

                    )
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            val scopes = listOf(
                                FileSearchScope.ROOT,
                                FileSearchScope.CURRENT_DIRECTORY,
                            )
                            scopes.forEachIndexed { index, scope ->
                                SegmentedButton(
                                    selected = selectedSearchScope == scope,
                                    onClick = {
                                        selectedSearchScope = scope
                                        viewModel.cancelSearch()
                                        if (searchQuery.isNotBlank()) {
                                            viewModel.search(searchQuery, scope)
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = scopes.size,
                                    ),
                                    label = { Text(scope.label()) },
                                )
                            }
                        }
                    }
                }
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
                        if (isAdmin) {
                            TopBarSelectionAction(
                                icon = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                label = if (allSelected) "取消全选" else "全选",
                                onClick = { viewModel.selectAll() },
                            )
                            TopBarSelectionAction(
                                icon = Icons.Default.FlipToBack,
                                label = "反选",
                                onClick = { viewModel.invertSelection() },
                            )
                            TopBarSelectionAction(
                                icon = Icons.AutoMirrored.Filled.DriveFileMove,
                                label = "移动",
                                enabled = selectedPaths.isNotEmpty(),
                                onClick = {
                                    val files = viewModel.getSelectedFiles()
                                    if (files.isNotEmpty()) {
                                        batchTransferRequest = BatchTransferRequest(files, TransferType.MOVE)
                                        viewModel.openDestinationPicker(activePath)
                                    }
                                },
                            )
                            TopBarSelectionAction(
                                icon = Icons.Default.ContentCopy,
                                label = "复制",
                                enabled = selectedPaths.isNotEmpty(),
                                onClick = {
                                    val files = viewModel.getSelectedFiles()
                                    if (files.isNotEmpty()) {
                                        batchTransferRequest = BatchTransferRequest(files, TransferType.COPY)
                                        viewModel.openDestinationPicker(activePath)
                                    }
                                },
                            )
                            TopBarSelectionAction(
                                icon = Icons.Default.Download,
                                label = "下载",
                                enabled = selectedPaths.isNotEmpty(),
                                onClick = {
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
                                },
                            )
                            TopBarSelectionAction(
                                icon = Icons.Default.Delete,
                                label = "删除",
                                enabled = selectedPaths.isNotEmpty(),
                                destructive = true,
                                onClick = {
                                    val files = viewModel.getSelectedFiles()
                                    if (files.isNotEmpty()) viewModel.batchDelete(files)
                                },
                            )
                        }
                        IconButton(onClick = {
                            val next = !previewPaneVisible
                            viewModel.setPreviewPaneOpen(next)
                            if (next) {
                                // 非管理员没有选中集合概念，用最后单击的文件驱动
                                if (!isAdmin) lastClickedFile?.let { viewModel.previewPane(it) }
                            } else {
                                viewModel.clearPreviewPane()
                            }
                        }) {
                            Icon(PreviewPaneIcon, contentDescription = "预览窗格")
                        }
                        IconButton(onClick = {
                            if (!isRefreshing) {
                                searchActive = true
                                searchQuery = ""
                                viewModel.cancelSearch()
                            }
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索文件和文件夹")
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新文件列表")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (isAdmin && !searchActive) {
                FloatingActionMenu(
                    expanded = showActionMenu,
                    onExpandedChange = { expanded ->
                        showActionMenu = expanded
                        if (!expanded) showUploadMenu = false
                    },
                ) {
                    FloatingAction(
                        icon = Icons.Default.CreateNewFolder,
                        label = "新建文件夹",
                        onClick = {
                            showActionMenu = false
                            showUploadMenu = false
                            showCreateDirectoryDialog = true
                        },
                    )
                    FloatingActionSubmenu(
                        visible = showUploadMenu,
                        toggle = {
                            FloatingAction(
                                icon = if (showUploadMenu) Icons.Default.Close else Icons.Default.UploadFile,
                                label = if (showUploadMenu) "收起上传选项" else "上传",
                                widthReferenceLabel = "收起上传选项",
                                animateContentChanges = true,
                                onClick = { showUploadMenu = !showUploadMenu },
                            )
                        },
                    ) {
                        FloatingAction(
                            icon = Icons.Default.UploadFile,
                            label = "上传文件",
                            onClick = {
                                showActionMenu = false
                                showUploadMenu = false
                                pickFilesForUpload { files ->
                                    onUploadFiles(files, activePath)
                                }
                            },
                        )
                        FloatingAction(
                            icon = Icons.Default.Folder,
                            label = "上传文件夹",
                            onClick = {
                                showActionMenu = false
                                showUploadMenu = false
                                pickDirectoryForUpload { dir ->
                                    onUploadDirectory(dir, activePath)
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        if (searchActive) {
            FileSearchContent(
                state = searchState,
                isAdmin = isAdmin,
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                onRetry = { viewModel.retrySearch() },
                multiSelectMode = multiSelectMode,
                selectedPaths = selectedPaths,
                onManage = { file -> selectedFile = file },
                onFileClick = { file ->
                    if (isAdmin) {
                        viewModel.toggleSelection(file.path)
                    } else if (file.isDirectory) {
                        searchActive = false
                        searchQuery = ""
                        viewModel.cancelSearch()
                        viewModel.openDirectory(WebDavPath.parseDecoded(file.path))
                    } else {
                        selectedFile = file
                    }
                },
                onFileLongClick = { file ->
                    selectedFile = file
                },
            )
        } else {
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
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(if (previewPaneVisible) 2f else 1f).fillMaxHeight()) {
                            FileList(
                                files = contentState.files.filter { isAdmin || !it.isUploadTemporary },
                                onFileClick = { file ->
                                    lastClickedFile = file
                                    // 单击一律单选选中（管理员/普通用户一致），双击才打开
                                    viewModel.selectOnly(file)
                                    if (previewPaneVisible) viewModel.previewPane(file)
                                },
                                onFileCtrlClick = { file ->
                                    if (isAdmin) viewModel.toggleSelection(file.path)
                                },
                                onToggleSelection = { file ->
                                    if (isAdmin) viewModel.toggleSelection(file.path)
                                },
                                onDragSelect = { files ->
                                    if (isAdmin && files.isNotEmpty()) viewModel.selectFiles(files)
                                },
                                onClearSelection = {
                                    // 管理员与普通用户点击空白均清除选择
                                    viewModel.selectFiles(emptyList())
                                },
                                onFileOpen = { file ->
                                    if (file.isDirectory) {
                                        viewModel.openDirectory(WebDavPath.parseDecoded(file.path))
                                    } else {
                                        selectedFile = file
                                    }
                                },
                                onFileLongClick = { file ->
                                    // 右键：弹出更多菜单（详情）
                                    selectedFile = file
                                },
                                onManage = { selectedFile = it },
                                isAdmin = isAdmin,
                                multiSelectMode = multiSelectMode,
                                selectedPaths = selectedPaths,
                                modifier = Modifier.fillMaxSize(),
                            )
                            // 刷新指示器（与安卓 PullToRefreshBox 效果一致：白色圆底 + 转圈线条）
                            androidx.compose.animation.AnimatedVisibility(
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
                        if (previewPaneVisible) {
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                PreviewPane(
                                    state = previewPaneState,
                                    onClose = {
                                        viewModel.setPreviewPaneOpen(false)
                                        viewModel.clearPreviewPane()
                                    },
                                    onRetry = viewModel::previewPane,
                                )
                            }
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
        is FilePreviewUiState.Unsupported -> Unit
        FilePreviewUiState.MultiSelected -> Unit
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

private fun pickFilesForUpload(
    onFiles: (List<File>) -> Unit,
) {
    val paths = NativeFileDialog.pickFiles("选择要上传的文件", allowMultiple = true)
    val files = paths.map { File(it) }.filter { it.exists() && it.isFile }
    if (files.isNotEmpty()) onFiles(files)
}

private fun pickDirectoryForUpload(
    onDirectory: (File) -> Unit,
) {
    val path = NativeFileDialog.pickFolder("选择要上传的文件夹")
    if (path != null) {
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) onDirectory(dir)
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun FileList(
    files: List<FileNode>,
    onFileClick: (FileNode) -> Unit,
    onManage: (FileNode) -> Unit,
    isAdmin: Boolean,
    multiSelectMode: Boolean = false,
    selectedPaths: Set<String> = emptySet(),
    onFileOpen: (FileNode) -> Unit = {},
    onFileLongClick: (FileNode) -> Unit = {},
    onFileCtrlClick: (FileNode) -> Unit = {},
    onToggleSelection: (FileNode) -> Unit = {},
    onDragSelect: (List<FileNode>) -> Unit = {},
    onClearSelection: () -> Unit = {},
    showPath: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var dragCtrl by remember { mutableStateOf(false) }
    var pressCtrl by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    var dragCurrentIndex by remember { mutableStateOf(-1) }
    var dragScrollOffset by remember { mutableStateOf(0f) }
    var dragBasePaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    val currentSelectedPaths by rememberUpdatedState(selectedPaths)

    // 屏幕 y 坐标 → 文件索引（基于当前可见项）
    fun findIndexAt(y: Float): Int {
        val info = listState.layoutInfo
        var lastBelow = -1
        for (itemInfo in info.visibleItemsInfo) {
            val top = itemInfo.offset.toFloat()
            val bottom = top + itemInfo.size
            if (y >= top && y <= bottom) return itemInfo.index
            if (y > bottom) lastBelow = itemInfo.index
        }
        return lastBelow
    }

    // 基于索引区间更新框选：滚动时区间随当前命中索引自然扩展
    fun updateDragSelection() {
        val cur = dragCurrent ?: return
        if (dragStartIndex < 0) return
        val curIndex = findIndexAt(cur.y)
        if (curIndex >= 0) dragCurrentIndex = curIndex
        val from = minOf(dragStartIndex, dragCurrentIndex).coerceIn(0, files.size - 1)
        val to = maxOf(dragStartIndex, dragCurrentIndex).coerceIn(0, files.size - 1)
        if (from > to) return
        val hit = files.subList(from, to + 1)
        if (dragCtrl) {
            val hitPaths = hit.map { it.path }.toSet()
            val remaining = files.filter { it.path in dragBasePaths && it.path !in hitPaths }
            val added = hit.filter { it.path !in dragBasePaths }
            onDragSelect(remaining + added)
        } else {
            onDragSelect(hit)
        }
    }

    // 拖拽到列表顶部/底部时自动滚动（类似 Windows 资源管理器）
    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect
        while (isActive) {
            val cur = dragCurrent
            val start = dragStart
            if (cur == null || start == null) {
                delay(16)
                continue
            }
            val info = listState.layoutInfo
            val viewportH = info.viewportSize.height.toFloat()
            val edge = 56f
            when {
                cur.y < edge -> {
                    val scrolled = listState.scrollBy(-12f)
                    dragScrollOffset += scrolled
                    withFrameNanos {}  // 等一帧，让列表重排
                    // 鼠标可能在窗口外，用视口第一个可见项作为当前索引，保证顶部项被选中
                    val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index
                    if (first != null && first >= 0) dragCurrentIndex = first
                    AppLogger.debug("AUTOSCROLL UP: scrolled=$scrolled accOff=${dragScrollOffset.toInt()} idx=$dragCurrentIndex")
                    updateDragSelection()
                }
                cur.y > viewportH - edge -> {
                    val scrolled = listState.scrollBy(12f)
                    dragScrollOffset += scrolled
                    withFrameNanos {}  // 等一帧，让列表重排
                    // 鼠标可能在窗口外，用视口最后一个可见项作为当前索引，保证底部项被选中
                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                    if (last != null && last >= 0) dragCurrentIndex = last
                    AppLogger.debug("AUTOSCROLL DOWN: scrolled=$scrolled accOff=${dragScrollOffset.toInt()} idx=$dragCurrentIndex")
                    updateDragSelection()
                }
            }
            delay(16)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPointerEvent(PointerEventType.Press) { event ->
                pressCtrl = (event.awtEventOrNull as? java.awt.event.MouseEvent)?.isControlDown == true
                AppLogger.debug("BLANK: onPointerEvent Press 触发, ctrl=$pressCtrl")
            }
            .pointerInput(isAdmin, multiSelectMode, files) {
                if (!isAdmin) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStart = offset
                        dragCurrent = offset
                        isDragging = true
                        dragCtrl = pressCtrl
                        dragBasePaths = files.filter { it.path in currentSelectedPaths }.map { it.path }.toSet()
                        dragStartIndex = findIndexAt(offset.y)
                        dragCurrentIndex = dragStartIndex
                        dragScrollOffset = 0f
                        AppLogger.debug("DRAGSTART: y=${offset.y.toInt()} idx=$dragStartIndex")
                    },
                    onDrag = { change, _ ->
                        dragCurrent = change.position
                        updateDragSelection()
                        change.consume()
                    },
                    onDragEnd = {
                        dragStart = null
                        dragCurrent = null
                        isDragging = false
                        dragStartIndex = -1
                        dragCurrentIndex = -1
                        dragScrollOffset = 0f
                    },
                    onDragCancel = {
                        dragStart = null
                        dragCurrent = null
                        isDragging = false
                        dragStartIndex = -1
                        dragCurrentIndex = -1
                        dragScrollOffset = 0f
                    },
                )
            }
            .pointerInput(files, multiSelectMode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    AppLogger.debug("BLANK: down ${downPos.x.toInt()},${downPos.y.toInt()} multiSelect=$multiSelectMode")
                    var moved = false
                    var gotUp = false
                    while (!gotUp) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        when (event.type) {
                            PointerEventType.Move -> {
                                if ((change.position - downPos).getDistance() > 12f) moved = true
                            }
                            PointerEventType.Release -> {
                                gotUp = true
                                AppLogger.debug("BLANK: release moved=$moved")
                            }
                            else -> gotUp = true
                        }
                    }
                    if (!moved) {
                        val info = listState.layoutInfo
                        val onItem = info.visibleItemsInfo.any { itemInfo ->
                            val top = itemInfo.offset.toFloat()
                            val bottom = top + itemInfo.size
                            downPos.y >= top && downPos.y <= bottom
                        }
                        AppLogger.debug("BLANK: check onItem=$onItem")
                        if (!onItem) {
                            AppLogger.debug("BLANK_CLICK: 点击空白处，清除选择")
                            onClearSelection()
                        }
                    }
                }
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 8.dp,
                end = 12.dp,
                bottom = if (isAdmin) 140.dp else 112.dp,
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
                    Column {
                        if (showPath) {
                            Text(
                                text = file.path,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = if (file.isDirectory) {
                                "文件夹 · ${formatDate(file.lastModified)}"
                            } else {
                                "${formatFileSize(file.size)} · ${formatDate(file.lastModified)}"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                leadingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isAdmin) {
                            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection(file) })
                            Spacer(Modifier.width(6.dp))
                        }
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
                trailingContent = if (file.isDirectory || isAdmin) {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (file.isDirectory) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // 三点按钮已隐藏：右键菜单已实现相同功能
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
                    .pointerInput(file, multiSelectMode) {
                        var lastTapTime = 0L
                        awaitEachGesture {
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                up.consume()
                                val now = System.currentTimeMillis()
                                val isDouble = now - lastTapTime < 300L
                                lastTapTime = now
                                when {
                                    isDouble -> onFileOpen(file)
                                    pressCtrl -> onFileCtrlClick(file)
                                    else -> onFileClick(file)
                                }
                            }
                        }
                    }
                    .pointerInput(file, multiSelectMode) {
                        awaitEachGesture {
                            val event = awaitPointerEvent()
                            val awt = event.awtEventOrNull
                            if (awt is java.awt.event.MouseEvent &&
                               awt.button == java.awt.event.MouseEvent.BUTTON3
                            ) {
                                event.changes.forEach { it.consume() }
                                onFileLongClick(file)
                            }
                        }
                    },
            )
        }
        }

        // 拖拽框选矩形
        val ds = dragStart
        val dc = dragCurrent
        if (ds != null && dc != null) {
            val dragFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            val dragBorder = MaterialTheme.colorScheme.primary
            // 锚点 = 按下点屏幕坐标 - 自按下以来累计滚动量（随滚动移动）
            val anchorY = ds.y - dragScrollOffset
            val left = minOf(ds.x, dc.x)
            val top = minOf(anchorY, dc.y)
            val w = kotlin.math.abs(dc.x - ds.x)
            val h = kotlin.math.abs(dc.y - anchorY)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val borderWidth = 1.dp.toPx()
                drawRect(
                    color = dragFill,
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                )
                drawRect(
                    color = dragBorder,
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    style = Stroke(width = borderWidth),
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

// 预览窗格图标：长方形，中间被一条竖线分割（类似 Windows 资源管理器）
private val PreviewPaneIcon: ImageVector = ImageVector.Builder(
    name = "PreviewPane",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
    ) {
        moveTo(3f, 4f)
        lineTo(21f, 4f)
        lineTo(21f, 20f)
        lineTo(3f, 20f)
        close()
    }
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
    ) {
        moveTo(14.5f, 4f)
        lineTo(14.5f, 20f)
    }
}.build()

// 右侧预览窗格：复用现有预览功能（FilePreviewUiState / FilePreviewContent）
@Composable
private fun PreviewPane(
    state: FilePreviewUiState,
    onClose: () -> Unit,
    onRetry: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxSize()) {
            val previewFile = when (state) {
                is FilePreviewUiState.Loading -> state.file
                is FilePreviewUiState.Content -> state.file
                is FilePreviewUiState.Editing -> state.file
                is FilePreviewUiState.Error -> state.file
                is FilePreviewUiState.Unsupported -> state.file
                FilePreviewUiState.Idle -> null
                FilePreviewUiState.MultiSelected -> null
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = previewFile?.name ?: "预览",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭预览", modifier = Modifier.size(18.dp))
                }
            }
            Divider()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (state) {
                    FilePreviewUiState.Idle -> Text(
                        "选择要预览的文件",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is FilePreviewUiState.Unsupported -> Text(
                        "没有预览",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilePreviewUiState.MultiSelected -> Text(
                        "没有预览",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is FilePreviewUiState.Loading -> CircularProgressIndicator()
                    is FilePreviewUiState.Content -> when (val preview = state.preview) {
                        is FilePreviewContent.Text -> {
                            val scrollState = rememberScrollState()
                            Box(
                                modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
                            ) {
                                Text(
                                    text = preview.text,
                                    style = if (preview.monospace) {
                                        MaterialTheme.typography.bodySmall
                                    } else {
                                        MaterialTheme.typography.bodyMedium
                                    },
                                    fontFamily = if (preview.monospace) {
                                        androidx.compose.ui.text.font.FontFamily.Monospace
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                        is FilePreviewContent.Image -> {
                            val bitmap = remember(preview.bytes) { decodeImageBitmap(preview.bytes) }
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap,
                                    contentDescription = state.file.name,
                                    modifier = Modifier.fillMaxSize().padding(12.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                )
                            } else {
                                Text("图片解码失败", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    is FilePreviewUiState.Editing -> Text(
                        "正在编辑中…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is FilePreviewUiState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { onRetry(state.file) }) { Text("重试") }
                    }
                }
            }
        }
    }
}

private fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = try {
    org.jetbrains.skia.Image.makeFromEncoded(bytes).asImageBitmap()
} catch (_: Exception) {
    null
}

@Composable
private fun FileSearchContent(
    state: FileSearchUiState,
    isAdmin: Boolean,
    multiSelectMode: Boolean,
    selectedPaths: Set<String>,
    onRetry: () -> Unit,
    onManage: (FileNode) -> Unit,
    onFileClick: (FileNode) -> Unit,
    onFileLongClick: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        FileSearchUiState.Idle -> EmptyPane(
            message = "输入关键词开始搜索",
            icon = Icons.Default.Search,
            modifier = modifier,
        )
        is FileSearchUiState.Loading -> if (state.files.isEmpty()) {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(
                    text = if (state.scannedDirectories == 0) {
                        "正在准备搜索"
                    } else {
                        "正在搜索，已扫描 ${state.scannedDirectories} 个目录"
                    },
                    modifier = Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(modifier = modifier) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "仍在搜索",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text = "已找到 ${state.visibleFileCount} 项 · 已扫描 ${state.scannedDirectories} 个目录",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (state.incomplete) {
                    Text(
                        text = "部分目录无法访问，当前结果可能不完整",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FileList(
                    files = state.files,
                    onFileClick = onFileClick,
                    modifier = Modifier.weight(1f),
                    isAdmin = isAdmin,
                    multiSelectMode = multiSelectMode,
                    selectedPaths = selectedPaths,
                    onManage = onManage,
                    onFileLongClick = onFileLongClick,
                    showPath = true,
                )
            }
        }
        is FileSearchUiState.Empty -> EmptyPane(
            message = if (state.incomplete) {
                "未找到匹配项，部分目录无法访问"
            } else {
                "未找到匹配的文件或文件夹"
            },
            icon = Icons.Default.SearchOff,
            modifier = modifier,
        )
        is FileSearchUiState.Error -> ErrorPane(
            message = state.message,
            onRetry = onRetry,
            modifier = modifier,
        )
        is FileSearchUiState.Success -> Column(modifier = modifier) {
            if (state.incomplete) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "部分目录无法访问，搜索结果可能不完整",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            FileList(
                files = state.files,
                onFileClick = onFileClick,
                modifier = Modifier.weight(1f),
                isAdmin = isAdmin,
                multiSelectMode = multiSelectMode,
                selectedPaths = selectedPaths,
                onManage = onManage,
                onFileLongClick = onFileLongClick,
                showPath = true,
            )
        }
    }
}

private fun FileSearchScope.label(): String = when (this) {
    FileSearchScope.ROOT -> "整个云盘"
    FileSearchScope.CURRENT_DIRECTORY -> "当前目录"
    FileSearchScope.SELECTED -> "已选内容"
}

@Composable
private fun TopBarSelectionAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = MaterialTheme.shapes.medium,
            color = when {
                !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest
                destructive -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                destructive -> MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.onSecondaryContainer
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = label,
            modifier = Modifier.padding(top = 2.dp),
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
