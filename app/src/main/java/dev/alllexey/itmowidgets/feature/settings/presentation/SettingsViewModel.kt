package dev.alllexey.itmowidgets.feature.settings.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsEvent {
    data object WidgetsRefreshStarted : SettingsEvent
    data object OpenNotificationSettings : SettingsEvent
    data object ChooseCustomSpoiler : SettingsEvent
    data object ResetCustomSpoiler : SettingsEvent
    data class ShowError(val error: AppError) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val customServicesRepository: CustomServicesRepository,
    private val widgetRefreshRequester: WidgetRefreshRequester,
    private val appVersion: AppVersion,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val page = SettingsPage.fromArgument(savedStateHandle[SettingsPage.ARGUMENT])

    private val notificationPermissionGranted = MutableStateFlow<Boolean?>(null)
    private val customSpoilerConfigured = MutableStateFlow(false)
    private val customSpoilerBusy = MutableStateFlow(false)
    // Start masked so cached backend values cannot flash before the fresh request.
    private val privacyRefreshInProgress = MutableStateFlow(page == SettingsPage.PRIVACY)
    private val privacyRefreshMutex = Mutex()
    private val displayedSharingSettings = combine(
        repository.observeSharingSettings(), privacyRefreshInProgress
    ) { sharing, refreshing ->
        if (refreshing) SharingSettingsState.Loading else sharing
    }
    private val localSettings = repository.observeLocalSettings()
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val previewSettings: StateFlow<WidgetPreviewSettings?> = localSettings
        .map { local ->
            when (page) {
                SettingsPage.QR_WIDGET -> WidgetPreviewSettings.Qr(local.qrWidget)
                SettingsPage.SCHEDULE_WIDGETS -> WidgetPreviewSettings.Schedule(local.scheduleWidget)
                else -> null
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Do not render made-up defaults while DataStore is loading. Otherwise every
    // persisted `true` appears as an off -> on transition when Settings opens.
    private val mutableSections = MutableStateFlow<List<SettingSection>>(emptyList())
    val sections: StateFlow<List<SettingSection>> = mutableSections.asStateFlow()
    private val mutableLocalSettingsLoaded = MutableStateFlow(false)
    val localSettingsLoaded: StateFlow<Boolean> = mutableLocalSettingsLoaded.asStateFlow()

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    init {
        combine(
            localSettings,
            displayedSharingSettings,
            notificationPermissionGranted,
            customSpoilerConfigured,
            customSpoilerBusy
        ) { local, sharing, notificationsGranted, hasCustomSpoiler, imageBusy ->
            buildSections(
                local = local,
                sharing = sharing,
                notificationsGranted = notificationsGranted,
                hasCustomSpoiler = hasCustomSpoiler,
                imageBusy = imageBusy
            )
        }
            .onEach {
                mutableSections.value = it
                mutableLocalSettingsLoaded.value = true
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            localSettings
                .map { local -> local.customServicesEnabled }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    if (enabled && page == SettingsPage.PRIVACY) {
                        refreshPrivacySettings()
                    } else if (!enabled) {
                        privacyRefreshInProgress.value = false
                        repository.disableSharingSettings()
                    }
                }
        }
    }

    private suspend fun refreshPrivacySettings() {
        if (!privacyRefreshMutex.tryLock()) return
        privacyRefreshInProgress.value = true
        try {
            coroutineScope {
                // Run together: a slow network response must not pay an extra 300 ms.
                launch { delay(MIN_PRIVACY_LOADING_MS) }
                repository.refreshSharingSettings()
            }
        } finally {
            privacyRefreshInProgress.value = false
            privacyRefreshMutex.unlock()
        }
    }

    fun onNotificationPermissionChanged(granted: Boolean) {
        notificationPermissionGranted.value = granted
    }

    fun onCustomSpoilerChanged(configured: Boolean, refreshWidgets: Boolean = false, busy: Boolean = false) {
        customSpoilerConfigured.value = configured
        customSpoilerBusy.value = busy
        if (refreshWidgets) widgetRefreshRequester.refreshAll()
    }

    fun onToggleChanged(key: String, checked: Boolean) {
        when (key) {
            KEY_CUSTOM_SERVICES -> updateCustomServices(checked)
            KEY_SCHEDULE_SHARING -> updateSharing {
                repository.setScheduleSharing(checked)
            }
            KEY_SPORT_SHARING -> updateSharing {
                repository.setSportSharing(checked)
            }
            KEY_SCHEDULE_SPORT_AUTO_SIGN -> updateLocalSetting {
                repository.setScheduleSportAutoSignEnabled(checked)
            }
            KEY_WIDGET_NEXT_LESSON_EARLY -> updateWidgetSetting {
                repository.setNextLessonEarlyEnabled(checked)
            }
            KEY_WIDGET_HIDE_TEACHER -> updateWidgetSetting {
                repository.setWidgetTeacherHidden(checked)
            }
            KEY_WIDGET_HIDE_PAST -> updateWidgetSetting {
                repository.setPastLessonsHidden(checked)
            }
            KEY_WIDGET_SHOW_TOMORROW -> updateWidgetSetting {
                repository.setTomorrowScheduleEnabled(checked)
            }
            KEY_QR_DYNAMIC_COLORS -> updateWidgetSetting {
                repository.setQrDynamicColorsEnabled(checked)
            }
            KEY_QR_SPOILER -> updateWidgetSetting {
                repository.setQrSpoilerEnabled(checked)
            }
            KEY_SPORT_TEACHER_FILTER -> updateLocalSetting {
                repository.setTeacherSelectorHidden(!checked)
            }
            KEY_SPORT_TIME_FILTER -> updateLocalSetting {
                repository.setTimeSelectorHidden(!checked)
            }
        }
    }

    fun onChoiceChanged(key: String, optionKey: String) {
        if (key != KEY_QR_ANIMATION) return
        val animation = QrAnimationType.entries.firstOrNull { it.name == optionKey } ?: return
        updateWidgetSetting { repository.setQrAnimationType(animation) }
    }

    fun onAction(key: String) {
        when (key) {
            KEY_REFRESH_WIDGETS -> {
                widgetRefreshRequester.refreshAll()
                eventChannel.trySend(SettingsEvent.WidgetsRefreshStarted)
            }
            KEY_NOTIFICATIONS -> eventChannel.trySend(SettingsEvent.OpenNotificationSettings)
            KEY_QR_CUSTOM_IMAGE -> eventChannel.trySend(SettingsEvent.ChooseCustomSpoiler)
            KEY_QR_RESET_IMAGE -> eventChannel.trySend(SettingsEvent.ResetCustomSpoiler)
            KEY_RETRY_PRIVACY -> viewModelScope.launch {
                refreshPrivacySettings()
            }
        }
    }

    private fun updateCustomServices(enabled: Boolean) {
        viewModelScope.launch {
            try {
                customServicesRepository.setEnabled(enabled)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                eventChannel.send(SettingsEvent.ShowError(AppError.Unknown(error)))
            }
        }
    }

    private fun updateSharing(action: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            when (val result = action()) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> eventChannel.send(SettingsEvent.ShowError(result.error))
            }
        }
    }

    private fun updateWidgetSetting(action: suspend () -> Unit) {
        updateLocalSetting(refreshWidgets = true, action = action)
    }

    private fun updateLocalSetting(
        refreshWidgets: Boolean = false,
        action: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                action()
                if (refreshWidgets) widgetRefreshRequester.refreshAll()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                eventChannel.send(SettingsEvent.ShowError(AppError.Unknown(error)))
            }
        }
    }

    private fun buildSections(
        local: LocalSettings,
        sharing: SharingSettingsState,
        notificationsGranted: Boolean?,
        hasCustomSpoiler: Boolean,
        imageBusy: Boolean
    ): List<SettingSection> = when (page) {
        SettingsPage.ROOT -> listOf(
            SettingSection(
                title = UiText.Resource(R.string.settings_group_access),
                items = listOf(
                    navigation(
                        SettingsPage.SERVICES,
                        value = UiText.Resource(
                            if (local.customServicesEnabled) {
                                R.string.settings_services_enabled
                            } else {
                                R.string.settings_services_disabled
                            }
                        )
                    ),
                    navigation(SettingsPage.PRIVACY),
                    SettingItem.Action(
                        key = KEY_NOTIFICATIONS,
                        title = UiText.Resource(R.string.settings_notifications_title),
                        value = UiText.Resource(
                            when (notificationsGranted) {
                                true -> R.string.settings_notifications_allowed
                                false -> R.string.settings_notifications_blocked
                                null -> R.string.settings_notifications_checking
                            }
                        ),
                        trailingIconRes = R.drawable.ic_chevron_right
                    )
                )
            ),
            SettingSection(
                title = UiText.Resource(R.string.settings_group_widgets),
                items = listOf(
                    navigation(
                        SettingsPage.SCHEDULE_WIDGETS,
                        title = UiText.Resource(R.string.settings_schedule_short_title)
                    ),
                    navigation(
                        SettingsPage.QR_WIDGET,
                        title = UiText.Resource(R.string.settings_qr_short_title)
                    )
                )
            ),
            SettingSection(
                title = UiText.Resource(R.string.me_group_app),
                items = listOf(
                    navigation(SettingsPage.SCHEDULE),
                    navigation(SettingsPage.SPORT),
                    navigation(SettingsPage.MAINTENANCE)
                )
            )
        )
        SettingsPage.SERVICES -> listOf(
            SettingSection(
                title = null,
                items = listOf(
                    SettingItem.Toggle(
                        key = KEY_CUSTOM_SERVICES,
                        title = UiText.Resource(R.string.settings_custom_services_title),
                        checked = local.customServicesEnabled
                    )
                ),
                footer = UiText.Resource(R.string.settings_services_footer)
            )
        )
        SettingsPage.PRIVACY -> if (local.customServicesEnabled && sharing == SharingSettingsState.Loading) {
            emptyList()
        } else {
            buildPrivacySections(local, sharing)
        }
        SettingsPage.SCHEDULE_WIDGETS -> listOf(
            SettingSection(
                title = null,
                items = listOf(
                    SettingItem.Toggle(
                        key = KEY_WIDGET_NEXT_LESSON_EARLY,
                        title = UiText.Resource(R.string.settings_widget_next_early_title),
                        description = UiText.Resource(R.string.settings_widget_next_early_description),
                        checked = local.scheduleWidget.showNextLessonEarly
                    ),
                    SettingItem.Toggle(
                        key = KEY_WIDGET_HIDE_TEACHER,
                        title = UiText.Resource(R.string.settings_widget_hide_teacher_title),
                        checked = local.scheduleWidget.hideTeacher
                    ),
                    SettingItem.Toggle(
                        key = KEY_WIDGET_HIDE_PAST,
                        title = UiText.Resource(R.string.settings_widget_hide_past_title),
                        checked = local.scheduleWidget.hidePastLessons
                    ),
                    SettingItem.Toggle(
                        key = KEY_WIDGET_SHOW_TOMORROW,
                        title = UiText.Resource(R.string.settings_widget_tomorrow_title),
                        description = UiText.Resource(R.string.settings_widget_tomorrow_description),
                        checked = local.scheduleWidget.showTomorrowWhenTodayIsOver
                    )
                ),
                footer = UiText.Resource(R.string.settings_schedule_widgets_footer)
            )
        )
        SettingsPage.QR_WIDGET -> listOf(
            SettingSection(
                title = null,
                items = listOf(
                    SettingItem.Toggle(
                        key = KEY_QR_DYNAMIC_COLORS,
                        title = UiText.Resource(R.string.settings_qr_dynamic_colors_title),
                        checked = local.qrWidget.dynamicColors
                    ),
                    SettingItem.Toggle(
                        key = KEY_QR_SPOILER,
                        title = UiText.Resource(R.string.settings_qr_spoiler_title),
                        description = UiText.Resource(R.string.settings_qr_spoiler_description),
                        checked = local.qrWidget.spoilerEnabled
                    )
                )
            ),
            SettingSection(
                title = UiText.Resource(R.string.settings_group_spoiler),
                items = listOf(
                    SettingItem.Choice(
                        key = KEY_QR_ANIMATION,
                        title = UiText.Resource(R.string.settings_qr_animation_title),
                        value = local.qrWidget.animationType.label(),
                        options = QrAnimationType.entries.map { animation ->
                            ChoiceOption(animation.name, animation.label())
                        },
                        selectedOptionKey = local.qrWidget.animationType.name,
                        enabled = local.qrWidget.spoilerEnabled
                    ),
                    SettingItem.Action(
                        key = KEY_QR_CUSTOM_IMAGE,
                        title = UiText.Resource(R.string.settings_qr_custom_image_title),
                        value = UiText.Resource(
                            if (hasCustomSpoiler) {
                                R.string.settings_qr_custom_image_selected
                            } else {
                                R.string.settings_qr_custom_image_default
                            }
                        ),
                        trailingIconRes = R.drawable.ic_chevron_right,
                        enabled = local.qrWidget.spoilerEnabled && !imageBusy
                    ),
                    SettingItem.Action(
                        key = KEY_QR_RESET_IMAGE,
                        title = UiText.Resource(R.string.settings_qr_reset_image_title),
                        enabled = local.qrWidget.spoilerEnabled && hasCustomSpoiler && !imageBusy
                    )
                )
            )
        )
        SettingsPage.SCHEDULE -> listOf(
            SettingSection(
                title = null,
                items = listOf(
                    SettingItem.Toggle(
                        key = KEY_SCHEDULE_SPORT_AUTO_SIGN,
                        title = UiText.Resource(R.string.settings_schedule_sport_auto_sign_title),
                        description = UiText.Resource(R.string.settings_schedule_sport_auto_sign_description),
                        checked = local.showSportAutoSign
                    )
                ),
                footer = UiText.Resource(R.string.settings_schedule_footer)
            )
        )
        SettingsPage.SPORT -> listOf(
            SettingSection(
                title = null,
                items = listOf(
                    SettingItem.Toggle(
                        key = KEY_SPORT_TEACHER_FILTER,
                        title = UiText.Resource(R.string.settings_sport_teacher_filter_title),
                        checked = !local.sport.hideTeacherSelector
                    ),
                    SettingItem.Toggle(
                        key = KEY_SPORT_TIME_FILTER,
                        title = UiText.Resource(R.string.settings_sport_time_filter_title),
                        checked = !local.sport.hideTimeSelector
                    )
                ),
                footer = UiText.Resource(R.string.settings_sport_footer)
            )
        )
        SettingsPage.MAINTENANCE -> listOf(
            SettingSection(
                title = null,
                items = listOf(
                    SettingItem.Action(
                        key = KEY_REFRESH_WIDGETS,
                        title = UiText.Resource(R.string.settings_refresh_widgets_title),
                        description = UiText.Resource(R.string.settings_refresh_widgets_description),
                        trailingIconRes = R.drawable.ic_refresh
                    ),
                    SettingItem.Info(
                        key = KEY_VERSION,
                        title = UiText.Resource(R.string.settings_version_title),
                        value = UiText.Dynamic(appVersion.name)
                    )
                )
            )
        )
    }

    private fun buildPrivacySections(
        local: LocalSettings,
        sharing: SharingSettingsState
    ): List<SettingSection> {
        val content = (sharing as? SharingSettingsState.Content)
            ?.takeIf { local.customServicesEnabled }
        val editable = content != null && !content.updating
        val status = when {
            !local.customServicesEnabled -> R.string.settings_privacy_services_required
            sharing is SharingSettingsState.Error -> R.string.settings_privacy_load_error
            content == null -> R.string.settings_privacy_loading
            else -> null
        }?.let(UiText::Resource)
        val items = mutableListOf<SettingItem>(
            SettingItem.Toggle(
                key = KEY_SCHEDULE_SHARING,
                title = UiText.Resource(R.string.settings_schedule_sharing_title),
                checked = content?.settings?.scheduleSharing ?: false,
                enabled = editable,
                stateKnown = content != null
            ),
            SettingItem.Toggle(
                key = KEY_SPORT_SHARING,
                title = UiText.Resource(R.string.settings_sport_sharing_title),
                checked = content?.settings?.sportSharing ?: false,
                enabled = editable,
                stateKnown = content != null
            )
        )
        if (!local.customServicesEnabled) {
            items += navigation(SettingsPage.SERVICES)
        } else if (sharing is SharingSettingsState.Error) {
            items += SettingItem.Action(
                key = KEY_RETRY_PRIVACY,
                title = UiText.Resource(R.string.settings_privacy_retry)
            )
        }
        return listOf(
            SettingSection(
                title = null,
                items = items,
                footer = status ?: UiText.Resource(R.string.settings_privacy_footer)
            )
        )
    }

    private fun navigation(
        page: SettingsPage,
        title: UiText = page.title,
        value: UiText? = null
    ) = SettingItem.Navigation(
        key = "page_${page.name.lowercase()}",
        title = title,
        value = value,
        page = page
    )

    private fun QrAnimationType.label(): UiText.Resource {
        return UiText.Resource(
            when (this) {
                QrAnimationType.CIRCLE -> R.string.settings_qr_animation_circle
                QrAnimationType.FADE -> R.string.settings_qr_animation_fade
                QrAnimationType.NONE -> R.string.settings_qr_animation_none
            }
        )
    }

    companion object {
        private const val MIN_PRIVACY_LOADING_MS = 300L

        const val KEY_CUSTOM_SERVICES = "custom_services"
        const val KEY_NOTIFICATIONS = "notifications"
        const val KEY_SCHEDULE_SHARING = "schedule_sharing"
        const val KEY_SPORT_SHARING = "sport_sharing"
        const val KEY_SCHEDULE_SPORT_AUTO_SIGN = "schedule_sport_auto_sign"
        const val KEY_RETRY_PRIVACY = "retry_privacy"
        const val KEY_WIDGET_NEXT_LESSON_EARLY = "widget_next_lesson_early"
        const val KEY_WIDGET_HIDE_TEACHER = "widget_hide_teacher"
        const val KEY_WIDGET_HIDE_PAST = "widget_hide_past"
        const val KEY_WIDGET_SHOW_TOMORROW = "widget_show_tomorrow"
        const val KEY_QR_DYNAMIC_COLORS = "qr_dynamic_colors"
        const val KEY_QR_SPOILER = "qr_spoiler"
        const val KEY_QR_ANIMATION = "qr_animation"
        const val KEY_QR_CUSTOM_IMAGE = "qr_custom_image"
        const val KEY_QR_RESET_IMAGE = "qr_reset_image"
        const val KEY_SPORT_TEACHER_FILTER = "sport_teacher_filter"
        const val KEY_SPORT_TIME_FILTER = "sport_time_filter"
        const val KEY_REFRESH_WIDGETS = "refresh_widgets"
        const val KEY_VERSION = "app_version"
    }
}

/** Wraps the version string so the ViewModel stays free of Android resources. */
data class AppVersion(val name: String)
