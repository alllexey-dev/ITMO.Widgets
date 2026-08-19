package dev.alllexey.itmowidgets.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SportDisplaySettings
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface SettingsEvent {
    data object WidgetsRefreshStarted : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val widgetRefreshRequester: WidgetRefreshRequester,
    private val appVersion: AppVersion
) : ViewModel() {

    private val mutableSections = MutableStateFlow<List<SettingSection>>(emptyList())
    val sections: StateFlow<List<SettingSection>> = mutableSections.asStateFlow()

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    init {
        repository.observeSportDisplaySettings()
            .onEach { mutableSections.value = buildSections(it) }
            .launchIn(viewModelScope)
    }

    fun onToggleChanged(key: String, checked: Boolean) {
        viewModelScope.launch {
            when (key) {
                KEY_SPORT_TEACHER_FILTER -> repository.setTeacherSelectorHidden(!checked)
                KEY_SPORT_TIME_FILTER -> repository.setTimeSelectorHidden(!checked)
            }
        }
    }

    fun onAction(key: String) {
        if (key != KEY_REFRESH_WIDGETS) return

        widgetRefreshRequester.refreshAll()
        eventChannel.trySend(SettingsEvent.WidgetsRefreshStarted)
    }

    private fun buildSections(sport: SportDisplaySettings): List<SettingSection> {
        return listOf(
            SettingSection(
                title = UiText.Resource(R.string.settings_group_widgets),
                items = listOf(
                    SettingItem.Action(
                        key = KEY_REFRESH_WIDGETS,
                        title = UiText.Resource(R.string.settings_refresh_widgets_title),
                        description = UiText.Resource(
                            R.string.settings_refresh_widgets_description
                        ),
                        trailingIconRes = R.drawable.ic_refresh
                    )
                )
            ),
            SettingSection(
                title = UiText.Resource(R.string.settings_group_sport),
                items = listOf(
                    SettingItem.Toggle(
                        key = KEY_SPORT_TEACHER_FILTER,
                        title = UiText.Resource(R.string.settings_sport_teacher_filter_title),
                        description = UiText.Resource(
                            R.string.settings_sport_teacher_filter_description
                        ),
                        checked = !sport.hideTeacherSelector
                    ),
                    SettingItem.Toggle(
                        key = KEY_SPORT_TIME_FILTER,
                        title = UiText.Resource(R.string.settings_sport_time_filter_title),
                        description = UiText.Resource(
                            R.string.settings_sport_time_filter_description
                        ),
                        checked = !sport.hideTimeSelector
                    )
                )
            ),
            SettingSection(
                title = UiText.Resource(R.string.settings_group_about),
                items = listOf(
                    SettingItem.Info(
                        key = KEY_VERSION,
                        title = UiText.Resource(R.string.settings_version_title),
                        value = UiText.Dynamic(appVersion.name)
                    )
                )
            )
        )
    }

    companion object {
        const val KEY_SPORT_TEACHER_FILTER = "sport_teacher_filter"
        const val KEY_SPORT_TIME_FILTER = "sport_time_filter"
        const val KEY_REFRESH_WIDGETS = "refresh_widgets"
        const val KEY_VERSION = "app_version"
    }
}

/** Wraps the version string so the ViewModel stays free of Android resources. */
data class AppVersion(val name: String)
