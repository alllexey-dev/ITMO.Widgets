package dev.alllexey.itmowidgets.core.text

import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlinx.datetime.format

/** "Перенесена на ср, 9 сентября, 10:00": what happened, by the first changed field for an updated lesson. */
fun ScheduleChange.summary(): UiText = when (kind) {
    ScheduleChangeKind.ADDED -> UiText.Res(CoreStrings.schedule_change_added, listOf(startText(checkNotNull(after))))
    ScheduleChangeKind.CANCELLED ->
        UiText.Res(CoreStrings.schedule_change_cancelled, listOf(startText(checkNotNull(before))))
    ScheduleChangeKind.UPDATED -> {
        val slot = checkNotNull(after)
        when (headlineField ?: ScheduleChangeField.TIME) {
            ScheduleChangeField.TIME -> UiText.Res(CoreStrings.schedule_change_moved, listOf(startText(slot)))
            ScheduleChangeField.FORMAT -> UiText.Res(CoreStrings.schedule_change_format, listOf(value(slot.format)))
            ScheduleChangeField.PLACE -> UiText.Res(CoreStrings.schedule_change_place, listOf(placeText(slot)))
            ScheduleChangeField.TEACHER ->
                UiText.Res(CoreStrings.schedule_change_teacher, listOf(value(slot.teacherName)))
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
        ?: UiText.Res(CoreStrings.schedule_unknown_subject)
    return UiText.Res(CoreStrings.schedule_change_headline, listOf(subject, UiText.LowercaseFirst(summary())))
}

/** "было → стало" per changed field of an updated lesson; an added or cancelled one has only its summary. */
fun ScheduleChange.detailLines(): List<UiText> {
    if (kind != ScheduleChangeKind.UPDATED) return listOf(summary())
    return changeLines().map { UiText.Res(CoreStrings.schedule_change_field_line, listOf(it.field, it.from, it.to)) }
}

/** One changed field of an updated lesson: [field] `Аудитория`, [from] what it was, [to] what it is. */
data class ScheduleChangeLine(val field: UiText, val from: UiText, val to: UiText)

/** The changed fields of an updated lesson in their priority order; none for an added or cancelled one. */
fun ScheduleChange.changeLines(): List<ScheduleChangeLine> {
    if (kind != ScheduleChangeKind.UPDATED) return emptyList()
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
        ScheduleChangeLine(UiText.Res(field.title()), from, to)
    }
}

private fun ScheduleChange.hasSingleField(): Boolean = kind == ScheduleChangeKind.UPDATED && fields.size == 1

private fun ScheduleChangeField.title(): String = when (this) {
    ScheduleChangeField.TIME -> CoreStrings.schedule_change_field_time
    ScheduleChangeField.FORMAT -> CoreStrings.schedule_change_field_format
    ScheduleChangeField.PLACE -> CoreStrings.schedule_change_field_place
    ScheduleChangeField.TEACHER -> CoreStrings.schedule_change_field_teacher
}

/** "ср, 9 сентября, 10:00". */
private fun startText(slot: LessonSlot): String = slot.startsAt.format(DateTexts.SHORT_WEEKDAY_DAY_MONTH_TIME)

/** "08:20–09:50". */
private fun timeRange(slot: LessonSlot): String = "${slot.start.format(DateTexts.TIME)}–${slot.end.format(DateTexts.TIME)}"

/** "1506, Кронва"; a missing half is left out, a missing place reads as "—". */
private fun placeText(slot: LessonSlot): UiText {
    val room = slot.room?.trim()?.takeIf(String::isNotEmpty)?.let(::roomShortTitle)
    val building = slot.building?.trim()?.takeIf(String::isNotEmpty)?.let { buildingShortTitle(it) }
    val parts = listOfNotNull(room, building)
    return if (parts.isEmpty()) UiText.Res(CoreStrings.schedule_change_value_none) else UiText.Joined(parts, ", ")
}

private fun value(raw: String?): UiText =
    raw?.trim()?.takeIf(String::isNotEmpty)?.let(UiText::Dynamic) ?: UiText.Res(CoreStrings.schedule_change_value_none)
