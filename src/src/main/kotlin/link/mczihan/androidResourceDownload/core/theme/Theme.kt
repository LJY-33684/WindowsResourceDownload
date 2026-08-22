package link.mczihan.androidResourceDownload.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Reads Windows system dark mode setting from registry.
 * Returns true if dark mode is enabled, false for light mode.
 * Falls back to false on non-Windows or read failure.
 */
private fun isWindowsDarkMode(): Boolean {
    return try {
        val process = ProcessBuilder(
            "reg", "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "/v", "AppsUseLightTheme"
        ).redirectErrorStream(true).start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
        process.waitFor()
        // AppsUseLightTheme = 0 means dark mode, 1 means light mode
        output.contains("0x0") || output.contains("REG_DWORD    0x0")
    } catch (_: Exception) {
        false
    }
}

/**
 * Provides a reactive system dark theme state that polls the Windows registry
 * every 1.5 seconds. This works around Compose Desktop 1.6.11's limitation
 * where isSystemInDarkTheme() does not trigger recomposition on theme change.
 */
@Composable
private fun rememberSystemDarkTheme(): Boolean {
    var dark by remember { mutableStateOf(isWindowsDarkMode()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            val current = isWindowsDarkMode()
            if (current != dark) {
                dark = current
            }
            delay(1500)
        }
    }
    return dark
}

@Composable
fun AndroidResourceDownloadTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    seedColorArgb: Int = DEFAULT_THEME_SEED_ARGB,
    schemeVariant: ThemeSchemeVariant = ThemeSchemeVariant.TONAL_SPOT,
    content: @Composable () -> Unit,
) {
    val systemDark = rememberSystemDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = remember(seedColorArgb, darkTheme, schemeVariant) {
        seedColorScheme(seedColorArgb, darkTheme, schemeVariant)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
