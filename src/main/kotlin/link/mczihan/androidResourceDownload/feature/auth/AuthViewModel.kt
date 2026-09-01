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
import link.mczihan.androidResourceDownload.core.platform.AppLogger
import link.mczihan.androidResourceDownload.data.auth.AuthRepository
import link.mczihan.androidResourceDownload.domain.model.AuthSession
import link.mczihan.androidResourceDownload.domain.model.LoginType
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.domain.model.User
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
    private var qqState: String? = null
    private var qqStateCreatedAt: Long = 0L

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
            // 测试模式（本地 UI 权限验证用）：跳过登录，以普通用户身份进入。
            // 由测试版启动器通过 -Dard.testUser=1 系统属性触发，正式版不受影响。
            if (System.getProperty("ard.testUser") == "1") {
                debugAuthLog("TEST MODE: ard.testUser=1，跳过登录以普通用户进入")
                _state.value = AuthUiState.Authenticated(
                    AuthSession(
                        accessToken = "ard-test-user",
                        refreshToken = "ard-test-user",
                        expiresAtEpochMillis = Long.MAX_VALUE,
                        user = User(
                            id = "test-user",
                            name = "测试用户",
                            email = "test@ard.local",
                            role = Role.USER,
                            loginType = LoginType.GITHUB,
                        ),
                    )
                )
                return@launch
            }
            val restoredState = try {
                repository.restoreSession()?.let {
                    debugAuthLog("restore: 自动恢复登录成功 ${it.user.name ?: it.user.email}")
                    AuthUiState.Authenticated(it)
                } ?: AuthUiState.Anonymous.also {
                    debugAuthLog("restore: 无有效 session，进入登录页")
                }
            } catch (error: Exception) {
                debugAuthLog("restore: 恢复失败: ${error.message}")
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
        // 每次登录都生成全新的 app_state 和 verifier（与安卓端一致）。
        // 若复用旧的 app_state，反复点击登录会导致后端 state 映射被覆盖，
        // 用户授权旧页面时后端回调会因 state 失效而返回 github_auth_failed。
        val transaction = PendingOAuth(
            appState = Pkce.generateState(),
            verifier = Pkce.generateVerifier(),
            createdAtMillis = System.currentTimeMillis(),
        ).also { pendingOAuthStore.save(it) }
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
     * Handle callback from a raw URL string (e.g. custom scheme WindowsResourceDownload://...).
     * Manually parses query parameters to avoid okhttp HttpUrl limitations with dotted schemes.
     */
    fun handleGithubCallbackUrl(urlString: String) {
        if (_state.value is AuthUiState.LoggingOut) {
            debugAuthLog("handleGithubCallbackUrl: ignored (LoggingOut)")
            return
        }
        // 兼容：剪贴板/外部输入可能是后端 callback URL（github/callback?code=...），
        // 需要先请求后端换取 deep link，再解析其中一次性 code 完成登录。
        if (urlString.contains("github/callback") && urlString.contains("code=") && !urlString.contains("oauth/callback")) {
            debugAuthLog("handleGithubCallbackUrl: backend callback URL, resolving via backend")
            val deepLink = resolveBackendCallback(urlString)
            if (deepLink != null) {
                debugAuthLog("handleGithubCallbackUrl: resolved deep link: $deepLink")
                return handleGithubCallbackUrl(deepLink)
            }
            return setError("后端回调解析失败，请重试")
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

    /**
     * 开始 QQ 网页授权（QQ 互联 client-side，response_type=token）。
     * 返回授权 URL；授权成功后 QQ 会以 redirect_uri#access_token=...&state=... 回跳，
     * 由内嵌 WebView 捕获 fragment 并调用 [handleQqCallbackUrl]。
     */
    fun beginQq(): String? {
        if (_state.value is AuthUiState.LoggingOut) return null
        if (_state.value is AuthUiState.Authenticating) return null
        val state = Pkce.generateState()
        qqState = state
        qqStateCreatedAt = System.currentTimeMillis()
        return buildString {
            append("https://graph.qq.com/oauth2.0/authorize")
            append("?response_type=token")
            append("&client_id=").append(BuildConfig.QQ_APP_ID)
            append("&redirect_uri=").append(java.net.URLEncoder.encode(BuildConfig.QQ_REDIRECT_URI, "UTF-8"))
            append("&scope=get_user_info")
            append("&state=").append(state)
        }
    }

    /**
     * 处理 QQ client-side 回调：URL 的 fragment（# 之后）携带 access_token。
     * 校验 state 后换取 openid 并调后端 /api/v1/auth/qq/login 完成登录。
     */
    fun handleQqCallbackUrl(urlString: String) {
        if (_state.value is AuthUiState.LoggingOut) {
            debugAuthLog("handleQqCallbackUrl: ignored (LoggingOut)")
            return
        }
        debugAuthLog("handleQqCallbackUrl: url=$urlString")
        val fragmentStart = urlString.indexOf('#')
        if (fragmentStart < 0 || fragmentStart >= urlString.length - 1) {
            debugAuthLog("handleQqCallbackUrl: no fragment")
            return setError("QQ 回调缺少令牌")
        }
        val fragment = urlString.substring(fragmentStart + 1)
        val params = parseQueryParams("?" + fragment)
        debugAuthLog("handleQqCallbackUrl: params keys=${params.keys}")
        val state = params["state"]
        val stored = qqState
        if (state == null || stored == null || state != stored ||
            System.currentTimeMillis() - qqStateCreatedAt > OAUTH_MAX_AGE
        ) {
            debugAuthLog("handleQqCallbackUrl: state mismatch or expired")
            return setError("QQ 登录状态已失效")
        }
        if (params["error"] != null) {
            qqState = null
            debugAuthLog("handleQqCallbackUrl: error=${params["error"]}")
            return setError("QQ 登录被取消")
        }
        val accessToken = params["access_token"] ?: run {
            debugAuthLog("handleQqCallbackUrl: missing access_token")
            return setError("QQ 回调缺少 access_token")
        }
        debugAuthLog("handleQqCallbackUrl: token captured, calling completeQqLogin")
        completeQqLogin(accessToken)
    }

    private fun completeQqLogin(accessToken: String) {
        viewModelScope.launch {
            if (_state.value is AuthUiState.Authenticated) {
                debugAuthLog("completeQqLogin: already authenticated, skipping")
                return@launch
            }
            _state.value = AuthUiState.Authenticating
            _state.value = try {
                val openId = fetchQqOpenId(accessToken)
                debugAuthLog("completeQqLogin: openId=$openId, calling repository.loginWithQq")
                val result = repository.loginWithQq(accessToken, openId)
                qqState = null
                debugAuthLog("completeQqLogin: success, user=${result.user.name ?: result.user.email}")
                result.asAuthenticatedState()
            } catch (error: Exception) {
                debugAuthLog("completeQqLogin: failed: ${error.message}")
                error.printStackTrace()
                AuthUiState.Error(error.userMessage())
            }
        }
    }

    /** 调用 QQ 互联 /oauth2.0/me 换取 openid（返回格式 callback( {...} );）。 */
    private fun fetchQqOpenId(accessToken: String): String {
        val url = "https://graph.qq.com/oauth2.0/me?access_token=$accessToken"
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "WindowsResourceDownload")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            debugAuthLog("fetchQqOpenId: body=${body.take(160)}")
            val jsonStart = body.indexOf('{')
            val jsonEnd = body.lastIndexOf('}')
            if (jsonStart < 0 || jsonEnd < 0) {
                throw IllegalStateException("QQ openid 解析失败")
            }
            val json = body.substring(jsonStart, jsonEnd + 1)
            val match = Regex("\"openid\"\\s*:\\s*\"([^\"]+)\"").find(json)
                ?: throw IllegalStateException("QQ openid 缺失")
            return match.groupValues[1]
        } finally {
            conn.disconnect()
        }
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
                    debugAuthLog("completeGithubLogin: success, user=${result.user.name ?: result.user.email}, expiresAt=${result.expiresAtEpochMillis}")
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
        AppLogger.debug("[Auth] $message")
    }

    private fun resolveBackendCallback(url: String): String? {
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "WindowsResourceDownload")
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            debugAuthLog("resolveBackendCallback: http=$code location=$location")
            conn.disconnect()
            if ((code == 302 || code == 301) && location != null) location else null
        } catch (e: Exception) {
            debugAuthLog("resolveBackendCallback: error=${e.message}")
            null
        }
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
