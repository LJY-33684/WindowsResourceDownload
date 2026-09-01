package link.mczihan.androidResourceDownload.data.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import link.mczihan.androidResourceDownload.core.theme.DEFAULT_THEME_SEED_ARGB
import link.mczihan.androidResourceDownload.core.theme.ThemeMode
import link.mczihan.androidResourceDownload.core.theme.ThemeSchemeVariant
import link.mczihan.androidResourceDownload.core.theme.ThemeSettings
import java.io.File

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

@Serializable
private data class StoredThemeSettings(
    val theme_mode: String = ThemeMode.SYSTEM.name,
    val dynamic_color_enabled: Boolean = false,
    val seed_color_argb: Int = DEFAULT_THEME_SEED_ARGB,
    val scheme_variant: String = ThemeSchemeVariant.TONAL_SPOT.name,
    val log_enabled: Boolean = false,
    val preview_pane_open: Boolean = false,
)

/**
 * Desktop ThemeRepository. Stores theme settings in a JSON file.
 * Also stores lightweight UI preferences (preview pane open state) in the same file.
 */
class DesktopThemeRepository(
    private val storageDir: File = defaultStorageDir(),
) {
    private val settingsFile = File(storageDir, "settings.json")
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()
    private val _previewPaneOpen = MutableStateFlow(readStoredSettings()?.preview_pane_open ?: false)
    val previewPaneOpen: StateFlow<Boolean> = _previewPaneOpen.asStateFlow()

    init {
        if (!storageDir.exists()) storageDir.mkdirs()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        updateSettings { it.copy(themeMode = mode) }
    }

    suspend fun setSeedColor(argb: Int) {
        updateSettings { it.copy(seedColorArgb = argb) }
    }

    suspend fun setSchemeVariant(variant: ThemeSchemeVariant) {
        updateSettings { it.copy(schemeVariant = variant) }
    }

    suspend fun setDynamicColorEnabled(enable: Boolean) {
        updateSettings { it.copy(dynamicColorEnabled = enable) }
    }

    suspend fun setLogEnabled(enable: Boolean) {
        updateSettings { it.copy(logEnabled = enable) }
    }

    suspend fun resetThemeColor() {
        updateSettings {
            it.copy(
                seedColorArgb = DEFAULT_THEME_SEED_ARGB,
                schemeVariant = ThemeSchemeVariant.TONAL_SPOT,
            )
        }
    }

    suspend fun setPreviewPaneOpen(open: Boolean) {
        _previewPaneOpen.value = open
        writeStoredSettings()
    }

    private fun updateSettings(transform: (ThemeSettings) -> ThemeSettings) {
        val newSettings = transform(_settings.value)
        _settings.value = newSettings
        writeStoredSettings()
    }

    private fun writeStoredSettings() {
        val current = _settings.value
        runCatching {
            val stored = StoredThemeSettings(
                theme_mode = current.themeMode.name,
                dynamic_color_enabled = current.dynamicColorEnabled,
                seed_color_argb = current.seedColorArgb,
                scheme_variant = current.schemeVariant.name,
                log_enabled = current.logEnabled,
                preview_pane_open = _previewPaneOpen.value,
            )
            settingsFile.writeText(json.encodeToString(StoredThemeSettings.serializer(), stored))
        }
    }

    private fun readStoredSettings(): StoredThemeSettings? {
        return runCatching {
            if (settingsFile.exists()) {
                json.decodeFromString(StoredThemeSettings.serializer(), settingsFile.readText())
            } else null
        }.getOrNull()
    }

    private fun readSettings(): ThemeSettings {
        val stored = readStoredSettings() ?: return ThemeSettings()
        return ThemeSettings(
            themeMode = ThemeMode.values().firstOrNull { it.name == stored.theme_mode } ?: ThemeMode.SYSTEM,
            dynamicColorEnabled = stored.dynamic_color_enabled,
            seedColorArgb = stored.seed_color_argb,
            schemeVariant = ThemeSchemeVariant.values().firstOrNull { it.name == stored.scheme_variant }
                ?: ThemeSchemeVariant.TONAL_SPOT,
            logEnabled = stored.log_enabled,
        )
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
