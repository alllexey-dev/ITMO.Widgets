package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.buildingShortTitle
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportOccupancy
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingConditions
import dev.alllexey.itmowidgets.feature.sport.presentation.common.queueText
import dev.alllexey.itmowidgets.feature.sport.presentation.common.restrictionText
import dev.alllexey.itmowidgets.feature.sport.presentation.common.shortText
import dev.alllexey.itmowidgets.feature.sport.presentation.common.sportDayTitle
import dev.alllexey.itmowidgets.feature.sport.presentation.common.tone
import dev.alllexey.itmowidgets.feature.sport.presentation.common.waiting
import dev.alllexey.itmowidgets.shared.feature.sport.SportPlurals
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** What «Запись» shows of the catalog; pure, so every case is a host test. */
class SportSignInput(
    val lessons: List<SportLesson>,
    val catalog: SportFilterCatalog,
    val filters: SportSignFilters,
    val pinned: Set<SectionName>,
    val busy: Set<Long>,
    /** The own timetable of the shown weeks, for the name of a lesson a sport lesson clashes with. */
    val timetable: List<LessonDay>,
    val showTeacher: Boolean,
    val showDayPart: Boolean,
    val now: Instant,
    val today: LocalDate,
    val zone: TimeZone,
)

object SportSignPresenter {

    /** The strip, the chips and the chosen day; [filtersOpen] adds the more-filters sheet. */
    fun present(input: SportSignInput, filtersOpen: Boolean): SportSignUiState = with(input) {
        val upcoming = lessons.filter { it.end > now }
        val visible = upcoming.filter { matches(it, filters, input) }
        val byDate = visible.groupBy { it.date(zone) }
        SportSignUiState(
            query = filters.query,
            chips = chips(filters, pinned.isNotEmpty()),
            filterCount = filters.count,
            weeks = weeks(byDate, filters.selectedDate, input),
            selectedWeek = today.weeksUntil(filters.selectedDate).coerceIn(0, MAX_WEEKS_FORWARD),
            body = day(upcoming, byDate, input),
            filters = if (filtersOpen) filtersSheet(upcoming, visible.size, input) else null,
        )
    }

    /** The lessons on [date] the filters show, in time order: what the page lists and the sheet's action checks. */
    fun shown(input: SportSignInput, date: LocalDate): List<SportLesson> =
        input.lessons.filter { it.end > input.now && it.date(input.zone) == date && matches(it, input.filters, input) }
            .sortedWith(compareBy<SportLesson> { it.start }.thenBy { it.sectionName.shorten() })

    private fun matches(lesson: SportLesson, filters: SportSignFilters, input: SportSignInput): Boolean {
        val query = filters.query.trim().lowercase()
        val start = lesson.start.toLocalDateTime(input.zone).time
        val mine = lesson.signed || lesson.signEntry?.waiting == true
        return (query.isEmpty() || query in lesson.sectionName.raw.lowercase() || query in lesson.teacherFio.lowercase()) &&
            (filters.sections.isEmpty() || lesson.sectionName in filters.sections) &&
            (filters.building == null || buildingName(lesson, input.catalog) == filters.building) &&
            (filters.teacher == null || teacherName(lesson, input.catalog) == filters.teacher) &&
            filters.dayPart.contains(start) &&
            (!filters.onlyFree || mine || (lesson.isLessonReal && lesson.available > 0 && lesson.canSignIn)) &&
            (!filters.onlyPinned || lesson.sectionName in input.pinned) &&
            (!filters.onlyFriends || lesson.friendsBookings.isNotEmpty())
    }

    private fun chips(filters: SportSignFilters, hasPinned: Boolean): List<SportChip> = buildList {
        add(SportChip(SportQuickFilter.FREE, UiText.Res(SportStrings.sport_chip_free), filters.onlyFree))
        add(SportChip(SportQuickFilter.EVENING, UiText.Res(SportStrings.sport_chip_evening), filters.dayPart == SportDayPart.EVENING))
        if (hasPinned) add(SportChip(SportQuickFilter.PINNED, UiText.Res(SportStrings.sport_chip_pinned), filters.onlyPinned))
        add(SportChip(SportQuickFilter.FRIENDS, UiText.Res(SportStrings.sport_chip_friends), filters.onlyFriends))
        add(SportChip(SportQuickFilter.MORNING, UiText.Res(SportStrings.sport_chip_morning), filters.dayPart == SportDayPart.MORNING))
    }

