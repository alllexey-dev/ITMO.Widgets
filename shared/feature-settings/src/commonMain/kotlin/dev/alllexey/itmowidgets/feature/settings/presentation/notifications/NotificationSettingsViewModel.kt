package dev.alllexey.itmowidgets.feature.settings.presentation.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * «Уведомления», the one home of notification settings (DECISIONS.md). The local checks (marks, schedule changes) are
 * switched here; turning one on without the permission asks for it, and while the system restricts the app in the
 * background the once-per-device hint is offered and a row leads to the system page. The pushes of ITMO.Widgets have
 * no switch of the app: the connection and the system decide them, so their rows lead there.
 *
 * The host reports what only it can read: [onPermissionChanged], [onChannelsChanged] (Android's per-channel switches)
 * and, on every return, [onBackgroundWorkChanged].
 */
class NotificationSettingsViewModel(
    private val settings: SettingsRepository,
    private val scheduleChanges: ScheduleChangeTracking,
    private val markTracking: MarkTracking,
    private val backgroundWork: BackgroundWorkAccess,
    private val capabilities: PlatformCapabilities,
) : ViewModel() {

    private val permission = MutableStateFlow(NotificationPermission.UNKNOWN)
    private val channels = MutableStateFlow<Map<String, Boolean>?>(null)
    // Unknown until the screen asks; the row stays hidden rather than flash in.
    private val backgroundUnrestricted = MutableStateFlow<Boolean?>(null)
    private var backgroundRecheck: Job? = null
    private val hintMutex = Mutex()
    private val eventQueue = EventQueue<NotificationSettingsEvent>()
    val events: Flow<NotificationSettingsEvent> = eventQueue.events

    private val local = settings.observeLocalSettings().shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val uiState: StateFlow<NotificationSettingsUiState> = combine(
        local,
        permission,
        channels,
        backgroundUnrestricted,
    ) { stored, granted, channelStates, unrestricted ->
        val marks = marksOf(stored)
        NotificationSettingsUiState(
            loaded = true,
            permission = granted,
            marks = marks,
            scheduleChanges = stored.scheduleChangesEnabled,
            pushes = PushKind.entries.map { kind ->
                PushNotifications(kind, stored.customServicesEnabled, channelStates?.get(kind.channel))
            },
            backgroundWorkRestricted = unrestricted == false && (stored.scheduleChangesEnabled || marks?.enabled == true),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NotificationSettingsUiState())

    fun onPermissionChanged(granted: Boolean) {
        permission.value = if (granted) NotificationPermission.ALLOWED else NotificationPermission.DENIED
    }

    /** iOS has never asked: not a denial, so no gate; the first check turned on asks. */
    fun onPermissionNotAsked() {
        permission.value = NotificationPermission.UNKNOWN
    }

    /** Android's channels by id, enabled or not; iOS has none per type. */
    fun onChannelsChanged(enabled: Map<String, Boolean>) {
        channels.value = enabled
    }

    /** Re-reads the background restriction; the screen calls it on every return. */
    fun onBackgroundWorkChanged() {
        backgroundUnrestricted.value = backgroundWork.isUnrestricted()
        backgroundRecheck?.cancel()
        // HyperOS saves «Нет ограничений» only once its page has gone, which is after this screen resumes.
        backgroundRecheck = viewModelScope.launch {
            delay(BACKGROUND_RECHECK_MS)
            backgroundUnrestricted.value = backgroundWork.isUnrestricted()
        }
    }

    /** `Баллы` as a whole: off stops every source, on starts My ITMO and the sheets, and BARS once it has answered. */
    fun setMarks(enabled: Boolean) = updateCheck(enabled) {
        markTracking.setMyItmoEnabled(enabled)
        if (local.first().barsMarksEnabled != null) markTracking.setBarsEnabled(enabled)
        markTracking.setSheetsEnabled(enabled)
    }

    fun setMarkSource(source: MarkCheckSource, enabled: Boolean) = updateCheck(enabled) {
        when (source) {
            MarkCheckSource.MY_ITMO -> markTracking.setMyItmoEnabled(enabled)
            MarkCheckSource.BARS -> markTracking.setBarsEnabled(enabled)
            MarkCheckSource.SHEETS -> markTracking.setSheetsEnabled(enabled)
        }
    }

    fun setScheduleChanges(enabled: Boolean) = updateCheck(enabled) { scheduleChanges.setEnabled(enabled) }

    /** A push row: its system channel where there is one, else the connection when it is off, else the app's page. */
    fun openPush(kind: PushKind) {
        val push = uiState.value.pushes.firstOrNull { it.kind == kind } ?: return
        send(
            when {
                !push.connected -> NotificationSettingsEvent.OpenConnection
                push.systemEnabled != null -> NotificationSettingsEvent.OpenChannelSettings(kind.channel)
                else -> NotificationSettingsEvent.OpenSystemSettings
            },
        )
    }

    /** The gate's button. */
    fun requestPermission() = send(NotificationSettingsEvent.RequestPermission)

    fun openSystemSettings() = send(NotificationSettingsEvent.OpenSystemSettings)

    fun openBackgroundWorkSettings() = send(NotificationSettingsEvent.OpenBackgroundWorkSettings)

    private fun marksOf(stored: LocalSettings): MarksNotifications? {
        if (!capabilities.marks) return null
        return MarksNotifications(
            listOfNotNull(
                MarkCheckSwitch(MarkCheckSource.MY_ITMO, stored.myItmoMarksEnabled),
                stored.barsMarksEnabled?.let { MarkCheckSwitch(MarkCheckSource.BARS, it) },
                MarkCheckSwitch(MarkCheckSource.SHEETS, stored.sheetMarksEnabled),
            ),
        )
    }

    /** Stores a check's switch; turning it on asks for notifications if they are off and offers the hint once. */
    private fun updateCheck(enabled: Boolean, store: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                store()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                eventQueue.send(NotificationSettingsEvent.ShowError(AppError.Unknown(error)))
                return@launch
            }
            if (!enabled) return@launch
            if (permission.value != NotificationPermission.ALLOWED) eventQueue.send(NotificationSettingsEvent.RequestPermission)
            offerBackgroundWorkHint()
        }
    }

    private suspend fun offerBackgroundWorkHint() {
        if (backgroundUnrestricted.value != false) return
        hintMutex.withLock {
            if (local.first().backgroundWorkHintShown) return
            try {
                settings.setBackgroundWorkHintShown()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                eventQueue.send(NotificationSettingsEvent.ShowError(AppError.Unknown(error)))
                return
            }
            eventQueue.send(NotificationSettingsEvent.ShowBackgroundWorkHint)
        }
    }

    private fun send(event: NotificationSettingsEvent) {
        viewModelScope.launch { eventQueue.send(event) }
    }

    private companion object {
        const val BACKGROUND_RECHECK_MS = 1_000L
    }
}
