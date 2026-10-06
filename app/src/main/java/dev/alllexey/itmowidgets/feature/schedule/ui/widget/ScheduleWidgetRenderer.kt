package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.RemoteViewsCompat
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.ui.navigation.AppEntryIntentFactory
import dev.alllexey.itmowidgets.core.ui.withAppLocale
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetLesson
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetLessonState
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetPendingStatus
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.ui.colorRes
import dev.alllexey.itmowidgets.feature.schedule.ui.nameRes
import dev.alllexey.itmowidgets.feature.schedule.ui.shortTitle
import kotlin.math.roundToInt

object ScheduleWidgetRenderer {

    fun renderSingle(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        snapshot: ScheduleWidgetSnapshot,
    ) {
        val views = singleLessonViews(context, snapshot)
        views.setOnClickPendingIntent(
            R.id.lesson_widget_root,
            openSchedulePendingIntent(context, appWidgetId)
        )
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    /** Builds the exact widget layout without registering a host or issuing an update. */
    fun singleLessonViews(context: Context, snapshot: ScheduleWidgetSnapshot): RemoteViews {
        val content = snapshot.singleLesson
        val localized = context.withAppLocale()
        val views = RemoteViews(context.packageName, singleLessonLayout(snapshot.singleLessonStyle))
        applyTextSize(views, singleLessonTextSp, snapshot.resolvedCompactTextSize)
        val lesson = content.lesson
        if (lesson == null) {
            bindSingleMessage(localized, views, content.kind)
        } else {
            views.setViewVisibility(R.id.widget_message, View.GONE)
            views.setViewVisibility(R.id.lesson_content, View.VISIBLE)
            bindLesson(localized, views, lesson, snapshot.singleLessonStyle)
            applyLessonAlpha(localized, views, R.id.lesson_content, singleLessonTextColors, lessonAlpha(lesson))
            views.setViewVisibility(R.id.more_lessons_layout, View.VISIBLE)
            views.setTextViewText(
                R.id.more_lessons_text,
                supportingText(localized, lesson.state, content.remainingLessons)
            )
        }
        return views
    }

    /**
     * A full update with the rows inline (`RemoteCollectionItems`): no adapter service round trip and no separate
     * data-changed notification, so a launcher never shows rows from an older snapshot under a newer shell.
     */
    fun renderList(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        snapshot: ScheduleWidgetSnapshot,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_lesson_list)
        RemoteViewsCompat.setRemoteAdapter(
            context,
            views,
            appWidgetId,
            R.id.lesson_list,
            ScheduleListRowRenderer(context).collectionItems(snapshot)
        )
        views.setPendingIntentTemplate(
            R.id.lesson_list,
            openSchedulePendingIntent(context, appWidgetId)
        )
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    fun lessonListRow(
        context: Context,
        lesson: ScheduleWidgetLesson,
        style: LessonStyle,
        textSize: WidgetTextSize = WidgetTextSize.NORMAL,
    ): RemoteViews {
        val localized = context.withAppLocale()
        return RemoteViews(context.packageName, lessonListLayout(style)).apply {
            applyTextSize(this, lessonRowTextSp, textSize)
            bindLesson(localized, this, lesson, style)
            // Fade the complete row, including its time column, exactly once.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Reset alpha left by older widget views that only faded lesson_content.
                setFloat(R.id.lesson_content, "setAlpha", 1f)
            }
            applyLessonAlpha(localized, this, R.id.item_root, lessonRowTextColors, lessonAlpha(lesson))
            setOnClickFillInIntent(R.id.item_root, Intent())
        }
    }

    fun listMessageRow(
        context: Context,
        layoutId: Int,
        textViewId: Int,
        text: CharSequence,
        textSize: WidgetTextSize = WidgetTextSize.NORMAL,
    ): RemoteViews {
        return RemoteViews(context.packageName, layoutId).apply {
            messageRowTextSp[textViewId]?.let { sp ->
                setTextViewTextSize(textViewId, TypedValue.COMPLEX_UNIT_SP, sp * textSize.scale)
            }
            setTextViewText(textViewId, text)
            setOnClickFillInIntent(R.id.item_root, Intent())
        }
    }

