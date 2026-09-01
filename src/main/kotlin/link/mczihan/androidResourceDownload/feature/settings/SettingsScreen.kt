package link.mczihan.androidResourceDownload.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import link.mczihan.androidResourceDownload.core.common.RolePreview
import link.mczihan.androidResourceDownload.domain.model.LoginType
import link.mczihan.androidResourceDownload.domain.model.Role as UserRole
import link.mczihan.androidResourceDownload.domain.model.User
import link.mczihan.androidResourceDownload.feature.profile.profileAvatarUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import link.mczihan.androidResourceDownload.BuildConfig
import link.mczihan.androidResourceDownload.core.ui.FastScrollbar
import link.mczihan.androidResourceDownload.core.theme.DEFAULT_THEME_SEED_ARGB
import link.mczihan.androidResourceDownload.core.theme.ThemeMode
import link.mczihan.androidResourceDownload.core.theme.ThemeSchemeVariant
import link.mczihan.androidResourceDownload.core.theme.ThemeSeedPreset
import link.mczihan.androidResourceDownload.core.theme.ThemeTone
import link.mczihan.androidResourceDownload.core.theme.normalizeThemeSeedArgb
import link.mczihan.androidResourceDownload.core.theme.seedColorScheme
import link.mczihan.androidResourceDownload.core.theme.themeSeedFromTone
import link.mczihan.androidResourceDownload.core.theme.themeToneFromArgb

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    user: User,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    themeDynamicColorEnabled: Boolean = false,
    themeSeedColorArgb: Int = DEFAULT_THEME_SEED_ARGB,
    themeSchemeVariant: ThemeSchemeVariant = ThemeSchemeVariant.TONAL_SPOT,
    onThemeDynamicColorEnabledChange: (Boolean) -> Unit = {},
    logEnabled: Boolean = false,
    onLogEnabledChange: (Boolean) -> Unit = {},
    onThemeSeedColorChange: (Int) -> Unit = {},
    onThemeSchemeVariantChange: (ThemeSchemeVariant) -> Unit = {},
    onResetThemeColor: () -> Unit = {},
    noticeState: NoticeUiState = NoticeUiState.Loading,
    onRetryNotice: () -> Unit = {},
    updateState: UpdateUiState = UpdateUiState.Idle,
    onCheckUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    onOpenUpdateUrl: (String) -> Boolean = { false },
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAbout by remember { mutableStateOf(false) }
    var showNotice by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }
    var showCustomColor by remember { mutableStateOf(false) }
    var showSchemePicker by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    // 检查更新有结果时弹出对话框
    LaunchedEffect(updateState) {
        if (updateState is UpdateUiState.Available ||
            updateState is UpdateUiState.UpToDate ||
            updateState is UpdateUiState.Error
        ) {
            showUpdateDialog = true
        }
    }
    val previewDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    Box(modifier = modifier.fillMaxSize()) {

        Scaffold(

            modifier = Modifier.fillMaxSize(),

            topBar = { TopAppBar(title = { Text("设置") }) },

        ) { innerPadding ->
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
            UserHeader(
                user = user,
                onRequestLogout = { showLogout = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "外观",
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val modes = ThemeMode.values()
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = modes.size,
                            ),
                            label = { Text(mode.shortLabel()) },
                            icon = {
                                Icon(
                                    imageVector = mode.icon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }
            // Windows 端"莫奈自动取色"：使用系统强调色（DWM 从壁纸提取，聚焦/动态壁纸均可用）。
            ListItem(
                headlineContent = { Text("莫奈自动取色") },
                supportingContent = {
                    Text(
                        if (themeDynamicColorEnabled) {
                            "使用系统强调色生成应用配色"
                        } else {
                            "使用下方选择的主题色"
                        },
                    )
                },
                leadingContent = { SettingsIcon(Icons.Default.AutoAwesome) },
                trailingContent = {
                    Switch(
                        checked = themeDynamicColorEnabled,
                        onCheckedChange = onThemeDynamicColorEnabledChange,
                    )
                },
                modifier = Modifier.clickable {
                    onThemeDynamicColorEnabledChange(!themeDynamicColorEnabled)
                },
            )
            // 与安卓端一致：开启自动取色时隐藏手动调色盘
            AnimatedVisibility(
                visible = !themeDynamicColorEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ThemeColorEditor(
                    selectedSeedColorArgb = themeSeedColorArgb,
                    schemeVariant = themeSchemeVariant,
                    darkTheme = previewDarkTheme,
                    onSeedColorChange = onThemeSeedColorChange,
                    onSchemeVariant = { showSchemePicker = true },
                    onReset = onResetThemeColor,
                    onCustomColor = { showCustomColor = true },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ListItem(
                headlineContent = { Text("启用日志") },
                supportingContent = {
                    Text(
                        if (logEnabled) "记录运行日志到 log.txt 文件" else "日志已关闭，不再写入 log.txt",
                    )
                },
                leadingContent = { SettingsIcon(Icons.Default.BugReport) },
                trailingContent = {
                    Switch(
                        checked = logEnabled,
                        onCheckedChange = onLogEnabledChange,
                    )
                },
                modifier = Modifier.clickable { onLogEnabledChange(!logEnabled) },
            )
            ListItem(
                headlineContent = { Text("公告") },
                supportingContent = {
                    Text(
                        when (noticeState) {
                            NoticeUiState.Loading -> "正在获取最新公告"
                            is NoticeUiState.Content -> "查看最新公告"
                            NoticeUiState.Empty -> "暂无公告"
                            NoticeUiState.Error -> "获取失败"
                        },
                    )
                },
                leadingContent = { SettingsIcon(Icons.Default.Campaign) },
                modifier = Modifier.clickable { showNotice = true },
            )
            ListItem(
                headlineContent = { Text("关于") },
                supportingContent = { Text("查看资源云盘的各项信息") },
                leadingContent = { SettingsIcon(Icons.Default.Info) },
                modifier = Modifier.clickable { showAbout = true },
            )
            }
            FastScrollbar(
                scrollState = scrollState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("退出登录？") },
            text = { Text("退出后需要重新验证身份。") },
            confirmButton = {
                TextButton(onClick = onLogout) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) { Text("取消") }
            },
        )
    }

    if (showNotice) {
        AlertDialog(
            onDismissRequest = { showNotice = false },
            title = { Text("公告") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (val state = noticeState) {
                        NoticeUiState.Loading -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("正在获取最新公告")
                        }
                        is NoticeUiState.Content -> MarkdownText(state.text)
                        NoticeUiState.Empty -> Text("暂无公告")
                        NoticeUiState.Error -> Text("公告获取失败，请检查网络后重试。")
                    }
                }
            },
            confirmButton = {
                if (noticeState == NoticeUiState.Error || noticeState == NoticeUiState.Empty) {
                    TextButton(onClick = onRetryNotice) { Text("重试") }
                } else {
                    TextButton(onClick = { showNotice = false }) { Text("关闭") }
                }
            },
            dismissButton = if (
                noticeState == NoticeUiState.Error || noticeState == NoticeUiState.Empty
            ) {
                { TextButton(onClick = { showNotice = false }) { Text("关闭") } }
            } else {
                null
            },
        )
    }

    if (showAbout) {
        AboutScreen(
            onNavigateBack = { showAbout = false },
            updateState = updateState,
            onCheckUpdate = onCheckUpdate,
            onDismissUpdate = onDismissUpdate,
            onOpenUrl = onOpenUpdateUrl,
        )
    }

    if (showUpdateDialog && !showAbout && !showNotice && !showCustomColor && !showSchemePicker) {
        val dismiss = { showUpdateDialog = false; onDismissUpdate() }
        when (val state = updateState) {
            is UpdateUiState.Available -> AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("发现新版本") },
                text = {
                    Column {
                        Text("当前版本 ${state.currentVersion}\n最新版本 ${state.latestVersion}")
                        if (!state.releaseNotes.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text("更新内容", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                MarkdownText(state.releaseNotes, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (onOpenUpdateUrl(state.updateUrl)) dismiss()
                        },
                    ) { Text("下载") }
                },
                dismissButton = {
                    TextButton(onClick = dismiss) { Text("取消") }
                },
            )
            is UpdateUiState.UpToDate -> AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("已是最新版本") },
                text = {
                    Column {
                        Text("当前版本 ${state.currentVersion}")
                        if (!state.releaseNotes.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text("当前版本更新内容", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                MarkdownText(state.releaseNotes, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = dismiss) { Text("确定") }
                },
            )
            is UpdateUiState.Error -> AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("检查更新失败") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { showUpdateDialog = false; onCheckUpdate() }) { Text("重试") }
                },
                dismissButton = {
                    TextButton(onClick = dismiss) { Text("取消") }
                },
            )
            UpdateUiState.Idle,
            UpdateUiState.Checking,
            -> Unit
        }
    }



    if (showCustomColor) {
        CustomThemeColorDialog(
            initialSeedColorArgb = themeSeedColorArgb,
            schemeVariant = themeSchemeVariant,
            darkTheme = previewDarkTheme,
            onDismiss = { showCustomColor = false },
            onConfirm = { seedColorArgb ->
                onThemeSeedColorChange(seedColorArgb)
                showCustomColor = false
            },
        )
    }

    if (showSchemePicker) {
        ThemeSchemeVariantDialog(
            selected = themeSchemeVariant,
            onDismiss = { showSchemePicker = false },
            onSelect = { variant ->
                onThemeSchemeVariantChange(variant)
                showSchemePicker = false
            },
        )
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeColorEditor(
    selectedSeedColorArgb: Int,
    schemeVariant: ThemeSchemeVariant,
    darkTheme: Boolean,
    onSeedColorChange: (Int) -> Unit,
    onSchemeVariant: () -> Unit,
    onReset: () -> Unit,
    onCustomColor: () -> Unit,
) {
    val normalizedSeed = normalizeThemeSeedArgb(selectedSeedColorArgb)
    val selectedPreset = ThemeSeedPreset.values().firstOrNull {
        it.seedColorArgb == normalizedSeed
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeColorHeading()
            Spacer(Modifier.weight(1f))
            ThemeColorActions(schemeVariant, onSchemeVariant, onReset)
        }
        if (schemeVariant != ThemeSchemeVariant.MONOCHROME) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThemeSeedPreset.values().forEach { preset ->
                    ThemePaletteTile(
                        seedColorArgb = preset.seedColorArgb,
                        schemeVariant = schemeVariant,
                        darkTheme = darkTheme,
                        selected = selectedPreset == preset,
                        contentDescription = "主题色 ${preset.label()}",
                        onClick = { onSeedColorChange(preset.seedColorArgb) },
                    )
                }
                ThemePaletteTile(
                    seedColorArgb = normalizedSeed,
                    schemeVariant = schemeVariant,
                    darkTheme = darkTheme,
                    selected = selectedPreset == null,
                    contentDescription = "自定义主题色",
                    showAdd = selectedPreset != null,
                    onClick = onCustomColor,
                )
            }
        }
    }
}

