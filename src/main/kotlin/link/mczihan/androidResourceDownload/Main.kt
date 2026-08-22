package link.mczihan.androidResourceDownload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import link.mczihan.androidResourceDownload.app.AndroidResourceDownloadRoot
import link.mczihan.androidResourceDownload.core.platform.AppLogger
import link.mczihan.androidResourceDownload.core.platform.DesktopDragDrop
import link.mczihan.androidResourceDownload.core.theme.ThemeMode
import link.mczihan.androidResourceDownload.di.AppContainer
import link.mczihan.androidResourceDownload.feature.auth.SingleInstanceChannel
import link.mczihan.androidResourceDownload.feature.auth.WindowsSchemeRegistrar
import link.mczihan.androidResourceDownload.feature.auth.WindowsTitleBarHelper
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URI

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private const val CALLBACK_SCHEME_PREFIX = "link.mczihan.androidresourcedownload://"

/** Read Windows system dark mode from registry. */
private fun isWindowsDarkMode(): Boolean {
    return try {
        val process = ProcessBuilder(
            "reg", "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "/v", "AppsUseLightTheme"
        ).redirectErrorStream(true).start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
        process.waitFor()
        output.contains("0x0") || output.contains("REG_DWORD    0x0")
    } catch (_: Exception) {
        false
    }
}

fun main(args: Array<String>) {
    // Initialize logger in app working directory
    val appDir = File(System.getProperty("user.dir"))
    AppLogger.init(appDir)
    AppLogger.info("应用启动，参数: ${args.joinToString(" ")}")

    val callbackUrl = args.firstOrNull { it.startsWith(CALLBACK_SCHEME_PREFIX) }

    if (callbackUrl != null) {
        AppLogger.debug("Found callback URL in args: $callbackUrl")
        if (SingleInstanceChannel.trySendToExistingInstance(callbackUrl)) {
            AppLogger.debug("Sent callback to existing instance, exiting")
            return
        }
        AppLogger.debug("No existing instance, will process callback after startup")
    }

    val pendingFromFile = WindowsSchemeRegistrar.readPendingCallback()
    if (pendingFromFile != null) {
        AppLogger.debug("Found pending callback from file: $pendingFromFile")
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "资源下载",
            icon = painterResource("app_icon.png"),
            state = rememberWindowState(width = 840.dp, height = 534.dp),
        ) {
            // Apply native title bar dark mode via Windows DWM API (works on Win10 20H1+/Win11)
            LaunchedEffect(Unit) {
                while (isActive) {
                    val dark = isWindowsDarkMode()
                    WindowsTitleBarHelper.setDarkMode(window, dark)
                    delay(1500)
                }
            }
            // Enforce minimum window size (2/3 of initial 840x534 = 560x356 dp)
            val density = LocalDensity.current
            LaunchedEffect(window, density) {
                val minW = with(density) { 560.dp.toPx() }.toInt()
                val minH = with(density) { 356.dp.toPx() }.toInt()
                window.minimumSize = Dimension(minW, minH)
            }
            // Global drag-and-drop target for file upload (admin-only, gated by DesktopDragDrop.enabled)
            DisposableEffect(window) {
                val dropTarget = object : DropTarget() {
                    override fun dragEnter(dtde: DropTargetDragEvent) {
                        if (!DesktopDragDrop.enabled) {
                            dtde.rejectDrag()
                            return
                        }
                        if (!dtde.transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            dtde.rejectDrag()
                            return
                        }
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val files = dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                            DesktopDragDrop.isDragOver = true
                            dtde.acceptDrag(DnDConstants.ACTION_COPY)
                        } catch (_: Exception) {
                            dtde.rejectDrag()
                        }
                    }
                    override fun dragOver(dtde: DropTargetDragEvent) {
                        if (DesktopDragDrop.isDragOver) dtde.acceptDrag(DnDConstants.ACTION_COPY)
                    }
                    override fun dragExit(dte: DropTargetEvent) {
                        DesktopDragDrop.isDragOver = false
                    }
                    override fun drop(dtde: DropTargetDropEvent) {
                        DesktopDragDrop.isDragOver = false
                        try {
                            if (!DesktopDragDrop.enabled) {
                                dtde.rejectDrop()
                                return
                            }
                            dtde.acceptDrop(DnDConstants.ACTION_COPY)
                            @Suppress("UNCHECKED_CAST")
                            val files = dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                            if (files.isNotEmpty()) {
                                DesktopDragDrop.onFilesDrop?.invoke(files)
                            }
                            dtde.dropComplete(true)
                        } catch (_: Exception) {
                            dtde.dropComplete(false)
                        }
                    }
                }
                window.dropTarget = dropTarget
                onDispose {
                    window.dropTarget = null
                    DesktopDragDrop.isDragOver = false
                    DesktopDragDrop.enabled = false
                    DesktopDragDrop.onFilesDrop = null
                }
            }
            AppContent(initialCallback = callbackUrl ?: pendingFromFile)
        }
    }
}

