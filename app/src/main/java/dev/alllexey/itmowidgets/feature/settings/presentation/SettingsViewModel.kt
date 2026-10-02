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
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.QrTileAddResult
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

sealed interface SettingsEvent {
    data object WidgetsRefreshStarted : SettingsEvent
    data object OpenNotificationSettings : SettingsEvent
    /** Asks for the Android 13 permission, or opens the system page when it cannot be asked. */
    data object RequestNotificationPermission : SettingsEvent
    data object ChooseCustomSpoiler : SettingsEvent
    data object ResetCustomSpoiler : SettingsEvent
    data object OpenDiagnostics : SettingsEvent
    data object CloseOverlays : SettingsEvent
    /** Opens the system page where Android stops restricting the app in the background. */
    data object OpenBackgroundWorkSettings : SettingsEvent
    /** The one-time dialog about background work, offered when a background check is turned on. */
    data object ShowBackgroundWorkHint : SettingsEvent
    /** Asks the system to add the QR pass tile; the answer comes back through `onQrTileResult`. */
    data object RequestQrTile : SettingsEvent
    /** Asks for the calendar permission when needed, then reports back through `onCalendarAccessGranted`. */
    data object RequestCalendarAccess : SettingsEvent
    /** The «Выгрузить в .ics» sheet. */
    data object OpenIcsExport : SettingsEvent
    /** A page of the ITMO.Widgets site, [path] relative to its base address. */
    data class OpenWebPage(val path: String) : SettingsEvent
    data class ShowMessage(val text: UiText) : SettingsEvent
    data class ShowError(val error: AppError) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val customServicesRepository: CustomServicesRepository,
    private val onboardingRepository: OnboardingRepository,
    private val widgetRefreshRequester: WidgetRefreshRequester,
    private val appVersion: AppVersion,
    private val tracking: ScheduleChangeTracking,
    private val markTracking: MarkTracking,
    private val backgroundWork: BackgroundWorkAccess,
    private val tileAccess: QuickSettingsTileAccess,
    private val calendarSync: CalendarSync,
    diagnostics: AppDiagnostics,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val page = SettingsPage.fromArgument(savedStateHandle[SettingsPage.ARGUMENT])