    private fun weeks(byDate: Map<LocalDate, List<SportLesson>>, selected: LocalDate, input: SportSignInput): List<List<SportWeekDay>> {
        val monday = input.today.weekMonday()
        return (0..MAX_WEEKS_FORWARD).map { week ->
            (0 until DAYS_IN_WEEK).map { offset ->
                val date = monday.plus(week * DAYS_IN_WEEK + offset, DateTimeUnit.DAY)
                val count = byDate[date].orEmpty().size
                SportWeekDay(
                    date = date,
                    weekday = DateTexts.shortWeekday(date.dayOfWeek),
                    number = date.day.toString(),
                    lessons = count,
                    today = date == input.today,
                    past = date < input.today,
                    selected = date == selected,
                    description = UiText.Res(
                        SportStrings.sport_week_day_description,
                        listOf(date.format(DateTexts.WEEKDAY_DAY_MONTH), UiText.Plural(SportPlurals.sport_lessons_count, count, listOf(count))),
                    ),
                )
            }
        }
    }

    private fun day(upcoming: List<SportLesson>, byDate: Map<LocalDate, List<SportLesson>>, input: SportSignInput): SportSignBody.Day {
        val date = input.filters.selectedDate
        val rows = shown(input, date).map { row(it, input) }
        val all = upcoming.count { it.date(input.zone) == date }
        val hidden = all - rows.size
        val count = when {
            rows.isEmpty() -> null
            hidden > 0 -> UiText.Res(SportStrings.sport_day_count_filtered, listOf(rows.size, all))
            else -> UiText.Plural(SportPlurals.sport_lessons_count, rows.size, listOf(rows.size))
        }
        val empty = when {
            rows.isNotEmpty() -> null
            all > 0 -> SportDayEmpty(
                title = UiText.Res(SportStrings.sport_day_filtered_title),
                text = UiText.Plural(SportPlurals.sport_day_filtered_text, all, listOf(all)),
                action = SportEmptyAction.ResetFilters(UiText.Res(SportStrings.sport_filters_reset_all)),
                filtered = true,
            )
            else -> {
                val next = byDate.keys.filter { it > date }.minOrNull()
                SportDayEmpty(
                    title = UiText.Res(SportStrings.sport_day_empty_title),
                    text = next?.let { UiText.Res(SportStrings.sport_day_empty_next, listOf(it.format(DateTexts.WEEKDAY_DAY_MONTH))) },
                    action = next?.let { SportEmptyAction.ShowDay(it, UiText.Res(SportStrings.sport_day_show)) },
                    filtered = false,
                )
            }
        }
        return SportSignBody.Day(
            title = sportDayTitle(date),
            count = count,
            rows = rows,
            hidden = if (rows.isNotEmpty() && hidden > 0) UiText.Plural(SportPlurals.sport_filters_hidden, hidden, listOf(hidden)) else null,
            empty = empty,
        )
    }

    /** One lesson as a row; also the sheet's view of a lesson the filters hide. */
    fun row(lesson: SportLesson, input: SportSignInput): SportCatalogRow {
        val start = lesson.start.toLocalDateTime(input.zone)
        val end = lesson.end.toLocalDateTime(input.zone)
        val occupancy = SportOccupancy.from(lesson.isLessonReal, lesson.available, lesson.limit)
        return SportCatalogRow(
            lessonId = lesson.lessonId,
            start = start.time.format(DateTexts.TIME),
            end = end.time.format(DateTexts.TIME),
            section = lesson.sectionName.shorten(),
            kind = lesson.kind.shortText(),
            kindTone = lesson.kind.tone(),
            place = buildingName(lesson, input.catalog)?.let { buildingShortTitle(it) }
                ?: lesson.roomName.takeIf { lesson.roomId == ONLINE_ROOM }?.let { UiText.Res(SportStrings.sport_place_online) },
            occupancy = occupancy?.toUi(),
            friends = lesson.friendsBookings.map { it.friend.name },
            clash = clashingLesson(lesson, input.timetable, input.zone)?.let { UiText.Res(SportStrings.sport_row_clash, listOf(it.subject.trim())) }
                ?: UiText.Res(SportStrings.sport_row_clash_unknown).takeIf { lesson.intersection },
            action = action(lesson, lesson.lessonId in input.busy, input.now),
        )
    }

