package dev.alllexey.itmowidgets.feature.schedule.presentation.lesson

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.lesson.LessonPill
import dev.alllexey.itmowidgets.core.lesson.LessonPillKind
import dev.alllexey.itmowidgets.core.lesson.LessonSubject
import dev.alllexey.itmowidgets.core.lesson.LessonSubjectSource
import dev.alllexey.itmowidgets.core.lesson.LessonTimelines
import dev.alllexey.itmowidgets.core.lesson.isOnlinePlaceholder
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.reviews.tone
import dev.alllexey.itmowidgets.core.schedule.LessonOccurrence
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.schedule.lessonTypeName
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.changeLines
import dev.alllexey.itmowidgets.core.text.durationText
import dev.alllexey.itmowidgets.core.text.roomShortTitle
import dev.alllexey.itmowidgets.core.text.summary
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.schedule.ScheduleStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

/**
 * The lesson sheet over any lesson list (docs/features/schedule.md): the lesson it was opened with (`ARG_LESSON`, a
 * timetable lesson, or `ARG_SPORT`, an own sport queue), its newest found change, the friends on it and the tone of
 * its teacher's reviews (both behind the opt-in), and its subject from the [subjects] that know it. Re-evaluated every
 * minute for `идёт ещё 48 мин`.
 */
