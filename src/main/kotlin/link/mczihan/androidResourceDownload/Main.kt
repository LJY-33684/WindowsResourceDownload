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
import link.mczihan.androidResourceDownload.feature.auth.GithubLoginWebViewDialog
import link.mczihan.androidResourceDownload.feature.auth.QqLoginWebViewDialog
import link.mczihan.androidResourceDownload.feature.auth.WindowsTitleBarHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Bridge so the Window-level title bar poller can see the in-app theme mode. */
private object ThemeModeBridge {
    @Volatile var current: ThemeMode = ThemeMode.SYSTEM
}
private const val CALLBACK_SCHEME_PREFIX = "WindowsResourceDownload://"

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
    configureSystemProxyForJavaFx()

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
            // Native title bar dark/light follows the in-app theme (via ThemeModeBridge);
            // falls back to the system theme when set to SYSTEM.
            LaunchedEffect(Unit) {
                while (isActive) {
                    val dark = when (ThemeModeBridge.current) {
                        ThemeMode.DARK -> true
                        ThemeMode.LIGHT -> false
                        ThemeMode.SYSTEM -> isWindowsDarkMode()
                    }
                    WindowsTitleBarHelper.setDarkMode(window, dark)
                    delay(800)
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
            // 挂到窗口内容面板（ComposePanel）而非 JFrame：拖拽事件实际由内容面板接收
            val dropView = window.contentPane
            // 递归给内容面板及所有子组件安装 DropTarget：无论命中哪一层渲染组件都能收到拖拽
            fun installDropTargets(container: java.awt.Container, target: DropTarget) {
                container.dropTarget = target
                for (child in container.components) {
                    child.dropTarget = target
                    if (child is java.awt.Container) installDropTargets(child, target)
                }
            }
            fun countComponents(container: java.awt.Container): Int =
                1 + container.components.sumOf { if (it is java.awt.Container) countComponents(it) else 1 }
            DisposableEffect(dropView) {
                AppLogger.debug("DD install target=${dropView.javaClass.name} dtBefore=${dropView.dropTarget != null}")
                val dropTarget = object : DropTarget() {
                    override fun dragEnter(dtde: DropTargetDragEvent) {
                        AppLogger.debug("DD dragEnter: enabled=${DesktopDragDrop.enabled} fileFlavor=${dtde.transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)}")
                        if (!DesktopDragDrop.enabled) {
                            AppLogger.debug("DD reject: enabled=false")
                            dtde.rejectDrag()
                            return
                        }
                        if (!dtde.transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            AppLogger.debug("DD reject: no javaFileListFlavor")
                            dtde.rejectDrag()
                            return
                        }
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val files = dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                            DesktopDragDrop.isDragOver = true
                            dtde.acceptDrag(DnDConstants.ACTION_COPY)
                            AppLogger.debug("DD accept: files=${files.size}")
                        } catch (e: Exception) {
                            AppLogger.debug("DD reject: exception ${e.message}")
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
                                AppLogger.debug("DD drop reject: enabled=false")
                                dtde.rejectDrop()
                                return
                            }
                            dtde.acceptDrop(DnDConstants.ACTION_COPY)
                            @Suppress("UNCHECKED_CAST")
                            val files = dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                            AppLogger.debug("DD drop: files=${files.size} handler=${DesktopDragDrop.onFilesDrop != null}")
                            if (files.isNotEmpty()) {
                                DesktopDragDrop.onFilesDrop?.invoke(files)
                            }
                            dtde.dropComplete(true)
                        } catch (e: Exception) {
                            AppLogger.debug("DD drop exception: ${e.message}")
                            dtde.dropComplete(false)
                        }
                    }
                }
                installDropTargets(dropView, dropTarget)
                AppLogger.debug("DD installed recursive comps=${countComponents(dropView)}")
                onDispose {
                    dropView.dropTarget = null
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
    // Keep the Window-level title bar poller informed of the in-app theme mode,
    // so manual DARK/LIGHT switching inside the app updates the title bar too.
    LaunchedEffect(container.themeViewModel.settings) {
        while (isActive) {
            ThemeModeBridge.current = container.themeViewModel.settings.value.themeMode
            delay(500)
        }
    }
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

    var githubLoginUrl by remember { mutableStateOf<String?>(null) }
    var qqLoginUrl by remember { mutableStateOf<String?>(null) }

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
                    if (content != null) {
                        if ((content.startsWith(CALLBACK_SCHEME_PREFIX) || content.contains("oauth/callback")) && content.contains("code=")) {
                            AppLogger.debug("Found callback URL in clipboard: $content")
                            clipboard.setContents(StringSelection(""), null)
                            withContext(Dispatchers.Main) {
                                container.authViewModel.handleGithubCallbackUrl(content)
                            }
                        } else if (content.contains("github/callback") && content.contains("code=")) {
                            AppLogger.debug("Found backend callback URL in clipboard: ${content.take(160)}")
                            clipboard.setContents(StringSelection(""), null)
                            withContext(Dispatchers.Main) {
                                container.authViewModel.handleGithubCallbackUrl(content)
                            }
                        } else if (content.contains("ardapi") || content.contains("link.mczihan") || content.contains("callback") || content.contains("github.com")) {
                            // 记录非匹配但相关的剪贴板内容，便于诊断登录流程
                            AppLogger.debug("Clipboard URL-like (no match): ${content.take(140)}")
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.debug("Clipboard poll error: ${e.message}")
            }
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

            // 用 OkHttp 请求后端 start 接口（绕过 Cloudflare 对浏览器直连的拦截），
            // 解析 302 响应拿到 GitHub 授权 URL，再交给内嵌 WebView 打开。
            // 这样无需外部浏览器、无需手动复制 deep link，WebView 直接拦截回调导航。
            val githubUrl = resolveGithubAuthorizeUrl(startUrl) ?: startUrl
            AppLogger.debug("GitHub login opening in WebView: $githubUrl")
            githubLoginUrl = githubUrl
        },
        onOpenQqLogin = {
            val qqUrl = container.authViewModel.beginQq()
            if (qqUrl == null) {
                container.authViewModel.reportError("无法开始 QQ 登录，请稍后重试")
                return@AndroidResourceDownloadRoot
            }
            AppLogger.debug("QQ login opening in WebView: $qqUrl")
            qqLoginUrl = qqUrl
        },
    )

    githubLoginUrl?.let { url ->
        GithubLoginWebViewDialog(
            authorizeUrl = url,
            onDeepLink = { deepLink ->
                githubLoginUrl = null
                AppLogger.debug("WebView captured deep link: ${deepLink.take(140)}")
                container.authViewModel.handleGithubCallbackUrl(deepLink)
            },
            onCancel = { githubLoginUrl = null },
        )
    }

    qqLoginUrl?.let { url ->
        QqLoginWebViewDialog(
            authorizeUrl = url,
            onDeepLink = { deepLink ->
                qqLoginUrl = null
                AppLogger.debug("QQ WebView captured token: ${deepLink.take(140)}")
                container.authViewModel.handleQqCallbackUrl(deepLink)
            },
            onCancel = { qqLoginUrl = null },
        )
    }
}