    private val notificationPermissionGranted = MutableStateFlow<Boolean?>(null)
    // Unknown until the screen asks; the row stays hidden rather than flash in.
    private val backgroundWorkUnrestricted = MutableStateFlow<Boolean?>(null)
    private val backgroundWorkHintMutex = Mutex()
    private var backgroundWorkRecheck: Job? = null
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
    // Only the schedule page shows it; other pages do not wait for its file.
    private val calendarState = if (page == SettingsPage.SCHEDULE) calendarSync.observeState()
    else flowOf(CalendarSyncState())
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
            diagnosticsCount,
            backgroundWorkUnrestricted,
            calendarState
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            buildSections(
                local = values[0] as LocalSettings,
                sharing = values[1] as SharingSettingsState,
                notificationsGranted = values[2] as Boolean?,
                hasCustomSpoiler = values[3] as Boolean,
                imageBusy = values[4] as Boolean,
                diagnosticsCount = values[5] as Int,
                backgroundWorkRestricted = values[6] == false,
                calendar = values[7] as CalendarSyncState
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

    /** Re-reads whether Android restricts the app in the background; the screen calls it on every return. */
    fun onBackgroundWorkChanged() {
        backgroundWorkUnrestricted.value = backgroundWork.isUnrestricted()
        backgroundWorkRecheck?.cancel()
        // HyperOS saves «Нет ограничений» only once its page has gone, which is after this screen resumes.
        backgroundWorkRecheck = viewModelScope.launch {
            delay(BACKGROUND_WORK_RECHECK_MS)
            backgroundWorkUnrestricted.value = backgroundWork.isUnrestricted()
        }
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
            KEY_SCHEDULE_CHANGES -> {
                updateLocalSetting { tracking.setEnabled(checked) }
                if (checked && notificationPermissionGranted.value == false) {
                    eventChannel.trySend(SettingsEvent.RequestNotificationPermission)
                }
                if (checked) offerBackgroundWorkHint()
            }
            KEY_MYITMO_MARKS -> {
                updateLocalSetting { markTracking.setMyItmoEnabled(checked) }
                if (checked && notificationPermissionGranted.value == false) {
                    eventChannel.trySend(SettingsEvent.RequestNotificationPermission)
                }
                if (checked) offerBackgroundWorkHint()
            }
            KEY_BARS_MARKS -> {
                updateLocalSetting { markTracking.setBarsEnabled(checked) }
                if (checked && notificationPermissionGranted.value == false) {
                    eventChannel.trySend(SettingsEvent.RequestNotificationPermission)
                }
                if (checked) offerBackgroundWorkHint()
            }
            KEY_SHEET_MARKS -> {
                updateLocalSetting { markTracking.setSheetsEnabled(checked) }
                if (checked && notificationPermissionGranted.value == false) {
                    eventChannel.trySend(SettingsEvent.RequestNotificationPermission)
                }
                if (checked) offerBackgroundWorkHint()
            }
            KEY_CALENDAR_SYNC -> if (checked) {
                eventChannel.trySend(SettingsEvent.RequestCalendarAccess)
            } else {
                updateLocalSetting { calendarSync.disable() }
            }
            KEY_HOME_CARD_SCHEDULE, KEY_HOME_CARD_SCHEDULE_CHANGES, KEY_HOME_CARD_MARKS, KEY_HOME_CARD_SPORT,
            KEY_HOME_CARD_FRIENDS -> updateLocalSetting {
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
            KEY_BACKGROUND_WORK -> eventChannel.trySend(SettingsEvent.OpenBackgroundWorkSettings)
            KEY_QR_TILE -> eventChannel.trySend(SettingsEvent.RequestQrTile)
            KEY_ICS_EXPORT -> eventChannel.trySend(SettingsEvent.OpenIcsExport)
            KEY_DELETE_ACCOUNT -> eventChannel.trySend(SettingsEvent.OpenWebPage(DELETE_ACCOUNT_PATH))
            KEY_PRIVACY_POLICY -> eventChannel.trySend(SettingsEvent.OpenWebPage(PRIVACY_POLICY_PATH))
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

    /** The calendar permission is there: synchronization turns on into the app's own calendar. */
    fun onCalendarAccessGranted() {
        viewModelScope.launch {
            when (calendarSync.enable()) {
                CalendarSyncResult.DONE -> Unit
                CalendarSyncResult.NO_PERMISSION -> showMessage(R.string.calendar_access_denied)
                CalendarSyncResult.FAILED -> eventChannel.send(SettingsEvent.ShowError(AppError.Unknown()))
                CalendarSyncResult.DEMO_UNAVAILABLE -> eventChannel.send(SettingsEvent.ShowError(AppError.DemoUnavailable))
            }
        }
    }

    private suspend fun showMessage(res: Int) {
        eventChannel.send(SettingsEvent.ShowMessage(UiText.Resource(res)))
    }

    /** The system's answer to the add request: the row leaves once the tile is known to be in the quick settings. */
    fun onQrTileResult(result: QrTileAddResult) {
        when (result) {
            QrTileAddResult.ADDED -> rememberQrTileAdded()
            QrTileAddResult.ALREADY_ADDED -> rememberQrTileAdded(R.string.settings_qr_tile_already_added)
            QrTileAddResult.NOT_ADDED, QrTileAddResult.IN_PROGRESS -> Unit
            QrTileAddResult.FAILED ->
                eventChannel.trySend(SettingsEvent.ShowMessage(UiText.Resource(R.string.settings_qr_tile_failed)))
        }
    }

    private fun rememberQrTileAdded(messageRes: Int? = null) {
        viewModelScope.launch {
            try {
                repository.setQrTileAdded(true)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                eventChannel.send(SettingsEvent.ShowError(AppError.Unknown(error)))
                return@launch
            }
            messageRes?.let { eventChannel.send(SettingsEvent.ShowMessage(UiText.Resource(it))) }
        }
    }

    /** Once per device, when a background check is turned on while Android restricts the app in the background. */
    private fun offerBackgroundWorkHint() {
        if (backgroundWorkUnrestricted.value != false) return
        viewModelScope.launch {
            backgroundWorkHintMutex.withLock {
                if (localSettings.first().backgroundWorkHintShown) return@launch
                try {
                    repository.setBackgroundWorkHintShown()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    eventChannel.send(SettingsEvent.ShowError(AppError.Unknown(error)))
                    return@launch
                }
                eventChannel.send(SettingsEvent.ShowBackgroundWorkHint)
            }
        }
    }

    private fun updateCustomServices(enabled: Boolean) {
        viewModelScope.launch {
            if (!customServicesRepository.isChangeable()) {
                eventChannel.send(SettingsEvent.ShowError(AppError.DemoUnavailable))
                return@launch
            }
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
        diagnosticsCount: Int,
        backgroundWorkRestricted: Boolean,
        calendar: CalendarSyncState
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
                    navigation(SettingsPage.RECORDBOOK),
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
            ),
            // Shown with the switch off too: an account may remain from an earlier connection.
            SettingSection(
                title = null,
                items = listOf(
                    SettingItem.Action(
                        key = KEY_DELETE_ACCOUNT,
                        title = UiText.Resource(R.string.settings_delete_account_title),
                        description = UiText.Resource(R.string.settings_delete_account_description),
                        trailingIconRes = R.drawable.ic_open_in_new
                    )
                )
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
        SettingsPage.QR_WIDGET -> listOfNotNull(
            qrTileSection(local),
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
                items = listOfNotNull(
                    SettingItem.Toggle(
                        key = KEY_SCHEDULE_CHANGES,
                        title = UiText.Resource(R.string.settings_schedule_changes_title),
                        description = UiText.Resource(
                            if (notificationsGranted == false) {
                                R.string.settings_schedule_changes_notifications_off
                            } else {
                                R.string.settings_schedule_changes_description
                            }
                        ),
                        checked = local.scheduleChangesEnabled
                    ),
                    backgroundWorkRow().takeIf { backgroundWorkRestricted && local.scheduleChangesEnabled }
                )
            ),
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
            ),
            calendarSection(calendar)
        )
        SettingsPage.RECORDBOOK -> listOf(
            SettingSection(
                title = null,
                items = listOfNotNull(
                    SettingItem.Toggle(
                        key = KEY_MYITMO_MARKS,
                        title = UiText.Resource(R.string.settings_marks_myitmo_title),
                        checked = local.myItmoMarksEnabled
                    ),
                    // BARS appears with the account's first BARS answer; before it there is nothing to check.
                    local.barsMarksEnabled?.let { enabled ->
                        SettingItem.Toggle(
                            key = KEY_BARS_MARKS,
                            title = UiText.Resource(R.string.settings_marks_bars_title),
                            checked = enabled
                        )
                    },
                    SettingItem.Toggle(
                        key = KEY_SHEET_MARKS,
                        title = UiText.Resource(R.string.settings_marks_sheets_title),
                        checked = local.sheetMarksEnabled
                    ),
                    backgroundWorkRow().takeIf {
                        backgroundWorkRestricted &&
                            (local.myItmoMarksEnabled || local.barsMarksEnabled == true || local.sheetMarksEnabled)
                    }
                ),
                footer = UiText.Resource(
                    if (notificationsGranted == false) R.string.settings_marks_notifications_off
                    else R.string.settings_marks_footer
                )
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
                    SettingItem.Action(
                        key = KEY_PRIVACY_POLICY,
                        title = UiText.Resource(R.string.settings_privacy_policy_title),
                        trailingIconRes = R.drawable.ic_open_in_new
                    ),
                    SettingItem.Info(
                        key = KEY_VERSION,
                        title = UiText.Resource(R.string.settings_version_title),
                        value = UiText.Dynamic(appVersion.name)
                    )
                ),
                footer = UiText.Resource(R.string.app_unofficial_notice)
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

    /** Android 13+ asks the system to add the tile; the row leaves once the tile is known to be added. */
    private fun qrTileSection(local: LocalSettings): SettingSection? {
        if (!tileAccess.canRequestAdd() || local.qrTileAdded) return null
        return SettingSection(
            title = null,
            items = listOf(
                SettingItem.Action(
                    key = KEY_QR_TILE,
                    title = UiText.Resource(R.string.settings_qr_tile_title),
                    description = UiText.Resource(R.string.settings_qr_tile_description)
                )
            )
        )
    }

    /** The switch says where the lessons go, or why it turned itself off. */
    private fun calendarSection(calendar: CalendarSyncState) = SettingSection(
        title = null,
        items = listOf(
            SettingItem.Toggle(
                key = KEY_CALENDAR_SYNC,
                title = UiText.Resource(R.string.settings_calendar_sync_title),
                description = UiText.Resource(
                    when (calendar.problem) {
                        CalendarSyncProblem.NO_PERMISSION -> R.string.settings_calendar_sync_no_permission
                        CalendarSyncProblem.CALENDAR_MISSING -> R.string.settings_calendar_sync_calendar_missing
                        null -> R.string.settings_calendar_sync_description
                    }
                ),
                checked = calendar.enabled
            ),
            SettingItem.Action(
                key = KEY_ICS_EXPORT,
                title = UiText.Resource(R.string.settings_ics_export_title),
                description = UiText.Resource(R.string.settings_ics_export_description),
                trailingIconRes = R.drawable.ic_download
            )
        )
    )

    /** The whole row is the button: it opens the system page, and the row leaves once Android lets the app work. */
    private fun backgroundWorkRow() = SettingItem.Action(
        key = KEY_BACKGROUND_WORK,
        title = UiText.Resource(R.string.settings_background_work_title),
        description = UiText.Resource(R.string.background_work_hint),
        trailingIconRes = R.drawable.ic_open_in_new
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
        private const val BACKGROUND_WORK_RECHECK_MS = 1_000L

        const val KEY_CUSTOM_SERVICES = "custom_services"
        const val KEY_NOTIFICATIONS = "notifications"
        const val KEY_SCHEDULE_SHARING = "schedule_sharing"
        const val KEY_SPORT_SHARING = "sport_sharing"
        const val KEY_FRIENDS_SHARING = "friends_sharing"
        const val KEY_SCHEDULE_SPORT_AUTO_SIGN = "schedule_sport_auto_sign"
        const val KEY_SCHEDULE_CHANGES = "schedule_changes"
        const val KEY_HOME_CARD_SCHEDULE = "home_card_schedule"
        const val KEY_HOME_CARD_SCHEDULE_CHANGES = "home_card_schedule_changes"
        const val KEY_HOME_CARD_MARKS = "home_card_marks"
        const val KEY_MYITMO_MARKS = "myitmo_marks"
        const val KEY_BARS_MARKS = "bars_marks"
        const val KEY_SHEET_MARKS = "sheet_marks"
        const val KEY_BACKGROUND_WORK = "background_work"
        const val KEY_CALENDAR_SYNC = "calendar_sync"
        const val KEY_ICS_EXPORT = "ics_export"
        const val KEY_HOME_CARD_SPORT = "home_card_sport"
        const val KEY_HOME_CARD_FRIENDS = "home_card_friends"

        /** Row key, the kind it hides, its title; the feed order is the row order. */
        private val HOME_CARDS = listOf(
            Triple(KEY_HOME_CARD_SCHEDULE, HomeCardKind.SCHEDULE, R.string.settings_home_card_schedule_title),
            Triple(
                KEY_HOME_CARD_SCHEDULE_CHANGES,
                HomeCardKind.SCHEDULE_CHANGES,
                R.string.settings_home_card_schedule_changes_title
            ),
            Triple(KEY_HOME_CARD_MARKS, HomeCardKind.MARKS, R.string.settings_home_card_marks_title),
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
        const val KEY_QR_TILE = "qr_tile"
        const val KEY_SPORT_TEACHER_FILTER = "sport_teacher_filter"
        const val KEY_SPORT_TIME_FILTER = "sport_time_filter"
        const val KEY_REFRESH_WIDGETS = "refresh_widgets"
        const val KEY_RESTART_ONBOARDING = "restart_onboarding"
        const val KEY_VERSION = "app_version"
        const val KEY_DIAGNOSTICS = "diagnostics"
        const val KEY_DELETE_ACCOUNT = "delete_account"
        const val KEY_PRIVACY_POLICY = "privacy_policy"
        const val DELETE_ACCOUNT_PATH = "/delete-account"
        const val PRIVACY_POLICY_PATH = "/privacy.html"
    }
}

/** Wraps the version string so the ViewModel stays free of Android resources. */
data class AppVersion(val name: String)
