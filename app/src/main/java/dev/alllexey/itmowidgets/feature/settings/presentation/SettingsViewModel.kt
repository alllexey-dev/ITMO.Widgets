package dev.alllexey.itmowidgets.feature.settings.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.presentation.EventQueue
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

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

    private val page = SettingsPage.fromArgument(savedStateHandle[SettingsPage.ARGUMENT])

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

    // Do not render made-up defaults while DataStore is loading: the initial state is not loaded and has no rows,
    // and the first loaded state already carries the persisted values. Otherwise every persisted `true` appears as
    // an off -> on transition when Settings opens.
    val uiState: StateFlow<SettingsUiState> = combine(
        localSettings,
        displayedSharingSettings,
        notificationPermissionGranted,
        customSpoilerConfigured,
        customSpoilerBusy,
        diagnosticsCount,
        backgroundWorkUnrestricted,
        calendarState
    ) { values ->
        val local = values[0] as LocalSettings
        SettingsUiState(
            page = page,
            sections = buildSections(
                local = local,
                sharing = values[1] as SharingSettingsState,
                notificationsGranted = values[2] as Boolean?,
                hasCustomSpoiler = values[3] as Boolean,
                imageBusy = values[4] as Boolean,
                diagnosticsCount = values[5] as Int,
                backgroundWorkRestricted = values[6] == false,
                calendar = values[7] as CalendarSyncState
            ),
            loaded = true,
            previewSettings = previewSettings(local)
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(page))

    private val eventQueue = EventQueue<SettingsEvent>()
    val events: Flow<SettingsEvent> = eventQueue.events

    init {
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

    fun onToggleChanged(id: SettingRowId, checked: Boolean) {
        when (id) {
            SettingRowId.CUSTOM_SERVICES -> updateCustomServices(checked)
            SettingRowId.SCHEDULE_SPORT_AUTO_SIGN -> updateWidgetSetting {
                repository.setScheduleSportAutoSignEnabled(checked)
            }
            SettingRowId.SCHEDULE_CHANGES -> updateBackgroundCheck(checked) { tracking.setEnabled(checked) }
            SettingRowId.MYITMO_MARKS -> updateBackgroundCheck(checked) { markTracking.setMyItmoEnabled(checked) }
            SettingRowId.BARS_MARKS -> updateBackgroundCheck(checked) { markTracking.setBarsEnabled(checked) }
            SettingRowId.SHEET_MARKS -> updateBackgroundCheck(checked) { markTracking.setSheetsEnabled(checked) }
            SettingRowId.CALENDAR_SYNC -> if (checked) {
                send(SettingsEvent.RequestCalendarAccess)
            } else {
                updateLocalSetting { calendarSync.disable() }
            }
            SettingRowId.HOME_CARD_SCHEDULE, SettingRowId.HOME_CARD_SCHEDULE_CHANGES, SettingRowId.HOME_CARD_MARKS,
            SettingRowId.HOME_CARD_SPORT, SettingRowId.HOME_CARD_FRIENDS -> updateLocalSetting {
                val kind = HOME_CARDS.first { it.first == id }.second
                repository.setHomeCardVisible(kind, checked)
            }
            SettingRowId.COMPACT_WIDGET_NEXT_LESSON_EARLY -> updateWidgetSetting {
                repository.setCompactWidgetNextLessonEarlyEnabled(checked)
            }
            SettingRowId.COMPACT_WIDGET_HIDE_TEACHER -> updateWidgetSetting {
                repository.setCompactWidgetTeacherHidden(checked)
            }
            SettingRowId.FULL_WIDGET_HIDE_TEACHER -> updateWidgetSetting {
                repository.setFullWidgetTeacherHidden(checked)
            }
            SettingRowId.FULL_WIDGET_HIDE_PAST -> updateWidgetSetting {
                repository.setFullWidgetPastLessonsHidden(checked)
            }
            SettingRowId.FULL_WIDGET_SHOW_TOMORROW -> updateWidgetSetting {
                repository.setFullWidgetTomorrowEnabled(checked)
            }
            SettingRowId.QR_DYNAMIC_COLORS -> updateWidgetSetting {
                repository.setQrDynamicColorsEnabled(checked)
            }
            SettingRowId.QR_SPOILER -> updateWidgetSetting {
                repository.setQrSpoilerEnabled(checked)
            }
            SettingRowId.SPORT_TEACHER_FILTER -> updateLocalSetting {
                repository.setTeacherSelectorHidden(!checked)
            }
            SettingRowId.SPORT_TIME_FILTER -> updateLocalSetting {
                repository.setTimeSelectorHidden(!checked)
            }
            else -> Unit
        }
    }

    fun onChoiceChanged(id: SettingRowId, optionKey: String) {
        when (id) {
            SettingRowId.QR_ANIMATION -> {
                val animation = QrAnimationType.entries.firstOrNull { it.name == optionKey } ?: return
                updateWidgetSetting { repository.setQrAnimationType(animation) }
            }
            SettingRowId.COMPACT_WIDGET_TEXT_SIZE, SettingRowId.FULL_WIDGET_TEXT_SIZE -> {
                val size = WidgetTextSize.entries.firstOrNull { it.name == optionKey } ?: return
                updateWidgetSetting {
                    if (id == SettingRowId.COMPACT_WIDGET_TEXT_SIZE) {
                        repository.setCompactWidgetTextSize(size)
                    } else {
                        repository.setFullWidgetTextSize(size)
                    }
                }
            }
            SettingRowId.SCHEDULE_SHARING, SettingRowId.SPORT_SHARING, SettingRowId.FRIENDS_SHARING -> {
                val visibility = SharingVisibility.entries.firstOrNull { it.name == optionKey } ?: return
                val item = uiState.value.sections.flatMap(SettingSection::items)
                    .filterIsInstance<SettingItem.Choice>().firstOrNull { it.id == id } ?: return
                // A dialog can outlive the state that opened it. Reject stale and duplicate actions.
                if (!item.enabled || item.selectedOptionKey == null || privacyUpdateInProgress.value) return
                if (item.selectedOptionKey == optionKey) return
                updateSharing {
                    when (id) {
                        SettingRowId.SCHEDULE_SHARING -> repository.setScheduleVisibility(visibility)
                        SettingRowId.FRIENDS_SHARING -> repository.setFriendsVisibility(visibility)
                        else -> repository.setSportVisibility(visibility)
                    }
                }
            }
            else -> Unit
        }
    }

    fun onAction(id: SettingRowId) {
        when (id) {
            SettingRowId.REFRESH_WIDGETS -> {
                widgetRefreshRequester.refreshAll()
                send(SettingsEvent.WidgetsRefreshStarted)
            }
            SettingRowId.NOTIFICATIONS -> send(SettingsEvent.OpenNotificationSettings)
            SettingRowId.QR_CUSTOM_IMAGE -> send(SettingsEvent.ChooseCustomSpoiler)
            SettingRowId.QR_RESET_IMAGE -> send(SettingsEvent.ResetCustomSpoiler)
            SettingRowId.DIAGNOSTICS -> send(SettingsEvent.OpenDiagnostics)
            SettingRowId.BACKGROUND_WORK -> send(SettingsEvent.OpenBackgroundWorkSettings)
            SettingRowId.QR_TILE -> send(SettingsEvent.RequestQrTile)
            SettingRowId.ICS_EXPORT -> send(SettingsEvent.OpenIcsExport)
            SettingRowId.DELETE_ACCOUNT -> send(SettingsEvent.OpenWebPage(DELETE_ACCOUNT_PATH))
            SettingRowId.PRIVACY_POLICY -> send(SettingsEvent.OpenWebPage(PRIVACY_POLICY_PATH))
            SettingRowId.RESTART_ONBOARDING -> viewModelScope.launch {
                // The stored flag is what the root gate reads; the overlay only has to get out of the way.
                onboardingRepository.reset()
                eventQueue.send(SettingsEvent.CloseOverlays)
            }
            SettingRowId.RETRY_PRIVACY -> viewModelScope.launch {
                refreshPrivacySettings()
            }
            else -> Unit
        }
    }

    /**
     * Sends [event] before the calling action returns, as the action's own effect. The queue is buffered, so the
     * undispatched send completes at once; it only waits when 64 events are undelivered.
     */
    private fun send(event: SettingsEvent) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { eventQueue.send(event) }
    }

    /** The calendar permission is there: synchronization turns on into the app's own calendar. */
    fun onCalendarAccessGranted() {
        viewModelScope.launch {
            when (calendarSync.enable()) {
                CalendarSyncResult.DONE -> Unit
                CalendarSyncResult.NO_PERMISSION -> showMessage(R.string.calendar_access_denied)
                CalendarSyncResult.FAILED -> eventQueue.send(SettingsEvent.ShowError(AppError.Unknown()))
                CalendarSyncResult.DEMO_UNAVAILABLE -> eventQueue.send(SettingsEvent.ShowError(AppError.DemoUnavailable))
            }
        }
    }

    private suspend fun showMessage(res: Int) {
        eventQueue.send(SettingsEvent.ShowMessage(UiText.Resource(res)))
    }

    /** The system's answer to the add request: the row leaves once the tile is known to be in the quick settings. */
    fun onQrTileResult(result: QrTileAddResult) {
        when (result) {
            QrTileAddResult.ADDED -> rememberQrTileAdded()
            QrTileAddResult.ALREADY_ADDED -> rememberQrTileAdded(R.string.settings_qr_tile_already_added)
            QrTileAddResult.NOT_ADDED, QrTileAddResult.IN_PROGRESS -> Unit
            QrTileAddResult.FAILED -> send(SettingsEvent.ShowMessage(UiText.Resource(R.string.settings_qr_tile_failed)))
        }
    }

    private fun rememberQrTileAdded(messageRes: Int? = null) {
        viewModelScope.launch {
            try {
                repository.setQrTileAdded(true)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                eventQueue.send(SettingsEvent.ShowError(AppError.Unknown(error)))
                return@launch
            }
            messageRes?.let { showMessage(it) }
        }
    }

    /**
     * A background check's switch: stores the value and, when it is turned on, asks for notifications if they are
     * off and offers the background work hint.
     */
    private fun updateBackgroundCheck(checked: Boolean, store: suspend () -> Unit) {
        updateLocalSetting(action = store)
        if (!checked) return
        if (notificationPermissionGranted.value == false) send(SettingsEvent.RequestNotificationPermission)
        offerBackgroundWorkHint()
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
                    eventQueue.send(SettingsEvent.ShowError(AppError.Unknown(error)))
                    return@launch
                }
                eventQueue.send(SettingsEvent.ShowBackgroundWorkHint)
            }
        }
    }

    private fun updateCustomServices(enabled: Boolean) {
        viewModelScope.launch {
            if (!customServicesRepository.isChangeable()) {
                eventQueue.send(SettingsEvent.ShowError(AppError.DemoUnavailable))
                return@launch
            }
            try {
                customServicesRepository.setEnabled(enabled)
                widgetRefreshRequester.refreshAll()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                eventQueue.send(SettingsEvent.ShowError(AppError.Unknown(error)))
            }
        }
    }

    private fun updateSharing(action: suspend () -> AppResult<Unit>) {
        privacyUpdateInProgress.value = true
        viewModelScope.launch {
            try {
                when (val result = action()) {
                    is AppResult.Success -> Unit
                    is AppResult.Failure -> eventQueue.send(SettingsEvent.ShowError(result.error))
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
                eventQueue.send(SettingsEvent.ShowError(AppError.Unknown(error)))
            }
        }
    }

    private fun previewSettings(local: LocalSettings): WidgetPreviewSettings? = when (page) {
        SettingsPage.QR_WIDGET -> WidgetPreviewSettings.Qr(local.qrWidget)
        SettingsPage.COMPACT_SCHEDULE_WIDGET -> WidgetPreviewSettings.Schedule(local.scheduleWidget, ScheduleWidgetFormat.COMPACT)
        SettingsPage.FULL_SCHEDULE_WIDGET -> WidgetPreviewSettings.Schedule(local.scheduleWidget, ScheduleWidgetFormat.FULL)
        else -> null
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
                        id = SettingRowId.NOTIFICATIONS,
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
                        id = SettingRowId.CUSTOM_SERVICES,
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
                        id = SettingRowId.DELETE_ACCOUNT,
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
                        id = SettingRowId.COMPACT_WIDGET_NEXT_LESSON_EARLY,
                        title = UiText.Resource(R.string.settings_widget_next_early_title),
                        description = UiText.Resource(R.string.settings_widget_next_early_description),
                        checked = local.scheduleWidget.compact.showNextLessonEarly
                    ),
                    SettingItem.Toggle(
                        id = SettingRowId.COMPACT_WIDGET_HIDE_TEACHER,
                        title = UiText.Resource(R.string.settings_widget_hide_teacher_title),
                        checked = local.scheduleWidget.compact.hideTeacher
                    ),
                    textSizeChoice(SettingRowId.COMPACT_WIDGET_TEXT_SIZE, local.scheduleWidget.compact.textSize)
                )
            )
        )
        SettingsPage.FULL_SCHEDULE_WIDGET -> listOf(
            SettingSection(
                title = null,
                items = listOf(
                    SettingItem.Toggle(
                        id = SettingRowId.FULL_WIDGET_HIDE_TEACHER,
                        title = UiText.Resource(R.string.settings_widget_hide_teacher_title),
                        checked = local.scheduleWidget.full.hideTeacher
                    ),
                    SettingItem.Toggle(
                        id = SettingRowId.FULL_WIDGET_HIDE_PAST,
                        title = UiText.Resource(R.string.settings_widget_hide_past_title),
                        checked = local.scheduleWidget.full.hidePastLessons
                    ),
                    SettingItem.Toggle(
                        id = SettingRowId.FULL_WIDGET_SHOW_TOMORROW,
                        title = UiText.Resource(R.string.settings_widget_tomorrow_title),
                        description = UiText.Resource(R.string.settings_widget_tomorrow_description),
                        checked = local.scheduleWidget.full.showTomorrowWhenTodayIsOver
                    ),
                    textSizeChoice(SettingRowId.FULL_WIDGET_TEXT_SIZE, local.scheduleWidget.full.textSize)
                )
            )
        )
        SettingsPage.QR_WIDGET -> listOfNotNull(
            qrTileSection(local),
            SettingSection(
                title = null,
                items = listOf(
                    SettingItem.Toggle(
                        id = SettingRowId.QR_DYNAMIC_COLORS,
                        title = UiText.Resource(R.string.settings_qr_dynamic_colors_title),
                        checked = local.qrWidget.dynamicColors
                    ),
                    SettingItem.Toggle(
                        id = SettingRowId.QR_SPOILER,
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
                        id = SettingRowId.QR_ANIMATION,
                        title = UiText.Resource(R.string.settings_qr_animation_title),
                        value = local.qrWidget.animationType.label(),
                        options = QrAnimationType.entries.map { animation ->
                            ChoiceOption(animation.name, animation.label())
                        },
                        selectedOptionKey = local.qrWidget.animationType.name,
                        enabled = local.qrWidget.spoilerEnabled
                    ),
                    SettingItem.Action(
                        id = SettingRowId.QR_CUSTOM_IMAGE,
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
                        id = SettingRowId.QR_RESET_IMAGE,
                        title = UiText.Resource(R.string.settings_qr_reset_image_title),
                        enabled = local.qrWidget.spoilerEnabled && hasCustomSpoiler && !imageBusy
                    )
                )
            )
        )
        SettingsPage.HOME -> listOf(
            SettingSection(
                title = null,
                items = HOME_CARDS.map { (id, kind, titleRes) ->
                    SettingItem.Toggle(
                        id = id,
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
                        id = SettingRowId.SCHEDULE_CHANGES,
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
                        id = SettingRowId.SCHEDULE_SPORT_AUTO_SIGN,
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
                        id = SettingRowId.MYITMO_MARKS,
                        title = UiText.Resource(R.string.settings_marks_myitmo_title),
                        checked = local.myItmoMarksEnabled
                    ),
                    // BARS appears with the account's first BARS answer; before it there is nothing to check.
                    local.barsMarksEnabled?.let { enabled ->
                        SettingItem.Toggle(
                            id = SettingRowId.BARS_MARKS,
                            title = UiText.Resource(R.string.settings_marks_bars_title),
                            checked = enabled
                        )
                    },
                    SettingItem.Toggle(
                        id = SettingRowId.SHEET_MARKS,
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
                        id = SettingRowId.SPORT_TEACHER_FILTER,
                        title = UiText.Resource(R.string.settings_sport_teacher_filter_title),
                        checked = !local.sport.hideTeacherSelector
                    ),
                    SettingItem.Toggle(
                        id = SettingRowId.SPORT_TIME_FILTER,
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
                        id = SettingRowId.REFRESH_WIDGETS,
                        title = UiText.Resource(R.string.settings_refresh_widgets_title),
                        trailingIconRes = R.drawable.ic_refresh
                    ),
                    SettingItem.Action(
                        id = SettingRowId.RESTART_ONBOARDING,
                        title = UiText.Resource(R.string.settings_restart_onboarding_title),
                        description = UiText.Resource(R.string.settings_restart_onboarding_description),
                        trailingIconRes = R.drawable.ic_refresh
                    ),
                    SettingItem.Action(
                        id = SettingRowId.DIAGNOSTICS,
                        title = UiText.Resource(R.string.settings_diagnostics_title),
                        value = UiText.Resource(R.string.settings_diagnostics_count, listOf(diagnosticsCount)),
                        trailingIconRes = R.drawable.ic_chevron_right
                    ),
                    SettingItem.Action(
                        id = SettingRowId.PRIVACY_POLICY,
                        title = UiText.Resource(R.string.settings_privacy_policy_title),
                        trailingIconRes = R.drawable.ic_open_in_new
                    ),
                    SettingItem.Info(
                        id = SettingRowId.VERSION,
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
                id = SettingRowId.SCHEDULE_SHARING,
                title = UiText.Resource(R.string.settings_schedule_sharing_title),
                visibility = content?.settings?.scheduleVisibility,
                enabled = editable
            ),
            sharingChoice(
                id = SettingRowId.SPORT_SHARING,
                title = UiText.Resource(R.string.settings_sport_sharing_title),
                visibility = content?.settings?.sportVisibility,
                enabled = editable
            ),
            sharingChoice(
                id = SettingRowId.FRIENDS_SHARING,
                title = UiText.Resource(R.string.settings_friends_sharing_title),
                visibility = content?.settings?.friendsVisibility,
                enabled = editable
            )
        )
        if (!local.customServicesEnabled) {
            items += navigation(SettingsPage.SERVICES)
        } else if (sharing is SharingSettingsState.Error) {
            items += SettingItem.Action(
                id = SettingRowId.RETRY_PRIVACY,
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
        id: SettingRowId,
        title: UiText,
        visibility: SharingVisibility?,
        enabled: Boolean
    ) = SettingItem.Choice(
        id = id,
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
                    id = SettingRowId.QR_TILE,
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
                id = SettingRowId.CALENDAR_SYNC,
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
                id = SettingRowId.ICS_EXPORT,
                title = UiText.Resource(R.string.settings_ics_export_title),
                description = UiText.Resource(R.string.settings_ics_export_description),
                trailingIconRes = R.drawable.ic_download
            )
        )
    )

    /** The whole row is the button: it opens the system page, and the row leaves once Android lets the app work. */
    private fun backgroundWorkRow() = SettingItem.Action(
        id = SettingRowId.BACKGROUND_WORK,
        title = UiText.Resource(R.string.settings_background_work_title),
        description = UiText.Resource(R.string.background_work_hint),
        trailingIconRes = R.drawable.ic_open_in_new
    )

    private fun navigation(
        page: SettingsPage,
        title: UiText = page.title,
        value: UiText? = null
    ) = SettingItem.Navigation(
        id = SettingRowId.navigation(page),
        title = title,
        value = value,
        page = page
    )

    private fun textSizeChoice(id: SettingRowId, size: WidgetTextSize) = SettingItem.Choice(
        id = id,
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

        /** Row id, the kind it hides, its title; the feed order is the row order. */
        private val HOME_CARDS = listOf(
            Triple(SettingRowId.HOME_CARD_SCHEDULE, HomeCardKind.SCHEDULE, R.string.settings_home_card_schedule_title),
            Triple(
                SettingRowId.HOME_CARD_SCHEDULE_CHANGES,
                HomeCardKind.SCHEDULE_CHANGES,
                R.string.settings_home_card_schedule_changes_title
            ),
            Triple(SettingRowId.HOME_CARD_MARKS, HomeCardKind.MARKS, R.string.settings_home_card_marks_title),
            Triple(SettingRowId.HOME_CARD_SPORT, HomeCardKind.SPORT, R.string.settings_home_card_sport_title),
            Triple(SettingRowId.HOME_CARD_FRIENDS, HomeCardKind.FRIEND_REQUESTS, R.string.settings_home_card_friends_title)
        )
        const val DELETE_ACCOUNT_PATH = "/delete-account"
        const val PRIVACY_POLICY_PATH = "/privacy.html"
    }
}
