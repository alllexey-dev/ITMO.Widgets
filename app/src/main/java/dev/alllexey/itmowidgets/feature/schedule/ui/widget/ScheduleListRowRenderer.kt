package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.core.widget.RemoteViewsCompat
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.ui.withAppLocale
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleListWidgetItem
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleListWidgetItemKind
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One bound day-list row and its stable id. */
class ScheduleListRow(val id: Long, val views: RemoteViews)

/** [palette] is the app's theme while the widgets follow it, null for the rows' own colours. */
class ScheduleListRowRenderer(context: Context, private val palette: WidgetPalette? = null) {
    private val context = context.withAppLocale()

    /** The day-list rows of [snapshot], shared by the widget's collection items and the legacy adapter service. */
    fun rows(snapshot: ScheduleWidgetSnapshot): List<ScheduleListRow> {
        val ids = rowIds(snapshot.lessonList)
        return snapshot.lessonList.mapIndexedNotNull { index, item ->
            render(item, snapshot.lessonListStyle, snapshot.resolvedFullTextSize)
                ?.let { views -> ScheduleListRow(ids[index], views) }
        }
    }

    fun collectionItems(snapshot: ScheduleWidgetSnapshot): RemoteViewsCompat.RemoteCollectionItems {
        val builder = RemoteViewsCompat.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .setViewTypeCount(VIEW_TYPE_COUNT)
        rows(snapshot).forEach { row -> builder.addItem(row.id, row.views) }
        return builder.build()
    }

    fun render(
        item: ScheduleListWidgetItem,
        style: LessonStyle,
        textSize: WidgetTextSize = WidgetTextSize.NORMAL,
    ): RemoteViews? {
        return when (item.kind) {
            ScheduleListWidgetItemKind.LESSON -> {
                val lesson = item.lesson ?: return null
                ScheduleWidgetRenderer.lessonListRow(
                    context = context,
                    lesson = lesson,
                    style = style,
                    textSize = textSize,
                    palette = palette
                )
            }

            ScheduleListWidgetItemKind.HEADER -> header(item, textSize)
            ScheduleListWidgetItemKind.EMPTY_TODAY -> message(
                textSize,
                R.layout.item_lesson_list_empty,
                R.id.no_lessons,
                R.string.schedule_widget_empty_today
            )

            ScheduleListWidgetItemKind.EMPTY_TODAY_AND_TOMORROW -> message(
                textSize,
                R.layout.item_lesson_list_empty,
                R.id.no_lessons,
                R.string.schedule_widget_empty_today_and_tomorrow
            )

            ScheduleListWidgetItemKind.NO_MORE_TODAY -> message(
                textSize,
                R.layout.item_lesson_list_no_more,
                R.id.no_more_lessons,
                R.string.schedule_widget_no_more_today
            )

            ScheduleListWidgetItemKind.END -> message(
                textSize,
                R.layout.item_lesson_list_end,
                R.id.end_marker,
                if (item.tomorrow) {
                    R.string.schedule_widget_end_tomorrow
                } else {
                    R.string.schedule_widget_end_today
                }
            )

            ScheduleListWidgetItemKind.ERROR -> message(
                textSize,
                R.layout.item_lesson_list_error,
                R.id.empty_view,
                R.string.schedule_widget_error
            )

            ScheduleListWidgetItemKind.SIGNED_OUT -> message(
                textSize,
                R.layout.item_lesson_list_error,
                R.id.empty_view,
                R.string.schedule_widget_signed_out
            )

            ScheduleListWidgetItemKind.LOADING -> message(
                textSize,
                R.layout.item_lesson_list_updating,
                R.id.empty_view,
                R.string.schedule_widget_loading
            )
        }
    }

    private fun header(item: ScheduleListWidgetItem, textSize: WidgetTextSize): RemoteViews {
        val date = item.dateIso?.let(LocalDate::parse)
        val formattedDate = date?.format(DATE_FORMATTER).orEmpty()
        val day = context.getString(
            if (item.tomorrow) {
                R.string.schedule_widget_tomorrow
            } else {
                R.string.schedule_widget_today
            }
        )
        return ScheduleWidgetRenderer.listMessageRow(
            context = context,
            layoutId = R.layout.item_lesson_list_day_title,
            textViewId = R.id.day_title,
            text = context.getString(
                R.string.schedule_widget_day_header,
                day,
                formattedDate
            ),
            textSize = textSize,
            palette = palette
        )
    }

    private fun message(textSize: WidgetTextSize, layoutId: Int, textViewId: Int, textId: Int): RemoteViews {
        return ScheduleWidgetRenderer.listMessageRow(
            context = context,
            layoutId = layoutId,
            textViewId = textViewId,
            text = context.getString(textId),
            textSize = textSize,
            palette = palette
        )
    }

    companion object {
        /** Not below the 9 view types 2.2's adapter declared: launchers keep that adapter until the first re-render. */
        const val VIEW_TYPE_COUNT = 9

        private val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))
        private const val FNV_OFFSET = -0x340d631b7bdddcdbL
        private const val FNV_PRIME = 0x100000001b3L
        private const val SEPARATOR = "\u0000"

        /**
         * Ids from row identity (kind, date, start, subject), not position, so a launcher keeps the scroll and
         * recycles the right row when a lesson above changes. A lesson takes the date of its header; equal
         * identities (an official and a pending row at the same time) get the next free id.
         */
        private fun rowIds(items: List<ScheduleListWidgetItem>): LongArray {
            val used = HashSet<Long>()
            var date = ""
            return LongArray(items.size) { index ->
                val item = items[index]
                if (item.kind == ScheduleListWidgetItemKind.HEADER) date = item.dateIso.orEmpty()
                val identity = listOf(
                    item.kind.name,
                    item.dateIso ?: date,
                    item.tomorrow.toString(),
                    item.lesson?.start.orEmpty(),
                    item.lesson?.subject.orEmpty()
                ).joinToString(SEPARATOR)
                var id = fnv1a(identity)
                while (!used.add(id)) id++
                id
            }
        }

        private fun fnv1a(text: String): Long =
            text.encodeToByteArray().fold(FNV_OFFSET) { hash, byte -> (hash xor (byte.toLong() and 0xff)) * FNV_PRIME }
    }
}
