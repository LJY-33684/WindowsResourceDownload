package link.mczihan.androidResourceDownload.di

import link.mczihan.androidResourceDownload.BuildConfig
import link.mczihan.androidResourceDownload.core.security.DesktopSessionStore
import link.mczihan.androidResourceDownload.core.security.SessionStore
import link.mczihan.androidResourceDownload.data.auth.AuthApi
import link.mczihan.androidResourceDownload.data.auth.DefaultAuthRepository
import link.mczihan.androidResourceDownload.data.auth.WebDavCredentialApi
import link.mczihan.androidResourceDownload.data.download.DesktopDownloadFileOpener
import link.mczihan.androidResourceDownload.data.download.DesktopPublicDownloadStore
import link.mczihan.androidResourceDownload.data.download.DownloadFileStore
import link.mczihan.androidResourceDownload.data.download.DownloadRepository
import link.mczihan.androidResourceDownload.data.download.InMemoryDownloadTaskDao
import link.mczihan.androidResourceDownload.data.file.WebDavFileRepository
import link.mczihan.androidResourceDownload.data.file.WindowsFileUploadSource
import link.mczihan.androidResourceDownload.data.notice.NoticeRepository
import link.mczihan.androidResourceDownload.data.profile.QqNicknameRepository
import link.mczihan.androidResourceDownload.data.settings.DesktopThemeRepository
import link.mczihan.androidResourceDownload.data.update.UpdateRepository
import link.mczihan.androidResourceDownload.data.webdav.BackendWebDavCredentialLoader
import link.mczihan.androidResourceDownload.data.webdav.CredentialBackedWebDavClient
import link.mczihan.androidResourceDownload.data.webdav.InMemoryWebDavCredentialProvider
import link.mczihan.androidResourceDownload.domain.webdav.WebDavClient
import link.mczihan.androidResourceDownload.domain.webdav.WebDavCredentialProvider
import link.mczihan.androidResourceDownload.feature.auth.DesktopOAuthCallbackBus
import link.mczihan.androidResourceDownload.feature.auth.DesktopOAuthServer
import link.mczihan.androidResourceDownload.feature.auth.DesktopPendingOAuthStore
import link.mczihan.androidResourceDownload.feature.auth.AuthViewModel
import link.mczihan.androidResourceDownload.feature.downloads.DownloadsViewModel
import link.mczihan.androidResourceDownload.feature.files.FilesViewModel
import link.mczihan.androidResourceDownload.feature.profile.ProfileViewModel
import link.mczihan.androidResourceDownload.feature.settings.SettingsViewModel
import link.mczihan.androidResourceDownload.feature.settings.ThemeViewModel
import link.mczihan.androidResourceDownload.feature.uploads.UploadsViewModel
import link.mczihan.androidResourceDownload.service.DesktopDownloadExecutionRegistry
import link.mczihan.androidResourceDownload.service.DesktopDownloadQueueController
import link.mczihan.androidResourceDownload.service.DesktopDownloadTransferEngine
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import java.io.File

/**
 * Manual dependency injection container for the desktop app.
 * Replaces Hilt.
 */
class AppContainer {
    // ─── Network ───────────────────────────────────────────────
    private val backendHttpClient: OkHttpClient = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor { message -> println("OkHttp: $message") }.apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    },
                )
            }
        }
        .build()

    private val webDavHttpClient: OkHttpClient = OkHttpClient.Builder().build()

    // Public client for QQ nickname, notice, update checks (no auth)
    private val publicHttpClient: OkHttpClient = OkHttpClient.Builder().build()

    private val backendJson = Json { ignoreUnknownKeys = true }

    private val backendRetrofit: Retrofit = run {
        val baseUrl = BuildConfig.API_BASE_URL.trim().let {
            if (it.endsWith('/')) it else "$it/"
        }
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(backendHttpClient)
            .addConverterFactory(backendJson.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private val authApi: AuthApi = backendRetrofit.create(AuthApi::class.java)
    private val credentialApi: WebDavCredentialApi = backendRetrofit.create(WebDavCredentialApi::class.java)

    // ─── Session & Auth ────────────────────────────────────────
    val sessionStore: SessionStore = DesktopSessionStore()
    val authRepository = DefaultAuthRepository(authApi, sessionStore)

    // ─── WebDAV ────────────────────────────────────────────────
    val webDavCredentialProvider: WebDavCredentialProvider = InMemoryWebDavCredentialProvider(
        BackendWebDavCredentialLoader(credentialApi, authRepository),
    )
    val webDavClient: WebDavClient = CredentialBackedWebDavClient(webDavCredentialProvider, webDavHttpClient)
    val fileRepository = WebDavFileRepository(webDavClient)
    val uploadSource = WindowsFileUploadSource()

    // ─── Download ──────────────────────────────────────────────
    private val downloadDao = InMemoryDownloadTaskDao()
    private val downloadFileStore = DownloadFileStore(File(System.getProperty("java.io.tmpdir"), "ard-downloads"))
    val publicDownloadStore = DesktopPublicDownloadStore()
    val downloadRepository = DownloadRepository(downloadDao, downloadFileStore, publicDownloadStore)
    private val executionRegistry = DesktopDownloadExecutionRegistry()
    private val transferEngine = DesktopDownloadTransferEngine(
        repository = downloadRepository,
        webDavClient = webDavClient,
        fileStore = downloadFileStore,
        publicDownloadStore = publicDownloadStore,
        executionRegistry = executionRegistry,
    )
    val downloadQueueController = DesktopDownloadQueueController(
        repository = downloadRepository,
        executionRegistry = executionRegistry,
        transferEngine = transferEngine,
    )
    val downloadFileOpener = DesktopDownloadFileOpener(publicDownloadStore, downloadFileStore)

    // ─── Settings ──────────────────────────────────────────────
    private val themeRepository = DesktopThemeRepository()
    private val qqNicknameRepository = QqNicknameRepository(publicHttpClient)
    private val noticeRepository = NoticeRepository(publicHttpClient)
    private val updateRepository = UpdateRepository(publicHttpClient)

    // ─── OAuth ─────────────────────────────────────────────────
    val pendingOAuthStore = DesktopPendingOAuthStore()
    val oauthCallbackBus = DesktopOAuthCallbackBus()
    val oauthServer = DesktopOAuthServer(oauthCallbackBus)

    // ─── ViewModels ────────────────────────────────────────────
    val authViewModel = AuthViewModel(
        repository = authRepository,
        pendingOAuthStore = pendingOAuthStore,
        credentialProvider = webDavCredentialProvider,
        oauthCallbackBus = oauthCallbackBus,
        downloadQueueController = downloadQueueController,
    )
    val filesViewModel = FilesViewModel(repository = fileRepository, uploadSource = uploadSource, themeRepository = themeRepository)
    val downloadsViewModel = DownloadsViewModel(
        repository = downloadRepository,
        queueController = downloadQueueController,
        fileOpener = downloadFileOpener,
    )
    val uploadsViewModel = UploadsViewModel(
        repository = fileRepository,
        uploadSource = uploadSource,
    )
    val themeViewModel = ThemeViewModel(themeRepository = themeRepository)
    val profileViewModel = ProfileViewModel(qqNicknameRepository = qqNicknameRepository)
    val settingsViewModel = SettingsViewModel(
        noticeRepository = noticeRepository,
        updateRepository = updateRepository,
    )
}
