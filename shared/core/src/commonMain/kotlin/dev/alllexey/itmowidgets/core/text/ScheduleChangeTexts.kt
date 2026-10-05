package dev.alllexey.itmowidgets.core.text

import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.schedule_change_added
import dev.alllexey.itmowidgets.shared.core.schedule_change_cancelled
import dev.alllexey.itmowidgets.shared.core.schedule_change_field_format
import dev.alllexey.itmowidgets.shared.core.schedule_change_field_line
import dev.alllexey.itmowidgets.shared.core.schedule_change_field_place
import dev.alllexey.itmowidgets.shared.core.schedule_change_field_teacher
import dev.alllexey.itmowidgets.shared.core.schedule_change_field_time
import dev.alllexey.itmowidgets.shared.core.schedule_change_format
import dev.alllexey.itmowidgets.shared.core.schedule_change_headline
import dev.alllexey.itmowidgets.shared.core.schedule_change_moved
import dev.alllexey.itmowidgets.shared.core.schedule_change_place
import dev.alllexey.itmowidgets.shared.core.schedule_change_teacher
import dev.alllexey.itmowidgets.shared.core.schedule_change_value_none
import dev.alllexey.itmowidgets.shared.core.schedule_unknown_subject
import kotlinx.datetime.format
import org.jetbrains.compose.resources.StringResource

/** "Перенесена на ср, 9 сентября, 10:00": what happened, by the first changed field for an updated lesson. */
fun ScheduleChange.summary(): UiText = when (kind) {
    ScheduleChangeKind.ADDED -> UiText.Res(Res.string.schedule_change_added, listOf(startText(checkNotNull(after))))
    ScheduleChangeKind.CANCELLED -> UiText.Res(Res.string.schedule_change_cancelled, listOf(startText(checkNotNull(before))))
    ScheduleChangeKind.UPDATED -> {
        val slot = checkNotNull(after)
        when (headlineField ?: ScheduleChangeField.TIME) {
            ScheduleChangeField.TIME -> UiText.Res(Res.string.schedule_change_moved, listOf(startText(slot)))
            ScheduleChangeField.FORMAT -> UiText.Res(Res.string.schedule_change_format, listOf(value(slot.format)))
            ScheduleChangeField.PLACE -> UiText.Res(Res.string.schedule_change_place, listOf(placeText(slot)))
            ScheduleChangeField.TEACHER -> UiText.Res(Res.string.schedule_change_teacher, listOf(value(slot.teacherName)))
        }
    }
}

/**
 * The main line of a list row. With one changed field it is that field's "было → стало" line: the summary would only
 * repeat it ("Формат: Дистанционный" over "Формат: Очный → Дистанционный").
 */
fun ScheduleChange.listSummary(): UiText = if (hasSingleField()) detailLines().single() else summary()

/** The "было → стало" lines under [listSummary]; empty when the summary already is the only one. */
fun ScheduleChange.listLines(): List<UiText> =
    if (kind == ScheduleChangeKind.UPDATED && !hasSingleField()) detailLines() else emptyList()

/** "Физика — отменена: вт, 8 сентября, 10:00", the one line a notification or a card has room for. */
fun ScheduleChange.headline(): UiText {
    val subject = subjectName.trim().takeIf(String::isNotEmpty)?.let(UiText::Dynamic)
        ?: UiText.Res(Res.string.schedule_unknown_subject)
    return UiText.Res(Res.string.schedule_change_headline, listOf(subject, UiText.LowercaseFirst(summary())))
}

/** "было → стало" per changed field of an updated lesson; an added or cancelled one has only its summary. */
fun ScheduleChange.detailLines(): List<UiText> {
    if (kind != ScheduleChangeKind.UPDATED) return listOf(summary())
    val old = checkNotNull(before)
    val new = checkNotNull(after)
    return ScheduleChangeField.entries.filter { it in fields }.map { field ->
        val (from, to) = when (field) {
            ScheduleChangeField.TIME ->
                if (old.date == new.date) UiText.Dynamic(timeRange(old)) to UiText.Dynamic(timeRange(new))
                else UiText.Dynamic(startText(old)) to UiText.Dynamic(startText(new))
            ScheduleChangeField.FORMAT -> value(old.format) to value(new.format)
            ScheduleChangeField.PLACE -> placeText(old) to placeText(new)
            ScheduleChangeField.TEACHER -> value(old.teacherName) to value(new.teacherName)
        }
        UiText.Res(Res.string.schedule_change_field_line, listOf(UiText.Res(field.title()), from, to))
    }
}

private fun ScheduleChange.hasSingleField(): Boolean = kind == ScheduleChangeKind.UPDATED && fields.size == 1

private fun ScheduleChangeField.title(): StringResource = when (this) {
    ScheduleChangeField.TIME -> Res.string.schedule_change_field_time
    ScheduleChangeField.FORMAT -> Res.string.schedule_change_field_format
    ScheduleChangeField.PLACE -> Res.string.schedule_change_field_place
    ScheduleChangeField.TEACHER -> Res.string.schedule_change_field_teacher
}

/** "ср, 9 сентября, 10:00". */
private fun startText(slot: LessonSlot): String = slot.startsAt.format(DateTexts.SHORT_WEEKDAY_DAY_MONTH_TIME)

/** "08:20–09:50". */
private fun timeRange(slot: LessonSlot): String = "${slot.start.format(DateTexts.TIME)}–${slot.end.format(DateTexts.TIME)}"

/** "1506 · Кронва"; a missing half is left out, a missing place reads as "—". */
private fun placeText(slot: LessonSlot): UiText {
    val room = slot.room?.trim()?.takeIf(String::isNotEmpty)?.let(::roomShortTitle)
    val building = slot.building?.trim()?.takeIf(String::isNotEmpty)?.let { buildingShortTitle(it) }
    val parts = listOfNotNull(room, building)
    return if (parts.isEmpty()) UiText.Res(Res.string.schedule_change_value_none) else UiText.Joined(parts, " · ")
}

private fun value(raw: String?): UiText =
    raw?.trim()?.takeIf(String::isNotEmpty)?.let(UiText::Dynamic) ?: UiText.Res(Res.string.schedule_change_value_none)