@Composable
private fun ThemeColorHeading() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Palette,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text("主题色彩", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ThemeColorActions(
    schemeVariant: ThemeSchemeVariant,
    onSchemeVariant: () -> Unit,
    onReset: () -> Unit,
) {
    FilledTonalButton(
        onClick = onSchemeVariant,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(schemeVariant.displayName)
    }
    IconButton(onClick = onReset) {
        Icon(Icons.Default.Refresh, contentDescription = "恢复默认主题色")
    }
}

@Composable
private fun ThemePaletteTile(
    seedColorArgb: Int,
    schemeVariant: ThemeSchemeVariant,
    darkTheme: Boolean,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    showAdd: Boolean = false,
) {
    val previewScheme = remember(seedColorArgb, schemeVariant, darkTheme) {
        seedColorScheme(seedColorArgb, darkTheme = darkTheme, variant = schemeVariant)
    }
    Surface(
        modifier = Modifier
            .size(72.dp)
            .semantics { this.contentDescription = contentDescription }
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (showAdd) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            } else {
                PalettePreview(
                    colors = listOf(
                        previewScheme.primary,
                        previewScheme.primaryContainer,
                        previewScheme.secondaryContainer,
                        previewScheme.tertiaryContainer,
                    ),
                    modifier = Modifier.size(52.dp),
                )
            }
            if (selected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .size(24.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSchemeVariantDialog(
    selected: ThemeSchemeVariant,
    onDismiss: () -> Unit,
    onSelect: (ThemeSchemeVariant) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color scheme") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                ThemeSchemeVariant.values().forEach { variant ->
                    val isSelected = variant == selected
                    ListItem(
                        headlineContent = { Text(variant.displayName) },
                        leadingContent = {
                            RadioButton(selected = isSelected, onClick = null)
                        },
                        modifier = Modifier.selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(variant) },
                        ),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun PalettePreview(colors: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clip(CircleShape)) {
        colors.take(4).forEachIndexed { index, color ->
            drawArc(
                color = color,
                startAngle = -90f + (index * 90f),
                sweepAngle = 90f,
                useCenter = true,
            )
        }
    }
}

@Composable
private fun CustomThemeColorDialog(
    initialSeedColorArgb: Int,
    schemeVariant: ThemeSchemeVariant,
    darkTheme: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val initialTone = remember(initialSeedColorArgb) { themeToneFromArgb(initialSeedColorArgb) }
    var hue by remember(initialSeedColorArgb) { mutableFloatStateOf(initialTone.hue) }
    var chroma by remember(initialSeedColorArgb) {
        mutableFloatStateOf(initialTone.chroma.coerceIn(0f, 100f))
    }
    var tone by remember(initialSeedColorArgb) {
        mutableFloatStateOf(initialTone.tone.coerceIn(20f, 80f))
    }
    val seedColorArgb = themeSeedFromTone(ThemeTone(hue, chroma, tone))
    val previewScheme = remember(seedColorArgb, schemeVariant, darkTheme) {
        seedColorScheme(seedColorArgb, darkTheme = darkTheme, variant = schemeVariant)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Colorize, contentDescription = null) },
        title = { Text("自定义主题色") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PalettePreview(
                    colors = listOf(
                        previewScheme.primary,
                        previewScheme.primaryContainer,
                        previewScheme.secondaryContainer,
                        previewScheme.tertiaryContainer,
                    ),
                    modifier = Modifier.size(88.dp),
                )
                Text(
                    text = "#%06X".format(seedColorArgb and 0xFFFFFF),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ThemeToneSlider(
                    label = "色相",
                    value = hue,
                    valueRange = 0f..360f,
                    valueText = "${hue.toInt()}°",
                    steps = 71,
                    onValueChange = { hue = it },
                )
                if (schemeVariant.usesSourceChromaAndTone) {
                    ThemeToneSlider(
                        label = "色彩浓度",
                        value = chroma.coerceIn(0f, 100f),
                        valueRange = 0f..100f,
                        valueText = chroma.toInt().toString(),
                        steps = 19,
                        onValueChange = { chroma = it },
                    )
                    ThemeToneSlider(
                        label = "明度",
                        value = tone.coerceIn(20f, 80f),
                        valueRange = 20f..80f,
                        valueText = tone.toInt().toString(),
                        steps = 11,
                        onValueChange = { tone = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(seedColorArgb) }) { Text("应用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ThemeToneSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
        )
    }
}

@Composable
private fun SettingsIcon(
    imageVector: ImageVector,
    isError: Boolean = false,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            )
        }
    }
}

private fun ThemeMode.shortLabel(): String = when (this) {
    ThemeMode.SYSTEM -> "系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

private fun ThemeMode.fullLabel(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "始终浅色"
    ThemeMode.DARK -> "始终深色"
}

private fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
    ThemeMode.LIGHT -> Icons.Default.LightMode
    ThemeMode.DARK -> Icons.Default.DarkMode
}

private fun ThemeSeedPreset.label(): String = when (this) {
    ThemeSeedPreset.FOREST -> "森林"
    ThemeSeedPreset.INDIGO -> "靛蓝"
    ThemeSeedPreset.CORAL -> "珊瑚"
    ThemeSeedPreset.SKY -> "晴空"
    ThemeSeedPreset.OLIVE -> "橄榄"
    ThemeSeedPreset.CYAN -> "青色"
    ThemeSeedPreset.MINT -> "薄荷"
    ThemeSeedPreset.ROSE -> "玫瑰"
    ThemeSeedPreset.VIOLET -> "紫罗兰"
    }


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserHeader(
    user: User,
    onRequestLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var avatarBitmap by remember(user.avatarUrl) { mutableStateOf<ImageBitmap?>(null) }
    var showRoleConfirm by remember { mutableStateOf(false) }
    var roleNotice by remember { mutableStateOf<String?>(null) }
    val roleInteractionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val roleHovered by roleInteractionSource.collectIsHoveredAsState()
    val effectiveRoleAdmin = user.role == UserRole.ADMIN && !RolePreview.asUser
    LaunchedEffect(user.avatarUrl) {
        val url = user.profileAvatarUrl()
        avatarBitmap = if (url != null) {
            withContext(Dispatchers.IO) { loadAvatar(url) }
        } else {
            null
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val bitmap = avatarBitmap
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = user.name ?: "未登录",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box {
                            Surface(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .then(
                                        if (user.role == UserRole.ADMIN) {
                                            Modifier
                                                .hoverable(roleInteractionSource)
                                                .clickable { showRoleConfirm = true }
                                        } else {
                                            Modifier
                                        },
                                    ),
                                shape = CircleShape,
                                color = if (effectiveRoleAdmin) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                            ) {
                                Text(
                                    text = if (effectiveRoleAdmin) "管理员" else "普通用户",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = if (effectiveRoleAdmin) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            // 仅管理员悬停时显示切换提示（浮窗，不挤占布局）
                            if (user.role == UserRole.ADMIN && roleHovered) {
                                Popup(
                                    alignment = Alignment.TopCenter,
                                    offset = IntOffset(
                                        x = 0,
                                        y = with(density) { (-14).dp.roundToPx() },
                                    ),
                                    onDismissRequest = {},
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.inverseSurface,
                                        shadowElevation = 4.dp,
                                    ) {
                                        Text(
                                            text = "单击可切换用户",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onRequestLogout),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("退出登录", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "登录方式 ${when (user.loginType) {
                            LoginType.GITHUB -> "GitHub"
                            LoginType.EMAIL -> "邮箱验证码"
                            LoginType.QQ -> "QQ"
                        }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }

    if (showRoleConfirm) {
        AlertDialog(
            onDismissRequest = { showRoleConfirm = false },
            title = { Text(if (RolePreview.asUser) "恢复管理员视角" else "切换为普通用户视角") },
            text = {
                Text(
                    if (RolePreview.asUser) {
                        "将恢复管理员视角，重新显示全部管理员功能。是否继续？"
                    } else {
                        "将以普通用户视角预览界面（隐藏上传、复选、拖拽上传等管理员功能）。是否继续？"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRoleConfirm = false
                    val nowPreview = RolePreview.toggle()
                    roleNotice = if (nowPreview) {
                        "已切换为普通用户视角，再次点击角色标记或重启应用可恢复"
                    } else {
                        "已恢复管理员视角"
                    }
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showRoleConfirm = false }) { Text("取消") }
            },
        )
    }
    roleNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { roleNotice = null },
            title = { Text("提示") },
            text = { Text(notice) },
            confirmButton = {
                TextButton(onClick = { roleNotice = null }) { Text("知道了") }
            },
        )
    }
}

private suspend fun loadAvatar(url: String): ImageBitmap? {
    return try {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .proxySelector(java.net.ProxySelector.getDefault())
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val bytes = response.body?.bytes() ?: return@use null
            Image.makeFromEncoded(bytes).asImageBitmap()
        }
    } catch (error: Exception) {
        link.mczihan.androidResourceDownload.core.platform.AppLogger.debug("Settings avatar 加载失败: ${error.message}")
        null
    }
}

