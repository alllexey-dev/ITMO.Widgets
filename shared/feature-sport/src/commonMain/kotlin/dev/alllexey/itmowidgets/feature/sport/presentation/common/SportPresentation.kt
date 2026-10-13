package dev.alllexey.itmowidgets.feature.sport.presentation.common

import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonPill
import dev.alllexey.itmowidgets.core.lesson.LessonPillKind
import dev.alllexey.itmowidgets.core.lesson.LessonState
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.lesson.LessonTimelines
import dev.alllexey.itmowidgets.core.lesson.LessonUi
import dev.alllexey.itmowidgets.core.lesson.SportMark
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.buildingShortTitle
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportCommon
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLessonKind
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

// The words every sport screen shares: the kind of a lesson, its place, its day and a booking as a lesson card.

/** MyITMO's lesson type id of sport in the timetable; it colours a booking as the schedule colours it. */
const val SPORT_TYPE_ID = 11

/** «Свободное», «Тренировка», «Секция», «Сборная», or the full name of a rarer kind (`Нормативы`). */
fun SportLessonKind.shortText(): UiText = UiText.Res(
    when (this) {
        SportLessonKind.FREE_ATTENDANCE -> SportStrings.sport_kind_free_short
        SportLessonKind.TRAINING_SECTION -> SportStrings.sport_kind_training_short
        SportLessonKind.INTERMEDIATE_SECTION -> SportStrings.sport_kind_intermediate_short
        SportLessonKind.TEAM_SECTION -> SportStrings.sport_kind_team_short
        SportLessonKind.OPEN -> SportStrings.sport_lesson_open
        SportLessonKind.DEBT -> SportStrings.sport_lesson_debt
        SportLessonKind.STANDARDS -> SportStrings.sport_lesson_standards
        SportLessonKind.EXTERNAL -> SportStrings.sport_lesson_external
        SportLessonKind.ADDITIONAL -> SportStrings.sport_lesson_additional
        SportLessonKind.UNKNOWN -> SportStrings.sport_lesson_unknown
    },
)

/** The colour family of a kind: free lessons, sections, the national team, the rest. */
enum class SportKindTone { FREE, SECTION, TEAM, OTHER }

fun SportLessonKind.tone(): SportKindTone = when (this) {
    SportLessonKind.FREE_ATTENDANCE, SportLessonKind.OPEN -> SportKindTone.FREE
    SportLessonKind.TRAINING_SECTION, SportLessonKind.INTERMEDIATE_SECTION -> SportKindTone.SECTION
    SportLessonKind.TEAM_SECTION -> SportKindTone.TEAM
    else -> SportKindTone.OTHER
}

/** The kind a lesson level names when a booking has no catalog lesson behind it. */
fun sportKindOfLevel(lessonLevel: Int): SportLessonKind = when (lessonLevel) {
    2 -> SportLessonKind.TRAINING_SECTION
    3 -> SportLessonKind.INTERMEDIATE_SECTION
    4 -> SportLessonKind.TEAM_SECTION
    1 -> SportLessonKind.FREE_ATTENDANCE
    else -> SportLessonKind.UNKNOWN
}

/**
 * MyITMO's room text of a sport lesson split in two: the building's address at its start (`Кронверкский пр., 49`)
 * and the hall after it (`спортивный зал`); a text without an address is the room alone.
 */
data class SportPlace(val room: String?, val building: String?) {
    /** `спортивный зал, Кронва`, the building's short name; null when MyITMO named nothing. */
    fun short(): UiText? {
        val parts = listOfNotNull(room?.let(UiText::Dynamic), building?.let { buildingShortTitle(it) })
        return if (parts.isEmpty()) null else UiText.Joined(parts, ", ")
    }
}

fun SportCommon.place(): SportPlace {
    val raw = roomName.trim()
    val address = extractBuildingAddress()?.trim()?.takeIf { it.isNotEmpty() && raw.startsWith(it) }
        ?: return SportPlace(raw.takeIf(String::isNotEmpty), null)
    val room = raw.removePrefix(address).trim().trimStart(',').trim()
    return SportPlace(room.takeIf(String::isNotEmpty), address)
}

