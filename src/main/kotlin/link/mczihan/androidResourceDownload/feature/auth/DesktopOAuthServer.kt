package link.mczihan.androidResourceDownload.feature.auth

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Desktop OAuth callback + proxy server.
 *
 * Two endpoints:
 * - /proxy?target=<url>  → proxies the request to the backend via OkHttp (which reliably
 *                            gets 302), then returns the 302 Location to the browser.
 *                            This bypasses Cloudflare blank-page issues when the browser
 *                            directly requests the backend.
 * - /callback             → receives the final OAuth callback from the backend and
 *                            publishes it to the bus.
 */
class DesktopOAuthServer(
    private val callbackBus: DesktopOAuthCallbackBus,
) {
    private var server: HttpServer? = null
    private val _port = MutableStateFlow(0)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun start(): Int {
        if (server != null) return _port.value
        val newServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val actualPort = newServer.address.port

        // Proxy endpoint: browser → localhost → backend (via OkHttp) → GitHub
        newServer.createContext("/proxy") { exchange ->
            try {
                val query = exchange.requestURI.rawQuery ?: ""
                val params = parseQuery(query)
                val target = params["target"]
                if (target == null) {
                    val body = "Missing target parameter".toByteArray()
                    exchange.sendResponseHeaders(400, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                    return@createContext
                }
                // Use OkHttp to request the backend (reliably gets 302)
                var githubUrl: String? = null
                var errorMsg: String? = null
                try {
                    val request = Request.Builder().url(target).get().build()
                    httpClient.newCall(request).execute().use { resp ->
                        val location = resp.header("Location")
                        if (location != null && (resp.code == 302 || resp.code == 301)) {
                            githubUrl = location
                        } else {
                            errorMsg = "Backend returned ${resp.code} instead of redirect"
                        }
                    }
                } catch (e: Exception) {
                    errorMsg = "Proxy request failed: ${e.message}"
                }

                // 使用服务器端 302 重定向（浏览器原生跟随，最可靠），
                // 避免 meta refresh / JS 在部分浏览器（如 Edge）中被拦截导致无法自动跳转。
                if (githubUrl != null) {
                    exchange.responseHeaders.set("Location", githubUrl)
                    exchange.sendResponseHeaders(302, -1)
                    exchange.close()
                    return@createContext
                }
                val responseHtml = """
                    <html><head><meta charset="utf-8"><title>错误</title></head>
                    <body style="font-family: sans-serif; padding: 20px;">
                    <h2>代理请求失败</h2>
                    <p>${errorMsg ?: "未知错误"}</p>
                    <p>请返回应用重试，或检查网络连接</p>
                    </body></html>
                    """.trimIndent()
                val bodyBytes = responseHtml.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, bodyBytes.size.toLong())
                exchange.responseBody.use { it.write(bodyBytes) }
            } catch (e: Exception) {
                val body = "Proxy error: ${e.message}".toByteArray()
                exchange.sendResponseHeaders(502, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } finally {
                exchange.close()
            }
        }

        // Callback endpoint: receives the final OAuth callback
        newServer.createContext("/callback") { exchange ->
            try {
                val query = exchange.requestURI.rawQuery ?: ""
                val fullUrl = "http://127.0.0.1:$actualPort/callback?$query"
                callbackBus.publish(fullUrl.toHttpUrl())
                val response = """
                    <html><head><meta charset="utf-8"><title>登录成功</title></head>
                    <body style="font-family: sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; margin: 0;">
                    <h2>登录成功</h2>
                    <p>正在返回应用...</p>
                    <p style="color: #666; font-size: 14px;">如果没有自动返回，请切换到应用窗口</p>
                    <script>setTimeout(function(){ window.close(); }, 1500);</script>
                    </body></html>
                """.trimIndent()
                exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
                exchange.responseBody.use { it.write(response.toByteArray()) }
            } finally {
                exchange.close()
            }
        }

        newServer.executor = null
        newServer.start()
        server = newServer
        _port.value = actualPort
        return actualPort
    }

    fun stop() {
        server?.stop(0)
        server = null
        _port.value = 0
    }

    fun redirectUri(): String = "http://127.0.0.1:${_port.value}/callback"

    fun proxyUrl(targetUrl: String): String {
        val encoded = java.net.URLEncoder.encode(targetUrl, "UTF-8")
        return "http://127.0.0.1:${_port.value}/proxy?target=$encoded"
    }

    private fun parseQuery(query: String): Map<String, String> {
        return query.split("&")
            .mapNotNull { pair ->
                val eq = pair.indexOf('=')
                if (eq < 0) null else {
                    val key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8.name())
                    val value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8.name())
                    key to value
                }
            }
            .toMap()
    }
}
