package link.mczihan.androidResourceDownload.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.core.theme.AndroidResourceDownloadTheme
import link.mczihan.androidResourceDownload.domain.model.DownloadStatus
import link.mczihan.androidResourceDownload.domain.model.DownloadTask
import link.mczihan.androidResourceDownload.domain.model.FileNode
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
import java.awt.Desktop
import java.net.URI

private object RootRoute {
    const val Login = "login"
    const val Email = "email"
    const val Main = "main"
    const val Profile = "profile"
}

private enum class ShellRoute(
    val route: String,
    val label: String,
    val adminOnly: Boolean = false,
) {
    Files("files", "文件"),
    Uploads("uploads", "上传", adminOnly = true),
    Downloads("downloads", "下载"),
    Settings("settings", "设置"),
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
) {
    val themeMode by themeViewModel.themeMode.collectAsState()
    val themeSettings by themeViewModel.settings.collectAsState()
    val authState by authViewModel.state.collectAsState()
    val privacyConsentAccepted by authViewModel.privacyConsentAccepted.collectAsState()

    AndroidResourceDownloadTheme(
        themeMode = themeMode,
        seedColorArgb = themeSettings.seedColorArgb,
        schemeVariant = themeSettings.schemeVariant,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val navController = rememberNavController()
            LaunchedEffect(authState) {
                when (authState) {
                    is AuthUiState.Authenticated -> navController.navigate(RootRoute.Main) {
                        popUpTo(RootRoute.Login) { inclusive = true }
                        launchSingleTop = true
                    }
                    AuthUiState.Anonymous -> navController.navigate(RootRoute.Login) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                    else -> Unit
                }
            }
            NavHost(
                navController = navController,
                startDestination = RootRoute.Login,
            ) {
                composable(RootRoute.Login) {
                    LoginScreen(
                        onGithubLogin = {
                            onOpenGithubLogin()
                        },
                        onEmailLogin = { navController.navigate(RootRoute.Email) },
                        busy = authState is AuthUiState.Restoring ||
                            authState is AuthUiState.Authenticating ||
                            authState is AuthUiState.LoggingOut,
                        message = (authState as? AuthUiState.Error)?.message,
                        onPolicyAccepted = authViewModel::acceptPrivacyPolicy,
                    )
                }
                composable(RootRoute.Email) {
                    EmailVerificationScreen(
                        onBack = { navController.popBackStack() },
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
                }
                composable(RootRoute.Main) {
                    val user = (authState as? AuthUiState.Authenticated)?.session?.user
                    if (user == null) {
                        LaunchedEffect(Unit) {
                            navController.navigate(RootRoute.Login) {
                                popUpTo(RootRoute.Main) { inclusive = true }
                            }
                        }
                    } else {
                        MainShell(
                            user = user,
                            themeMode = themeMode,
                            themeSeedColorArgb = themeSettings.seedColorArgb,
                            themeSchemeVariant = themeSettings.schemeVariant,
                            onThemeModeChange = themeViewModel::setThemeMode,
                            onThemeSeedColorChange = themeViewModel::setSeedColor,
                            onThemeSchemeVariantChange = themeViewModel::setSchemeVariant,
                            onResetThemeColor = themeViewModel::resetThemeColor,
                            onProfile = { navController.navigate(RootRoute.Profile) },
                            onLogout = { authViewModel.logout() },
                            filesViewModel = filesViewModel,
                            downloadsViewModel = downloadsViewModel,
                            uploadsViewModel = uploadsViewModel,
                            settingsViewModel = settingsViewModel,
                        )
                    }
                }
                composable(RootRoute.Profile) {
                    val user = (authState as? AuthUiState.Authenticated)?.session?.user
                    if (user == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
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
                            onBack = { navController.popBackStack() },
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
    user: link.mczihan.androidResourceDownload.domain.model.User,
    themeMode: link.mczihan.androidResourceDownload.core.theme.ThemeMode,
    themeSeedColorArgb: Int,
    themeSchemeVariant: link.mczihan.androidResourceDownload.core.theme.ThemeSchemeVariant,
    onThemeModeChange: (link.mczihan.androidResourceDownload.core.theme.ThemeMode) -> Unit,
    onThemeSeedColorChange: (Int) -> Unit,
    onThemeSchemeVariantChange: (link.mczihan.androidResourceDownload.core.theme.ThemeSchemeVariant) -> Unit,
    onResetThemeColor: () -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit,
    filesViewModel: FilesViewModel,
    downloadsViewModel: DownloadsViewModel,
    uploadsViewModel: UploadsViewModel,
    settingsViewModel: SettingsViewModel,
) {
    val isAdmin = user.role == link.mczihan.androidResourceDownload.domain.model.Role.ADMIN
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
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

    // Start speed tracking when on downloads/uploads tab, stop when leaving
    LaunchedEffect(currentRoute) {
        if (currentRoute == ShellRoute.Downloads.route) {
            downloadsViewModel.startSpeedTracking()
        } else {
            downloadsViewModel.stopSpeedTracking()
        }
        if (currentRoute == ShellRoute.Uploads.route) {
            uploadsViewModel.startSpeedTracking()
        } else {
            uploadsViewModel.stopSpeedTracking()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                ShellRoute.values()
                    .filter { destination -> !destination.adminOnly || isAdmin }
                    .forEach { destination ->
                    val selected = currentRoute == destination.route
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.16f else 1f,
                        label = "navigationIconScale",
                    )
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
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
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
        },
    ) { shellPadding ->
        NavHost(
            navController = navController,
            startDestination = ShellRoute.Files.route,
            modifier = Modifier.padding(shellPadding),
        ) {
            composable(ShellRoute.Files.route) {
                FilesScreen(
                    viewModel = filesViewModel,
                    role = user.role,
                    onProfile = onProfile,
                    onDownload = { file, relativePath -> downloadsViewModel.enqueue(file, relativePath) },
                    onUploadFiles = { files, destination ->
                        uploadsViewModel.enqueueFiles(files, destination)
                        navController.navigate(ShellRoute.Uploads.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onUploadDirectory = { dir, destination ->
                        uploadsViewModel.enqueueDirectory(dir, destination)
                        navController.navigate(ShellRoute.Uploads.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onMessage = ::showMessage,
                )
            }
            composable(ShellRoute.Uploads.route) {
                UploadsScreen(
                    tasks = uploadTasks,
                    currentSpeeds = uploadSpeeds,
                    preparingSelections = preparingUploads,
                    onRetry = uploadsViewModel::retry,
                    onCancel = uploadsViewModel::cancel,
                    onDelete = uploadsViewModel::delete,
                    onCancelAll = uploadsViewModel::cancelAll,
                    onClearTerminal = uploadsViewModel::clearTerminal,
                )
            }
            composable(ShellRoute.Downloads.route) {
                DownloadsScreen(
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
                )
            }
            composable(ShellRoute.Settings.route) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    themeSeedColorArgb = themeSeedColorArgb,
                    themeSchemeVariant = themeSchemeVariant,
                    onThemeSeedColorChange = onThemeSeedColorChange,
                    onThemeSchemeVariantChange = onThemeSchemeVariantChange,
                    onResetThemeColor = onResetThemeColor,
                    noticeState = noticeState,
                    onRetryNotice = settingsViewModel::refreshNotice,
                    updateState = updateState,
                    onCheckUpdate = settingsViewModel::checkForUpdate,
                    onDismissUpdate = settingsViewModel::dismissUpdateResult,
                    onOpenUpdateUrl = { url ->
                        runCatching {
                            Desktop.getDesktop().browse(URI(url))
                        }.isSuccess
                    },
                    onLogout = onLogout,
                )
            }
        }
    }
}
