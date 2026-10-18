package dev.alllexey.itmowidgets.feature.onboarding.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.lesson.DayMoment
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonState
import dev.alllexey.itmowidgets.core.lesson.LessonTimelines
import dev.alllexey.itmowidgets.core.lesson.TimelineItem
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.DayLessonsGateway
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.personInitials
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

/**
 * The first-run flow (DECISIONS.md, Первый запуск): three steps, no `Включить всё`. The first shows the user's own
 * data (today's lesson, the sport score) as it loads; the second asks each permission with its own button, and
 * `Далее` works without any; the third connects to ITMO.Widgets through the consent sheet or leaves it with
 * `Не сейчас`. Every choice is written through its own repository the moment it is made, so leaving at any step
 * leaves a consistent app. The step lives in the saved state: process death returns to the same page.
 */
class OnboardingViewModel(
    private val onboardingRepository: OnboardingRepository,
    private val customServicesRepository: CustomServicesRepository,
    private val sessionRepository: SessionRepository,
    private val days: DayLessonsGateway,
    private val sportScores: SportScoreRepository,
    private val calendarSync: CalendarSync,
    private val time: AcademicTimeProvider,
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
                val me = (session as? SessionState.SignedIn)?.user?.let { user ->
                    user.name?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
                        OnboardingMe(name, personInitials(name, user.familyName, user.givenName), user.pictureUrl)
                    }
                }
                mutableState.update { it.copy(me = me) }
            }
            .launchIn(viewModelScope)
        if (capabilities.calendarExport) {
            calendarSync.observeState()
                .onEach { calendar -> mutableState.update { it.copy(calendarSynced = calendar.enabled) } }
                .launchIn(viewModelScope)
        }
        val today = time.today()
        days.observe(today, today.plus(DatePeriod(days = 1)))
            .onEach { loaded -> mutableState.update { it.copy(lesson = lessonOf(loaded)) } }
            .launchIn(viewModelScope)
        viewModelScope.launch { days.refresh(today, today.plus(DatePeriod(days = 1))) }
        viewModelScope.launch { loadSport() }
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

    private suspend fun loadSport() {
        val periods = sportScores.getScorePeriods() as? AppResult.Success ?: return
        val current = periods.value.firstOrNull { it.current } ?: return
        val summary = sportScores.getScoreSummary(current.id) as? AppResult.Success ?: return
        mutableState.update {
            it.copy(sport = UiText.Res(AccountStrings.onboarding_value_sport_score, listOf(summary.value.totalCapped)))
        }
    }

    /** Today's lesson now or next by the timeline's rules, else tomorrow's first. */
    private fun lessonOf(loaded: List<LessonDay>): OnboardingLesson? {
        val today = time.today()
        val now = time.localNow()
        val todays = loaded.firstOrNull { it.date == today }?.lessons.orEmpty()
        LessonTimelines.build(todays, DayMoment.Today(now.time), today)
            .filterIsInstance<TimelineItem.Lesson>()
            .firstOrNull { it.lesson.state == LessonState.NOW || it.lesson.state == LessonState.NEXT }
            ?.let { item ->
                val title = if (item.lesson.state == LessonState.NOW) AccountStrings.onboarding_value_now else AccountStrings.onboarding_value_next
                return OnboardingLesson(UiText.Res(title), item.lesson)
            }
        val tomorrow = today.plus(DatePeriod(days = 1))
        val first = loaded.firstOrNull { it.date == tomorrow }?.lessons?.minByOrNull { it.start } ?: return null
        return OnboardingLesson(UiText.Res(AccountStrings.onboarding_value_tomorrow), LessonTimelines.lesson(first, LessonState.UPCOMING))
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
        return OnboardingStep.entries.firstOrNull { it.name == stored } ?: OnboardingStep.VALUE
    }

    private companion object {
        const val KEY_STEP = "onboarding_step"
    }
}