/** «Сегодня, 7 сент.», «Завтра, 8 сент.», «Чт, 10 сент.». */
fun sportDayLabel(date: LocalDate, today: LocalDate): UiText {
    val day = "${date.day} ${date.format(DateTexts.MONTH_SHORT)}"
    return when (date) {
        today -> UiText.Res(SportStrings.sport_day_today, listOf(day))
        today.plus(1, DateTimeUnit.DAY) -> UiText.Res(SportStrings.sport_day_tomorrow, listOf(day))
        else -> UiText.Dynamic(date.format(DateTexts.SHORT_WEEKDAY_DAY_SHORT_MONTH).capitalized())
    }
}

/** «Вторник, 8 сентября». */
fun sportDayTitle(date: LocalDate): UiText = UiText.Dynamic(date.format(DateTexts.WEEKDAY_DAY_MONTH).capitalized())

/** A queue entry that still waits for a place or for its lesson. */
val SportQueueEntry.waiting: Boolean
    get() = !isCancelled && status in SportQueueEntryStatus.notifiableStatuses

/** «очередь 2 из 5»; «очередь» while the position is unknown. */
fun SportQueueEntry.queueText(): UiText =
    if (position > 0 && total > 0) UiText.Res(SportStrings.sport_queue_position, listOf(position, total))
    else UiText.Res(SportStrings.sport_queue_pill)

/**
 * A booking or a queue as the lesson card every screen draws (docs/design.md "Lessons"): the section, `Спорт,
 * преподаватель`, `зал, корпус` and the pill `записаны`, `очередь 2 из 5` or, for a lesson My ITMO has not published
 * yet, `ждём расписание`.
 */
fun SportCommon.toLessonUi(time: AcademicTimeProvider): LessonUi {
    val start = start.toLocalDateTime(time.timeZone)
    val end = end.toLocalDateTime(time.timeZone)
    val place = place()
    val entry = signEntry?.takeIf { it.waiting && !signed }
    val lesson = DayLesson(
        start = start.time,
        end = end.time,
        subject = sectionName.raw.trim(),
        typeId = SPORT_TYPE_ID,
        teacher = teacherFio.trim().takeIf(String::isNotEmpty),
        room = place.room,
        building = place.building,
        online = false,
        target = LessonTarget.PendingSport(
            PendingSportDetailsArgs(
                lessonId = lessonId,
                sectionName = sectionName.raw,
                autoSign = entry is SportAutoSignEntry,
                isPrediction = !isLessonReal,
                start = this.start.toString(),
                end = this.end.toString(),
                teacherFio = teacherFio,
                roomName = roomName,
            ),
        ),
        note = null,
        sport = if (entry != null) SportMark.QUEUED else SportMark.BOOKED,
    )
    val ui = LessonTimelines.lesson(lesson, LessonState.UPCOMING)
    // The shared rule says `очередь`; here the position is known, or the lesson is not published yet.
    val queue = when {
        entry == null -> null
        !isLessonReal -> UiText.Res(SportStrings.sport_queue_unpublished)
        else -> entry.queueText()
    }
    return ui.copy(pills = ui.pills.map { if (it.kind == LessonPillKind.QUEUE && queue != null) LessonPill(it.kind, queue) else it })
}

/** MyITMO's own words when it gave any, otherwise the obstacle's: `Не та группа здоровья`. */
fun SportBookingRestriction.restrictionText(): UiText = detail?.let(UiText::Dynamic) ?: UiText.Res(kind.textKey())

fun SportBookingObstacle.textKey(): String = when (this) {
    SportBookingObstacle.TIME_CONFLICT -> SportStrings.sport_rule_time_conflict
    SportBookingObstacle.DAILY_LIMIT -> SportStrings.sport_rule_daily_limit
    SportBookingObstacle.WEEKLY_LIMIT -> SportStrings.sport_rule_weekly_limit
    SportBookingObstacle.CREDIT -> SportStrings.sport_rule_credit
    SportBookingObstacle.SELECTION -> SportStrings.sport_rule_selection
    SportBookingObstacle.EXTERNAT -> SportStrings.sport_rule_externat
    SportBookingObstacle.DEBT_ONLY -> SportStrings.sport_rule_debt_only
    SportBookingObstacle.DENIED -> SportStrings.sport_rule_denied
    SportBookingObstacle.HEALTH -> SportStrings.sport_rule_health
    SportBookingObstacle.STARTED -> SportStrings.sport_rule_started
    SportBookingObstacle.UNKNOWN -> SportStrings.sport_rule_unknown
}

private fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }
