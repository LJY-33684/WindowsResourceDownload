package link.mczihan.androidResourceDownload.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI
import link.mczihan.androidResourceDownload.core.theme.AndroidResourceDownloadTheme
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.Role
import link.mczihan.androidResourceDownload.core.common.RolePreview
import link.mczihan.androidResourceDownload.core.theme.ThemeMode
import link.mczihan.androidResourceDownload.core.theme.ThemeSchemeVariant
import link.mczihan.androidResourceDownload.domain.model.User
import link.mczihan.androidResourceDownload.feature.auth.AuthUiState
import link.mczihan.androidResourceDownload.feature.auth.AuthViewModel
import link.mczihan.androidResourceDownload.feature.auth.EmailVerificationScreen
import link.mczihan.androidResourceDownload.feature.auth.LoginScreen
import link.mczihan.androidResourceDownload.feature.downloads.DownloadsScreen
import link.mczihan.androidResourceDownload.feature.downloads.DownloadsViewModel
import link.mczihan.androidResourceDownload.feature.files.FilesScreen
import link.mczihan.androidResourceDownload.feature.files.FilesViewModel
import link.mczihan.androidResourceDownload.feature.profile.ProfileScreen
import link.mczihan.androidResourceDownload.feature.profile.ProfileViewModel
import link.mczihan.androidResourceDownload.feature.settings.SettingsScreen
import link.mczihan.androidResourceDownload.feature.settings.SettingsViewModel
import link.mczihan.androidResourceDownload.feature.settings.ThemeViewModel
import link.mczihan.androidResourceDownload.feature.uploads.UploadsScreen
import link.mczihan.androidResourceDownload.feature.uploads.UploadsViewModel

private enum class RootScreen { Login, Email, Main, Profile }

private enum class ShellRoute(
    val label: String,
    val adminOnly: Boolean = false,
) {
    Files("文件"),
    Uploads("上传", adminOnly = true),
    Downloads("下载"),
    Settings("设置"),
}

