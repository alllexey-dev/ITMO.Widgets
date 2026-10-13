package dev.alllexey.itmowidgets.feature.sport.presentation.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.errorOrNull
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.schedule.DayLessonsGateway
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportRecurringQueueRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportRecurringRule
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportLessonActions
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportLessonSheetPresenter
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportLessonSheetUi
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingConditions
import dev.alllexey.itmowidgets.feature.sport.presentation.common.toDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportAutoSignFlow
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportBookingDelegate
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

/**
 * «Мои секции» on both platforms: the sections pinned on this device with their next lessons from the catalog and
 * their actions, the queue limit, and «Очередь через неделю» through [SportRecurringQueueRepository], shown only while
 * it is [SportRecurringQueueRepository.available].
 */
class SportSectionsViewModel(
    private val scheduleRepository: SportScheduleRepository,
    private val dataRepository: SportDataRepository,
    private val preferences: SportSignPreferencesRepository,
    private val recurring: SportRecurringQueueRepository,
    timetable: DayLessonsGateway,
    private val time: AcademicTimeProvider,
    delegate: SportBookingDelegate,
    autoSignFlow: SportAutoSignFlow,
) : ViewModel() {

    private val actions = SportLessonActions(viewModelScope, delegate, autoSignFlow, time)
    private val refreshes = RefreshTracker(viewModelScope)
    private val pickerOpen = MutableStateFlow(false)
    private val noticeQueue = EventQueue<UiText>()
    private var lessons: List<SportLesson> = emptyList()
    private var pinned: Set<SectionName> = emptySet()

    private val rules: Flow<List<SportRecurringRule>?> =
        if (recurring.available) recurring.observeRules() else flowOf(null)

    private val inputs = combine(
        scheduleRepository.observeSportSchedule().map<LoadState<List<SportLesson>>, LoadState<List<SportLesson>>?> { it }.onStart { emit(null) },
        preferences.observePinnedSections(),
        dataRepository.observeSportAutoSignLimits().map { it.valueOrNull() },
        rules.onStart { emit(null) },
        timetable.observe(time.today(), time.today().plus(TIMETABLE_WEEKS, DateTimeUnit.WEEK)).onStart { emit(emptyList()) },
    ) { schedule, pinned, limits, rules, days -> Inputs(schedule, pinned, limits, rules, days) }

    val uiState: StateFlow<SportSectionsUiState> = combine(
        inputs,
        actions.busy,
        actions.prompt,
        pickerOpen,
        refreshes.refreshing,
    ) { inputs, busy, prompt, picker, refreshing -> state(inputs, busy, refreshing).copy(prompt = prompt, picker = picker(inputs, picker)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SportSectionsUiState())

    val events: Flow<UiText> = merge(noticeQueue.events, actions.notices)

    init {
        refresh(RefreshMode.Silent)
    }

    fun refresh(mode: RefreshMode) {
        refreshes.launch(mode) {
            coroutineScope {
                awaitAll(
                    async { scheduleRepository.refreshSportSchedule() },
                    async { dataRepository.refreshSportAutoSignLimits() },
                    async { dataRepository.refreshSportQueueEntries() },
                    async { dataRepository.refreshSportQueues() },
                )
            }
        }
    }

    fun openPicker() {
        pickerOpen.value = true
    }

    fun closePicker() {
        pickerOpen.value = false
    }

    /** Pins or unpins the section My ITMO calls [name]. */
    fun togglePinned(name: String) {
        val section = SectionName(name)
        viewModelScope.launch { preferences.setPinned(section, section !in pinned) }
    }

    /** The row's own button on a next lesson. */
    fun act(lessonId: Long) {
        val lesson = lesson(lessonId) ?: return
        val action = lesson.bookingConditions().evaluate(time.now()).action
        if (action == SportBookingAction.SIGN || action == SportBookingAction.AUTO) actions.run(lesson, action)
    }

    fun details(lessonId: Long): SportCommonDetailsArgs? = lesson(lessonId)?.toDetailsArgs()

    fun onSheetAction(lessonId: Long, action: SportBookingAction) {
        lesson(lessonId)?.let { actions.runOffered(it, action) }
    }

    /** The lesson sheet of [details] now, as the iOS host draws it; [submitted] once it has sent its action. */
    fun sheet(details: SportCommonDetailsArgs, submitted: Boolean): SportLessonSheetUi =
        SportLessonSheetPresenter.present(details, time.now(), time.timeZone, busy = isBusy(details.lessonId), submitted = submitted)

    /** Whether a request for [lessonId] is in flight: its sheet opens with the action disabled. */
    fun isBusy(lessonId: Long): Boolean = lessonId in actions.busy.value

    fun confirmPrompt(lateSign: Boolean) = actions.confirm(lateSign)

    fun dismissPrompt() = actions.dismiss()

    /** «Очередь через неделю» of the section [name]: on for the slot of its next lesson, or off. */
    fun toggleRepeat(name: String) {
        val card = (uiState.value.body as? SportSectionsBody.Content)?.sections?.firstOrNull { it.name == name } ?: return
        val repeat = card.repeat ?: return
        val lesson = lessons.filter { it.sectionName.raw == name && it.isLessonReal && it.end > time.now() }.minByOrNull { it.start } ?: return
        val rule = SportSectionsPresenter.rule(lesson, time.timeZone)
        viewModelScope.launch {
            val result = if (repeat.on) recurring.disable(rule) else recurring.enable(rule)
            if (result is AppResult.Failure) noticeQueue.send(result.error.toUiText())
        }
    }

    private fun lesson(lessonId: Long): SportLesson? = lessons.firstOrNull { it.lessonId == lessonId && it.end > time.now() }

    private fun state(inputs: Inputs, busy: Set<Long>, refreshing: Boolean): SportSectionsUiState {
        inputs.schedule?.valueOrNull()?.let { lessons = it }
        pinned = inputs.pinned
        val input = SportSectionsInput(
            lessons = lessons,
            pinned = inputs.pinned,
            limits = inputs.limits,
            rules = inputs.rules,
            timetable = inputs.days,
            busy = busy,
            now = time.now(),
            today = time.today(),
            zone = time.timeZone,
        )
        val error = inputs.schedule?.errorOrNull()
        val body = when {
            inputs.pinned.isEmpty() -> SportSectionsBody.Empty
            inputs.schedule == null || (inputs.schedule == LoadState.Loading && lessons.isEmpty()) -> SportSectionsBody.Loading
            error != null && lessons.isEmpty() -> SportSectionsBody.Failed(error)
            else -> SportSectionsBody.Content(
                sections = SportSectionsPresenter.cards(input),
                note = UiText.Res(SportStrings.sport_sections_note).takeIf { inputs.rules != null },
                refreshing = refreshing,
            )
        }
        return SportSectionsUiState(
            subtitle = SportSectionsPresenter.subtitle(inputs.limits),
            body = body,
            limitGate = SportSectionsPresenter.limitGate(input),
        )
    }

    private fun picker(inputs: Inputs, open: Boolean) = if (open) SportSectionsPresenter.picker(lessons, inputs.pinned) else null

    private class Inputs(
        val schedule: LoadState<List<SportLesson>>?,
        val pinned: Set<SectionName>,
        val limits: SportAutoSignLimits?,
        val rules: List<SportRecurringRule>?,
        val days: List<LessonDay>,
    )

    private companion object {
        /** The timetable a repeat's dates are checked against: six weeks ahead. */
        const val TIMETABLE_WEEKS = 7
    }
}
