package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.durationText
import dev.alllexey.itmowidgets.core.ui.lessonTypeColorRes
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.ui.widget.WidgetColors
import dev.alllexey.itmowidgets.core.ui.widget.WidgetIntents
import dev.alllexey.itmowidgets.core.ui.withAppLocale
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.DayWidgetContent
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.DaySummaryKind
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.WidgetContentKind
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.WidgetLesson
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.WidgetLessonState
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.cardsFirstOffset
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.cardsPages
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format

/**
 * The cards widget (`CardsWidgetProvider`): a page of [cardsPages] with its title, the prev and next day buttons and
 * a card per lesson left that day, as many as fit the launcher's size; the rest fold into one last row. The current
 * lesson shows its elapsed share as M3 Expressive's wavy indicator. From API 31 one layout per launcher size.
 */
object CardsWidgetRenderer {

    /** Page [page] of the widget for [sizes] (dp) of the launcher; [widgetId] null builds a preview without taps. */
    fun views(
        context: Context,
        snapshot: ScheduleWidgetSnapshot,
        palette: WidgetPalette?,
        sizes: List<SizeF>,
        page: Int,
        widgetId: Int? = null,
    ): RemoteViews {
        val shown = sizes.ifEmpty { listOf(DEFAULT_SIZE) }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && shown.size > 1) {
            RemoteViews(shown.distinct().associateWith { layout(context, snapshot, palette, it, page, widgetId) })
        } else {
            layout(context, snapshot, palette, shown.first(), page, widgetId)
        }
    }

    fun layout(
        base: Context,
        snapshot: ScheduleWidgetSnapshot,
        palette: WidgetPalette?,
        size: SizeF,
        page: Int,
        widgetId: Int?,
    ): RemoteViews {
        // Russian plurals whatever the system language (AppLocale).
        val context = base.withAppLocale()
        val colors = WidgetColors(context, palette)
        val texts = ScheduleWidgetTexts(context)
        val scale = snapshot.fullTextSize.scale
        val pages = snapshot.cardsPages()
        val index = page.coerceIn(0, pages.lastIndex)
        val day = pages[index]
        val offset = snapshot.cardsFirstOffset() + index
        val views = RemoteViews(context.packageName, R.layout.widget_cards)
        colors.applyBackground(views)
        colors.apply(views, R.id.cards_title, "setTextColor", R.color.widget_on_surface)
        colors.apply(views, R.id.cards_subtitle, "setTextColor", R.color.widget_on_surface_variant)
        colors.apply(views, R.id.widget_message, "setTextColor", R.color.widget_on_surface_variant)
        textSize(views, R.id.cards_title, TITLE_SP, scale)
        textSize(views, R.id.cards_subtitle, SUBTITLE_SP, scale)
        textSize(views, R.id.widget_message, MESSAGE_SP, scale)
        widgetId?.let {
            views.setOnClickPendingIntent(android.R.id.background, WidgetIntents.schedule(context, it))
            views.setOnClickPendingIntent(R.id.cards_title_box, WidgetIntents.schedule(context, it, "header"))
        }
        val date = day.date?.let(LocalDate::parse)
        views.setViewVisibility(R.id.cards_header, if (date != null) View.VISIBLE else View.GONE)
        if (date != null) {
            views.setTextViewText(R.id.cards_title, title(context, date, offset))
            views.setTextViewText(R.id.cards_subtitle, context.getString(R.string.widget_cards_dot, date.format(DateTexts.DAY_MONTH), count(context, texts, day, offset)))
            arrow(views, colors, R.id.cards_prev, R.id.cards_prev_background, R.id.cards_prev_icon, index - 1, pages.indices, widgetId, context)
            arrow(views, colors, R.id.cards_next, R.id.cards_next_background, R.id.cards_next_icon, index + 1, pages.indices, widgetId, context)
        }
        // Today's past lessons give their room to the ones left: the widget never scrolls.
        val lessons = day.lessons.filter { offset != 0 || it.state != WidgetLessonState.DONE }
        val content = day.kind == WidgetContentKind.CONTENT && lessons.isNotEmpty()
        views.setViewVisibility(R.id.widget_message, if (content) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.cards_list, if (content) View.VISIBLE else View.GONE)
        views.removeAllViews(R.id.cards_list)
        if (!content) {
            views.setTextViewText(R.id.widget_message, message(context, texts, day))
            return views
        }
        val highlighted = lessons.indexOfFirst { it.state == WidgetLessonState.NOW }
            .takeIf { it >= 0 } ?: lessons.indexOfFirst { !it.cancelled && !it.queued }
        val now = day.now.takeIf { offset == 0 }
        val heights = lessons.map { (if (it.state == WidgetLessonState.NOW && now != null) CURRENT_CARD else CARD).dp(scale) }
        val available = size.height - CHROME.dp(scale)
        val count = fit(heights, MORE.dp(scale), available)
        lessons.take(count).forEachIndexed { position, lesson ->
            views.addView(R.id.cards_list, card(context, colors, texts, lesson, position == highlighted, now, scale, widgetId, position))
        }
        val rest = lessons.drop(count)
        if (rest.isNotEmpty() && heights.take(count).sum() + MORE.dp(scale) <= available) {
            views.addView(R.id.cards_list, more(context, colors, texts, rest, scale, widgetId))
        }
        return views
    }

    /**
     * How many of the cards of [heights] (dp) to show in [available] dp: all that fit, else as many as fit above the
     * [more] row folding the rest, at least one.
     */
    internal fun fit(heights: List<Float>, more: Float, available: Float): Int {
        if (heights.sum() <= available) return heights.size
        var used = more
        var count = 0
        for (height in heights) {
            if (used + height > available) break
            used += height
            count++
        }
        return count.coerceAtLeast(minOf(1, heights.size))
    }

    private fun title(context: Context, date: LocalDate, offset: Int): String = when (offset) {
        0 -> context.getString(R.string.schedule_widget_today)
        1 -> context.getString(R.string.schedule_widget_tomorrow)
        else -> date.format(DateTexts.WEEKDAY).replaceFirstChar(Char::uppercaseChar)
    }

    /** `ещё 3 пары` on today (`4 пары` before the first), `на сегодня всё`; `4 пары и спорт` or `пар нет` on another day. */
    private fun count(context: Context, texts: ScheduleWidgetTexts, day: DayWidgetContent, offset: Int): String {
        val summary = day.summary?.takeIf { day.kind == WidgetContentKind.CONTENT && (it.lessons > 0 || it.sport || it.kind == DaySummaryKind.OVER) }
            ?: return context.getString(R.string.widget_cards_none)
        return when {
            offset == 0 && summary.kind == DaySummaryKind.REMAINING ->
                context.getString(R.string.widget_cards_remaining, texts.lessons(summary))
            summary.kind == DaySummaryKind.PLANNED || summary.kind == DaySummaryKind.REMAINING -> texts.lessons(summary)
            else -> texts.summary(summary)
        }
    }

    private fun message(context: Context, texts: ScheduleWidgetTexts, day: DayWidgetContent): String = when (day.kind) {
        WidgetContentKind.CONTENT -> context.getString(R.string.schedule_widget_no_more_today)
        WidgetContentKind.EMPTY -> context.getString(R.string.widget_day_empty)
        else -> texts.message(day.kind)
    }

    private fun arrow(
        views: RemoteViews,
        colors: WidgetColors,
        buttonId: Int,
        backgroundId: Int,
        iconId: Int,
        target: Int,
        pages: IntRange,
        widgetId: Int?,
        context: Context,
    ) {
        val enabled = target in pages
        colors.tint(views, backgroundId, R.color.widget_surface_container_high)
        colors.tint(views, iconId, R.color.widget_on_surface)
        views.setInt(iconId, "setImageAlpha", if (enabled) OPAQUE else DISABLED_ALPHA)
        if (enabled && widgetId != null) views.setOnClickPendingIntent(buttonId, CardsWidgetProvider.pageIntent(context, widgetId, target))
    }

    private fun card(
        context: Context,
        colors: WidgetColors,
        texts: ScheduleWidgetTexts,
        lesson: WidgetLesson,
        highlighted: Boolean,
        now: Int?,
        scale: Float,
        widgetId: Int?,
        position: Int,
    ): RemoteViews {
        val card = RemoteViews(context.packageName, R.layout.widget_cards_lesson)
        val ink = if (highlighted) R.color.widget_on_primary_container else R.color.widget_on_surface
        val quiet = if (highlighted) R.color.widget_on_primary_container else R.color.widget_on_surface_variant
        colors.tint(card, R.id.card_background, if (highlighted) R.color.widget_primary_container else R.color.widget_surface_container_high)
        card.setTextViewText(R.id.card_start, lesson.start)
        card.setTextViewText(R.id.card_end, lesson.end)
        card.setTextViewText(R.id.card_subject, lesson.subject)
        val place = if (lesson.online) texts.online() else texts.room(lesson) ?: texts.building(lesson)
        card.setTextViewText(R.id.card_place, listOfNotNull(place, lesson.teacher).joinToString(PLACE_SEPARATOR))
        colors.apply(card, R.id.card_start, "setTextColor", ink)
        colors.apply(card, R.id.card_end, "setTextColor", quiet)
        colors.apply(card, R.id.card_subject, "setTextColor", ink)
        colors.apply(card, R.id.card_place, "setTextColor", if (lesson.changed) R.color.widget_warn else quiet)
        chip(context, colors, texts, card, lesson)
        textSize(card, R.id.card_start, START_SP, scale)
        textSize(card, R.id.card_end, DETAIL_SP, scale)
        textSize(card, R.id.card_subject, SUBJECT_SP, scale)
        textSize(card, R.id.card_place, DETAIL_SP, scale)
        textSize(card, R.id.card_chip, CHIP_SP, scale)
        card.setInt(R.id.card_start, "setMinWidth", (TIME_WIDTH_DP * scale * context.resources.displayMetrics.density).toInt())
        if (lesson.state == WidgetLessonState.NOW && now != null) progress(context, colors, card, lesson, now, scale)
        if (lesson.cancelled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) card.setFloat(R.id.card_root, "setAlpha", CANCELLED_ALPHA)
        widgetId?.let {
            card.setOnClickPendingIntent(R.id.card_root, NextLessonWidgetRenderer.lessonIntent(context, it, "cards$position", lesson))
        }
        return card
    }

    /** The lesson type on a pill of its colour, or `отменена` and the queue on the warning tone. */
    private fun chip(context: Context, colors: WidgetColors, texts: ScheduleWidgetTexts, card: RemoteViews, lesson: WidgetLesson) {
        val status = when {
            lesson.cancelled -> context.getString(R.string.lesson_pill_cancelled)
            lesson.queued -> texts.queue(lesson)
            else -> null
        }
        if (status != null) {
            card.setTextViewText(R.id.card_chip, status)
            colors.tint(card, R.id.card_chip_background, R.color.widget_warn_container)
            colors.apply(card, R.id.card_chip, "setTextColor", R.color.widget_on_warn_container)
            return
        }
        val type = ContextCompat.getColor(context, lessonTypeColorRes(lesson.typeId))
        card.setTextViewText(R.id.card_chip, texts.type(lesson))
        // The type's colour faint behind its name, the name dark on light and light on dark for contrast.
        card.setInt(R.id.card_chip_background, "setColorFilter", type)
        card.setInt(R.id.card_chip_background, "setImageAlpha", CHIP_ALPHA)
        colors.applyPair(
            card, R.id.card_chip, "setTextColor",
            light = ColorUtils.blendARGB(type, Color.BLACK, CHIP_INK_SHADE),
            dark = ColorUtils.blendARGB(type, Color.WHITE, CHIP_INK_TINT),
        )
    }

    /** The wave up to the elapsed share, the track with its stop dot after a gap, and the minutes left. */
    private fun progress(context: Context, colors: WidgetColors, card: RemoteViews, lesson: WidgetLesson, now: Int, scale: Float) {
        val total = (lesson.endMinute - lesson.startMinute).coerceAtLeast(1)
        val elapsed = ((now - lesson.startMinute).toFloat() / total).coerceIn(0f, 1f)
        card.setViewVisibility(R.id.card_progress, View.VISIBLE)
        card.setInt(R.id.card_wave, "setImageLevel", (elapsed * LEVELS).toInt())
        card.setInt(R.id.card_track, "setImageLevel", ((1 - elapsed) * LEVELS - GAP_LEVELS).toInt().coerceAtLeast(0))
        colors.tint(card, R.id.card_wave, R.color.widget_primary)
        colors.tint(card, R.id.card_track, R.color.widget_on_primary_container)
        card.setInt(R.id.card_track, "setImageAlpha", TRACK_ALPHA)
        val left = durationText((lesson.endMinute - now).coerceAtLeast(0)).resolve(context)
        card.setTextViewText(R.id.card_left, context.getString(R.string.widget_next_left, left))
        colors.apply(card, R.id.card_left, "setTextColor", R.color.widget_on_primary_container)
        textSize(card, R.id.card_left, LEFT_SP, scale)
    }

    /** `13:30 · Лабораторная` and `ещё 2 пары`: the first lesson that did not fit and how many did not. */
    private fun more(
        context: Context,
        colors: WidgetColors,
        texts: ScheduleWidgetTexts,
        rest: List<WidgetLesson>,
        scale: Float,
        widgetId: Int?,
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_cards_more)
        val first = rest.first()
        row.setTextViewText(R.id.more_text, context.getString(R.string.widget_cards_dot, first.start, texts.type(first)))
        row.setTextViewText(R.id.more_count, context.resources.getQuantityString(R.plurals.widget_cards_more, rest.size, rest.size))
        colors.tint(row, R.id.more_background, R.color.widget_surface_container_high)
        colors.tint(row, R.id.more_pill_background, R.color.widget_tertiary)
        row.setInt(R.id.more_pill_background, "setImageAlpha", CHIP_ALPHA)
        colors.apply(row, R.id.more_text, "setTextColor", R.color.widget_on_surface_variant)
        colors.apply(row, R.id.more_count, "setTextColor", R.color.widget_on_surface)
        textSize(row, R.id.more_text, MORE_SP, scale)
        textSize(row, R.id.more_count, CHIP_SP, scale)
        widgetId?.let { row.setOnClickPendingIntent(R.id.more_root, WidgetIntents.schedule(context, it, "cards-more")) }
        return row
    }

    private fun textSize(views: RemoteViews, id: Int, sp: Float, scale: Float) =
        views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp * scale)

    /** A height in dp: [fixed] paddings and margins plus [text] lines that grow with the text size. */
    private class Height(val fixed: Float, val text: Float) {
        fun dp(scale: Float) = fixed + text * scale
    }

    /** The launcher's 4 x 4 cells, when it reports no size. */
    val DEFAULT_SIZE = SizeF(320f, 330f)
    // Measured on the screenshots at the normal text size, plus 2 dp for launcher fonts.
    private val CHROME = Height(fixed = 30f, text = 48f)
    private val CARD = Height(fixed = 24f, text = 41f)
    private val CURRENT_CARD = Height(fixed = 32f, text = 60f)
    private val MORE = Height(fixed = 18f, text = 20f)
    private const val PLACE_SEPARATOR = " · "
    private const val TIME_WIDTH_DP = 58f
    private const val LEVELS = 10_000f
    /** The gap between the wave's end and the track, in image levels of the indicator's width. */
    private const val GAP_LEVELS = 350f
    private const val OPAQUE = 255
    private const val DISABLED_ALPHA = 97
    private const val CHIP_ALPHA = 72
    private const val TRACK_ALPHA = 90
    private const val CHIP_INK_SHADE = 0.45f
    private const val CHIP_INK_TINT = 0.5f
    private const val CANCELLED_ALPHA = 0.5f
    private const val TITLE_SP = 20f
    private const val SUBTITLE_SP = 13f
    private const val START_SP = 21f
    private const val SUBJECT_SP = 16f
    private const val DETAIL_SP = 13f
    private const val CHIP_SP = 12f
    private const val LEFT_SP = 14f
    private const val MORE_SP = 14f
    private const val MESSAGE_SP = 14f
}
