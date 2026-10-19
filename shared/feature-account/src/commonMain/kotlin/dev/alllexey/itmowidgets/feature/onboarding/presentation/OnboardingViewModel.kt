package dev.alllexey.itmowidgets.feature.onboarding.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.text.personInitials
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The first-run flow (owner, 2026-10-19): two steps, no `Включить всё`. The first shows who signed in and asks each
 * permission with its own button, and `Далее` works without any; the second connects to ITMO.Widgets through the
 * consent sheet or leaves it with `Не сейчас`. Every choice is written through its own repository the moment it is
 * made, so leaving at any step leaves a consistent app. The step lives in the saved state: process death returns to the same page.
 */
class OnboardingViewModel(
    private val onboardingRepository: OnboardingRepository,
    private val customServicesRepository: CustomServicesRepository,
    private val sessionRepository: SessionRepository,
    private val calendarSync: CalendarSync,
    capabilities: PlatformCapabilities,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        OnboardingUiState(step = restoredStep(), calendarSynced = false.takeIf { capabilities.calendarExport }),
    )
    val uiState: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    private val eventQueue = EventQueue<OnboardingEvent>()
    val events: Flow<OnboardingEvent> = eventQueue.events

    init {
        customServicesRepository.observeEnabled()
            .onEach { enabled -> mutableState.update { it.copy(servicesEnabled = enabled) } }
            .launchIn(viewModelScope)
        sessionRepository.state
            .onEach { session ->
                mutableState.update { it.copy(me = (session as? SessionState.SignedIn)?.user?.let(::meOf)) }
            }
            .launchIn(viewModelScope)
        if (capabilities.calendarExport) {
            calendarSync.observeState()
                .onEach { calendar -> mutableState.update { it.copy(calendarSynced = calendar.enabled) } }
                .launchIn(viewModelScope)
        }
    }

    /** The footer's primary action: one step forward, or the end of the flow on its last step. */
    fun next() {
        val current = mutableState.value
        val following = OnboardingStep.entries.getOrNull(current.stepIndex + 1)
        if (following == null) complete() else moveTo(following)
    }

    fun back() {
        val previous = OnboardingStep.entries.getOrNull(mutableState.value.stepIndex - 1) ?: return
        moveTo(previous)
    }

    /**
     * Granted: the system page is where the types live. Denied once: Android will not ask again, so the second tap
     * has to reach the same page.
     */
    fun requestNotifications() {
        val current = mutableState.value
        if (current.notificationsGranted || current.notificationsAsked) {
            send(OnboardingEvent.OpenNotificationSettings)
            return
        }
        mutableState.update { it.copy(notificationsAsked = true) }
        send(OnboardingEvent.RequestNotificationPermission)
    }

    fun onNotificationPermission(granted: Boolean) {
        mutableState.update { it.copy(notificationsGranted = granted) }
    }

    /** `Добавить пары`: the host asks for the calendar, then calls [onCalendarAccessGranted]. */
    fun addLessonsToCalendar() {
        if (mutableState.value.calendarSynced != false) return
        send(OnboardingEvent.RequestCalendarAccess)
    }

    fun onCalendarAccessGranted() {
        viewModelScope.launch {
            when (calendarSync.enable()) {
                CalendarSyncResult.DONE, CalendarSyncResult.NO_PERMISSION -> Unit
                CalendarSyncResult.FAILED -> eventQueue.send(OnboardingEvent.ShowError(AppError.Unknown()))
                CalendarSyncResult.DEMO_UNAVAILABLE -> eventQueue.send(OnboardingEvent.ShowError(AppError.DemoUnavailable))
            }
        }
    }

    /** `Подключить` of the last step: the consent sheet first; already connected, the flow ends. */
    fun connect() {
        if (mutableState.value.servicesEnabled) complete() else send(OnboardingEvent.ShowConsent)
    }

    /** The consent sheet's `Подключить`: the opt-in turns on and the flow ends. */
    fun onConsent() {
        if (mutableState.value.servicesBusy) return
        mutableState.update { it.copy(servicesBusy = true) }
        viewModelScope.launch {
            try {
                customServicesRepository.setEnabled(true)
                complete()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                eventQueue.send(OnboardingEvent.ShowError(AppError.Unknown(error)))
            } finally {
                mutableState.update { it.copy(servicesBusy = false) }
            }
        }
    }

    /** `Не сейчас`: leaving the connection off is a valid answer. */
    fun notNow() = complete()

    /** The full name when the token has its parts (`Фамилия Имя Отчество`), else its `name`. */
    private fun meOf(user: CurrentUser): OnboardingMe? {
        val name = if (user.familyName.isNullOrBlank() || user.givenName.isNullOrBlank()) {
            user.name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        } else {
            listOfNotNull(user.familyName, user.givenName, user.middleName?.takeIf { it.isNotBlank() }).joinToString(" ") { it.trim() }
        }
        return OnboardingMe(name, personInitials(name, user.familyName, user.givenName), user.pictureUrl, user.isu)
    }

    private fun send(event: OnboardingEvent) {
        viewModelScope.launch { eventQueue.send(event) }
    }

    private fun complete() {
        if (mutableState.value.finished) return
        mutableState.update { it.copy(finished = true) }
        viewModelScope.launch { onboardingRepository.complete() }
    }

    private fun moveTo(step: OnboardingStep) {
        savedStateHandle[KEY_STEP] = step.name
        mutableState.update { it.copy(step = step) }
    }

    private fun restoredStep(): OnboardingStep {
        val stored = savedStateHandle.get<String>(KEY_STEP)
        return OnboardingStep.entries.firstOrNull { it.name == stored } ?: OnboardingStep.PERMISSIONS
    }

    private companion object {
        const val KEY_STEP = "onboarding_step"
    }
}
