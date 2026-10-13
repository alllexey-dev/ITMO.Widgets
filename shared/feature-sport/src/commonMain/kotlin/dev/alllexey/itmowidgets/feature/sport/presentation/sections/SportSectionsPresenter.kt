package dev.alllexey.itmowidgets.feature.sport.presentation.sections

import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.shortPersonName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportRecurringRule
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportKindTone
import dev.alllexey.itmowidgets.feature.sport.presentation.common.place
import dev.alllexey.itmowidgets.feature.sport.presentation.common.shortText
import dev.alllexey.itmowidgets.feature.sport.presentation.common.sportDayLabel
import dev.alllexey.itmowidgets.feature.sport.presentation.common.tone
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportOption
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignPresenter
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** What «Мои секции» is made of; pure, so every case is a host test. */
class SportSectionsInput(
    val lessons: List<SportLesson>,
    val pinned: Set<SectionName>,
    val limits: SportAutoSignLimits?,
    /** Null while Backend keeps no rules: the repeat switch stays hidden. */
    val rules: List<SportRecurringRule>?,
    val timetable: List<LessonDay>,
    val busy: Set<Long>,
    val now: Instant,
    val today: LocalDate,
    val zone: TimeZone,
)

object SportSectionsPresenter {

    /** The next lessons of a pinned section on its card. */
    const val NEXT_LESSONS = 3

    /** The next dates of a repeat on its card. */
    const val REPEAT_DATES = 3

    /** The weeks between two lessons of the same slot. */
    private const val REPEAT_WEEKS = 2

    fun subtitle(limits: SportAutoSignLimits?): UiText? = limits?.let {
        UiText.Res(SportStrings.sport_sections_limit, listOf(it.limit - it.available, it.limit))
    }

    /** The gate when the limit is spent and the repeat could otherwise be switched on. */
    fun limitGate(input: SportSectionsInput): SportLimitGate? {
        val limits = input.limits ?: return null
        if (input.rules == null || limits.available > 0) return null
        return SportLimitGate(
            title = UiText.Res(SportStrings.sport_sections_limit_title),
            reason = UiText.Res(
                SportStrings.sport_sections_limit_reason,
                listOf(limits.limit, limits.nextAvailableAt.toLocalDateTime(input.zone).date.format(DateTexts.DAY_MONTH)),
            ),
            fix = UiText.Res(SportStrings.sport_sections_limit_fix),
        )
    }

    fun cards(input: SportSectionsInput): List<SportSectionCard> = input.pinned.sortedBy { it.shorten() }.map { section ->
        val upcoming = input.lessons.filter { it.sectionName == section && it.end > input.now }.sortedBy { it.start }
        val first = upcoming.firstOrNull()
        SportSectionCard(
            name = section.raw,
            title = section.shorten(),
            teacher = first?.teacherFio?.trim()?.takeIf(String::isNotEmpty)?.let(::shortPersonName),
            kind = first?.kind?.shortText(),
            kindTone = first?.kind?.tone() ?: SportKindTone.OTHER,
            place = first?.place()?.short(),
            lessons = upcoming.take(NEXT_LESSONS).map { lesson ->
                val start = lesson.start.toLocalDateTime(input.zone)
                SportSectionLesson(
                    lessonId = lesson.lessonId,
                    day = sportDayLabel(start.date, input.today),
                    time = start.time.format(DateTexts.TIME),
                    action = SportSignPresenter.action(lesson, lesson.lessonId in input.busy, input.now),
                )
            },
            repeat = input.rules?.let { rules -> upcoming.firstOrNull { it.isLessonReal }?.let { repeat(it, rules, input) } },
        )
    }

    /** The slot a repeat follows: the section's next published lesson. */
    fun rule(lesson: SportLesson, zone: TimeZone): SportRecurringRule {
        val start = lesson.start.toLocalDateTime(zone)
        return SportRecurringRule(lesson.lessonId, lesson.sectionId, lesson.teacherIsu, start.dayOfWeek, start.time)
    }

    private fun repeat(lesson: SportLesson, rules: List<SportRecurringRule>, input: SportSectionsInput): SportRepeatUi {
        val rule = rule(lesson, input.zone)
        val on = rules.any { it.sectionId == rule.sectionId && it.weekday == rule.weekday && it.start == rule.start && it.teacherIsu == rule.teacherIsu }
        val start = lesson.start.toLocalDateTime(input.zone)
        val end = lesson.end.toLocalDateTime(input.zone)
        val slot = "${DateTexts.weekday(start.dayOfWeek).replaceFirstChar { it.uppercaseChar() }}, ${start.time.format(DateTexts.TIME)}"
        val dates = (1..REPEAT_DATES).map { start.date.plus(it * REPEAT_WEEKS, DateTimeUnit.WEEK) }
        // The own lesson at the slot's time on a date, from the cached timetable.
        fun lessonOn(date: LocalDate) = input.timetable.firstOrNull { it.date == date }?.lessons?.firstOrNull {
            it.sport == null && it.change?.cancelled != true && it.start < end.time && start.time < it.end
        }
        val clash = if (on) dates.firstNotNullOfOrNull { date -> lessonOn(date)?.let { date to it.subject.trim() } } else null
        val kept = dates.filter { clash == null || it < clash.first }
        return SportRepeatUi(
            on = on,
            subtitle = UiText.Res(if (on) SportStrings.sport_repeat_on else SportStrings.sport_repeat_off, listOf(slot)),
            enabled = on || (input.limits?.available ?: 0) > 0,
            dates = if (on) kept.map { UiText.Dynamic(it.format(DateTexts.SHORT_WEEKDAY_DAY_SHORT_MONTH)) } else emptyList(),
            clash = clash?.let { (date, subject) ->
                val day = date.format(DateTexts.DAY_MONTH)
                kept.lastOrNull()?.let { UiText.Res(SportStrings.sport_repeat_clash_until, listOf(day, subject, it.format(DateTexts.DAY_MONTH))) }
                    ?: UiText.Res(SportStrings.sport_repeat_clash, listOf(day, subject))
            },
        )
    }

    /** Every section of the catalog, pinned ones checked, for the picker. */
    fun picker(lessons: List<SportLesson>, pinned: Set<SectionName>): List<SportOption> =
        (lessons.map { it.sectionName } + pinned).distinct().sortedBy { it.shorten() }
            .map { SportOption(it.raw, UiText.Dynamic(it.shorten()), it in pinned) }
}