class LessonSheetViewModel(
    savedStateHandle: SavedStateHandle,
    private val friendsRepository: LessonFriendsRepository,
    private val customServices: CustomServicesRepository,
    private val teacherLevels: TeacherLevelsRepository,
    changesRepository: ScheduleChangesRepository,
    private val subjects: List<LessonSubjectSource>,
    private val buildings: BuildingDirectory,
    private val time: AcademicTimeProvider,
) : ViewModel() {

    private val lesson: LessonDetailsArgs? = savedStateHandle.get<String>(ARG_LESSON)?.let(LessonSheetArgs::lesson)
    private val sport: PendingSportDetailsArgs? = savedStateHandle.get<String>(ARG_SPORT)?.let(LessonSheetArgs::sport)
    private val sheet: Sheet = lesson?.let(::sheetOf) ?: sheetOf(checkNotNull(sport) { "The lesson sheet needs a lesson" })

    private val friends = MutableStateFlow<List<UserSummary>>(emptyList())
    private val level = MutableStateFlow<TeacherLevel?>(null)
    private val subject = MutableStateFlow<LessonSubject?>(null)

    private val change: Flow<ScheduleChange?> = lesson?.let { args ->
        val occurrence = LessonOccurrence(args.pairId, LocalDate.parse(args.date))
        changesRepository.observeChanges()
            .map { changes -> changes.filter { occurrence in it.occurrences() }.maxByOrNull(ScheduleChange::detectedAt) }
            .onStart { emit(null) }
    } ?: flowOf(null)

    private val clock: Flow<LocalDateTime> = flow {
        while (true) {
            emit(time.localNow())
            delay(1.minutes)
        }
    }

    val uiState: StateFlow<LessonSheetUiState> = combine(clock, change, friends, level, subject) { now, change, friends, level, subject ->
        state(now, change, friends, level, subject)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), state(time.localNow(), null, emptyList(), null, null))

    init {
        viewModelScope.launch { loadFriends() }
        viewModelScope.launch { loadLevel() }
        viewModelScope.launch { loadSubject() }
    }

    private suspend fun loadFriends() {
        val args = lesson ?: return
        if (!customServices.isEnabled()) return
        val found = friendsRepository.friendsOnLesson(args.pairId, LocalDate.parse(args.date))
        if (found is AppResult.Success) friends.value = found.value
    }

    private suspend fun loadLevel() {
        val isu = sheet.teacherIsu ?: return
        if (!customServices.isEnabled()) return
        level.value = teacherLevels.levels(setOf(isu))[isu]
    }

    private suspend fun loadSubject() {
        val args = lesson ?: return
        val id = args.subjectId ?: return
        subject.value = subjects.firstNotNullOfOrNull { it.subject(id, args.subjectName, sheet.date) }
    }

    private fun state(
        now: LocalDateTime,
        change: ScheduleChange?,
        friends: List<UserSummary>,
        level: TeacherLevel?,
        subject: LessonSubject?,
    ): LessonSheetUiState {
        val cancelled = change?.kind == ScheduleChangeKind.CANCELLED
        return LessonSheetUiState(
            over = UiText.Joined(
                listOf(lessonTypeName(sheet.typeId), UiText.Dynamic(sheet.date.format(DateTexts.SHORT_WEEKDAY_DAY_MONTH))),
                SEPARATOR,
            ),
            status = status(now, change != null, cancelled),
            subject = sheet.subject,
            room = sheet.room?.takeIf { !sheet.online }?.let(::roomShortTitle),
            online = sheet.online,
            time = UiText.Joined(
                listOf(
                    UiText.Dynamic("${sheet.start.format(DateTexts.TIME)}–${sheet.end.format(DateTexts.TIME)}"),
                    durationText(minutes(sheet.start, sheet.end)),
                ),
                SEPARATOR,
            ),
            address = sheet.address.takeIf { !sheet.online },
            actions = actions(friends, subject),
            changes = change?.let {
                LessonSheetChanges(
                    summary = if (it.kind == ScheduleChangeKind.UPDATED) null else it.summary(),
                    lines = it.changeLines(),
                    note = LessonTimelines.changeNote(it.detectedAt.toLocalDateTime(time.timeZone), now.date),
                )
            },
            note = sheet.note,
            password = lesson?.zoomPassword,
            teacher = sheet.teacher?.let { name ->
                LessonTeacher(
                    name = name,
                    isu = sheet.teacherIsu,
                    tone = level?.tone()?.words ?: UiText.Res(ScheduleStrings.schedule_sheet_teacher),
                )
            },
            flow = lesson?.flowName,
            sport = sportRow(),
            friends = friends,
        )
    }

    /** `отменена`, `изменена`, `очередь`, `идёт ещё 48 мин` or `через 2 ч 58 мин`; none on other days. */
    private fun status(now: LocalDateTime, changed: Boolean, cancelled: Boolean): LessonPill? = when {
        cancelled -> LessonPill(LessonPillKind.CANCELLED, UiText.Res(CoreStrings.lesson_pill_cancelled))
        changed -> LessonPill(LessonPillKind.CHANGED, UiText.Res(CoreStrings.lesson_pill_changed))
        sport != null -> LessonPill(LessonPillKind.QUEUE, UiText.Res(CoreStrings.lesson_pill_queue))
        now.date != sheet.date || now.time >= sheet.end -> null
        now.time >= sheet.start -> LessonPill(
            LessonPillKind.NOW,
            UiText.Res(ScheduleStrings.schedule_sheet_now, listOf(durationText(minutes(now.time, sheet.end)))),
        )
        else -> LessonPill(
            LessonPillKind.NEXT,
            UiText.Res(ScheduleStrings.schedule_sheet_soon, listOf(durationText(minutes(now.time, sheet.start)))),
        )
    }

    private fun actions(friends: List<UserSummary>, subject: LessonSubject?): List<LessonAction> = buildList {
        lesson?.zoomUrl?.let { add(LessonAction(LessonActionKind.VIDEO, UiText.Res(ScheduleStrings.schedule_sheet_video), url = it)) }
        if (friends.isNotEmpty()) {
            add(LessonAction(LessonActionKind.FRIENDS, UiText.Res(ScheduleStrings.schedule_sheet_friends, listOf(friends.size))))
        }
        if (subject != null) {
            if (subject.links > 0) {
                add(
                    LessonAction(
                        LessonActionKind.LINKS,
                        UiText.Res(ScheduleStrings.schedule_sheet_links, listOf(subject.links)),
                        route = subject.materials,
                    ),
                )
            }
            add(LessonAction(LessonActionKind.SUBJECT, UiText.Res(ScheduleStrings.schedule_sheet_subject), route = subject.page))
        }
    }

    private fun sportRow(): LessonSport? {
        sport?.let {
            val text = if (it.isPrediction) ScheduleStrings.schedule_sheet_queue_prediction else ScheduleStrings.schedule_sheet_queue_waiting
            return LessonSport(UiText.Res(ScheduleStrings.schedule_sheet_queue), UiText.Res(text))
        }
        if (sheet.typeId != SPORT_TYPE) return null
        return LessonSport(UiText.Res(ScheduleStrings.schedule_sheet_booked), UiText.Res(ScheduleStrings.schedule_sheet_booked_text))
    }

    private fun sheetOf(args: LessonDetailsArgs): Sheet {
        val online = isOnlinePlaceholder(args.format) || isOnlinePlaceholder(args.room) || isOnlinePlaceholder(args.building)
        return Sheet(
            date = LocalDate.parse(args.date),
            start = LocalTime.parse(args.start),
            end = LocalTime.parse(args.end),
            subject = args.subjectName.trim(),
            typeId = args.typeId,
            room = args.room?.trim()?.takeIf(String::isNotEmpty),
            address = buildings.find(args.buildingId, args.mainBuildingId, args.building)?.address
                ?: args.building?.trim()?.takeIf(String::isNotEmpty),
            online = online,
            note = args.note?.trim()?.takeIf { it.isNotEmpty() && !(online && isOnlinePlaceholder(it)) },
            teacher = args.teacherFio?.trim()?.takeIf(String::isNotEmpty),
            teacherIsu = UserScreenArgs.profileIsu(args.teacherIsu),
        )
    }

    private fun sheetOf(args: PendingSportDetailsArgs): Sheet {
        val start = DateTexts.parseOffsetInstant(args.start).toLocalDateTime(time.timeZone)
        val end = DateTexts.parseOffsetInstant(args.end).toLocalDateTime(time.timeZone)
        return Sheet(
            date = start.date,
            start = start.time,
            end = end.time,
            subject = args.sectionName.trim(),
            typeId = SPORT_TYPE,
            room = args.roomName.trim().takeIf(String::isNotEmpty),
            address = null,
            online = false,
            note = null,
            teacher = args.teacherFio.trim().takeIf(String::isNotEmpty),
            teacherIsu = UserScreenArgs.profileIsu(args.teacherIsu?.toLong()),
        )
    }

    private fun minutes(from: LocalTime, to: LocalTime): Int = (to.toSecondOfDay() - from.toSecondOfDay()) / SECONDS_PER_MINUTE

    /** The lesson as the sheet reads it, whichever arguments it came with. */
    private data class Sheet(
        val date: LocalDate,
        val start: LocalTime,
        val end: LocalTime,
        val subject: String,
        val typeId: Int,
        val room: String?,
        val address: String?,
        val online: Boolean,
        val note: String?,
        val teacher: String?,
        val teacherIsu: Int?,
    )

    companion object {
        const val ARG_LESSON = "lesson_sheet_lesson"
        const val ARG_SPORT = "lesson_sheet_sport"
        private const val SPORT_TYPE = 11
        private const val SECONDS_PER_MINUTE = 60
        private const val SEPARATOR = ", "
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