@Composable
private fun AppContent(initialCallback: String? = null) {
    val container = remember { AppContainer() }
    val oauthServer = container.oauthServer
    val oauthStarted = remember { oauthServer.start() }
    val schemeRegistered = remember {
        val result = WindowsSchemeRegistrar.register()
        AppLogger.debug("Scheme registration result: $result, isRegistered: ${WindowsSchemeRegistrar.isRegistered()}")
        result
    }

    val singleInstanceStarted = remember {
        SingleInstanceChannel.startServer(appScope)
        true
    }

    LaunchedEffect(initialCallback) {
        initialCallback?.let { url ->
            AppLogger.debug("Processing initial callback: $url")
            withContext(Dispatchers.Main) {
                container.authViewModel.handleGithubCallbackUrl(url)
            }
        }
    }

    LaunchedEffect(Unit) {
        SingleInstanceChannel.callbacks.collect { url ->
            url?.let {
                SingleInstanceChannel.consume(it)
                AppLogger.debug("Processing single-instance callback: $it")
                withContext(Dispatchers.Main) {
                    container.authViewModel.handleGithubCallbackUrl(it)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        container.oauthCallbackBus.events.collect { url ->
            url?.let {
                container.oauthCallbackBus.consume(it)
                AppLogger.debug("Processing HTTP server callback: $it")
                withContext(Dispatchers.Main) {
                    container.authViewModel.handleGithubCallbackUrl(it.toString())
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(2000)
            try {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    val content = clipboard.getData(DataFlavor.stringFlavor) as? String
                    if (content != null && content.startsWith(CALLBACK_SCHEME_PREFIX) && content.contains("code=")) {
                        AppLogger.debug("Found callback URL in clipboard: $content")
                        clipboard.setContents(StringSelection(""), null)
                        withContext(Dispatchers.Main) {
                            container.authViewModel.handleGithubCallbackUrl(content)
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(3000)
            val fromFile = WindowsSchemeRegistrar.readPendingCallback()
            if (fromFile != null && fromFile.startsWith(CALLBACK_SCHEME_PREFIX)) {
                AppLogger.debug("Found callback URL in temp file: $fromFile")
                withContext(Dispatchers.Main) {
                    container.authViewModel.handleGithubCallbackUrl(fromFile)
                }
            }
        }
    }

    AndroidResourceDownloadRoot(
        themeViewModel = container.themeViewModel,
        authViewModel = container.authViewModel,
        filesViewModel = container.filesViewModel,
        downloadsViewModel = container.downloadsViewModel,
        uploadsViewModel = container.uploadsViewModel,
        profileViewModel = container.profileViewModel,
        settingsViewModel = container.settingsViewModel,
        onOpenGithubLogin = {
            val startUrl = container.authViewModel.beginGithub()
            if (startUrl == null) {
                container.authViewModel.reportError("未配置有效的后端 API 地址")
                return@AndroidResourceDownloadRoot
            }

            AppLogger.debug("GitHub login start URL: $startUrl")

            try {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(startUrl), null)
            } catch (_: Exception) { }

            openUrlInBrowser(startUrl)
        },
    )
}

private fun openUrlInBrowser(url: String) {
    var opened = false
    if (!opened) try {
        ProcessBuilder("cmd", "/c", "start", "", "\"$url\"").start()
        opened = true
    } catch (_: Exception) { }
    if (!opened) try {
        ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
        opened = true
    } catch (_: Exception) { }
    if (!opened) try {
        Desktop.getDesktop().browse(URI(url))
    } catch (_: Exception) { }
}
