package dev.alllexey.itmowidgets.feature.settings.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.QrTileAddResult
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One settings page. The page's [SettingsPageProvider] builds its rows and handles them; this ViewModel keeps what
 * outlives a page's rows (notification and background work state, privacy loading, the event queue) and hands it
 * over as the [SettingsPageScope].
 */
class SettingsViewModel(
    private val pages: SettingsPages,
    private val repository: SettingsRepository,
    private val widgetRefreshRequester: WidgetRefreshRequester,
    private val backgroundWork: BackgroundWorkAccess,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val page = SettingsPage.fromArgument(savedStateHandle[SettingsPage.ARGUMENT])
    private val provider = pages.forPage(page)

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
    private val localSettings = repository.observeLocalSettings()
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    private val pageState: Flow<SettingsPageState> = combine(
        localSettings,
        displayedSharingSettings,
        notificationPermissionGranted,
        customSpoilerConfigured,
        customSpoilerBusy,
        backgroundWorkUnrestricted
    ) { values ->
        SettingsPageState(
            local = values[0] as LocalSettings,
            sharing = values[1] as SharingSettingsState,
            notificationsGranted = values[2] as Boolean?,
            hasCustomSpoiler = values[3] as Boolean,
            imageBusy = values[4] as Boolean,
            backgroundWorkRestricted = values[5] == false
        )
    }

    // Do not render made-up defaults while DataStore is loading: the initial state is not loaded and has no rows,
    // and the first loaded state already carries the persisted values. Otherwise every persisted `true` appears as
    // an off -> on transition when Settings opens.
    val uiState: StateFlow<SettingsUiState> = provider.observeState(page, pageState)
        .map { state ->
            SettingsUiState(
                page = page,
                sections = provider.sections(page, state),
                loaded = true,
                previewSettings = provider.preview(page, state.local)
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(page))

    private val eventQueue = EventQueue<SettingsEvent>()
    val events: Flow<SettingsEvent> = eventQueue.events

    private val scope = PageScope()

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
        pages.forRow(id)?.onToggleChanged(scope, id, checked)
    }

    fun onChoiceChanged(id: SettingRowId, optionKey: String) {
        pages.forRow(id)?.onChoiceChanged(scope, id, optionKey)
    }

    fun onAction(id: SettingRowId) {
        pages.forRow(id)?.onAction(scope, id)
    }

    /** The calendar permission is there: synchronization turns on into the app's own calendar. */
    fun onCalendarAccessGranted() {
        pages.schedule.onCalendarAccessGranted(scope)
    }

    /** The system's answer to the add request: the row leaves once the tile is known to be in the quick settings. */
    fun onQrTileResult(result: QrTileAddResult) {
        pages.widgets.onQrTileResult(scope, result)
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

    private inner class PageScope : SettingsPageScope {

        override val currentItems: List<SettingItem>
            get() = uiState.value.sections.flatMap(SettingSection::items)

        override val privacyUpdateInProgress: Boolean
            get() = this@SettingsViewModel.privacyUpdateInProgress.value

        /** The queue is buffered, so the undispatched send completes at once; it only waits when 64 are undelivered. */
        override fun send(event: SettingsEvent) {
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { eventQueue.send(event) }
        }

        override fun launch(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }

        override suspend fun emit(event: SettingsEvent) {
            eventQueue.send(event)
        }

        override fun updateLocalSetting(refreshWidgets: Boolean, action: suspend () -> Unit) {
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

        override fun updateBackgroundCheck(checked: Boolean, store: suspend () -> Unit) {
            updateLocalSetting(action = store)
            if (!checked) return
            if (notificationPermissionGranted.value == false) send(SettingsEvent.RequestNotificationPermission)
            offerBackgroundWorkHint()
        }

        override fun updateSharing(action: suspend () -> AppResult<Unit>) {
            this@SettingsViewModel.privacyUpdateInProgress.value = true
            viewModelScope.launch {
                try {
                    when (val result = action()) {
                        is AppResult.Success -> Unit
                        is AppResult.Failure -> eventQueue.send(SettingsEvent.ShowError(result.error))
                    }
                } finally {
                    this@SettingsViewModel.privacyUpdateInProgress.value = false
                }
            }
        }

        override fun refreshPrivacy() {
            viewModelScope.launch { refreshPrivacySettings() }
        }
    }

    private companion object {
        const val MIN_PRIVACY_LOADING_MS = 300L
        const val BACKGROUND_WORK_RECHECK_MS = 1_000L
    }
}
