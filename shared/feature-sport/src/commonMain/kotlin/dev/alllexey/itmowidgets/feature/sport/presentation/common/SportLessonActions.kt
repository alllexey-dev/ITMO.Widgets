package dev.alllexey.itmowidgets.feature.sport.presentation.common

import dev.alllexey.itmowidgets.core.presentation.BusyKeys
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.buildingShortTitle
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportAutoSignDecision
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportAutoSignFlow
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportBookingDelegate
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignCommand
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/**
 * A question a sport screen asks before a queue changes, or the reason it cannot: one at a time, the screen shows it
 * as a sheet or an alert and answers through [SportLessonActions.confirm] or [SportLessonActions.dismiss].
 */
sealed interface SportPrompt {

    /**
     * `Встать в очередь?` for [lessonId]: [what] (`Фитнес, Чайковского`) and [whenText], the three [steps] of a queue,
     * the late-sign switch of a real lesson ([lateSign] with its [lateSignHint]), the queues left ([limit]).
     */
    data class Join(
        val lessonId: Long,
        val what: UiText,
        val whenText: UiText,
        val steps: List<UiText>,
        val lateSign: Boolean,
        val lateSignHint: UiText?,
        val limit: UiText?,
        val command: SportSignCommand,
    ) : SportPrompt

    /** `Выйти из очереди?` with the place in it. */
    data class Leave(val message: UiText, val command: SportSignCommand) : SportPrompt

    /** Why a queue cannot be joined now (no connection, the day or the limit is taken). */
    data class Info(val message: UiText) : SportPrompt
}

/**
 * The booking actions of a catalog lesson on every sport screen: sign in, sign out, join or leave a queue. One per
 * screen, in its ViewModel's scope. A [busy] lesson ignores a second tap; a queue asks first ([prompt]); [notices] say
 * what happened (`Записали`, an error). A debug template lesson (a negative id) reaches no server.
 */
