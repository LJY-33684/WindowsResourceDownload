package link.mczihan.androidResourceDownload.feature.auth

import link.mczihan.androidResourceDownload.configureSystemProxyForJavaFx
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import java.io.File

/**
 * 内嵌 WebView 的 GitHub 授权登录对话框。
 *
 * 原理：在应用内部用 JavaFX WebView 打开 GitHub 授权页，用户在 WebView 内完成授权后，
 * 后端会把浏览器重定向到 deep link（link.mczihan.androidresourcedownload://oauth/callback?...）。
 * 由于 Windows 的 Edge/Chrome 无法把带点号的自定义 scheme 拉回应用，这里直接在 WebView 中
 * 监听 location 变化并拦截该导航，从 deep link 中解析出一次性 code + app_state 回传给登录流程。
 * 这样完全绕开外部浏览器，无需手动复制地址栏。
 */
@Composable
fun GithubLoginWebViewDialog(
    authorizeUrl: String,
    onDeepLink: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val jfxPanel = remember { JFXPanel() }
    var error by remember { mutableStateOf<String?>(null) }
    var captured by remember { mutableStateOf(false) }
    var pageReady by remember { mutableStateOf(false) }

    LaunchedEffect(authorizeUrl) {
        // 每次打开登录页时重新读取系统代理并应用到 JavaFX WebView
        configureSystemProxyForJavaFx()
        Platform.setImplicitExit(false)
        Platform.runLater {
            val webView = WebView()
            val engine = webView.engine

            // 持久化 WebView 数据（cookie / localStorage），
            // 保持 GitHub 登录态，避免每次打开授权页都要重新登录。
            try {
                val userDataDir = File(
                    System.getProperty("user.home"),
                    ".WindowsResourceDownload-webview",
                )
                userDataDir.mkdirs()
                engine.userDataDirectory = userDataDir
            } catch (_: Exception) { }

            // 拦截 deep link 导航：一旦 WebView 导航到 oauth/callback（含安卓 deep link 或后端回调），
            // 就停止继续加载并回传完整 URL 给登录流程。
            engine.locationProperty().addListener { _, _, newLocation ->
                if (captured) return@addListener
                val loc = newLocation
                if (loc != null && (
                        loc.startsWith("link.mczihan.androidresourcedownload://") ||
                                loc.contains("oauth/callback") ||
                                loc.contains("auth/github/callback")
                        )) {
                    captured = true
                    onDeepLink(loc)
                    engine.load("about:blank")
                }
            }

            engine.loadWorker.stateProperty().addListener { _, _, newState ->
                when (newState) {
                    Worker.State.SUCCEEDED -> {
                        // 首次加载完成（CSS 渲染完毕）后再露出 WebView，
                        // 避免加载瞬间出现无样式的"复古"页面。
                        if (!captured) pageReady = true
                    }
                    Worker.State.FAILED -> {
                        if (!captured) {
                            val ex = engine.loadWorker.exception
                            error = "加载失败：${ex?.message ?: "未知错误"}"
                        }
                    }
                    else -> { }
                }
            }

            jfxPanel.scene = Scene(webView, 1200.0, 800.0)
            engine.load(authorizeUrl)
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            tonalElevation = 3.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("GitHub 授权登录", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = onCancel) { Text("取消") }
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (pageReady) {
                    SwingPanel(
                        factory = { jfxPanel },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("正在加载授权页…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
