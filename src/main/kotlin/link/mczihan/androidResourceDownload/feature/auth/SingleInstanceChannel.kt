package link.mczihan.androidResourceDownload.feature.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.ServerSocket
import java.net.Socket

/**
 * Single-instance communication via local socket.
 * The main instance listens on a fixed port; secondary instances connect
 * and send the OAuth callback URL, then exit.
 */
object SingleInstanceChannel {
    private const val PORT = 19876
    private val _callbacks = MutableStateFlow<String?>(null)
    val callbacks: StateFlow<String?> = _callbacks

    /**
     * Try to send a URL to an existing main instance.
     * Returns true if sent successfully (caller should exit), false if no instance is listening.
     */
    fun trySendToExistingInstance(url: String): Boolean {
        return try {
            Socket("127.0.0.1", PORT).use { socket ->
                socket.soTimeout = 2000
                socket.getOutputStream().write(url.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Start the server socket to receive callbacks from secondary instances.
     */
    fun startServer(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            var server: ServerSocket? = null
            try {
                server = ServerSocket(PORT, 4, java.net.InetAddress.getByName("127.0.0.1"))
                while (isActive) {
                    try {
                        val client = server.accept()
                        val url = client.getInputStream().bufferedReader(Charsets.UTF_8).readText()
                        client.close()
                        if (url.isNotBlank()) {
                            _callbacks.value = url
                        }
                    } catch (_: Exception) {
                        // ignore individual client errors
                    }
                }
            } catch (_: Exception) {
                // port already in use or other error — another instance is running
            } finally {
                try { server?.close() } catch (_: Exception) { }
            }
        }
    }

    fun consume(url: String) {
        _callbacks.compareAndSet(url, null)
    }
}
