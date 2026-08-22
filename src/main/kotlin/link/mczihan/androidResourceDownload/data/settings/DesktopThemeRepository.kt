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
    val seed_color_argb: Int = DEFAULT_THEME_SEED_ARGB,
    val scheme_variant: String = ThemeSchemeVariant.TONAL_SPOT.name,
)

/**
 * Desktop ThemeRepository. Stores theme settings in a JSON file.
 */
class DesktopThemeRepository(
    private val storageDir: File = defaultStorageDir(),
) {
    private val settingsFile = File(storageDir, "settings.json")
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()

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

    suspend fun resetThemeColor() {
        updateSettings {
            it.copy(
                seedColorArgb = DEFAULT_THEME_SEED_ARGB,
                schemeVariant = ThemeSchemeVariant.TONAL_SPOT,
            )
        }
    }

    private fun updateSettings(transform: (ThemeSettings) -> ThemeSettings) {
        val newSettings = transform(_settings.value)
        _settings.value = newSettings
        runCatching {
            val stored = StoredThemeSettings(
                theme_mode = newSettings.themeMode.name,
                seed_color_argb = newSettings.seedColorArgb,
                scheme_variant = newSettings.schemeVariant.name,
            )
            settingsFile.writeText(json.encodeToString(StoredThemeSettings.serializer(), stored))
        }
    }

    private fun readSettings(): ThemeSettings {
        return runCatching {
            if (settingsFile.exists()) {
                val stored = json.decodeFromString(StoredThemeSettings.serializer(), settingsFile.readText())
                ThemeSettings(
                    themeMode = ThemeMode.values().firstOrNull { it.name == stored.theme_mode } ?: ThemeMode.SYSTEM,
                    seedColorArgb = stored.seed_color_argb,
                    schemeVariant = ThemeSchemeVariant.values().firstOrNull { it.name == stored.scheme_variant }
                        ?: ThemeSchemeVariant.TONAL_SPOT,
                )
            } else null
        }.getOrNull() ?: ThemeSettings()
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
