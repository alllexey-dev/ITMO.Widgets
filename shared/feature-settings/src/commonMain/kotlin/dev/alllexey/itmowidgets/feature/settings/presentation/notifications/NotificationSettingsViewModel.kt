package dev.alllexey.itmowidgets.feature.settings.presentation.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
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
 * «Уведомления», the one home of notification settings (DECISIONS.md). Every type has its switch here: the local checks
 * (marks, schedule changes) start and stop the check itself, the pushes of ITMO.Widgets are dropped on the device while
 * off (`FcmPayloadDispatcher`, the iOS notification service). Turning a type on without the permission asks for it;
 * turning a check on while the system restricts the app in the background offers the once-per-device hint.
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
        val pushes = PushKind.entries.map { PushNotifications(it, it.channel !in stored.disabledPushes) }
        val wanted = listOfNotNull(
            AppNotificationChannels.MARKS.takeIf { marks?.enabled == true },
            AppNotificationChannels.SCHEDULE_CHANGES.takeIf { stored.scheduleChangesEnabled },
        ) + pushes.filter { it.enabled && stored.customServicesEnabled }.map { it.kind.channel }
        NotificationSettingsUiState(
            loaded = true,
            permission = granted,
            marks = marks,
            scheduleChanges = stored.scheduleChangesEnabled,
            pushes = pushes,
            connected = stored.customServicesEnabled,
            systemChannelsOff = granted != NotificationPermission.DENIED && wanted.any { channelStates?.get(it) == false },
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

    /** Android's channels by id (`AppNotificationChannels.ALL`), enabled or not; iOS has none per type. */
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

    /** A push needs no background work of the app, so no hint; the permission is still asked for. */
    fun setPush(kind: PushKind, enabled: Boolean) =
        update(enabled, offerHint = false) { settings.setPushEnabled(kind.channel, enabled) }

    fun openConnection() = send(NotificationSettingsEvent.OpenConnection)

    /** The permission gate's button. */
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

    private fun updateCheck(enabled: Boolean, store: suspend () -> Unit) = update(enabled, offerHint = true, store)

    /** Stores a switch; turning it on asks for notifications if they are off and, for a check, offers the hint once. */
    private fun update(enabled: Boolean, offerHint: Boolean, store: suspend () -> Unit) {
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
            if (offerHint) offerBackgroundWorkHint()
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
