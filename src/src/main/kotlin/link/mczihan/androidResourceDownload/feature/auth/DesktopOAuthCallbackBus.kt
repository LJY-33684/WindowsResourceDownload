package link.mczihan.androidResourceDownload.feature.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl

/**
 * Desktop OAuth callback bus. Uses okhttp3.HttpUrl instead of Android Uri.
 */
class DesktopOAuthCallbackBus {
    private val _events = MutableStateFlow<HttpUrl?>(null)
    val events: StateFlow<HttpUrl?> = _events.asStateFlow()

    fun publish(uri: HttpUrl?) {
        if (uri != null) _events.value = uri
    }

    fun consume(uri: HttpUrl) {
        _events.compareAndSet(uri, null)
    }
}
