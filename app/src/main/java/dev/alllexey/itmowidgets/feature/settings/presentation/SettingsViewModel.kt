package dev.alllexey.itmowidgets.feature.settings.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
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
    data object OpenDiagnostics : SettingsEvent
    data object CloseOverlays : SettingsEvent
    data class ShowError(val error: AppError) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val customServicesRepository: CustomServicesRepository,
    private val onboardingRepository: OnboardingRepository,
    private val widgetRefreshRequester: WidgetRefreshRequester,
    private val appVersion: AppVersion,
    diagnostics: AppDiagnostics,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val page = SettingsPage.fromArgument(savedStateHandle[SettingsPage.ARGUMENT])

    private val notificationPermissionGranted = MutableStateFlow<Boolean?>(null)
    private val customSpoilerConfigured = MutableStateFlow(false)
    private val customSpoilerBusy = MutableStateFlow(false)
    // Start masked so cached backend values cannot flash before the fresh request.
    private val privacyRefreshInProgress = MutableStateFlow(page == SettingsPage.PRIVACY)
    private val privacyUpdateInProgress = MutableStateFlow(false)
    private val privacyRefreshMutex = Mutex()
    private val displayedSharingSettings = combine(
        repository.observeSharingSettings(), privacyRefreshInProgress, privacyUpdateInProgress
    ) { sharing, refreshing, updating ->
        when {
            refreshing -> SharingSettingsState.Loading
            updating && sharing is SharingSettingsState.Content -> sharing.copy(updating = true)
            else -> sharing
        }
    }
    private val diagnosticsCount = diagnostics.observe().map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    private val localSettings = repository.observeLocalSettings()
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val previewSettings: StateFlow<WidgetPreviewSettings?> = localSettings
        .map { local ->
            when (page) {
                SettingsPage.QR_WIDGET -> WidgetPreviewSettings.Qr(local.qrWidget)
                SettingsPage.COMPACT_SCHEDULE_WIDGET -> WidgetPreviewSettings.Schedule(local.scheduleWidget, ScheduleWidgetFormat.COMPACT)
                SettingsPage.FULL_SCHEDULE_WIDGET -> WidgetPreviewSettings.Schedule(local.scheduleWidget, ScheduleWidgetFormat.FULL)
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
            customSpoilerBusy,
            diagnosticsCount
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            buildSections(
                local = values[0] as LocalSettings,
                sharing = values[1] as SharingSettingsState,
                notificationsGranted = values[2] as Boolean?,
                hasCustomSpoiler = values[3] as Boolean,
                imageBusy = values[4] as Boolean,
                diagnosticsCount = values[5] as Int
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
            KEY_SCHEDULE_SPORT_AUTO_SIGN -> updateWidgetSetting {
                repository.setScheduleSportAutoSignEnabled(checked)
            }
            KEY_HOME_CARD_SCHEDULE, KEY_HOME_CARD_SPORT, KEY_HOME_CARD_FRIENDS -> updateLocalSetting {
                val kind = HOME_CARDS.first { it.first == key }.second
                repository.setHomeCardVisible(kind, checked)
            }
            KEY_COMPACT_WIDGET_NEXT_LESSON_EARLY -> updateWidgetSetting {
                repository.setCompactWidgetNextLessonEarlyEnabled(checked)
            }
            KEY_COMPACT_WIDGET_HIDE_TEACHER -> updateWidgetSetting {
                repository.setCompactWidgetTeacherHidden(checked)
            }
            KEY_FULL_WIDGET_HIDE_TEACHER -> updateWidgetSetting {
                repository.setFullWidgetTeacherHidden(checked)
            }
            KEY_FULL_WIDGET_HIDE_PAST -> updateWidgetSetting {
                repository.setFullWidgetPastLessonsHidden(checked)
            }
            KEY_FULL_WIDGET_SHOW_TOMORROW -> updateWidgetSetting {
                repository.setFullWidgetTomorrowEnabled(checked)
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
        when (key) {
            KEY_QR_ANIMATION -> {
                val animation = QrAnimationType.entries.firstOrNull { it.name == optionKey } ?: return
                updateWidgetSetting { repository.setQrAnimationType(animation) }
            }
            KEY_COMPACT_WIDGET_TEXT_SIZE, KEY_FULL_WIDGET_TEXT_SIZE -> {
                val size = WidgetTextSize.entries.firstOrNull { it.name == optionKey } ?: return
                updateWidgetSetting {
                    if (key == KEY_COMPACT_WIDGET_TEXT_SIZE) {
                        repository.setCompactWidgetTextSize(size)
                    } else {
                        repository.setFullWidgetTextSize(size)
                    }
                }
            }
            KEY_SCHEDULE_SHARING, KEY_SPORT_SHARING, KEY_FRIENDS_SHARING -> {
                val visibility = SharingVisibility.entries.firstOrNull { it.name == optionKey } ?: return
                val item = sections.value.flatMap(SettingSection::items)
                    .filterIsInstance<SettingItem.Choice>().firstOrNull { it.key == key } ?: return
                // A dialog can outlive the state that opened it. Reject stale and duplicate actions.
                if (!item.enabled || item.selectedOptionKey == null || privacyUpdateInProgress.value) return
                if (item.selectedOptionKey == optionKey) return
                updateSharing {
                    when (key) {
                        KEY_SCHEDULE_SHARING -> repository.setScheduleVisibility(visibility)
                        KEY_FRIENDS_SHARING -> repository.setFriendsVisibility(visibility)
                        else -> repository.setSportVisibility(visibility)
                    }
                }
            }
        }
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
            KEY_DIAGNOSTICS -> eventChannel.trySend(SettingsEvent.OpenDiagnostics)
            KEY_RESTART_ONBOARDING -> viewModelScope.launch {
                // The stored flag is what the root gate reads; the overlay only has to get out of the way.
                onboardingRepository.reset()
                eventChannel.send(SettingsEvent.CloseOverlays)
            }
            KEY_RETRY_PRIVACY -> viewModelScope.launch {
                refreshPrivacySettings()
            }
        }
    }

    private fun updateCustomServices(enabled: Boolean) {
        viewModelScope.launch {
            try {
                customServicesRepository.setEnabled(enabled)
                widgetRefreshRequester.refreshAll()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                eventChannel.send(SettingsEvent.ShowError(AppError.Unknown(error)))
            }
        }
    }

    private fun updateSharing(action: suspend () -> AppResult<Unit>) {
        privacyUpdateInProgress.value = true
        viewModelScope.launch {
            try {
                when (val result = action()) {
                    is AppResult.Success -> Unit
                    is AppResult.Failure -> eventChannel.send(SettingsEvent.ShowError(result.error))
                }
            } finally {
                privacyUpdateInProgress.value = false
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
        imageBusy: Boolean,
        diagnosticsCount: Int
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
                    navigation(SettingsPage.COMPACT_SCHEDULE_WIDGET),
                    navigation(SettingsPage.FULL_SCHEDULE_WIDGET),
                    navigation(
                        SettingsPage.QR_WIDGET,
                        title = UiText.Resource(R.string.settings_qr_short_title)
                    )
                )
            ),
            SettingSection(
                title = UiText.Resource(R.string.me_group_app),
                items = listOf(
                    navigation(SettingsPage.HOME),
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
                        // The screen is already titled by the page; the switch names the action.
                        title = UiText.Resource(R.string.settings_custom_services_toggle),
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
        SettingsPage.COMPACT_SCHEDULE_WIDGET -> listOf(
            SettingSection(
                title = null,
                items = listOf(
                    SettingItem.Toggle(
                        key = KEY_COMPACT_WIDGET_NEXT_LESSON_EARLY,
                        title = UiText.Resource(R.string.settings_widget_next_early_title),
                        description = UiText.Resource(R.string.settings_widget_next_early_description),
                        checked = local.scheduleWidget.compact.showNextLessonEarly
                    ),
                    SettingItem.Toggle(
                        key = KEY_COMPACT_WIDGET_HIDE_TEACHER,
                        title = UiText.Resource(R.string.settings_widget_hide_teacher_title),
                        checked = local.scheduleWidget.compact.hideTeacher
                    ),
                    textSizeChoice(KEY_COMPACT_WIDGET_TEXT_SIZE, local.scheduleWidget.compact.textSize)
                )
            )
        )
        SettingsPage.FULL_SCHEDULE_WIDGET -> listOf(
            SettingSection(
                title = null,
                items = listOf(
                    SettingItem.Toggle(
                        key = KEY_FULL_WIDGET_HIDE_TEACHER,
                        title = UiText.Resource(R.string.settings_widget_hide_teacher_title),
                        checked = local.scheduleWidget.full.hideTeacher
                    ),
                    SettingItem.Toggle(
                        key = KEY_FULL_WIDGET_HIDE_PAST,
                        title = UiText.Resource(R.string.settings_widget_hide_past_title),
                        checked = local.scheduleWidget.full.hidePastLessons
                    ),
                    SettingItem.Toggle(
                        key = KEY_FULL_WIDGET_SHOW_TOMORROW,
                        title = UiText.Resource(R.string.settings_widget_tomorrow_title),
                        description = UiText.Resource(R.string.settings_widget_tomorrow_description),
                        checked = local.scheduleWidget.full.showTomorrowWhenTodayIsOver
                    ),
                    textSizeChoice(KEY_FULL_WIDGET_TEXT_SIZE, local.scheduleWidget.full.textSize)
                )
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
        SettingsPage.HOME -> listOf(
            SettingSection(
                title = null,
                items = HOME_CARDS.map { (key, kind, titleRes) ->
                    SettingItem.Toggle(
                        key = key,
                        title = UiText.Resource(titleRes),
                        checked = kind !in local.hiddenHomeCards
                    )
                }
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
                )
            )
        )
        SettingsPage.MAINTENANCE -> listOf(
            SettingSection(
                title = null,
                items = listOf(
                    SettingItem.Action(
                        key = KEY_REFRESH_WIDGETS,
                        title = UiText.Resource(R.string.settings_refresh_widgets_title),
                        trailingIconRes = R.drawable.ic_refresh
                    ),
                    SettingItem.Action(
                        key = KEY_RESTART_ONBOARDING,
                        title = UiText.Resource(R.string.settings_restart_onboarding_title),
                        description = UiText.Resource(R.string.settings_restart_onboarding_description),
                        trailingIconRes = R.drawable.ic_refresh
                    ),
                    SettingItem.Action(
                        key = KEY_DIAGNOSTICS,
                        title = UiText.Resource(R.string.settings_diagnostics_title),
                        value = UiText.Resource(R.string.settings_diagnostics_count, listOf(diagnosticsCount)),
                        trailingIconRes = R.drawable.ic_chevron_right
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
            sharingChoice(
                key = KEY_SCHEDULE_SHARING,
                title = UiText.Resource(R.string.settings_schedule_sharing_title),
                visibility = content?.settings?.scheduleVisibility,
                enabled = editable
            ),
            sharingChoice(
                key = KEY_SPORT_SHARING,
                title = UiText.Resource(R.string.settings_sport_sharing_title),
                visibility = content?.settings?.sportVisibility,
                enabled = editable
            ),
            sharingChoice(
                key = KEY_FRIENDS_SHARING,
                title = UiText.Resource(R.string.settings_friends_sharing_title),
                visibility = content?.settings?.friendsVisibility,
                enabled = editable
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

    private fun sharingChoice(
        key: String,
        title: UiText,
        visibility: SharingVisibility?,
        enabled: Boolean
    ) = SettingItem.Choice(
        key = key,
        title = title,
        value = visibility?.label() ?: UiText.Resource(R.string.settings_privacy_unknown),
        options = SharingVisibility.entries.map { ChoiceOption(it.name, it.label()) },
        selectedOptionKey = visibility?.name,
        enabled = enabled
    )

    private fun SharingVisibility.label() = UiText.Resource(
        when (this) {
            SharingVisibility.ALL -> R.string.settings_privacy_all
            SharingVisibility.FRIENDS -> R.string.settings_privacy_friends
            SharingVisibility.NOBODY -> R.string.settings_privacy_nobody
        }
    )

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

    private fun textSizeChoice(key: String, size: WidgetTextSize) = SettingItem.Choice(
        key = key,
        title = UiText.Resource(R.string.settings_widget_text_size_title),
        value = size.label(),
        options = WidgetTextSize.entries.map { ChoiceOption(it.name, it.label()) },
        selectedOptionKey = size.name
    )

    private fun WidgetTextSize.label(): UiText.Resource {
        return UiText.Resource(
            when (this) {
                WidgetTextSize.NORMAL -> R.string.settings_widget_text_size_normal
                WidgetTextSize.LARGE -> R.string.settings_widget_text_size_large
                WidgetTextSize.EXTRA_LARGE -> R.string.settings_widget_text_size_extra_large
            }
        )
    }

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
        const val KEY_FRIENDS_SHARING = "friends_sharing"
        const val KEY_SCHEDULE_SPORT_AUTO_SIGN = "schedule_sport_auto_sign"
        const val KEY_HOME_CARD_SCHEDULE = "home_card_schedule"
        const val KEY_HOME_CARD_SPORT = "home_card_sport"
        const val KEY_HOME_CARD_FRIENDS = "home_card_friends"

        /** Row key, the kind it hides, its title; the feed order is the row order. */
        private val HOME_CARDS = listOf(
            Triple(KEY_HOME_CARD_SCHEDULE, HomeCardKind.SCHEDULE, R.string.settings_home_card_schedule_title),
            Triple(KEY_HOME_CARD_SPORT, HomeCardKind.SPORT, R.string.settings_home_card_sport_title),
            Triple(KEY_HOME_CARD_FRIENDS, HomeCardKind.FRIEND_REQUESTS, R.string.settings_home_card_friends_title)
        )
        const val KEY_RETRY_PRIVACY = "retry_privacy"
        const val KEY_COMPACT_WIDGET_NEXT_LESSON_EARLY = "compact_widget_next_lesson_early"
        const val KEY_COMPACT_WIDGET_HIDE_TEACHER = "compact_widget_hide_teacher"
        const val KEY_FULL_WIDGET_HIDE_TEACHER = "full_widget_hide_teacher"
        const val KEY_FULL_WIDGET_HIDE_PAST = "full_widget_hide_past"
        const val KEY_FULL_WIDGET_SHOW_TOMORROW = "full_widget_show_tomorrow"
        const val KEY_COMPACT_WIDGET_TEXT_SIZE = "compact_widget_text_size"
        const val KEY_FULL_WIDGET_TEXT_SIZE = "full_widget_text_size"
        const val KEY_QR_DYNAMIC_COLORS = "qr_dynamic_colors"
        const val KEY_QR_SPOILER = "qr_spoiler"
        const val KEY_QR_ANIMATION = "qr_animation"
        const val KEY_QR_CUSTOM_IMAGE = "qr_custom_image"
        const val KEY_QR_RESET_IMAGE = "qr_reset_image"
        const val KEY_SPORT_TEACHER_FILTER = "sport_teacher_filter"
        const val KEY_SPORT_TIME_FILTER = "sport_time_filter"
        const val KEY_REFRESH_WIDGETS = "refresh_widgets"
        const val KEY_RESTART_ONBOARDING = "restart_onboarding"
        const val KEY_VERSION = "app_version"
        const val KEY_DIAGNOSTICS = "diagnostics"
    }
}

/** Wraps the version string so the ViewModel stays free of Android resources. */
data class AppVersion(val name: String)
