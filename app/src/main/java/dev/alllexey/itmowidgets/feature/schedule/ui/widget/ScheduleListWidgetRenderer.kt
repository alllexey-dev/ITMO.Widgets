package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.RemoteViewsCompat
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.ui.lessonTypeColorRes
import dev.alllexey.itmowidgets.core.ui.widget.WidgetColors
import dev.alllexey.itmowidgets.core.ui.widget.WidgetIntents
import dev.alllexey.itmowidgets.core.ui.withAppLocale
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.DaySummaryKind
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.DayWidgetContent
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.WidgetContentKind
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.WidgetLesson
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.WidgetLessonState
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format

/**
 * The classic list widget (`Список пар`, `DayScheduleWidgetProvider`), the day list of 2.2 drawn from the day widget's
 * content ([ScheduleWidgetSnapshot.day]): a scrolling list of the day's header, its lessons and the end marker, every
 * row opening the schedule. The rows are inline (`RemoteCollectionItems`): no adapter service round trip, so a
 * launcher never shows rows of an older snapshot under a newer shell.
 */
object ScheduleListWidgetRenderer {

    fun views(context: Context, snapshot: ScheduleWidgetSnapshot, palette: WidgetPalette?, widgetId: Int): RemoteViews {
        val views = shell(context, palette)
        RemoteViewsCompat.setRemoteAdapter(context, views, widgetId, R.id.lesson_list, ScheduleListRows(context, palette).collectionItems(snapshot))
        views.setPendingIntentTemplate(R.id.lesson_list, WidgetIntents.schedule(context, widgetId))
        return views
    }

    /**
     * The widget as a plain view for the gallery, the launcher preview images and the screenshots: the same shell and
     * rows, with a local adapter instead of a host's collection.
     */
    fun previewView(context: Context, snapshot: ScheduleWidgetSnapshot, palette: WidgetPalette?): View {
        val root = shell(context, palette).apply(context, FrameLayout(context))
        val rows = ScheduleListRows(context, palette).rows(snapshot)
        root.findViewById<ListView>(R.id.lesson_list).adapter = object : BaseAdapter() {
            override fun getCount() = rows.size
            override fun getItem(position: Int) = rows[position]
            override fun getItemId(position: Int) = rows[position].id
            override fun isEnabled(position: Int) = false
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val remote = rows[position].views
                if (convertView != null && convertView.tag == remote.layoutId) {
                    remote.reapply(context, convertView)
                    return convertView
                }
                return remote.apply(context, parent).apply { tag = remote.layoutId }
            }
        }
        return root
    }

    private fun shell(context: Context, palette: WidgetPalette?): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_lesson_list).apply {
            WidgetColors(context, palette).applyBackground(this)
        }
}

/** One bound list row and its stable id. */
class ScheduleListRow(val id: Long, val views: RemoteViews)

/** The rows of the classic list; [palette] is the app's theme while the widgets follow it, null for their own colours. */
class ScheduleListRows(context: Context, private val palette: WidgetPalette? = null) {
    private val context = context.withAppLocale()
    private val colors = WidgetColors(this.context, palette)
    private val texts = ScheduleWidgetTexts(this.context)

    fun collectionItems(snapshot: ScheduleWidgetSnapshot): RemoteViewsCompat.RemoteCollectionItems {
        val builder = RemoteViewsCompat.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .setViewTypeCount(VIEW_TYPE_COUNT)
        rows(snapshot).forEach { row -> builder.addItem(row.id, row.views) }
        return builder.build()
    }

    /** The rows of [snapshot]'s day, shared by the widget's collection items and the adapter service of 2.2. */
    fun rows(snapshot: ScheduleWidgetSnapshot): List<ScheduleListRow> {
        val day = snapshot.day
        val scale = snapshot.fullTextSize.scale
        val date = day.date?.let(LocalDate::parse)
        val rows = mutableListOf<Pair<String, RemoteViews>>()
        when (day.kind) {
            WidgetContentKind.CONTENT -> {
                if (date != null) rows += "header|$date" to header(date, day.tomorrow, scale)
                day.lessons.forEach { lesson -> rows += "lesson|${lesson.start}|${lesson.subject}" to lesson(lesson, scale) }
                rows += "end|${day.tomorrow}" to message(
                    R.layout.item_lesson_list_end, R.id.end_marker,
                    if (day.tomorrow) R.string.schedule_widget_end_tomorrow else R.string.schedule_widget_end_today, scale
                )
            }
            WidgetContentKind.EMPTY -> rows += "empty" to empty(day, scale)
            WidgetContentKind.LOADING ->
                rows += "loading" to message(R.layout.item_lesson_list_updating, R.id.empty_view, R.string.schedule_widget_loading, scale)
            WidgetContentKind.ERROR ->
                rows += "error" to message(R.layout.item_lesson_list_error, R.id.empty_view, R.string.schedule_widget_error, scale)
            WidgetContentKind.SIGNED_OUT ->
                rows += "signed-out" to message(R.layout.item_lesson_list_error, R.id.empty_view, R.string.schedule_widget_signed_out, scale)
        }
        val used = HashSet<Long>()
        return rows.map { (identity, views) ->
            // Ids from row identity, not position, so a launcher keeps the scroll when a lesson above changes; equal
            // identities (an official and a queued row at one time) take the next free id.
            var id = fnv1a("${day.date}|$identity")
            while (!used.add(id)) id++
            ScheduleListRow(id, views)
        }
    }

    private fun empty(day: DayWidgetContent, scale: Float): RemoteViews = when {
        day.tomorrow ->
            message(R.layout.item_lesson_list_empty, R.id.no_lessons, R.string.schedule_widget_empty_today_and_tomorrow, scale)
        day.summary?.kind == DaySummaryKind.NONE_TODAY ->
            message(R.layout.item_lesson_list_empty, R.id.no_lessons, R.string.schedule_widget_empty_today, scale)
        else -> message(R.layout.item_lesson_list_no_more, R.id.no_more_lessons, R.string.schedule_widget_no_more_today, scale)
    }

