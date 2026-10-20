package dev.alllexey.itmowidgets.feature.settings.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.settings.WidgetFormat
import dev.alllexey.itmowidgets.feature.settings.domain.QrTileAddResult
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One settings page. The page's [SettingsPageProvider] builds its rows and handles them; this ViewModel keeps what
 * outlives a page's rows (the notification permission the root shows, the custom spoiler state, the event queue)
 * and hands it over as the [SettingsPageScope]. «Сервер ITMO.Widgets», «Уведомления» and «Оформление» have view
 * models of their own (`ConnectionViewModel`, `NotificationSettingsViewModel`, `AppearanceViewModel`).
 */
class SettingsViewModel(
    private val pages: SettingsPages,
    private val repository: SettingsRepository,
    private val widgetRefreshRequester: WidgetRefreshRequester,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val page = SettingsPage.fromArgument(savedStateHandle[SettingsPage.ARGUMENT])
    private val provider = pages.forPage(page)

    private val notificationPermissionGranted = MutableStateFlow<Boolean?>(null)
    private val placedWidgets = MutableStateFlow<Map<WidgetFormat, Int>>(emptyMap())
    private val customSpoilerConfigured = MutableStateFlow(false)
    private val customSpoilerBusy = MutableStateFlow(false)
    private val localSettings = repository.observeLocalSettings()
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    private val pageState: Flow<SettingsPageState> = combine(
        localSettings,
        notificationPermissionGranted,
        customSpoilerConfigured,
        customSpoilerBusy,
        placedWidgets,
    ) { local, notificationsGranted, hasCustomSpoiler, imageBusy, placed ->
        SettingsPageState(
            local = local,
            notificationsGranted = notificationsGranted,
            hasCustomSpoiler = hasCustomSpoiler,
            imageBusy = imageBusy,
            placedWidgets = placed,
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
                gallery = provider.gallery(page, state)
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(page))

    private val eventQueue = EventQueue<SettingsEvent>()
    val events: Flow<SettingsEvent> = eventQueue.events

    private val scope = PageScope()

    fun onNotificationPermissionChanged(granted: Boolean) {
        notificationPermissionGranted.value = granted
    }

    /** The home screen holds [count] widgets of [format]: the gallery counts them each time it is shown. */
    fun onPlacedWidgets(format: WidgetFormat, count: Int) {
        placedWidgets.value = placedWidgets.value + (format to count)
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
        pages.sections.onCalendarAccessGranted(scope)
    }

    /** The system's answer to the add request: the row leaves once the tile is known to be in the quick settings. */
    fun onQrTileResult(result: QrTileAddResult) {
        pages.widgets.onQrTileResult(scope, result)
    }

    private inner class PageScope : SettingsPageScope {

        override val currentItems: List<SettingItem>
            get() = uiState.value.sections.flatMap(SettingSection::items)

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
    }
}
