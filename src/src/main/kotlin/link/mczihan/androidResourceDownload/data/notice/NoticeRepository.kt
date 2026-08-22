package link.mczihan.androidResourceDownload.data.notice

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class NoticeRepository(
    private val client: OkHttpClient,
) {
    private val mutex = Mutex()
    private var cachedContent: String? = null

    suspend fun load(): String? = mutex.withLock {
        try {
            fetch().also { content -> if (content != null) cachedContent = content }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            cachedContent ?: throw error
        }
    }

    private suspend fun fetch(): String? {
        val request = Request.Builder().url(NOTICE_URL).build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@use null
                if (!response.isSuccessful) throw IOException("Notice request failed: ${response.code}")
                val bytes = response.body?.bytes() ?: throw IOException("Notice is too large")
                if (bytes.size > MAX_NOTICE_BYTES) throw IOException("Notice is too large")
                normalizeNotice(bytes)
            }
        }
    }

    private companion object {
        const val MAX_NOTICE_BYTES = 64 * 1024
        const val NOTICE_URL =
            "https://raw.githubusercontent.com/zhuzhuzihan/AndroidResourceDownload/main/notice.txt"
    }
}

internal fun normalizeNotice(bytes: ByteArray): String? = String(bytes, Charsets.UTF_8)
    .removePrefix("\uFEFF")
    .trim()
    .takeIf(String::isNotEmpty)