    private fun header(date: LocalDate, tomorrow: Boolean, scale: Float): RemoteViews {
        val day = context.getString(if (tomorrow) R.string.schedule_widget_tomorrow else R.string.schedule_widget_today)
        val text = context.getString(R.string.schedule_widget_day_header, day, date.format(DateTexts.DAY_MONTH))
        return row(R.layout.item_lesson_list_day_title, R.id.day_title, text, scale)
    }

    private fun message(layoutId: Int, textViewId: Int, textId: Int, scale: Float): RemoteViews =
        row(layoutId, textViewId, context.getString(textId), scale)

    private fun row(layoutId: Int, textViewId: Int, text: CharSequence, scale: Float): RemoteViews =
        RemoteViews(context.packageName, layoutId).apply {
            MESSAGE_SP[textViewId]?.let { setTextViewTextSize(textViewId, TypedValue.COMPLEX_UNIT_SP, it * scale) }
            MESSAGE_COLORS[textViewId]?.let { colors.apply(this, textViewId, "setTextColor", it) }
            setTextViewText(textViewId, text)
            setOnClickFillInIntent(R.id.item_root, Intent())
        }

    private fun lesson(lesson: WidgetLesson, scale: Float): RemoteViews =
        RemoteViews(context.packageName, R.layout.item_lesson_list_entry).apply {
            LESSON_SP.forEach { (id, sp) -> setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp * scale) }
            colors.applyText(this, LESSON_COLORS)
            setTextViewText(R.id.time_start, lesson.start)
            setTextViewText(R.id.time_end, lesson.end)
            setTextViewText(R.id.title, lesson.subject.ifBlank { context.getString(R.string.schedule_unknown_subject) })
            setTextViewText(R.id.type, when {
                lesson.cancelled -> context.getString(R.string.lesson_pill_cancelled)
                lesson.queued -> texts.queue(lesson)
                else -> texts.type(lesson)
            })
            setImageViewResource(R.id.type_indicator, if (lesson.queued) R.drawable.widget_pending_indicator else R.drawable.shape_circle_filled)
            setInt(R.id.type_indicator, "setColorFilter", ContextCompat.getColor(context, lessonTypeColorRes(lesson.typeId)))
            val place = if (lesson.online) texts.online() else listOfNotNull(texts.room(lesson), texts.building(lesson)).joinToString(" ")
            val details = listOf(place, lesson.teacher.orEmpty()).filter(String::isNotBlank).joinToString(" · ")
            setViewVisibility(R.id.secondary_text, if (details.isBlank()) View.GONE else View.VISIBLE)
            setTextViewText(R.id.secondary_text, details)
            fade(this, if (lesson.state == WidgetLessonState.DONE || lesson.cancelled) DONE_ALPHA else 1f)
            setOnClickFillInIntent(R.id.item_root, Intent())
        }

    /**
     * `View.setAlpha` is a RemoteViews method only from API 31; below it the row fails to inflate. There every label
     * gets its colour with the alpha folded in and the type indicator an image alpha, set on every render so a reused
     * row drops an earlier fade.
     */
    private fun fade(views: RemoteViews, alpha: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setFloat(R.id.item_root, "setAlpha", alpha)
            return
        }
        LESSON_COLORS.forEach { (id, res) -> views.setTextColor(id, colors.resolve(res).withAlpha(alpha)) }
        views.setInt(R.id.type_indicator, "setImageAlpha", (alpha * OPAQUE).roundToInt())
    }

    private fun Int.withAlpha(alpha: Float): Int =
        ColorUtils.setAlphaComponent(this, (Color.alpha(this) * alpha).roundToInt())

    companion object {
        /** Not below the 9 view types 2.2's adapter declared: launchers keep that adapter until the first re-render. */
        const val VIEW_TYPE_COUNT = 9

        private const val DONE_ALPHA = 0.62f
        private const val OPAQUE = 255
        private const val FNV_OFFSET = -0x340d631b7bdddcdbL
        private const val FNV_PRIME = 0x100000001b3L

        // The layouts' own sizes and colours; WidgetRenderingTest inflates every layout.
        private val LESSON_SP = mapOf(
            R.id.time_start to 12f,
            R.id.time_end to 10f,
            R.id.title to 13f,
            R.id.type to 10f,
            R.id.secondary_text to 10f
        )
        private val LESSON_COLORS = mapOf(
            R.id.time_start to R.color.widget_on_surface,
            R.id.time_end to R.color.widget_on_surface_variant,
            R.id.title to R.color.widget_on_surface,
            R.id.type to R.color.widget_on_surface_variant,
            R.id.secondary_text to R.color.widget_on_surface_variant
        )
        private val MESSAGE_SP = mapOf(
            R.id.day_title to 11f,
            R.id.end_marker to 11f,
            R.id.no_lessons to 14f,
            R.id.no_more_lessons to 14f,
            R.id.empty_view to 14f
        )
        private val MESSAGE_COLORS = mapOf(
            R.id.day_title to R.color.widget_on_surface_variant,
            R.id.end_marker to R.color.widget_on_surface_variant,
            R.id.no_lessons to R.color.widget_on_surface,
            R.id.no_more_lessons to R.color.widget_on_surface,
            R.id.empty_view to R.color.widget_on_surface
        )

        private fun fnv1a(text: String): Long =
            text.encodeToByteArray().fold(FNV_OFFSET) { hash, byte -> (hash xor (byte.toLong() and 0xff)) * FNV_PRIME }
    }
}
