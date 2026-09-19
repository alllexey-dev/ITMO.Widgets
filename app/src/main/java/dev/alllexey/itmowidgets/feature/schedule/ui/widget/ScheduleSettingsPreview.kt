package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListView
import androidx.core.view.isEmpty
import androidx.core.view.updateLayoutParams
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetFormat
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.databinding.ViewWidgetPreviewScheduleBinding
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewLabels
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot

class ScheduleSettingsPreview(
    private val context: Context,
    private val scenario: SchedulePreviewScenario
) : WidgetPreview {
    private val binding = ViewWidgetPreviewScheduleBinding.inflate(LayoutInflater.from(context))
    override val view: View = binding.root
    private var appearance: WidgetPreviewSettings.Schedule? = null
    private val fullHeight: Int
    private var evening = false
    private var snapshot: ScheduleWidgetSnapshot? = null
    private val rowRenderer = ScheduleListRowRenderer(context)
    private val labels = SchedulePreviewLabels(
        context.getString(R.string.widget_preview_subject_history),
        context.getString(R.string.widget_preview_subject_math),
        context.getString(R.string.widget_preview_subject_programming),
        context.getString(R.string.widget_preview_subject_physics),
        context.getString(R.string.widget_preview_teacher)
    )

    init {
        // Scale the bounded area by actual widget text, not a 160 sp value: Android's
        // non-linear font scaling treats large text sizes differently from body text.
        val textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics
        )
        fullHeight = (160f * textSize / 14f).toInt()
        updateTimeLabel()
        binding.previewTime.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.widget_preview_time_title)
                .setSingleChoiceItems(
                    arrayOf(context.getString(R.string.widget_preview_time_lesson), context.getString(R.string.widget_preview_time_evening)),
                    if (evening) 1 else 0
                ) { dialog, which ->
                    evening = which == 1
                    updateTimeLabel()
                    updateSnapshot()
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.common_cancel, null)
                .show()
        }
    }

    override fun bind(settings: WidgetPreviewSettings) {
        val next = settings as WidgetPreviewSettings.Schedule
        if (next == appearance) return
        if (next.format != appearance?.format) {
            binding.schedulePreviewContent.removeAllViews()
            // The day list needs a bounded area; the single lesson is as tall as the widget itself.
            binding.schedulePreviewContent.updateLayoutParams {
                height = if (next.format == ScheduleWidgetFormat.COMPACT) ViewGroup.LayoutParams.WRAP_CONTENT else fullHeight
            }
        }
        appearance = next
        updateSnapshot()
    }

    override fun saveState() = Bundle().apply {
        putBoolean("evening", evening)
    }

    override fun restoreState(state: Bundle) {
        evening = state.getBoolean("evening")
        updateTimeLabel()
        updateSnapshot()
    }

    private fun updateTimeLabel() {
        binding.previewTime.setText(if (evening) R.string.widget_preview_time_evening_short else R.string.widget_preview_time_lesson_short)
        binding.previewTime.contentDescription = context.getString(R.string.widget_preview_time_description) + ". " + binding.previewTime.text
    }

    private fun updateSnapshot() {
        val settings = appearance ?: return
        snapshot = scenario.snapshot(settings.appearance, evening, labels)
        renderPreview()
    }

    private fun renderPreview() {
        val content = snapshot ?: return
        val root = binding.schedulePreviewContent
        if (appearance?.format == ScheduleWidgetFormat.COMPACT) {
            val remote = ScheduleWidgetRenderer.singleLessonViews(context, content)
            if (root.isEmpty()) {
                root.addView(remote.apply(context, root), FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
            } else {
                remote.reapply(context, root.getChildAt(0))
            }
        } else {
            if (root.isEmpty()) {
                LayoutInflater.from(context).inflate(R.layout.widget_lesson_list, root, true)
            }
            val list = root.findViewById<ListView>(R.id.lesson_list)
            list.adapter = object : BaseAdapter() {
                override fun getCount() = content.lessonList.size
                override fun getItem(position: Int) = content.lessonList[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun isEnabled(position: Int) = false
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val remote = checkNotNull(rowRenderer.render(getItem(position), content.lessonListStyle))
                    if (convertView != null && convertView.tag == remote.layoutId) {
                        remote.reapply(context, convertView)
                        return convertView
                    }
                    return remote.apply(context, parent).apply { tag = remote.layoutId }
                }
            }
        }
    }

    override fun close() {
        binding.schedulePreviewContent.removeAllViews()
        snapshot = null
    }
}
