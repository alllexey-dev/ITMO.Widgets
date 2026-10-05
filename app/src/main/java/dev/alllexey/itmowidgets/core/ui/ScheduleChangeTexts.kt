package dev.alllexey.itmowidgets.core.ui

import android.content.Context
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.text.DateTexts
import java.util.Locale
import kotlinx.datetime.format
import kotlinx.datetime.toJavaLocalTime

/** "Перенесена на ср, 9 сентября, 10:00": what happened, by the first changed field for an updated lesson. */
fun ScheduleChange.summary(context: Context): String = when (kind) {
    ScheduleChangeKind.ADDED -> context.getString(R.string.schedule_change_added, startText(checkNotNull(after)))
    ScheduleChangeKind.CANCELLED -> context.getString(R.string.schedule_change_cancelled, startText(checkNotNull(before)))
    ScheduleChangeKind.UPDATED -> {
        val slot = checkNotNull(after)
        when (headlineField ?: ScheduleChangeField.TIME) {
            ScheduleChangeField.TIME -> context.getString(R.string.schedule_change_moved, startText(slot))
            ScheduleChangeField.FORMAT -> context.getString(R.string.schedule_change_format, value(context, slot.format))
            ScheduleChangeField.PLACE -> context.getString(R.string.schedule_change_place, placeText(context, slot))
            ScheduleChangeField.TEACHER -> context.getString(R.string.schedule_change_teacher, value(context, slot.teacherName))
        }
    }
}

/**
 * The main line of a list row. With one changed field it is that field's "было → стало" line: the summary would only
 * repeat it ("Формат: Дистанционный" over "Формат: Очный → Дистанционный").
 */
fun ScheduleChange.listSummary(context: Context): String =
    if (hasSingleField()) detailLines(context).single() else summary(context)

/** The "было → стало" lines under [listSummary]; empty when the summary already is the only one. */
fun ScheduleChange.listLines(context: Context): List<String> =
    if (kind == ScheduleChangeKind.UPDATED && !hasSingleField()) detailLines(context) else emptyList()

private fun ScheduleChange.hasSingleField(): Boolean = kind == ScheduleChangeKind.UPDATED && fields.size == 1

/** "Физика — отменена: вт, 8 сентября, 10:00", the one line a notification or a card has room for. */
fun ScheduleChange.headline(context: Context): String {
    val subject = subjectName.trim().ifEmpty { context.getString(R.string.schedule_unknown_subject) }
    val summary = summary(context).replaceFirstChar { it.lowercase(RUSSIAN) }
    return context.getString(R.string.schedule_change_headline, subject, summary)
}

/** "было → стало" per changed field of an updated lesson; an added or cancelled one has only its summary. */
fun ScheduleChange.detailLines(context: Context): List<String> {
    if (kind != ScheduleChangeKind.UPDATED) return listOf(summary(context))
    val old = checkNotNull(before)
    val new = checkNotNull(after)
    return ScheduleChangeField.entries.filter { it in fields }.map { field ->
        val (from, to) = when (field) {
            ScheduleChangeField.TIME ->
                if (old.date == new.date) timeRange(old) to timeRange(new)
                else startText(old) to startText(new)
            ScheduleChangeField.FORMAT -> value(context, old.format) to value(context, new.format)
            ScheduleChangeField.PLACE -> placeText(context, old) to placeText(context, new)
            ScheduleChangeField.TEACHER -> value(context, old.teacherName) to value(context, new.teacherName)
        }
        context.getString(R.string.schedule_change_field_line, context.getString(field.titleRes()), from, to)
    }
}

private fun ScheduleChangeField.titleRes(): Int = when (this) {
    ScheduleChangeField.TIME -> R.string.schedule_change_field_time
    ScheduleChangeField.FORMAT -> R.string.schedule_change_field_format
    ScheduleChangeField.PLACE -> R.string.schedule_change_field_place
    ScheduleChangeField.TEACHER -> R.string.schedule_change_field_teacher
}

/** "ср, 9 сентября, 10:00". */
private fun startText(slot: LessonSlot): String = slot.startsAt.format(DateTexts.SHORT_WEEKDAY_DAY_MONTH_TIME)

private fun timeRange(slot: LessonSlot): String = timeRangeText(slot.start.toJavaLocalTime(), slot.end.toJavaLocalTime())

/** "1506 · Кронва"; a missing half is left out, a missing place reads as "—". */
private fun placeText(context: Context, slot: LessonSlot): String {
    val room = slot.room?.trim()?.takeIf(String::isNotEmpty)?.let { roomShortTitle(context, it) }
    val building = slot.building?.trim()?.takeIf(String::isNotEmpty)?.let { buildingShortTitle(context, it) }
    return listOfNotNull(room, building).joinToString(" · ").ifEmpty { context.getString(R.string.schedule_change_value_none) }
}

private fun value(context: Context, raw: String?): String =
    raw?.trim()?.takeIf(String::isNotEmpty) ?: context.getString(R.string.schedule_change_value_none)

private val RUSSIAN: Locale = Locale.forLanguageTag("ru")
