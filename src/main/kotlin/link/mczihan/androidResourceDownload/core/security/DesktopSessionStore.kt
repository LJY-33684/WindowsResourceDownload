package link.mczihan.androidResourceDownload.core.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import link.mczihan.androidResourceDownload.core.platform.AppLogger
import link.mczihan.androidResourceDownload.domain.model.AuthSession
import link.mczihan.androidResourceDownload.domain.model.LoginType
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.domain.model.User
import java.io.File
import java.io.IOException

/**
 * Desktop session store. Uses a simple JSON file in the user's home directory.
 */
class DesktopSessionStore(
    private val storageDir: File = defaultStorageDir(),
) : SessionStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val sessionFile: File = File(storageDir, "auth_session.json")

    init {
        if (!storageDir.exists()) storageDir.mkdirs()
    }

    override suspend fun read(): AuthSession? = withContext(Dispatchers.IO) {
        if (!sessionFile.exists()) {
            AppLogger.debug("SessionStore: ${sessionFile.absolutePath} 不存在")
            return@withContext null
        }
        try {
            val encoded = sessionFile.readText(Charsets.UTF_8)
            val session = json.decodeFromString(PersistedSession.serializer(), encoded).toDomain()
            AppLogger.debug("SessionStore: session 已读取 (${encoded.length} 字节), 过期时间=${session.expiresAtEpochMillis}")
            session
        } catch (error: Exception) {
            AppLogger.error("SessionStore: session 读取/解码失败，已清除", error)
            clearBestEffort()
            null
        }
    }

    override suspend fun write(session: AuthSession) = withContext(Dispatchers.IO) {
        val encoded = json.encodeToString(PersistedSession.serializer(), PersistedSession.fromDomain(session))
        try {
            sessionFile.parentFile?.mkdirs()
            sessionFile.writeText(encoded, Charsets.UTF_8)
            AppLogger.debug("SessionStore: session 已写入 ${sessionFile.absolutePath} (${encoded.length} 字节)")
        } catch (error: Exception) {
            AppLogger.error("SessionStore: session 写入失败 ${sessionFile.absolutePath}", error)
            throw SessionStorageException("Unable to persist session", error)
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            if (sessionFile.exists()) {
                sessionFile.delete()
                AppLogger.debug("SessionStore: session 已清除 ${sessionFile.absolutePath}")
            }
        } catch (error: Exception) {
            throw SessionStorageException("Unable to clear session", error)
        }
    }

    private fun clearBestEffort() {
        runCatching { if (sessionFile.exists()) sessionFile.delete() }
    }

    companion object {
        fun defaultStorageDir(): File {
            val os = System.getProperty("os.name").lowercase()
            val userHome = System.getProperty("user.home")
            return when {
                os.contains("win") -> File(System.getenv("APPDATA") ?: File(userHome, "AppData/Roaming").absolutePath, "AndroidResourceDownload")
                os.contains("mac") -> File(userHome, "Library/Application Support/AndroidResourceDownload")
                else -> File(userHome, ".androidresourcedownload")
            }
        }
    }
}

class SessionStorageException(message: String, cause: Throwable? = null) : IOException(message, cause)

@Serializable
internal data class PersistedSession(
    val schemaVersion: Int,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMillis: Long,
    val user: PersistedUser,
) {
    fun toDomain(): AuthSession {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) { "Unsupported session schema" }
        return AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochMillis = expiresAtEpochMillis,
            user = user.toDomain(),
        )
    }

    companion object {
        private const val CURRENT_SCHEMA_VERSION = 1

        fun fromDomain(session: AuthSession): PersistedSession = PersistedSession(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            expiresAtEpochMillis = session.expiresAtEpochMillis,
            user = PersistedUser.fromDomain(session.user),
        )
    }
}

@Serializable
internal data class PersistedUser(
    val id: String,
    val name: String?,
    val email: String?,
    val role: Role,
    val loginType: LoginType,
    val avatarUrl: String?,
) {
    fun toDomain(): User = User(
        id = id,
        name = name,
        email = email,
        role = role,
        loginType = loginType,
        avatarUrl = avatarUrl,
    )

    companion object {
        fun fromDomain(user: User): PersistedUser = PersistedUser(
            id = user.id,
            name = user.name,
            email = user.email,
            role = user.role,
            loginType = user.loginType,
            avatarUrl = user.avatarUrl,
        )
    }
}