    private fun bindLesson(
        context: Context,
        views: RemoteViews,
        lesson: ScheduleWidgetLesson,
        style: LessonStyle,
    ) {
        views.setViewVisibility(R.id.time_column, View.VISIBLE)
        views.setViewVisibility(R.id.type_layout, View.VISIBLE)
        views.setViewVisibility(R.id.type_indicator, View.VISIBLE)
        views.setTextViewText(
            R.id.title,
            lesson.subject.ifBlank { context.getString(R.string.schedule_unknown_subject) }
        )
        views.setTextViewText(R.id.time_start, lesson.start)
        views.setTextViewText(R.id.time_end, lesson.end)
        views.setTextViewText(
            R.id.type,
            context.getString(when (lesson.pendingStatus) {
                ScheduleWidgetPendingStatus.WAITING -> R.string.schedule_widget_pending_waiting
                ScheduleWidgetPendingStatus.PREDICTED -> R.string.schedule_widget_pending_prediction
                null -> Lesson.TypeId(lesson.typeId).nameRes()
            })
        )
        views.setContentDescription(R.id.type, lesson.pendingStatus?.let {
            context.getString(if (it == ScheduleWidgetPendingStatus.PREDICTED)
                R.string.schedule_auto_sign_prediction_description else R.string.schedule_auto_sign_waiting_description)
        })
        views.setImageViewResource(R.id.type_indicator, when {
            lesson.pendingStatus != null -> R.drawable.widget_pending_indicator
            style == LessonStyle.DOT -> R.drawable.shape_circle_filled
            else -> R.drawable.indicator_dash
        })
        views.setInt(
            R.id.type_indicator,
            "setColorFilter",
            ContextCompat.getColor(context, Lesson.TypeId(lesson.typeId).colorRes())
        )

        val room = lesson.room?.let(::Room)?.shortTitle(context).orEmpty()
        val building = lesson.building
            ?.let(::Building)
            ?.shortTitle(context, maxLength = BUILDING_MAX_LENGTH)
            .orEmpty()
        val location = listOf(room, building)
            .filter(String::isNotBlank)
            .joinToString(" ")
        val details = compactLessonDetails(location, lesson.teacher)
        views.setViewVisibility(
            R.id.secondary_text,
            if (details.isBlank()) View.GONE else View.VISIBLE
        )
        views.setTextViewText(R.id.secondary_text, details)
    }