class SportLessonActions(
    private val scope: CoroutineScope,
    private val delegate: SportBookingDelegate,
    private val autoSignFlow: SportAutoSignFlow,
    private val time: AcademicTimeProvider,
) {
    private val busyLessons = BusyKeys<Long>(scope)
    private val commands = BusyKeys<Unit>(scope)
    private val mutablePrompt = MutableStateFlow<SportPrompt?>(null)
    private val noticeQueue = EventQueue<UiText>()
    private var decision: Job? = null

    val busy: StateFlow<Set<Long>> = busyLessons.busy
    val prompt: StateFlow<SportPrompt?> = mutablePrompt.asStateFlow()
    val notices: Flow<UiText> = noticeQueue.events

    /** Runs what [lesson] offers as [action]; a queue opens its [prompt] first. */
    fun run(lesson: SportLesson, action: SportBookingAction) {
        if (action == SportBookingAction.NONE) return
        if (lesson.lessonId < 0) {
            scope.launch { noticeQueue.send(UiText.Res(SportStrings.debug_sport_lesson_action_disabled)) }
            return
        }
        when (action) {
            SportBookingAction.SIGN -> book(lesson.lessonId, SportStrings.sport_sign_success) { delegate.signIn(lesson) }
            SportBookingAction.CANCEL -> book(lesson.lessonId, SportStrings.sport_unsign_success) { delegate.signOut(lesson) }
            SportBookingAction.AUTO, SportBookingAction.CANCEL_AUTO -> ask(lesson)
            SportBookingAction.NONE -> Unit
        }
    }

    /**
     * The lesson sheet asked for [action] on [lesson]: it runs only while the lesson still offers it now; an offer that
     * changed meanwhile (the lesson started, the queue closed) says the lesson is unavailable.
     */
    fun runOffered(lesson: SportLesson, action: SportBookingAction) {
        if (lesson.lessonId in busyLessons) return
        val offered = lesson.bookingConditions().evaluate(time.now()).action
        if (offered != action) {
            scope.launch { noticeQueue.send(UiText.Res(SportStrings.sport_lesson_gone)) }
            return
        }
        run(lesson, offered)
    }

    /** Answers the open [prompt]: joins with [lateSign] or leaves; an [SportPrompt.Info] only closes. */
    fun confirm(lateSign: Boolean = false) {
        val command = when (val open = mutablePrompt.value) {
            is SportPrompt.Join -> open.command
            is SportPrompt.Leave -> open.command
            is SportPrompt.Info, null -> null
        }
        mutablePrompt.value = null
        command ?: return
        commands.launch(Unit) {
            when (val result = autoSignFlow.execute(command, lateSign)) {
                is AppResult.Success -> noticeQueue.send(UiText.Res(command.successKey()))
                is AppResult.Failure -> noticeQueue.send(result.error.toUiText())
            }
        }
    }

    fun dismiss() {
        decision?.cancel()
        mutablePrompt.value = null
    }

    private fun book(lessonId: Long, success: String, action: suspend () -> AppResult<Unit>) {
        busyLessons.launch(lessonId) {
            when (val result = action()) {
                is AppResult.Success -> noticeQueue.send(UiText.Res(success))
                is AppResult.Failure -> noticeQueue.send(result.error.toUiText())
            }
        }
    }

    /** A newer tap replaces a decision still waiting for the queue limits. */
    private fun ask(lesson: SportLesson) {
        decision?.cancel()
        decision = scope.launch {
            when (val outcome = autoSignFlow.onClick(lesson)) {
                is SportAutoSignDecision.Failed -> noticeQueue.send(outcome.error.toUiText())
                else -> mutablePrompt.value = prompt(lesson, outcome)
            }
        }
    }

    private fun prompt(lesson: SportLesson, decision: SportAutoSignDecision): SportPrompt? = when (decision) {
        SportAutoSignDecision.ServicesDisabled -> SportPrompt.Info(UiText.Res(SportStrings.sport_community_services_disabled))
        is SportAutoSignDecision.LeaveQueue -> SportPrompt.Leave(
            UiText.Res(SportStrings.sport_queue_leave_message, listOf(decision.position, decision.total)),
            decision.command,
        )
        is SportAutoSignDecision.ConfirmFreeSign -> join(lesson, decision.command, predicted = false, limit = null)
        is SportAutoSignDecision.ConfirmAutoSign -> join(
            lesson,
            decision.command,
            predicted = true,
            limit = UiText.Res(SportStrings.sport_queue_limit_left, listOf(decision.available, decision.limit)),
        )
        is SportAutoSignDecision.DayTaken ->
            SportPrompt.Info(UiText.Res(SportStrings.sport_auto_sign_existing_day, listOf(decision.section, decision.teacher)))
        is SportAutoSignDecision.LimitReached ->
            SportPrompt.Info(UiText.Res(SportStrings.sport_auto_sign_limit_reached, listOf(decision.nextAvailable)))
        is SportAutoSignDecision.Failed -> null
    }

    private fun join(lesson: SportLesson, command: SportSignCommand, predicted: Boolean, limit: UiText?): SportPrompt.Join {
        val start = lesson.start.toLocalDateTime(time.timeZone)
        val end = lesson.end.toLocalDateTime(time.timeZone)
        val closes = (lesson.start - FREE_QUEUE_CLOSES_BEFORE).toLocalDateTime(time.timeZone).time.format(DateTexts.TIME)
        val startTime = start.time.format(DateTexts.TIME)
        val place = lesson.place()
        val what = listOfNotNull(UiText.Dynamic(lesson.sectionName.raw.trim()), place.building?.let { buildingShortTitle(it) })
        val steps = if (predicted) {
            listOf(
                UiText.Res(SportStrings.sport_join_future_step_1),
                UiText.Res(SportStrings.sport_join_future_step_2),
                UiText.Res(SportStrings.sport_join_future_step_3),
            )
        } else {
            listOf(
                lesson.occupancy()?.let { UiText.Res(SportStrings.sport_join_full_step_1, listOf(it.occupied, it.limit)) }
                    ?: UiText.Res(SportStrings.sport_join_full_step_1_plain),
                UiText.Res(SportStrings.sport_join_full_step_2),
                UiText.Res(SportStrings.sport_join_full_step_3, listOf(closes)),
            )
        }
        return SportPrompt.Join(
            lessonId = lesson.lessonId,
            what = UiText.Joined(what, ", "),
            whenText = UiText.Dynamic(
                "${start.date.format(DateTexts.SHORT_WEEKDAY_DAY_SHORT_MONTH)}, $startTime–${end.time.format(DateTexts.TIME)}",
            ),
            steps = steps,
            lateSign = !predicted,
            lateSignHint = if (predicted) null else UiText.Res(SportStrings.sport_join_late_hint, listOf(startTime, closes)),
            limit = limit,
            command = command,
        )
    }

    private fun SportLesson.occupancy(): SportOccupancy? = SportOccupancy.from(isLessonReal, available, limit)

    private fun SportSignCommand.successKey(): String = when (this) {
        is SportSignCommand.CreateFreeSign, is SportSignCommand.CreateAutoSign -> SportStrings.sport_queue_joined
        is SportSignCommand.CancelFreeSign, is SportSignCommand.CancelAutoSign -> SportStrings.sport_queue_left
    }

    companion object {
        /** The free queue closes an hour before the start unless the late sign is on. */
        val FREE_QUEUE_CLOSES_BEFORE = 1.hours
    }
}
