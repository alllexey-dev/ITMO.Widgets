package dev.alllexey.itmowidgets.feature.settings.presentation.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * «Оформление»: the stored [ThemeSpec] as one choice per part, each applied at once to the whole app and to the
 * widgets that follow the theme. The meaning of the stored values is the one DS-ACC1 and DS-ACC2 gave them; the page
 * only shows them differently (docs/settings.md, Appearance).
 */
class AppearanceViewModel(
    private val repository: SettingsRepository,
    private val widgetRefreshRequester: WidgetRefreshRequester,
    private val capabilities: PlatformCapabilities,
) : ViewModel() {

    private val eventQueue = EventQueue<AppearanceEvent>()
    val events: Flow<AppearanceEvent> = eventQueue.events

    val uiState: StateFlow<AppearanceUiState> = repository.observeLocalSettings()
        .map { local -> appearanceState(local.theme, capabilities) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceUiState())

    fun selectAccent(accent: AccentColor) = update { it.copy(accent = accent) }

    /** `Свой цвет` takes [argb] (alpha is dropped) and becomes the accent. */
    fun setCustomColor(argb: Int) = update { it.copy(accent = AccentColor.CUSTOM, customArgb = OPAQUE or argb) }

    fun selectStyle(style: ThemeStyle) = update { it.copy(style = style) }

    fun selectContrast(contrast: ThemeContrast) = update { it.copy(contrast = contrast) }

    fun setBlackBackground(enabled: Boolean) = update { it.copy(pureBlack = enabled) }

    /** Widgets that follow the theme redraw with every change of it; a failed store says so. */
    private fun update(change: (ThemeSpec) -> ThemeSpec) {
        viewModelScope.launch {
            try {
                repository.updateTheme(change)
                widgetRefreshRequester.refreshAll()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                eventQueue.send(AppearanceEvent.ShowError(AppError.Unknown(error)))
            }
        }
    }

    private companion object {
        const val OPAQUE = 0xFF000000.toInt()
    }
}

sealed interface AppearanceEvent {
    data class ShowError(val error: AppError) : AppearanceEvent
}