private fun openUrlInBrowser(url: String) {
    // 优先用系统默认浏览器打开，避免 cmd 对 URL 中特殊字符的处理问题
    try {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
            return
        }
    } catch (_: Exception) { }
    try {
        ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
    } catch (_: Exception) { }
}

/**
 * 通过 OkHttp 请求后端 start 接口（跟随重定向关闭），
 * 解析 302 响应的 Location 拿到 GitHub 授权 URL。
 * 失败（Cloudflare 拦截 / 超时 / 非 3xx）时返回 null，由调用方回退。
 */
private fun resolveGithubAuthorizeUrl(startUrl: String): String? {
    return try {
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(startUrl).build()
        client.newCall(request).execute().use { response ->
            if (response.code in 300..399) response.header("Location") else null
        }
    } catch (_: Exception) {
        null
    }
}


/**
 * 读取 Windows 系统代理设置并应用到 JavaFX WebView。
 * JavaFX WebView 使用 Java 网络栈，需要显式设置 http/https 代理，
 * 否则用户开启梯子时 WebView 无法访问 GitHub。
 */
private val defaultProxySelector: java.net.ProxySelector = java.net.ProxySelector.getDefault()

internal fun configureSystemProxyForJavaFx() {
    try {
        val base = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
        val enableOut = ProcessBuilder("reg", "query", base, "/v", "ProxyEnable")
            .redirectErrorStream(true).start().inputStream.bufferedReader().readText()
        val serverOut = ProcessBuilder("reg", "query", base, "/v", "ProxyServer")
            .redirectErrorStream(true).start().inputStream.bufferedReader().readText()
        val enabled = enableOut.contains("0x1") || enableOut.contains("REG_DWORD    0x1")
        if (!enabled) {
            // 系统代理已关闭 → 恢复默认 ProxySelector（直连）
            java.net.ProxySelector.setDefault(defaultProxySelector)
            AppLogger.debug("JavaFX WebView proxy: 系统代理已关闭，恢复直连")
            return
        }
        val m = Regex("ProxyServer\\s+REG_SZ\\s+(\\S+)").find(serverOut)
        val addr = m?.groupValues?.get(1) ?: return
        val parts = addr.split(":")
        val host = parts[0].trim()
        val port = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 80
        if (host.isBlank()) return
        // 1) Java 网络栈（HttpURLConnection/OkHttp 等）走系统代理
        System.setProperty("http.proxyHost", host)
        System.setProperty("http.proxyPort", port.toString())
        System.setProperty("https.proxyHost", host)
        System.setProperty("https.proxyPort", port.toString())
        // 2) 关键：JavaFX WebView 的 WebKit 网络栈不读取 http.proxyHost 系统属性，
        //    必须通过 java.net.ProxySelector 显式指定代理，否则 WebView 直连（国内访问 GitHub 超时）。
        java.net.ProxySelector.setDefault(
            object : java.net.ProxySelector() {
                override fun select(uri: java.net.URI): List<java.net.Proxy> {
                    return listOf(
                        java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(host, port)),
                    )
                }
                override fun connectFailed(
                    uri: java.net.URI,
                    sa: java.net.SocketAddress,
                    ioe: java.io.IOException,
                ) { }
            },
        )
        AppLogger.debug("JavaFX WebView proxy configured: $host:$port")
    } catch (error: Exception) {
        AppLogger.debug("configureSystemProxyForJavaFx failed: ${error.message}")
    }
}
