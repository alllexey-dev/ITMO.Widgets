package dev.alllexey.itmowidgets.feature.sport.presentation.my

import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.shortPersonName
import dev.alllexey.itmowidgets.feature.sport.domain.model.MySportSection
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSectionDateState
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSectionsState
import dev.alllexey.itmowidgets.feature.sport.presentation.common.sportDayLabel
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * `Сделать секцией` of a booked lesson, on its card in `Моё` and in its sheet: [on] with the place (`3-й из 7 в
 * секции`), or off with how many chose it; [enabled] false while a change runs or the slot is full (`Мест в секции нет`).
 */
data class SportSectionToggleUi(
    val lessonId: Long,
    val on: Boolean,
    val enabled: Boolean,
    val label: UiText,
    val detail: UiText?,
)

/**
 * A section in `Моё`: [title] (`Волейбол`), the slot (`Среда, 10:00, раз в две недели`), the teacher and the hall ([venue]),
 * the place in the section queue, the next [dates] with their switches; [busy] while a change runs.
 */
data class SportSectionCardUi(
    val id: Long,
    val title: String,
    val slot: UiText,
    val venue: String?,
    val place: UiText,
    val dates: List<SportSectionDateUi>,
    val busy: Boolean,
)

/**
 * One date of a section: [day] `Ср, 21 окт.`, [status] `в очереди`, `лимит на месяц исчерпан`, `пропустим это занятие`
 * or `встанем в очередь, когда откроется запись`; [on] false for a skipped date, [enabled] false for one the limit skipped.
 */
data class SportSectionDateUi(val start: Instant, val day: UiText, val status: UiText, val on: Boolean, val enabled: Boolean)

object SportSectionsPresenter {

    /** The switch of the booked [lessonId]; null until its slot is known (or it has none: no provable venue). */
    fun toggle(state: SportSectionsState, lessonId: Long): SportSectionToggleUi? {
        val slot = state.slots[lessonId] ?: return null
        val busy = lessonId in state.busyLessons
        val place = slot.place
        return when {
            slot.subscriptionId != null -> SportSectionToggleUi(
                lessonId = lessonId,
                on = true,
                enabled = !busy,
                label = UiText.Res(SportStrings.sport_section_on),
                detail = place?.let { UiText.Res(SportStrings.sport_section_place, listOf(it, slot.subscribers)) },
            )
            slot.full -> SportSectionToggleUi(
                lessonId = lessonId,
                on = false,
                enabled = false,
                label = UiText.Res(SportStrings.sport_section_make),
                detail = UiText.Res(SportStrings.sport_section_full),
            )
            else -> SportSectionToggleUi(
                lessonId = lessonId,
                on = false,
                enabled = !busy,
                label = UiText.Res(SportStrings.sport_section_make),
                detail = UiText.Res(SportStrings.sport_section_chosen, listOf(slot.subscribers)).takeIf { slot.subscribers > 0 },
            )
        }
    }

    fun cards(state: SportSectionsState, today: LocalDate, zone: TimeZone): List<SportSectionCardUi> =
        state.sections.valueOrNull().orEmpty().map { card(it, it.id in state.busySections, today, zone) }

    private fun card(section: MySportSection, busy: Boolean, today: LocalDate, zone: TimeZone): SportSectionCardUi {
        val lesson = section.lesson
        val start = lesson.start.toLocalDateTime(zone)
        val weekday = DateTexts.weekday(start.dayOfWeek).replaceFirstChar { it.uppercaseChar() }
        val teacher = lesson.teacherFio.trim().takeIf(String::isNotEmpty)?.let(::shortPersonName)
        return SportSectionCardUi(
            id = section.id,
            title = SectionName(lesson.sectionName).shorten(),
            slot = UiText.Res(SportStrings.sport_section_slot, listOf(weekday, start.time.format(DateTexts.TIME))),
            venue = listOfNotNull(teacher, lesson.roomName.trim().takeIf(String::isNotEmpty)).joinToString(", ").takeIf(String::isNotEmpty),
            place = UiText.Res(SportStrings.sport_section_place, listOf(section.place, section.subscribers)),
            dates = section.dates.map { date ->
                SportSectionDateUi(
                    start = date.start,
                    day = sportDayLabel(date.start.toLocalDateTime(zone).date, today),
                    status = UiText.Res(
                        when (date.state) {
                            SportSectionDateState.QUEUED -> SportStrings.sport_section_date_queued
                            SportSectionDateState.LIMIT -> SportStrings.sport_section_date_limit
                            SportSectionDateState.SKIPPED -> SportStrings.sport_section_date_skipped
                            null -> SportStrings.sport_section_date_waiting
                        },
                    ),
                    on = date.state == null || date.state == SportSectionDateState.QUEUED,
                    enabled = !busy && date.state != SportSectionDateState.LIMIT,
                )
            },
            busy = busy,
        )
    }
}
