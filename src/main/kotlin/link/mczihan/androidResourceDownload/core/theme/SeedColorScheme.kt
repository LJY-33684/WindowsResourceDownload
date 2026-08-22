package link.mczihan.androidResourceDownload.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant

internal fun seedColorScheme(
    seedColorArgb: Int,
    darkTheme: Boolean,
    variant: ThemeSchemeVariant = ThemeSchemeVariant.TONAL_SPOT,
): ColorScheme {
    val source = Hct.fromInt(normalizeThemeSeedArgb(seedColorArgb))
    val scheme = when (variant) {
        ThemeSchemeVariant.TONAL_SPOT -> SchemeTonalSpot(source, darkTheme, 0.0)
        ThemeSchemeVariant.FIDELITY -> SchemeFidelity(source, darkTheme, 0.0)
        ThemeSchemeVariant.MONOCHROME -> SchemeMonochrome(source, darkTheme, 0.0)
        ThemeSchemeVariant.NEUTRAL -> SchemeNeutral(source, darkTheme, 0.0)
        ThemeSchemeVariant.VIBRANT -> SchemeVibrant(source, darkTheme, 0.0)
        ThemeSchemeVariant.EXPRESSIVE -> SchemeExpressive(source, darkTheme, 0.0)
        ThemeSchemeVariant.CONTENT -> SchemeContent(source, darkTheme, 0.0)
        ThemeSchemeVariant.RAINBOW -> SchemeRainbow(source, darkTheme, 0.0)
    }
    val colors = MaterialDynamicColors()
    val builder = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary().resolve(scheme),
            onPrimary = colors.onPrimary().resolve(scheme),
            primaryContainer = colors.primaryContainer().resolve(scheme),
            onPrimaryContainer = colors.onPrimaryContainer().resolve(scheme),
            inversePrimary = colors.inversePrimary().resolve(scheme),
            secondary = colors.secondary().resolve(scheme),
            onSecondary = colors.onSecondary().resolve(scheme),
            secondaryContainer = colors.secondaryContainer().resolve(scheme),
            onSecondaryContainer = colors.onSecondaryContainer().resolve(scheme),
            tertiary = colors.tertiary().resolve(scheme),
            onTertiary = colors.onTertiary().resolve(scheme),
            tertiaryContainer = colors.tertiaryContainer().resolve(scheme),
            onTertiaryContainer = colors.onTertiaryContainer().resolve(scheme),
            background = colors.background().resolve(scheme),
            onBackground = colors.onBackground().resolve(scheme),
            surface = colors.surface().resolve(scheme),
            onSurface = colors.onSurface().resolve(scheme),
            surfaceVariant = colors.surfaceVariant().resolve(scheme),
            onSurfaceVariant = colors.onSurfaceVariant().resolve(scheme),
            surfaceTint = colors.surfaceTint().resolve(scheme),
            inverseSurface = colors.inverseSurface().resolve(scheme),
            inverseOnSurface = colors.inverseOnSurface().resolve(scheme),
            error = colors.error().resolve(scheme),
            onError = colors.onError().resolve(scheme),
            errorContainer = colors.errorContainer().resolve(scheme),
            onErrorContainer = colors.onErrorContainer().resolve(scheme),
            outline = colors.outline().resolve(scheme),
            outlineVariant = colors.outlineVariant().resolve(scheme),
            scrim = colors.scrim().resolve(scheme),
        )
    } else {
        lightColorScheme(
            primary = colors.primary().resolve(scheme),
            onPrimary = colors.onPrimary().resolve(scheme),
            primaryContainer = colors.primaryContainer().resolve(scheme),
            onPrimaryContainer = colors.onPrimaryContainer().resolve(scheme),
            inversePrimary = colors.inversePrimary().resolve(scheme),
            secondary = colors.secondary().resolve(scheme),
            onSecondary = colors.onSecondary().resolve(scheme),
            secondaryContainer = colors.secondaryContainer().resolve(scheme),
            onSecondaryContainer = colors.onSecondaryContainer().resolve(scheme),
            tertiary = colors.tertiary().resolve(scheme),
            onTertiary = colors.onTertiary().resolve(scheme),
            tertiaryContainer = colors.tertiaryContainer().resolve(scheme),
            onTertiaryContainer = colors.onTertiaryContainer().resolve(scheme),
            background = colors.background().resolve(scheme),
            onBackground = colors.onBackground().resolve(scheme),
            surface = colors.surface().resolve(scheme),
            onSurface = colors.onSurface().resolve(scheme),
            surfaceVariant = colors.surfaceVariant().resolve(scheme),
            onSurfaceVariant = colors.onSurfaceVariant().resolve(scheme),
            surfaceTint = colors.surfaceTint().resolve(scheme),
            inverseSurface = colors.inverseSurface().resolve(scheme),
            inverseOnSurface = colors.inverseOnSurface().resolve(scheme),
            error = colors.error().resolve(scheme),
            onError = colors.onError().resolve(scheme),
            errorContainer = colors.errorContainer().resolve(scheme),
            onErrorContainer = colors.onErrorContainer().resolve(scheme),
            outline = colors.outline().resolve(scheme),
            outlineVariant = colors.outlineVariant().resolve(scheme),
            scrim = colors.scrim().resolve(scheme),
        )
    }
    return builder
}

private fun DynamicColor.resolve(scheme: DynamicScheme): Color = Color(getArgb(scheme))
