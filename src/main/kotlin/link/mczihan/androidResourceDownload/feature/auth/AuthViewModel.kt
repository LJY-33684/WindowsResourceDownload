package link.mczihan.androidResourceDownload.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import link.mczihan.androidResourceDownload.BuildConfig
import link.mczihan.androidResourceDownload.data.auth.AuthRepository
import link.mczihan.androidResourceDownload.domain.model.AuthSession
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredentialProvider
import link.mczihan.androidResourceDownload.service.DesktopDownloadQueueController

sealed interface AuthUiState {
    data object Restoring : AuthUiState
    data object Anonymous : AuthUiState
    data object SendingCode : AuthUiState
    data class AwaitingCode(val email: String, val expiresInSeconds: Int) : AuthUiState
    data object Authenticating : AuthUiState
    data object LoggingOut : AuthUiState
    data class Authenticated(val session: AuthSession) : AuthUiState
    data class Error(val message: String, val recoverableState: AuthUiState = Anonymous) : AuthUiState
}

class AuthViewModel(
    private val repository: AuthRepository,
    private val pendingOAuthStore: DesktopPendingOAuthStore,
    private val credentialProvider: WebDavCredentialProvider,
    private val oauthCallbackBus: DesktopOAuthCallbackBus,
    private val downloadQueueController: DesktopDownloadQueueController,
) : ViewModel() {
    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Restoring)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()
    private val _privacyConsentAccepted = MutableStateFlow(false)
    val privacyConsentAccepted: StateFlow<Boolean> = _privacyConsentAccepted.asStateFlow()
    private val githubCallbackMutex = Mutex()

    fun acceptPrivacyPolicy() {
        _privacyConsentAccepted.value = true
    }

    init {
        restore()
        viewModelScope.launch {
            oauthCallbackBus.events.collect { url ->
                url?.let {
                    oauthCallbackBus.consume(it)
                    handleGithubCallback(it)
                }
            }
        }
    }

    fun restore() {
        viewModelScope.launch {
            _state.value = AuthUiState.Restoring
            val restoredState = try {
                repository.restoreSession()?.let(AuthUiState::Authenticated) ?: AuthUiState.Anonymous
            } catch (error: Exception) {
                AuthUiState.Error(error.userMessage(), AuthUiState.Anonymous)
            }
            if (_state.value is AuthUiState.Restoring) _state.value = restoredState
        }
    }

    fun requestCode(email: String) {
        if (_state.value is AuthUiState.LoggingOut) return
        val normalizedEmail = email.trim()
        viewModelScope.launch {
            _state.value = AuthUiState.SendingCode
            _state.value = try {
                AuthUiState.AwaitingCode(
                    email = normalizedEmail,
                    expiresInSeconds = repository.requestEmailCode(normalizedEmail).expiresInSeconds,
                )
            } catch (error: Exception) {
                AuthUiState.Error(error.userMessage())
            }
        }
    }

    fun loginWithEmail(email: String, code: String) {
        if (_state.value is AuthUiState.LoggingOut) return
        val previousState = _state.value.let { state ->
            if (state is AuthUiState.Error) state.recoverableState else state
        }
        val recoverableState = (previousState as? AuthUiState.AwaitingCode)
            ?.takeIf { it.email.equals(email.trim(), ignoreCase = true) }
            ?: AuthUiState.Anonymous
        viewModelScope.launch {
            _state.value = AuthUiState.Authenticating
            _state.value = try {
                repository.loginWithEmail(email, code).asAuthenticatedState()
            } catch (error: Exception) {
                AuthUiState.Error(error.userMessage(), recoverableState)
            }
        }
    }

    fun beginGithub(): String? {
        if (_state.value is AuthUiState.LoggingOut) return null
        if (_state.value is AuthUiState.Authenticating) return null
        if (BuildConfig.API_BASE_URL.contains("example.invalid")) return null
        val baseUrl = runCatching {
            BuildConfig.API_BASE_URL.trim().let {
                if (it.endsWith('/')) it else "$it/"
            }.toHttpUrl()
        }.getOrNull() ?: return null
        // Reuse existing pending transaction if still valid — prevents verifier
        // mismatch when user clicks login repeatedly due to blank browser page.
        val existing = pendingOAuthStore.read()
            ?.takeIf { System.currentTimeMillis() - it.createdAtMillis in 0..OAUTH_MAX_AGE }
        val transaction = existing ?: PendingOAuth(
            appState = Pkce.generateState(),
            verifier = Pkce.generateVerifier(),
            createdAtMillis = System.currentTimeMillis(),
        ).also { pendingOAuthStore.save(it) }
        // Backend does NOT accept redirect_uri — callback is hardcoded to
        // link.mczihan.androidresourcedownload://oauth/callback per API doc §6.2
        return baseUrl.newBuilder()
            .addPathSegments("api/v1/auth/github/start")
            .addQueryParameter("code_challenge", Pkce.challengeFor(transaction.verifier))
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("app_state", transaction.appState)
            .build()
            .toString()
    }

    fun handleGithubCallback(url: HttpUrl) {
        if (_state.value is AuthUiState.LoggingOut) return
        val state = url.queryParameter("app_state") ?: return setError("GitHub 回调缺少状态")
        val pending = pendingOAuthStore.read()
            ?.takeIf { it.appState == state && System.currentTimeMillis() - it.createdAtMillis in 0..OAUTH_MAX_AGE }
            ?: return setError("GitHub 登录状态已失效")
        url.queryParameter("error")?.let {
            pendingOAuthStore.clear()
            return setError("GitHub 登录被取消")
        }
        val code = url.queryParameter("code") ?: return setError("GitHub 回调缺少授权码")
        completeGithubLogin(code, pending)
    }

    /**
     * Handle callback from a raw URL string (e.g. custom scheme link.mczihan.androidresourcedownload://...).
     * Manually parses query parameters to avoid okhttp HttpUrl limitations with dotted schemes.
     */
    fun handleGithubCallbackUrl(urlString: String) {
        if (_state.value is AuthUiState.LoggingOut) {
            debugAuthLog("handleGithubCallbackUrl: ignored (LoggingOut)")
            return
        }
        debugAuthLog("handleGithubCallbackUrl: url=$urlString")
        val params = parseQueryParams(urlString)
        debugAuthLog("handleGithubCallbackUrl: params=$params")
        val state = params["app_state"] ?: run {
            debugAuthLog("handleGithubCallbackUrl: missing app_state")
            return setError("GitHub 回调缺少状态")
        }
        val stored = pendingOAuthStore.read()
        debugAuthLog("handleGithubCallbackUrl: stored appState=${stored?.appState}, age=${stored?.let { System.currentTimeMillis() - it.createdAtMillis }}ms")
        val pending = stored
            ?.takeIf { it.appState == state && System.currentTimeMillis() - it.createdAtMillis in 0..OAUTH_MAX_AGE }
            ?: run {
                debugAuthLog("handleGithubCallbackUrl: pending not found or expired/mismatch")
                return setError("GitHub 登录状态已失效")
            }
        params["error"]?.let {
            debugAuthLog("handleGithubCallbackUrl: error=$it")
            pendingOAuthStore.clear()
            return setError("GitHub 登录被取消")
        }
        val code = params["code"] ?: run {
            debugAuthLog("handleGithubCallbackUrl: missing code")
            return setError("GitHub 回调缺少授权码")
        }
        debugAuthLog("handleGithubCallbackUrl: success, calling completeGithubLogin with code=$code")
        completeGithubLogin(code, pending)
    }

    private fun completeGithubLogin(code: String, pending: PendingOAuth) {
        viewModelScope.launch {
            githubCallbackMutex.withLock {
                if (_state.value is AuthUiState.Authenticated) {
                    debugAuthLog("completeGithubLogin: already authenticated, skipping")
                    return@withLock
                }
                _state.value = AuthUiState.Authenticating
                debugAuthLog("completeGithubLogin: calling repository.completeGitHubLogin, verifier length=${pending.verifier.length}")
                _state.value = try {
                    val result = repository.completeGitHubLogin(code, pending.verifier)
                    debugAuthLog("completeGithubLogin: success, user=${result.user.name ?: result.user.email}")
                    result.asAuthenticatedState().also { pendingOAuthStore.clear() }
                } catch (error: Exception) {
                    debugAuthLog("completeGithubLogin: failed: ${error.message}")
                    error.printStackTrace()
                    AuthUiState.Error(error.userMessage())
                }
            }
        }
    }

    private fun debugAuthLog(message: String) {
        try {
            val logFile = java.io.File(System.getProperty("java.io.tmpdir"), "ard_debug.log")
            logFile.appendText("[${System.currentTimeMillis()}] [AuthViewModel] $message\n")
        } catch (_: Exception) { }
    }

    private fun parseQueryParams(url: String): Map<String, String> {
        val queryStart = url.indexOf('?')
        if (queryStart < 0 || queryStart >= url.length - 1) return emptyMap()
        val query = url.substring(queryStart + 1)
        val fragmentStart = query.indexOf('#')
        val cleanQuery = if (fragmentStart >= 0) query.substring(0, fragmentStart) else query
        return cleanQuery.split('&')
            .mapNotNull { pair ->
                val eq = pair.indexOf('=')
                if (eq < 0) null else {
                    val key = java.net.URLDecoder.decode(pair.substring(0, eq), "UTF-8")
                    val value = java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
                    key to value
                }
            }
            .toMap()
    }

    fun logout() {
        val session = (_state.value as? AuthUiState.Authenticated)?.session ?: return
        val ownerId = session.user.id
        ownerId?.let(downloadQueueController::block)
        _state.value = AuthUiState.LoggingOut
        viewModelScope.launch {
            credentialProvider.clear()
            pendingOAuthStore.clear()
            val stopJob = launch {
                runCatching { downloadQueueController.stop(ownerId) }
            }
            runCatching { repository.logout(session) }
            stopJob.join()
            _state.value = AuthUiState.Anonymous
        }
    }

    fun reportError(message: String) = setError(message)

    private suspend fun AuthSession.asAuthenticatedState(): AuthUiState.Authenticated {
        credentialProvider.clear()
        return AuthUiState.Authenticated(this)
    }

    private fun setError(message: String) {
        _state.value = AuthUiState.Error(message)
    }

    private fun Exception.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "请求失败，请稍后重试"

    private companion object {
        const val OAUTH_MAX_AGE = 10 * 60 * 1_000L
    }
}