    /** The one action of a lesson's row at [now]: its status, `Записаться`, `В очередь` or why it is closed. */
    fun action(lesson: SportLesson, busy: Boolean, now: Instant): SportRowAction {
        val entry = lesson.signEntry?.takeIf { it.waiting }
        val availability = lesson.bookingConditions().evaluate(now)
        return when {
            lesson.signed && lesson.isLessonReal -> SportRowAction.Status(UiText.Res(SportStrings.sport_pill_booked), booked = true)
            entry != null -> SportRowAction.Status(entry.queueText(), booked = false)
            availability.action == SportBookingAction.SIGN -> SportRowAction.Sign(busy)
            availability.action == SportBookingAction.AUTO -> SportRowAction.Queue(busy)
            else -> SportRowAction.Blocked(
                availability.restrictions.firstOrNull()?.restrictionText() ?: UiText.Res(SportStrings.sport_lesson_unavailable),
            )
        }
    }

    /**
     * The own lesson at the same time as [lesson], from the cached timetable; null without a clash MyITMO reported or
     * when the cache has no such lesson, then the clash reads `Пересекается с парой`.
     */
    fun clashingLesson(lesson: SportLesson, timetable: List<LessonDay>, zone: TimeZone): DayLesson? {
        if (!lesson.intersection) return null
        val start = lesson.start.toLocalDateTime(zone)
        val end = lesson.end.toLocalDateTime(zone)
        return timetable.firstOrNull { it.date == start.date }?.lessons?.firstOrNull {
            it.sport == null && it.typeId != SPORT_LESSON_TYPE && it.change?.cancelled != true &&
                it.start < end.time && start.time < it.end
        }
    }

    private fun filtersSheet(upcoming: List<SportLesson>, shown: Int, input: SportSignInput): SportFiltersUi {
        val filters = input.filters
        val sections = upcoming.map { it.sectionName }.distinct().sortedBy { it.shorten() }
        val buildings = upcoming.mapNotNull { buildingName(it, input.catalog) }.distinct().sorted()
        val teachers = upcoming.mapNotNull { teacherName(it, input.catalog) }.distinct().sorted()
        return SportFiltersUi(
            sections = sections.map { SportOption(it.raw, UiText.Dynamic(it.shorten()), it in filters.sections) },
            buildings = listOf(SportOption(null, UiText.Res(SportStrings.sport_filter_all), filters.building == null)) +
                buildings.map { SportOption(it, buildingShortTitle(it), it == filters.building) },
            dayPart = filters.dayPart,
            showDayPart = input.showDayPart,
            teachers = if (input.showTeacher) {
                listOf(SportOption(null, UiText.Res(SportStrings.sport_filter_any_teacher), filters.teacher == null)) +
                    teachers.map { SportOption(it, UiText.Dynamic(it), it == filters.teacher) }
            } else {
                null
            },
            teacher = filters.teacher,
            onlyFree = filters.onlyFree,
            onlyPinned = filters.onlyPinned.takeIf { input.pinned.isNotEmpty() },
            onlyFriends = filters.onlyFriends,
            shownCount = UiText.Plural(SportPlurals.sport_filters_show, shown, listOf(shown)),
        )
    }

    private fun SportOccupancy.toUi(): SportOccupancyUi = SportOccupancyUi(
        taken = occupied,
        total = limit,
        text = UiText.Res(SportStrings.sport_occupancy, listOf(occupied, limit)),
        tone = when {
            available == 0 -> SportSeatsTone.FULL
            available <= FEW_SEATS -> SportSeatsTone.FEW
            else -> SportSeatsTone.OK
        },
    )

    /** The building filter's name of a lesson; online rooms and venues outside the filter have none. */
    private fun buildingName(lesson: SportLesson, catalog: SportFilterCatalog): String? =
        lesson.buildingId?.takeIf { it > 0 && lesson.roomId != ONLINE_ROOM }
            ?.let { id -> catalog.buildings.firstOrNull { it.id == id }?.value }

    private fun teacherName(lesson: SportLesson, catalog: SportFilterCatalog): String? =
        catalog.teachers.firstOrNull { it.id == lesson.teacherIsu.toLong() }?.value ?: lesson.teacherFio.trim().takeIf(String::isNotEmpty)

    private fun SportLesson.date(zone: TimeZone): LocalDate = start.toLocalDateTime(zone).date

    private const val DAYS_IN_WEEK = 7
    private const val FEW_SEATS = 3
    private const val ONLINE_ROOM = -1L
    private const val SPORT_LESSON_TYPE = 11
}
