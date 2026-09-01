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
 * 内嵌 WebView 的 QQ 授权登录对话框（QQ 互联 client-side，response_type=token）。
 *
 * 原理：在应用内部用 JavaFX WebView 打开 QQ 授权页，用户在 WebView 内完成授权后，
 * QQ 会把浏览器重定向到 redirect_uri，并在 URL 的 fragment（# 之后）携带 access_token。
 * WebView 监听 location 变化并拦截该导航，把完整 URL 回传给登录流程解析 access_token，
 * 完全绕开外部浏览器，无需手动复制地址栏，也无需后端接收回调。
 */
@Composable
fun QqLoginWebViewDialog(
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

            // 持久化 WebView 数据（cookie / localStorage），避免每次都要重新扫码/授权。
            try {
                val userDataDir = File(
                    System.getProperty("user.home"),
                    ".WindowsResourceDownload-webview",
                )
                userDataDir.mkdirs()
                engine.userDataDirectory = userDataDir
            } catch (_: Exception) { }

            // 拦截 QQ 回调导航：一旦 URL 携带 access_token（fragment 或 query），
            // 就停止继续加载并回传完整 URL 给登录流程。
            engine.locationProperty().addListener { _, _, newLocation ->
                if (captured) return@addListener
                val loc = newLocation
                if (loc != null && loc.contains("access_token=")) {
                    captured = true
                    onDeepLink(loc)
                    engine.load("about:blank")
                }
            }

            engine.loadWorker.stateProperty().addListener { _, _, newState ->
                when (newState) {
                    Worker.State.SUCCEEDED -> {
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
                    Text("QQ 授权登录", style = MaterialTheme.typography.titleSmall)
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
