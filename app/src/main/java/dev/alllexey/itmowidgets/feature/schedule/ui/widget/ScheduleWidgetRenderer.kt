package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.MainActivity
import dev.alllexey.itmowidgets.core.settings.LessonStyle
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
        val views = RemoteViews(context.packageName, singleLessonLayout(snapshot.singleLessonStyle))
        val lesson = content.lesson
        if (lesson == null) {
            bindSingleMessage(context, views, content.kind)
        } else {
            views.setViewVisibility(R.id.widget_message, View.GONE)
            views.setViewVisibility(R.id.lesson_content, View.VISIBLE)
            views.setFloat(R.id.lesson_content, "setAlpha", lessonAlpha(lesson))
            bindLesson(context, views, lesson, snapshot.singleLessonStyle)
            views.setViewVisibility(R.id.more_lessons_layout, View.VISIBLE)
            views.setTextViewText(
                R.id.more_lessons_text,
                supportingText(context, lesson.state, content.remainingLessons)
            )
        }
        return views
    }

    fun renderListShell(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val serviceIntent = Intent(context, ScheduleWidgetRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = "itmowidgets://schedule-widget/$appWidgetId".toUri()
        }
        val views = RemoteViews(context.packageName, R.layout.lesson_list_widget).apply {
            setRemoteAdapter(R.id.lesson_list, serviceIntent)
            setPendingIntentTemplate(
                R.id.lesson_list,
                openSchedulePendingIntent(context, appWidgetId)
            )
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    fun notifyListChanged(
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.lesson_list)
    }

    fun lessonListRow(
        context: Context,
        lesson: ScheduleWidgetLesson,
        style: LessonStyle,
    ): RemoteViews {
        return RemoteViews(context.packageName, lessonListLayout(style)).apply {
            bindLesson(context, this, lesson, style)
            // Fade the complete row, including its time column, exactly once.
            // Reset alpha left by older widget views that only faded lesson_content.
            setFloat(R.id.lesson_content, "setAlpha", 1f)
            setFloat(R.id.item_root, "setAlpha", lessonAlpha(lesson))
            setOnClickFillInIntent(R.id.item_root, Intent())
        }
    }

    fun listMessageRow(
        context: Context,
        layoutId: Int,
        textViewId: Int,
        text: CharSequence,
    ): RemoteViews {
        return RemoteViews(context.packageName, layoutId).apply {
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
            LessonStyle.DOT -> R.layout.single_lesson_widget_dot
            LessonStyle.LINE -> R.layout.single_lesson_widget_dash
        }
    }

    private fun lessonListLayout(style: LessonStyle): Int {
        return when (style) {
            LessonStyle.DOT -> R.layout.item_lesson_list_entry_dot
            LessonStyle.LINE -> R.layout.item_lesson_list_entry_dash
        }
    }

    private fun openSchedulePendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_SCHEDULE
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
    private const val BUILDING_MAX_LENGTH = 14
}
