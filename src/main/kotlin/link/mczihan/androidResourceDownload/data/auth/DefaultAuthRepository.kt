package link.mczihan.androidResourceDownload.data.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import link.mczihan.androidResourceDownload.core.platform.AppLogger
import link.mczihan.androidResourceDownload.core.security.SessionStore
import link.mczihan.androidResourceDownload.domain.model.AuthSession
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredentialProvider

class DefaultAuthRepository(
    private val authApi: AuthApi,
    private val sessionStore: SessionStore,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : AuthRepository {
    private val refreshMutex = Mutex()
    private val sessionMutex = Mutex()

    override suspend fun restoreSession(): AuthSession? {
        val session = sessionStore.read() ?: run {
            AppLogger.debug("restoreSession: 无持久化 session")
            return null
        }
        val now = nowEpochMillis()
        val usable = session.hasUsableAccessToken(now, RESTORE_VALIDITY_BUFFER_MILLIS)
        AppLogger.debug("restoreSession: 过期时间=${session.expiresAtEpochMillis}, now=$now, usable=$usable")
        return if (usable) {
            AppLogger.debug("restoreSession: accessToken 有效，直接恢复")
            session
        } else {
            AppLogger.debug("restoreSession: accessToken 已过期，尝试 refresh")
            refreshSession()
        }
    }

    override suspend fun currentSession(): AuthSession? = sessionStore.read()

    override suspend fun requestEmailCode(email: String): EmailCodeChallenge {
        val response = executeBackendCall {
            authApi.requestEmailCode(EmailCodeRequestDto(email.trim()))
        }
        return EmailCodeChallenge(response.expiresIn)
    }

    override suspend fun loginWithEmail(
        email: String,
        code: String,
        deviceId: String,
    ): AuthSession {
        val response = executeBackendCall {
            authApi.loginWithEmail(
                EmailLoginRequestDto(
                    email = email.trim(),
                    code = code.trim(),
                    deviceId = deviceId,
                ),
            )
        }
        return persistLogin(response)
    }

    override suspend fun completeGitHubLogin(
        code: String,
        codeVerifier: String,
        deviceId: String,
    ): AuthSession {
        val response = executeBackendCall {
            authApi.loginWithGitHub(
                GitHubCompleteRequestDto(
                    code = code,
                    codeVerifier = codeVerifier,
                    deviceId = deviceId,
                ),
            )
        }
        return persistLogin(response)
    }

    override suspend fun loginWithQq(
        accessToken: String,
        openId: String,
        deviceId: String,
    ): AuthSession {
        val response = executeBackendCall {
            authApi.loginWithQq(
                QqLoginRequestDto(
                    accessToken = accessToken,
                    openId = openId,
                    deviceId = deviceId,
                ),
            )
        }
        return persistLogin(response)
    }

    override suspend fun refreshSession(): AuthSession? {
        val observedSession = sessionStore.read() ?: return null
        return refreshMutex.withLock {
            val latestSession = sessionStore.read() ?: return@withLock null
            if (latestSession.refreshToken != observedSession.refreshToken) {
                return@withLock latestSession
            }
            refreshLocked(latestSession)
        }
    }

    override suspend fun synchronizeUser(): AuthSession? {
        val session = restoreSession() ?: return null
        val user = try {
            executeBackendCall { authApi.me(session.bearerHeader()) }.toDomain()
        } catch (error: BackendApiException) {
            if (error.isAuthenticationFailure) sessionStore.clear()
            throw error
        }
        val synchronizedSession = session.copy(user = user)
        return sessionMutex.withLock {
            val current = sessionStore.read()
            if (current?.refreshToken != session.refreshToken) return@withLock current
            synchronizedSession.also { sessionStore.write(it) }
        }
    }

    override suspend fun logout(session: AuthSession?) {
        val targetSession = session ?: sessionStore.read()
        sessionMutex.withLock {
            val current = sessionStore.read()
            if (targetSession == null || current?.refreshToken == targetSession.refreshToken) {
                sessionStore.clear()
            }
        }
        if (targetSession != null) {
            executeBackendCall {
                authApi.logout(
                    authorization = targetSession.bearerHeader(),
                    request = RefreshTokenRequestDto(targetSession.refreshToken),
                )
            }
        }
    }

    private suspend fun persistLogin(response: LoginResponseDto): AuthSession {
        val session = AuthSession(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresAtEpochMillis = expiresAt(response.expiresIn),
            user = response.user.toDomain(),
        )
        sessionMutex.withLock { sessionStore.write(session) }
        return session
    }

    private suspend fun refreshLocked(session: AuthSession): AuthSession? {
        val response = try {
            executeBackendCall {
                authApi.refresh(RefreshTokenRequestDto(session.refreshToken))
            }
        } catch (error: BackendApiException) {
            AppLogger.warn("refreshSession: 后端返回错误 code=${error.backendCode} message=${error.backendMessage}")
            if (error.isAuthenticationFailure) {
                AppLogger.warn("refreshSession: 认证失败，清除持久化 session")
                sessionStore.clear()
                return null
            }
            throw error
        } catch (error: Exception) {
            AppLogger.warn("refreshSession: 网络/其他异常 ${error.message}，保留原 session 文件")
            throw error
        }
        val refreshedSession = session.copy(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresAtEpochMillis = expiresAt(response.expiresIn),
        )
        AppLogger.debug("refreshSession: 刷新成功，新 token 已持久化")
        return sessionMutex.withLock {
            if (sessionStore.read()?.refreshToken != session.refreshToken) return@withLock null
            refreshedSession.also { sessionStore.write(it) }
        }
    }

    private fun expiresAt(expiresInSeconds: Int): Long {
        if (expiresInSeconds <= 0) {
            throw BackendProtocolException("Token expiry must be positive")
        }
        return try {
            Math.addExact(
                nowEpochMillis(),
                Math.multiplyExact(expiresInSeconds.toLong(), MILLIS_PER_SECOND),
            )
        } catch (error: ArithmeticException) {
            throw BackendProtocolException("Token expiry overflowed", error)
        }
    }

    private fun AuthSession.bearerHeader(): String = "Bearer $accessToken"

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val RESTORE_VALIDITY_BUFFER_MILLIS = 30_000L
    }
}
