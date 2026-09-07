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
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.databinding.WidgetPreviewScheduleBinding
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewLabels
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot

class ScheduleSettingsPreview(
    private val context: Context,
    private val scenario: SchedulePreviewScenario
) : WidgetPreview {
    private val binding = WidgetPreviewScheduleBinding.inflate(LayoutInflater.from(context))
    override val view: View = binding.root
    private var appearance: ScheduleWidgetSettings? = null
    private var evening = false
    private var snapshot: ScheduleWidgetSnapshot? = null
    private val rowRenderer = ScheduleListRowRenderer(context)
    private val pageHolders = mutableSetOf<PreviewHolder>()
    private val labels = SchedulePreviewLabels(
        context.getString(R.string.widget_preview_subject_history),
        context.getString(R.string.widget_preview_subject_math),
        context.getString(R.string.widget_preview_subject_programming),
        context.getString(R.string.widget_preview_subject_physics),
        context.getString(R.string.widget_preview_teacher)
    )
    private val adapter = object : RecyclerView.Adapter<PreviewHolder>() {
        override fun getItemCount() = 2
        override fun getItemViewType(position: Int) = position
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PreviewHolder(
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                val padding = (4 * resources.displayMetrics.density).toInt()
                setPadding(0, padding, 0, padding)
            }
        )
        override fun onBindViewHolder(holder: PreviewHolder, position: Int) {
            holder.page = position
            pageHolders.add(holder)
            renderPage(holder)
        }
        override fun onViewRecycled(holder: PreviewHolder) { pageHolders.remove(holder) }
    }
    private val tabs = TabLayoutMediator(binding.previewTabs, binding.schedulePreviewPager) { tab, index ->
        tab.setText(if (index == 0) R.string.widget_preview_schedule_single else R.string.widget_preview_schedule_day)
    }

    init {
        // Scale the bounded area by actual widget text, not a 160 sp value: Android's
        // non-linear font scaling treats large text sizes differently from body text.
        val textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics
        )
        binding.schedulePreviewPager.layoutParams.height = (160f * textSize / 14f).toInt()
        binding.schedulePreviewPager.adapter = adapter
        binding.schedulePreviewPager.offscreenPageLimit = 1
        tabs.attach()
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
        val next = (settings as WidgetPreviewSettings.Schedule).appearance
        if (next == appearance) return
        appearance = next
        updateSnapshot()
    }

    override fun saveState() = Bundle().apply {
        putInt("page", binding.schedulePreviewPager.currentItem)
        putBoolean("evening", evening)
    }

    override fun restoreState(state: Bundle) {
        evening = state.getBoolean("evening")
        binding.schedulePreviewPager.setCurrentItem(state.getInt("page").coerceIn(0, 1), false)
        updateTimeLabel()
        updateSnapshot()
    }

    private fun updateTimeLabel() {
        binding.previewTime.setText(if (evening) R.string.widget_preview_time_evening_short else R.string.widget_preview_time_lesson_short)
        binding.previewTime.contentDescription = context.getString(R.string.widget_preview_time_description) + ". " + binding.previewTime.text
    }

    private fun updateSnapshot() {
        val settings = appearance ?: return
        snapshot = scenario.snapshot(settings, evening, labels)
        pageHolders.forEach(::renderPage)
    }

    private fun renderPage(holder: PreviewHolder) {
        val content = snapshot ?: return
        if (holder.page == 0) {
            val remote = ScheduleWidgetRenderer.singleLessonViews(context, content)
            if (holder.root.isEmpty()) {
                holder.root.addView(remote.apply(context, holder.root), FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
            } else {
                remote.reapply(context, holder.root.getChildAt(0))
            }
        } else {
            if (holder.root.isEmpty()) {
                LayoutInflater.from(context).inflate(R.layout.lesson_list_widget, holder.root, true)
            }
            val list = holder.root.findViewById<ListView>(R.id.lesson_list)
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
        tabs.detach()
        binding.schedulePreviewPager.adapter = null
        pageHolders.clear()
    }

    private class PreviewHolder(val root: FrameLayout) : RecyclerView.ViewHolder(root) {
        var page = 0
    }
}
