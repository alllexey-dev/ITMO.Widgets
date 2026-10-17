package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.core.ui.lessonTypeColorRes
import dev.alllexey.itmowidgets.core.ui.widget.WidgetColors
import dev.alllexey.itmowidgets.core.ui.widget.WidgetIntents
import dev.alllexey.itmowidgets.core.ui.withAppLocale
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.NextLessonMoment
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.NextLessonWidgetContent
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.WidgetContentKind
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.WidgetLesson
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * The next-lesson widget (W1, `SingleLessonWidgetProvider`): small (2 x 2) and medium (4 x 2) in one responsive update
 * from API 31, the layout of the widget's width below it. [now] times the countdown and the progress; the worker
 * draws again every minute while one runs.
 */
object NextLessonWidgetRenderer {

    /** The widget for every size the launcher may give it; [widgetId] null builds a preview without taps. */
    fun views(
        context: Context,
        snapshot: ScheduleWidgetSnapshot,
        palette: WidgetPalette?,
        now: Instant,
        widgetId: Int? = null,
        widthDp: Float = MEDIUM_WIDTH_DP,
    ): RemoteViews {
        val localized = context.withAppLocale()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && widgetId != null) {
            RemoteViews(
                mapOf(
                    SizeF(SMALL_WIDTH_DP, MIN_HEIGHT_DP) to layout(localized, snapshot, palette, now, widgetId, medium = false),
                    SizeF(MEDIUM_WIDTH_DP, MIN_HEIGHT_DP) to layout(localized, snapshot, palette, now, widgetId, medium = true),
                )
            )
        } else {
            layout(localized, snapshot, palette, now, widgetId, medium = widthDp >= MEDIUM_WIDTH_DP)
        }
    }

    fun layout(
        context: Context,
        snapshot: ScheduleWidgetSnapshot,
        palette: WidgetPalette?,
        now: Instant,
        widgetId: Int?,
        medium: Boolean,
    ): RemoteViews {
        val content = snapshot.next
        val colors = WidgetColors(context, palette)
        val texts = ScheduleWidgetTexts(context)
        val scale = snapshot.compactTextSize.scale
        val views = RemoteViews(context.packageName, if (medium) R.layout.widget_next_medium else R.layout.widget_next_small)
        colors.applyBackground(views)
        val lesson = content.lesson
        val hasContent = content.kind == WidgetContentKind.CONTENT && lesson != null
        views.setViewVisibility(R.id.widget_message, if (hasContent) View.GONE else View.VISIBLE)
        views.setViewVisibility(if (medium) R.id.next_content else R.id.next_body, if (hasContent) View.VISIBLE else View.GONE)
        textSize(views, R.id.widget_message, MESSAGE_SP, scale)
        colors.apply(views, R.id.widget_message, "setTextColor", R.color.widget_on_surface_variant)
        if (!hasContent) {
            views.setTextViewText(R.id.widget_message, texts.message(content.kind))
            widgetId?.let { views.setOnClickPendingIntent(android.R.id.background, WidgetIntents.schedule(context, it)) }
            return views
        }
        checkNotNull(lesson)
        bindBody(context, views, colors, texts, content, lesson, now, scale, hero = medium)
        if (medium) {
            colors.tint(views, R.id.next_hero_background, R.color.widget_primary_container)
            bindRest(context, views, colors, texts, content, scale, widgetId)
        }
        widgetId?.let {
            views.setOnClickPendingIntent(android.R.id.background, WidgetIntents.schedule(context, it))
            val target = lessonIntent(context, it, "next", lesson)
            views.setOnClickPendingIntent(if (medium) R.id.next_hero else R.id.next_body, target)
        }
        return views
    }

    private fun bindBody(
        context: Context,
        views: RemoteViews,
        colors: WidgetColors,
        texts: ScheduleWidgetTexts,
        content: NextLessonWidgetContent,
        lesson: WidgetLesson,
        now: Instant,
        scale: Float,
        hero: Boolean,
    ) {
        val ink = if (hero) R.color.widget_on_primary_container else R.color.widget_on_surface
        val quiet = if (hero) R.color.widget_on_primary_container else R.color.widget_on_surface_variant
        views.setTextViewText(R.id.next_headline, texts.headline(content))
        views.setTextViewText(R.id.next_subject, lesson.subject)
        views.setTextViewText(R.id.next_type, texts.type(lesson))
        views.setTextViewText(R.id.next_place, texts.bigPlace(lesson))
        views.setViewVisibility(R.id.next_online_icon, if (lesson.online) View.VISIBLE else View.GONE)
        views.setInt(R.id.next_bar, "setColorFilter", ContextCompat.getColor(context, lessonTypeColorRes(lesson.typeId)))
        colors.apply(views, R.id.next_headline, "setTextColor", if (hero) ink else R.color.widget_primary)
        colors.apply(views, R.id.next_subject, "setTextColor", ink)
        colors.apply(views, R.id.next_type, "setTextColor", quiet)
        colors.apply(views, R.id.next_place, "setTextColor", if (lesson.changed) R.color.widget_warn else ink)
        colors.tint(views, R.id.next_online_icon, ink)
        colors.apply(views, R.id.next_footer, "setTextColor", if (content.moment == NextLessonMoment.NOW) ink else quiet)
        colors.tint(views, R.id.next_footer_icon, quiet)
        // The quiet text of the hero is its ink, a little lighter.
        // View.setAlpha is a RemoteViews method only from API 31.
        if (hero && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) views.setFloat(R.id.next_type, "setAlpha", QUIET_ALPHA)
        textSize(views, R.id.next_headline, HEADLINE_SP, scale)
        textSize(views, R.id.next_subject, SUBJECT_SP, scale)
        textSize(views, R.id.next_type, DETAIL_SP, scale)
        textSize(views, R.id.next_place, PLACE_SP, scale)
        textSize(views, R.id.next_footer, DETAIL_SP, scale)

        views.setTextViewText(R.id.next_footer, texts.footer(content, now))
        val progress = texts.progress(content, now)
        views.setViewVisibility(R.id.next_progress, if (progress != null) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.next_footer_icon, if (progress != null) View.GONE else View.VISIBLE)
        if (progress != null) {
            views.setProgressBar(R.id.next_progress, PROGRESS_MAX, (progress * PROGRESS_MAX).roundToInt(), false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(R.id.next_progress, "setProgressTintList", ColorStateList.valueOf(colors.resolve(ink)))
                // The track is the fill's colour, faint.
                views.setColorStateList(
                    R.id.next_progress,
                    "setProgressBackgroundTintList",
                    ColorStateList.valueOf(ColorUtils.setAlphaComponent(colors.resolve(ink), TRACK_ALPHA))
                )
            }
        }
        views.setImageViewResource(
            R.id.next_footer_icon,
            when {
                content.until == null -> R.drawable.ic_event_available
                content.window -> R.drawable.ic_hourglass_top
                else -> R.drawable.ic_schedule
            }
        )
    }

    private fun bindRest(
        context: Context,
        views: RemoteViews,
        colors: WidgetColors,
        texts: ScheduleWidgetTexts,
        content: NextLessonWidgetContent,
        scale: Float,
        widgetId: Int?,
    ) {
        views.setTextViewText(R.id.next_rest_label, texts.restLabel(content.moment))
        colors.apply(views, R.id.next_rest_label, "setTextColor", R.color.widget_primary)
        textSize(views, R.id.next_rest_label, HEADLINE_SP, scale)
        views.removeAllViews(R.id.next_rest_list)
        content.rest.forEachIndexed { index, lesson ->
            val row = RemoteViews(context.packageName, R.layout.widget_next_rest_row)
            row.setTextViewText(R.id.rest_time, texts.time(lesson))
            row.setTextViewText(R.id.rest_subject, lesson.subject)
            row.setTextViewText(R.id.rest_place, texts.shortPlace(lesson))
            row.setViewVisibility(R.id.rest_online_icon, if (lesson.online && !lesson.queued) View.VISIBLE else View.GONE)
            row.setInt(R.id.rest_bar, "setColorFilter", ContextCompat.getColor(context, lessonTypeColorRes(lesson.typeId)))
            colors.apply(row, R.id.rest_time, "setTextColor", R.color.widget_on_surface_variant)
            colors.apply(row, R.id.rest_subject, "setTextColor", R.color.widget_on_surface)
            val warn = lesson.queued || lesson.changed
            colors.apply(row, R.id.rest_place, "setTextColor", if (warn) R.color.widget_warn else R.color.widget_on_surface_variant)
            colors.tint(row, R.id.rest_online_icon, R.color.widget_on_surface_variant)
            textSize(row, R.id.rest_time, DETAIL_SP, scale)
            textSize(row, R.id.rest_subject, REST_SUBJECT_SP, scale)
            textSize(row, R.id.rest_place, DETAIL_SP, scale)
            widgetId?.let { row.setOnClickPendingIntent(R.id.rest_root, lessonIntent(context, it, "rest$index", lesson)) }
            views.addView(R.id.next_rest_list, row)
        }
        val footer = content.lastEnd?.takeIf { content.rest.size < REST_ROWS && content.rest.isNotEmpty() }
            ?.let { context.getString(R.string.widget_next_last_until, it) }
        views.setViewVisibility(R.id.next_rest_footer, if (footer != null) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.next_rest_footer, footer)
        colors.apply(views, R.id.next_rest_footer, "setTextColor", R.color.widget_on_surface_variant)
        textSize(views, R.id.next_rest_footer, DETAIL_SP, scale)
    }

    /** The lesson sheet of [lesson], its sport lesson for a queue, the schedule when neither is known. */
    internal fun lessonIntent(context: Context, widgetId: Int, slot: String, lesson: WidgetLesson) = when {
        lesson.lesson != null -> WidgetIntents.lesson(context, widgetId, slot, checkNotNull(lesson.lesson))
        lesson.sport != null -> checkNotNull(lesson.sport).let { sport ->
            WidgetIntents.sport(context, widgetId, slot, kotlin.math.abs(sport.lessonId), sport.isPrediction)
        }
        else -> WidgetIntents.schedule(context, widgetId, slot)
    }

    private fun textSize(views: RemoteViews, id: Int, sp: Float, scale: Float) =
        views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp * scale)

    const val SMALL_WIDTH_DP = 110f
    const val MEDIUM_WIDTH_DP = 250f
    private const val MIN_HEIGHT_DP = 110f
    private const val PROGRESS_MAX = 1000
    private const val REST_ROWS = 2
    private const val QUIET_ALPHA = 0.75f
    private const val TRACK_ALPHA = 0x40
    private const val HEADLINE_SP = 13f
    private const val SUBJECT_SP = 15f
    private const val REST_SUBJECT_SP = 13f
    private const val DETAIL_SP = 12f
    private const val PLACE_SP = 28f
    private const val MESSAGE_SP = 14f
}
