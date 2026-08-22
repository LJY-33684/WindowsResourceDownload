package link.mczihan.androidResourceDownload.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import link.mczihan.androidResourceDownload.core.theme.ThemeMode
import link.mczihan.androidResourceDownload.core.theme.ThemeSchemeVariant
import link.mczihan.androidResourceDownload.core.theme.ThemeSettings
import link.mczihan.androidResourceDownload.data.settings.DesktopThemeRepository

class ThemeViewModel(
    private val themeRepository: DesktopThemeRepository,
) : ViewModel() {
    val settings: StateFlow<ThemeSettings> = themeRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ThemeSettings(),
    )

    val themeMode: StateFlow<ThemeMode> = object : StateFlow<ThemeMode> {
        override val value: ThemeMode get() = settings.value.themeMode
        override val replayCache: List<ThemeMode> get() = listOf(settings.value.themeMode)
        override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<ThemeMode>): Nothing {
            settings.collect { collector.emit(it.themeMode) }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themeRepository.setThemeMode(mode) }
    }

    fun setSeedColor(argb: Int) {
        viewModelScope.launch { themeRepository.setSeedColor(argb) }
    }

    fun setSchemeVariant(variant: ThemeSchemeVariant) {
        viewModelScope.launch { themeRepository.setSchemeVariant(variant) }
    }

    fun resetThemeColor() {
        viewModelScope.launch { themeRepository.resetThemeColor() }
    }
}
