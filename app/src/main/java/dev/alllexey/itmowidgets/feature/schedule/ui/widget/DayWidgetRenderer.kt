package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.content.Context
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.core.ui.lessonTypeColorRes
import dev.alllexey.itmowidgets.core.ui.widget.WidgetColors
import dev.alllexey.itmowidgets.core.ui.widget.WidgetIntents
import dev.alllexey.itmowidgets.core.ui.withAppLocale
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.DayWidgetContent
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.WidgetContentKind
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.WidgetLesson
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.WidgetLessonState
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate

/**
 * The day widget (W2, `DayScheduleWidgetProvider`): the header and the day to scale with the `сейчас` line. From API
 * 31 one layout per launcher size, its rows placed at their times (`setViewLayoutMargin`, `setViewLayoutHeight`);
 * below it the lessons stack without the axis.
 */
object DayWidgetRenderer {

    /** The widget for [sizes] (dp) of the launcher; [widgetId] null builds a preview without taps. */
    fun views(
        context: Context,
        snapshot: ScheduleWidgetSnapshot,
        palette: WidgetPalette?,
        sizes: List<SizeF>,
        widgetId: Int? = null,
    ): RemoteViews {
        val localized = context.withAppLocale()
        val shown = sizes.ifEmpty { listOf(DEFAULT_SIZE) }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && shown.size > 1) {
            RemoteViews(shown.distinct().associateWith { layout(localized, snapshot, palette, it, widgetId) })
        } else {
            layout(localized, snapshot, palette, shown.first(), widgetId)
        }
    }

    fun layout(base: Context, snapshot: ScheduleWidgetSnapshot, palette: WidgetPalette?, size: SizeF, widgetId: Int?): RemoteViews {
        // Russian plurals whatever the system language (AppLocale).
        val context = base.withAppLocale()
        val day = snapshot.day
        val colors = WidgetColors(context, palette)
        val texts = ScheduleWidgetTexts(context)
        val scale = snapshot.fullTextSize.scale
        val views = RemoteViews(context.packageName, R.layout.widget_day)
        colors.applyBackground(views)
        colors.apply(views, R.id.day_title, "setTextColor", R.color.widget_on_surface)
        colors.apply(views, R.id.day_subtitle, "setTextColor", R.color.widget_on_surface_variant)
        colors.apply(views, R.id.widget_message, "setTextColor", R.color.widget_on_surface_variant)
        textSize(views, R.id.day_title, TITLE_SP, scale)
        textSize(views, R.id.day_subtitle, SUBTITLE_SP, scale)
        textSize(views, R.id.widget_message, MESSAGE_SP, scale)
        widgetId?.let {
            views.setOnClickPendingIntent(android.R.id.background, WidgetIntents.schedule(context, it))
            views.setOnClickPendingIntent(R.id.day_header, WidgetIntents.schedule(context, it, "header"))
        }
        val date = day.date?.let(LocalDate::parse)
        views.setViewVisibility(R.id.day_header, if (date != null) View.VISIBLE else View.GONE)
        if (date != null) {
            views.setTextViewText(R.id.day_title, texts.dayTitle(date, day.tomorrow))
            views.setTextViewText(R.id.day_subtitle, day.summary?.let(texts::summary))
        }
        val content = day.kind == WidgetContentKind.CONTENT && day.lessons.isNotEmpty()
        val scaled = content && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        views.setViewVisibility(R.id.widget_message, if (content) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.day_canvas, if (scaled) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.day_list, if (content && !scaled) View.VISIBLE else View.GONE)
        views.removeAllViews(R.id.day_canvas)
        views.removeAllViews(R.id.day_list)
        if (!content) {
            views.setTextViewText(R.id.widget_message, texts.message(day.kind, emptyToday = date != null))
            return views
        }
        if (scaled) {
            canvas(context, views, colors, texts, day, size, scale, widgetId)
        } else {
            day.lessons.forEachIndexed { index, lesson ->
                views.addView(R.id.day_list, lessonRow(context, colors, texts, lesson, scale, widgetId, index, heightDp = null))
            }
        }
        return views
    }

    /** API 31+: the hours, the gaps, the `сейчас` line under the lessons and its time on top, each at its minute. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun canvas(
        context: Context,
        views: RemoteViews,
        colors: WidgetColors,
        texts: ScheduleWidgetTexts,
        day: DayWidgetContent,
        size: SizeF,
        scale: Float,
        widgetId: Int?,
    ) {
        val height = (size.height - CHROME_HEIGHT_DP * scale).coerceAtLeast(MIN_LESSON_DP)
        val layout = DayTimelineLayout(
            lessons = day.lessons.map { it.startMinute..it.endMinute },
            axisStart = day.axisStart,
            axisEnd = day.axisEnd,
            height = height,
            minLesson = MIN_LESSON_DP * scale,
        )
        val now = day.now
        for (hour in (day.axisStart / MINUTES_PER_HOUR + 1)..(day.axisEnd / MINUTES_PER_HOUR)) {
            // The time chip of the line covers the hour it is near.
            if (now != null && kotlin.math.abs(hour * MINUTES_PER_HOUR - now) < MINUTES_PER_HOUR / 2) continue
            val label = RemoteViews(context.packageName, R.layout.widget_day_hour)
            label.setTextViewText(R.id.hour_label, hour.toString())
            colors.apply(label, R.id.hour_label, "setTextColor", R.color.widget_on_surface_variant)
            textSize(label, R.id.hour_label, HOUR_SP, scale)
            place(label, R.id.hour_root, layout.y(hour * MINUTES_PER_HOUR) - HOUR_HEIGHT_DP / 2, null)
            views.addView(R.id.day_canvas, label)
        }
        day.gaps.forEach { gap ->
            val top = layout.y(gap.startMinute)
            val bottom = layout.y(gap.endMinute)
            if (bottom - top < MIN_GAP_DP) return@forEach
            val pill = RemoteViews(context.packageName, R.layout.widget_day_gap)
            pill.setTextViewText(R.id.gap_label, texts.gap(gap.endMinute - gap.startMinute))
            colors.apply(pill, R.id.gap_label, "setTextColor", R.color.widget_on_surface_variant)
            textSize(pill, R.id.gap_label, GAP_SP, scale)
            place(pill, R.id.gap_root, top, bottom - top, start = AXIS_WIDTH_DP)
            views.addView(R.id.day_canvas, pill)
        }
        val line = now?.let { RemoteViews(context.packageName, R.layout.widget_day_now) }
        if (line != null && now != null) {
            colors.tint(line, R.id.now_line, R.color.widget_primary)
            place(line, R.id.now_root, layout.y(now) - NOW_HEIGHT_DP / 2, null)
            line.setViewVisibility(R.id.now_chip_background, View.GONE)
            line.setViewVisibility(R.id.now_chip, View.GONE)
            views.addView(R.id.day_canvas, line)
        }
        day.lessons.forEachIndexed { index, lesson ->
            val (top, blockHeight) = layout.lesson(index)
            val row = lessonRow(context, colors, texts, lesson, scale, widgetId, index, heightDp = blockHeight - BLOCK_GAP_DP)
            place(row, R.id.lesson_root, top, blockHeight - BLOCK_GAP_DP, start = AXIS_WIDTH_DP)
            if (lesson.state == WidgetLessonState.NOW && now != null) {
                val elapsed = (layout.y(now) - top).coerceIn(0f, blockHeight - BLOCK_GAP_DP)
                row.setViewVisibility(R.id.lesson_elapsed, View.VISIBLE)
                row.setViewLayoutHeight(R.id.lesson_elapsed, elapsed, TypedValue.COMPLEX_UNIT_DIP)
                colors.tint(row, R.id.lesson_elapsed, R.color.widget_primary)
                row.setInt(R.id.lesson_elapsed, "setImageAlpha", ELAPSED_ALPHA)
            }
            views.addView(R.id.day_canvas, row)
        }
        if (line != null && now != null) {
            // The chip again on top of the lessons, the line itself stays under them.
            val chip = RemoteViews(context.packageName, R.layout.widget_day_now)
            chip.setViewVisibility(R.id.now_line, View.GONE)
            chip.setTextViewText(R.id.now_chip, "%02d:%02d".format(now / MINUTES_PER_HOUR, now % MINUTES_PER_HOUR))
            colors.tint(chip, R.id.now_chip_background, R.color.widget_primary)
            colors.apply(chip, R.id.now_chip, "setTextColor", R.color.widget_surface)
            textSize(chip, R.id.now_chip, HOUR_SP, scale)
            // The chip stays inside the canvas at its top.
            place(chip, R.id.now_root, (layout.y(now) - NOW_HEIGHT_DP / 2).coerceAtLeast(0f), null)
            views.addView(R.id.day_canvas, chip)
        }
    }

    private fun lessonRow(
        context: Context,
        colors: WidgetColors,
        texts: ScheduleWidgetTexts,
        lesson: WidgetLesson,
        scale: Float,
        widgetId: Int?,
        index: Int,
        heightDp: Float?,
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_day_lesson)
        val current = lesson.state == WidgetLessonState.NOW
        val ink = if (current) R.color.widget_on_primary_container else R.color.widget_on_surface
        val quiet = if (current) R.color.widget_on_primary_container else R.color.widget_on_surface_variant
        colors.tint(row, R.id.lesson_background, if (current) R.color.widget_primary_container else R.color.widget_surface_container_high)
        row.setInt(R.id.lesson_bar, "setColorFilter", ContextCompat.getColor(context, lessonTypeColorRes(lesson.typeId)))
        row.setTextViewText(R.id.lesson_subject, lesson.subject)
        row.setTextViewText(R.id.lesson_room, if (lesson.online) texts.online() else texts.room(lesson) ?: texts.building(lesson))
        row.setViewVisibility(R.id.lesson_online_icon, if (lesson.online) View.VISIBLE else View.GONE)
        row.setTextViewText(R.id.lesson_time, listOfNotNull(texts.time(lesson), lesson.teacher).joinToString(", "))
        val building = texts.building(lesson)?.takeIf { texts.room(lesson) != null }
        row.setViewVisibility(R.id.lesson_building, if (building != null) View.VISIBLE else View.GONE)
        row.setTextViewText(R.id.lesson_building, building)
        val pill = when {
            lesson.cancelled -> R.string.lesson_pill_cancelled
            lesson.queued -> R.string.lesson_pill_queue
            else -> null
        }
        row.setViewVisibility(R.id.lesson_pill, if (pill != null) View.VISIBLE else View.GONE)
        pill?.let { row.setTextViewText(R.id.lesson_pill, context.getString(it)) }
        colors.apply(row, R.id.lesson_subject, "setTextColor", ink)
        colors.apply(row, R.id.lesson_room, "setTextColor", if (lesson.changed) R.color.widget_warn else ink)
        colors.tint(row, R.id.lesson_online_icon, ink)
        colors.apply(row, R.id.lesson_time, "setTextColor", quiet)
        colors.apply(row, R.id.lesson_building, "setTextColor", quiet)
        textSize(row, R.id.lesson_subject, SUBJECT_SP, scale)
        textSize(row, R.id.lesson_room, SUBJECT_SP, scale)
        textSize(row, R.id.lesson_time, DETAIL_SP, scale)
        textSize(row, R.id.lesson_building, DETAIL_SP, scale)
        textSize(row, R.id.lesson_pill, DETAIL_SP, scale)
        // A block too low for two lines keeps the subject and the room.
        val twoLines = heightDp == null || heightDp >= TWO_LINES_DP * scale
        row.setViewVisibility(R.id.lesson_second_line, if (twoLines) View.VISIBLE else View.GONE)
        val faded = lesson.state == WidgetLessonState.DONE || lesson.cancelled
        if (faded && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) row.setFloat(R.id.lesson_root, "setAlpha", DONE_ALPHA)
        if (heightDp == null) {
            row.setViewPadding(R.id.lesson_content, dp(context, 6), dp(context, 6), dp(context, 10), dp(context, 6))
        }
        widgetId?.let {
            row.setOnClickPendingIntent(R.id.lesson_root, NextLessonWidgetRenderer.lessonIntent(context, it, "day$index", lesson))
        }
        return row
    }

    /** [viewId] at [top] dp of the canvas, [height] dp high (null: its own), [start] dp from its left edge. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun place(views: RemoteViews, viewId: Int, top: Float, height: Float?, start: Float = 0f) {
        views.setViewLayoutMargin(viewId, RemoteViews.MARGIN_TOP, top, TypedValue.COMPLEX_UNIT_DIP)
        if (start > 0f) views.setViewLayoutMargin(viewId, RemoteViews.MARGIN_START, start, TypedValue.COMPLEX_UNIT_DIP)
        height?.let { views.setViewLayoutHeight(viewId, it, TypedValue.COMPLEX_UNIT_DIP) }
    }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private fun textSize(views: RemoteViews, id: Int, sp: Float, scale: Float) =
        views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp * scale)

    /** The launcher's 4 x 4 cells, when it reports no size. */
    val DEFAULT_SIZE = SizeF(320f, 380f)
    private const val MINUTES_PER_HOUR = 60
    /** The padding, the header and the gap above the canvas. */
    private const val CHROME_HEIGHT_DP = 80f
    private const val AXIS_WIDTH_DP = 32f
    private const val MIN_LESSON_DP = 22f
    private const val TWO_LINES_DP = 34f
    private const val MIN_GAP_DP = 20f
    private const val BLOCK_GAP_DP = 3f
    private const val HOUR_HEIGHT_DP = 14f
    private const val NOW_HEIGHT_DP = 18f
    private const val DONE_ALPHA = 0.5f
    private const val ELAPSED_ALPHA = 40
    private const val TITLE_SP = 16f
    private const val SUBTITLE_SP = 12f
    private const val SUBJECT_SP = 13f
    private const val DETAIL_SP = 11f
    private const val HOUR_SP = 11f
    private const val GAP_SP = 12f
    private const val MESSAGE_SP = 14f
}