@Composable
fun AndroidResourceDownloadRoot(
    themeViewModel: ThemeViewModel,
    authViewModel: AuthViewModel,
    filesViewModel: FilesViewModel,
    downloadsViewModel: DownloadsViewModel,
    uploadsViewModel: UploadsViewModel,
    profileViewModel: ProfileViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenGithubLogin: () -> Unit,
    onOpenQqLogin: () -> Unit,
) {
    val themeMode by themeViewModel.themeMode.collectAsState()
    val themeSettings by themeViewModel.settings.collectAsState()
    val authState by authViewModel.state.collectAsState()
    val privacyConsentAccepted by authViewModel.privacyConsentAccepted.collectAsState()

    AndroidResourceDownloadTheme(
        themeMode = themeMode,
        dynamicColorEnabled = themeSettings.dynamicColorEnabled,
        seedColorArgb = themeSettings.seedColorArgb,
        schemeVariant = themeSettings.schemeVariant,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            var rootScreen by remember { mutableStateOf(RootScreen.Login) }

            LaunchedEffect(authState) {
                rootScreen = when (authState) {
                    is AuthUiState.Authenticated -> RootScreen.Main
                    AuthUiState.Anonymous -> RootScreen.Login
                    else -> rootScreen
                }
            }

            when (rootScreen) {
                RootScreen.Login -> LoginScreen(
                    onGithubLogin = { onOpenGithubLogin() },
                    onQqLogin = { onOpenQqLogin() },
                    onEmailLogin = { rootScreen = RootScreen.Email },
                    busy = authState is AuthUiState.Restoring ||
                        authState is AuthUiState.Authenticating ||
                        authState is AuthUiState.LoggingOut,
                    message = (authState as? AuthUiState.Error)?.message,
                    onPolicyAccepted = authViewModel::acceptPrivacyPolicy,
                )
                RootScreen.Email -> EmailVerificationScreen(
                    onBack = { rootScreen = RootScreen.Login },
                    onVerified = { _, _ -> },
                    onRequestCode = authViewModel::requestCode,
                    onLogin = authViewModel::loginWithEmail,
                    busy = authState is AuthUiState.SendingCode ||
                        authState is AuthUiState.Authenticating ||
                        authState is AuthUiState.LoggingOut,
                    message = (authState as? AuthUiState.Error)?.message,
                    codeSentEmail = when (val state = authState) {
                        is AuthUiState.AwaitingCode -> state.email
                        is AuthUiState.Error -> (state.recoverableState as? AuthUiState.AwaitingCode)?.email
                        else -> null
                    },
                )
                RootScreen.Main -> {
                    val user = (authState as? AuthUiState.Authenticated)?.session?.user
                    LaunchedEffect(user) {
                        if (user == null) rootScreen = RootScreen.Login
                    }
                    if (user != null) {
                        MainShell(
                            user = user,
                            themeMode = themeMode,
                            themeDynamicColorEnabled = themeSettings.dynamicColorEnabled,
                            themeSeedColorArgb = themeSettings.seedColorArgb,
                            themeSchemeVariant = themeSettings.schemeVariant,
                            onThemeModeChange = themeViewModel::setThemeMode,
                            onThemeDynamicColorEnabledChange = themeViewModel::setDynamicColorEnabled,
                            logEnabled = themeSettings.logEnabled,
                            onLogEnabledChange = themeViewModel::setLogEnabled,
                            onThemeSeedColorChange = themeViewModel::setSeedColor,
                            onThemeSchemeVariantChange = themeViewModel::setSchemeVariant,
                            onResetThemeColor = themeViewModel::resetThemeColor,
                            onProfile = { rootScreen = RootScreen.Profile },
                            onLogout = { authViewModel.logout() },
                            filesViewModel = filesViewModel,
                            downloadsViewModel = downloadsViewModel,
                            uploadsViewModel = uploadsViewModel,
                            settingsViewModel = settingsViewModel,
                        )
                    }
                }
                RootScreen.Profile -> {
                    val user = (authState as? AuthUiState.Authenticated)?.session?.user
                    LaunchedEffect(user) {
                        if (user == null) rootScreen = RootScreen.Login
                    }
                    if (user != null) {
                        val qqNickname by profileViewModel.qqNickname.collectAsState()
                        LaunchedEffect(
                            user.id,
                            user.email,
                            user.loginType,
                            privacyConsentAccepted,
                        ) {
                            if (privacyConsentAccepted) {
                                profileViewModel.load(user)
                            } else {
                                profileViewModel.clear()
                            }
                        }
                        ProfileScreen(
                            user = user,
                            qqNickname = qqNickname,
                            allowQqLookup = privacyConsentAccepted,
                            onBack = { rootScreen = RootScreen.Main },
                            onLogout = { authViewModel.logout() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainShell(
    user: User,
    themeMode: ThemeMode,
    themeDynamicColorEnabled: Boolean,
    themeSeedColorArgb: Int,
    themeSchemeVariant: ThemeSchemeVariant,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemeDynamicColorEnabledChange: (Boolean) -> Unit,
    logEnabled: Boolean,
    onLogEnabledChange: (Boolean) -> Unit,
    onThemeSeedColorChange: (Int) -> Unit,
    onThemeSchemeVariantChange: (ThemeSchemeVariant) -> Unit,
    onResetThemeColor: () -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit,
    filesViewModel: FilesViewModel,
    downloadsViewModel: DownloadsViewModel,
    uploadsViewModel: UploadsViewModel,
    settingsViewModel: SettingsViewModel,
) {
    val isAdmin = user.role == Role.ADMIN && !RolePreview.asUser
    var currentTab by remember { mutableStateOf(ShellRoute.Files) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tasks by downloadsViewModel.tasks.collectAsState()
    val currentSpeeds by downloadsViewModel.currentSpeeds.collectAsState()
    val uploadTasks by uploadsViewModel.tasks.collectAsState()
    val uploadSpeeds by uploadsViewModel.currentSpeeds.collectAsState()
    val preparingUploads by uploadsViewModel.preparingSelections.collectAsState()
    val noticeState by settingsViewModel.noticeState.collectAsState()
    val updateState by settingsViewModel.updateState.collectAsState()

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(downloadsViewModel, user.id) {
        downloadsViewModel.bindOwner(user.id)
    }
    LaunchedEffect(uploadsViewModel, user.id) {
        uploadsViewModel.bindOwner(user.id)
    }
    LaunchedEffect(downloadsViewModel) {
        downloadsViewModel.messages.collect(::showMessage)
    }
    LaunchedEffect(uploadsViewModel) {
        uploadsViewModel.messages.collect(::showMessage)
    }

    LaunchedEffect(currentTab) {
        if (currentTab == ShellRoute.Downloads) {
            downloadsViewModel.startSpeedTracking()
        } else {
            downloadsViewModel.stopSpeedTracking()
        }
        if (currentTab == ShellRoute.Uploads) {
            uploadsViewModel.startSpeedTracking()
        } else {
            uploadsViewModel.stopSpeedTracking()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { shellPadding ->
        Row(
            modifier = Modifier.fillMaxSize().padding(shellPadding),
        ) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                ShellRoute.values()
                    .filter { destination -> !destination.adminOnly || isAdmin }
                    .forEach { destination ->
                        val selected = currentTab == destination
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.16f else 1f,
                            label = "navigationIconScale",
                        )
                        NavigationRailItem(
                            selected = selected,
                            onClick = { currentTab = destination },
                            modifier = Modifier.padding(vertical = 14.dp),
                            icon = {
                                Icon(
                                    imageVector = when (destination) {
                                        ShellRoute.Files -> Icons.Default.Folder
                                        ShellRoute.Uploads -> Icons.Default.UploadFile
                                        ShellRoute.Downloads -> Icons.Default.Download
                                        ShellRoute.Settings -> Icons.Default.Settings
                                    },
                                    contentDescription = destination.label,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                                )
                            },
                            label = { Text(destination.label) },
                            colors = NavigationRailItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                when (currentTab) {
            ShellRoute.Files -> FilesScreen(
                viewModel = filesViewModel,
                role = user.role,
                onDownload = { file, relativePath -> downloadsViewModel.enqueue(file, relativePath) },
                onUploadFiles = { files, destination ->
                    uploadsViewModel.enqueueFiles(files, destination)
                    currentTab = ShellRoute.Uploads
                },
                onUploadDirectory = { dir, destination ->
                    uploadsViewModel.enqueueDirectory(dir, destination)
                    currentTab = ShellRoute.Uploads
                },
                onMessage = ::showMessage,
                modifier = Modifier.fillMaxSize(),
            )
            ShellRoute.Uploads -> UploadsScreen(
                tasks = uploadTasks,
                currentSpeeds = uploadSpeeds,
                preparingSelections = preparingUploads,
                onRetry = uploadsViewModel::retry,
                onCancel = uploadsViewModel::cancel,
                onDelete = uploadsViewModel::delete,
                onCancelAll = uploadsViewModel::cancelAll,
                onClearTerminal = uploadsViewModel::clearTerminal,
                modifier = Modifier.fillMaxSize(),
            )
            ShellRoute.Downloads -> DownloadsScreen(
                tasks = tasks,
                currentSpeeds = currentSpeeds,
                onStatusChange = { taskId, status ->
                    when (status) {
                        DownloadStatus.RUNNING -> downloadsViewModel.retry(taskId)
                        DownloadStatus.PAUSED -> downloadsViewModel.pause(taskId)
                        DownloadStatus.CANCELLED -> downloadsViewModel.cancel(taskId)
                        else -> Unit
                    }
                },
                onOpen = { task -> downloadsViewModel.open(task) },
                onDelete = { taskId -> downloadsViewModel.delete(taskId) },
                onDeleteWithOption = { taskId, deleteLocalFile ->
                    downloadsViewModel.delete(taskId, deleteLocalFile)
                },
                onCancelAll = { downloadsViewModel.cancelAll() },
                onClearTerminal = { deleteLocalFiles ->
                    downloadsViewModel.clearTerminal(deleteLocalFiles)
                },
                modifier = Modifier.fillMaxSize(),
            )
            ShellRoute.Settings -> SettingsScreen(
                user = user,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                themeDynamicColorEnabled = themeDynamicColorEnabled,
                themeSeedColorArgb = themeSeedColorArgb,
                themeSchemeVariant = themeSchemeVariant,
                onThemeDynamicColorEnabledChange = onThemeDynamicColorEnabledChange,
                logEnabled = logEnabled,
                onLogEnabledChange = onLogEnabledChange,
                onThemeSeedColorChange = onThemeSeedColorChange,
                onThemeSchemeVariantChange = onThemeSchemeVariantChange,
                onResetThemeColor = onResetThemeColor,
                noticeState = noticeState,
                updateState = updateState,
                onRetryNotice = settingsViewModel::refreshNotice,
                onCheckUpdate = settingsViewModel::checkForUpdate,
                onDismissUpdate = settingsViewModel::dismissUpdateResult,
                onOpenUpdateUrl = ::openUrlInBrowser,
                onLogout = onLogout,
                modifier = Modifier.fillMaxSize(),
            )
            }
            }
        }
    }
}

private fun openUrlInBrowser(url: String): Boolean {
    return try {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
            true
        } else {
            false
        }
    } catch (_: Exception) {
        try {
            ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
            true
        } catch (_: Exception) {
            false
        }
    }
}