    /** Always applied, so a recycled launcher view drops the size of its previous snapshot. */
    private fun applyTextSize(views: RemoteViews, baseSp: Map<Int, Float>, size: WidgetTextSize) {
        baseSp.forEach { (id, sp) ->
            views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp * size.scale)
        }
    }

    /**
     * `View.setAlpha` is a RemoteViews method only from API 31; below it the widget fails to inflate. There every
     * label gets its layout colour with the alpha folded in and the type indicator an image alpha, set on every
     * render so a reused view drops an earlier fade. Colours resolve through [context], the widget's themed
     * context, so night variants follow the configuration of the render.
     */
    private fun applyLessonAlpha(
        context: Context,
        views: RemoteViews,
        fadedViewId: Int,
        textColors: Map<Int, Int>,
        alpha: Float,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setFloat(fadedViewId, "setAlpha", alpha)
            return
        }
        textColors.forEach { (id, colorRes) ->
            views.setTextColor(id, ContextCompat.getColor(context, colorRes).withAlpha(alpha))
        }
        views.setInt(R.id.type_indicator, "setImageAlpha", (alpha * OPAQUE).roundToInt())
    }

    private fun Int.withAlpha(alpha: Float): Int =
        ColorUtils.setAlphaComponent(this, (Color.alpha(this) * alpha).roundToInt())

    private fun lessonAlpha(lesson: ScheduleWidgetLesson): Float =
        if (lesson.state == ScheduleWidgetLessonState.COMPLETED) COMPLETED_ALPHA else 1f

    private fun bindSingleMessage(
        context: Context,
        views: RemoteViews,
        kind: dev.alllexey.itmowidgets.feature.schedule.domain.widget.SingleLessonWidgetKind,
    ) {
        val title = when (kind) {
            dev.alllexey.itmowidgets.feature.schedule.domain.widget.SingleLessonWidgetKind.LOADING ->
                R.string.schedule_widget_loading

            dev.alllexey.itmowidgets.feature.schedule.domain.widget.SingleLessonWidgetKind.SIGNED_OUT ->
                R.string.schedule_widget_signed_out

            dev.alllexey.itmowidgets.feature.schedule.domain.widget.SingleLessonWidgetKind.EMPTY_TODAY ->
                R.string.schedule_widget_empty_today

            dev.alllexey.itmowidgets.feature.schedule.domain.widget.SingleLessonWidgetKind.NO_MORE_TODAY ->
                R.string.schedule_widget_no_more_today

            dev.alllexey.itmowidgets.feature.schedule.domain.widget.SingleLessonWidgetKind.ERROR ->
                R.string.schedule_widget_error

            dev.alllexey.itmowidgets.feature.schedule.domain.widget.SingleLessonWidgetKind.LESSON ->
                R.string.schedule_unknown_subject
        }
        views.setViewVisibility(R.id.lesson_content, View.GONE)
        views.setViewVisibility(R.id.widget_message, View.VISIBLE)
        views.setTextViewText(R.id.widget_message_title, context.getString(title))
    }

    private fun supportingText(
        context: Context,
        state: ScheduleWidgetLessonState,
        remainingLessons: Int,
    ): String {
        val status = if (state == ScheduleWidgetLessonState.CURRENT) {
            context.getString(R.string.schedule_widget_status_now)
        } else {
            context.getString(R.string.schedule_widget_status_next)
        }
        val remaining = if (remainingLessons == 0) {
            context.getString(R.string.schedule_widget_last_lesson_compact)
        } else {
            context.getString(
                R.string.schedule_widget_more_lessons_compact,
                remainingLessons
            )
        }
        return context.getString(R.string.schedule_widget_supporting_text, status, remaining)
    }

    private fun singleLessonLayout(style: LessonStyle): Int {
        return when (style) {
            LessonStyle.DOT -> R.layout.widget_single_lesson_dot
            LessonStyle.LINE -> R.layout.widget_single_lesson_dash
        }
    }

    private fun lessonListLayout(style: LessonStyle): Int {
        return when (style) {
            LessonStyle.DOT -> R.layout.item_lesson_list_entry_dot
            LessonStyle.LINE -> R.layout.item_lesson_list_entry_dash
        }
    }

    private fun openSchedulePendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = AppEntryIntentFactory.open(context, AppEntryIntents.ACTION_OPEN_SCHEDULE).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val COMPLETED_ALPHA = 0.62f
    private const val OPAQUE = 255
    private const val BUILDING_MAX_LENGTH = 14

    // The layouts' own sizes; ScheduleWidgetRenderingTest checks they match the XML.
    private val singleLessonTextSp = mapOf(
        R.id.type to 11f,
        R.id.time_start to 12f,
        R.id.time_separator to 12f,
        R.id.time_end to 12f,
        R.id.title to 14f,
        R.id.secondary_text to 10f,
        R.id.more_lessons_text to 10f,
        R.id.widget_message_title to 14f,
        R.id.widget_message_hint to 10f
    )
    private val lessonRowTextSp = mapOf(
        R.id.time_start to 12f,
        R.id.time_end to 10f,
        R.id.title to 13f,
        R.id.type to 10f,
        R.id.secondary_text to 10f
    )
    private val messageRowTextSp = mapOf(
        R.id.day_title to 11f,
        R.id.end_marker to 11f,
        R.id.no_lessons to 14f,
        R.id.no_more_lessons to 14f,
        R.id.empty_view to 14f
    )

    // The layouts' own text colours, for fading below API 31; ScheduleWidgetRenderingTest checks they match the XML.
    private val singleLessonTextColors = mapOf(
        R.id.type to R.color.widget_on_surface_variant,
        R.id.time_start to R.color.widget_on_surface,
        R.id.time_separator to R.color.widget_on_surface_variant,
        R.id.time_end to R.color.widget_on_surface_variant,
        R.id.title to R.color.widget_on_surface,
        R.id.secondary_text to R.color.widget_on_surface_variant,
        R.id.more_lessons_text to R.color.widget_primary
    )
    private val lessonRowTextColors = mapOf(
        R.id.time_start to R.color.widget_on_surface,
        R.id.time_end to R.color.widget_on_surface_variant,
        R.id.title to R.color.widget_on_surface,
        R.id.type to R.color.widget_on_surface_variant,
        R.id.secondary_text to R.color.widget_on_surface_variant
    )
}
