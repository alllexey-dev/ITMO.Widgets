package dev.alllexey.itmowidgets.feature.schedule.data.bell

import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.buildingShortTitle
import dev.alllexey.itmowidgets.core.text.roomShortTitle
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.toLessonDetailsArgs
import dev.alllexey.itmowidgets.shared.feature.schedule.ScheduleStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.format

/**
 * The found changes of the own schedule in the bell, from the local store: what happened to which lesson, `было` and
 * `стало`, leading to the lesson's sheet. Opening the bell reads them, which also removes their notification.
 */
class ScheduleChangesBellSource(private val repository: ScheduleChangesRepository) : BellSource {

    override fun observe(): Flow<List<BellItem>> = repository.observeChanges().map { changes -> changes.map(::item) }

    override suspend fun markAllRead() = repository.markAllRead()

    private fun item(change: ScheduleChange): BellItem {
        val subject = change.subjectName.trim().ifEmpty { null }?.let(UiText::Dynamic)
            ?: UiText.Res(ScheduleStrings.schedule_bell_unknown_subject)
        val (was, now) = sides(change)
        return BellItem(
            id = "change:${change.id}",
            kind = BellKind.SCHEDULE_CHANGE,
            at = change.detectedAt,
            title = UiText.Res(change.titleKey(), listOf(subject)),
            was = was,
            now = now,
            target = EntryRoute(AppTab.HOME, overlay = AppRoutes.LessonDetails(change.toLessonDetailsArgs())),
        )
    }

    private fun ScheduleChange.titleKey(): String = when (kind) {
        ScheduleChangeKind.ADDED -> ScheduleStrings.schedule_bell_change_added
        ScheduleChangeKind.CANCELLED -> ScheduleStrings.schedule_bell_change_cancelled
        ScheduleChangeKind.UPDATED ->
            if (ScheduleChangeField.TIME in fields) ScheduleStrings.schedule_bell_change_moved
            else ScheduleStrings.schedule_bell_change_updated
    }

    /** A teacher or a format alone says only that; otherwise the whole slot, `чт, 10 сент., 10:00, 1506, Кронва`. */
    private fun sides(change: ScheduleChange): Pair<UiText?, UiText?> {
        val before = change.before
        val after = change.after
        if (before != null && after != null && change.fields.all { it == ScheduleChangeField.TEACHER }) {
            return value(before.teacherName) to value(after.teacherName)
        }
        if (before != null && after != null && change.fields.all { it == ScheduleChangeField.FORMAT }) {
            return value(before.format) to value(after.format)
        }
        return before?.let(::slot) to after?.let(::slot)
    }

    private fun slot(slot: LessonSlot): UiText = UiText.Joined(
        listOfNotNull(
            UiText.Dynamic(slot.date.format(DateTexts.SHORT_WEEKDAY_DAY_SHORT_MONTH)),
            UiText.Dynamic(slot.start.format(DateTexts.TIME)),
            slot.room?.trim()?.takeIf(String::isNotEmpty)?.let(::roomShortTitle),
            slot.building?.trim()?.takeIf(String::isNotEmpty)?.let { buildingShortTitle(it) },
        ),
        SEPARATOR,
    )

    private fun value(raw: String?): UiText =
        raw?.trim()?.takeIf(String::isNotEmpty)?.let(UiText::Dynamic) ?: UiText.Res(ScheduleStrings.schedule_bell_value_none)

    private companion object {
        const val SEPARATOR = ", "
    }
}
