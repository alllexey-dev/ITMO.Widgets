package dev.alllexey.itmowidgets.ui.onboarding

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.transition.MaterialFadeThrough
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.ui.widgets.WidgetUtils
import dev.alllexey.itmowidgets.ui.widgets.data.LessonStyle
import dev.alllexey.itmowidgets.ui.widgets.data.SingleLessonWidgetData
import dev.alllexey.itmowidgets.util.ScheduleUtils

class ScheduleSetupFragment : Fragment(R.layout.fragment_onboarding_schedule) {

    private lateinit var previewContainer: FrameLayout
    private val appContainer by lazy { (requireContext().applicationContext as ItmoWidgetsApp).appContainer }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        previewContainer = view.findViewById(R.id.preview_container)
        val styleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.style_toggle_button_group)
        val switchTeacher = view.findViewById<MaterialSwitch>(R.id.switch_teacher)
        val switchBeforehand = view.findViewById<MaterialSwitch>(R.id.switch_beforehand)

        val settings = appContainer.storage.settings

        if (settings.getSingleLessonWidgetStyle() == LessonStyle.LINE) {
            styleGroup.check(R.id.toggle_button_line)
        } else {
            styleGroup.check(R.id.toggle_button_dot)
        }

        switchTeacher.isChecked = !settings.getHideTeacherState()
        switchBeforehand.isChecked = settings.getBeforehandSchedulingState()
        updatePreview(animate = false)

        styleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newStyle = when (checkedId) {
                    R.id.toggle_button_dot -> LessonStyle.DOT
                    R.id.toggle_button_line -> LessonStyle.LINE
                    else -> LessonStyle.DOT
                }
                settings.setSingleLessonWidgetStyle(newStyle)
                settings.setListLessonWidgetStyle(newStyle)

                updatePreview(animate = true)
                WidgetUtils.updateAllWidgets(requireContext())
            }
        }

        switchTeacher.setOnCheckedChangeListener { _, isChecked ->
            settings.setHideTeacherState(!isChecked)

            updatePreview(animate = false)
            WidgetUtils.updateAllWidgets(requireContext())
        }

        switchBeforehand.setOnCheckedChangeListener { _, isChecked ->
            settings.setBeforehandSchedulingState(isChecked)
            WidgetUtils.updateAllWidgets(requireContext())
        }
    }

    private fun updatePreview(animate: Boolean) {
        val settings = appContainer.storage.settings
        val isLine = settings.getSingleLessonWidgetStyle() == LessonStyle.LINE
        val showTeacher = !settings.getHideTeacherState()

        val layoutId = if (isLine) R.layout.single_lesson_widget_dash else R.layout.single_lesson_widget_dot

        if (animate) {
            val transition = MaterialFadeThrough()
            androidx.transition.TransitionManager.beginDelayedTransition(previewContainer, transition)
        }

        previewContainer.removeAllViews()
        val previewView = LayoutInflater.from(requireContext()).inflate(layoutId, previewContainer, false)

        val mockData = SingleLessonWidgetData(
            subject = "Математический анализ",
            times = "13:30 - 15:00",
            teacher = if (showTeacher) "Попов Антон Игоревич" else null,
            workTypeId = 3,
            room = "1337",
            building = "Кронверкский пр., д.49",
            moreLessonsText = "и ещё 2 пары до 18:40",
            layoutId = layoutId
        )

        bindMockWidgetView(previewView, mockData)
        previewContainer.addView(previewView)
    }

    private fun bindMockWidgetView(view: View, data: SingleLessonWidgetData) {
        val title = view.findViewById<TextView>(R.id.title)
        val time = view.findViewById<TextView>(R.id.time)
        val teacher = view.findViewById<TextView>(R.id.teacher)
        val room = view.findViewById<TextView>(R.id.location_room)
        val building = view.findViewById<TextView>(R.id.location_building)
        val moreText = view.findViewById<TextView>(R.id.more_lessons_text)

        val teacherLayout = view.findViewById<View>(R.id.teacher_layout)
        val locationLayout = view.findViewById<View>(R.id.location_layout)
        val timeLayout = view.findViewById<View>(R.id.time_layout)
        val moreLayout = view.findViewById<View>(R.id.more_lessons_layout)
        val typeIndicator = view.findViewById<ImageView>(R.id.type_indicator)

        title.text = data.subject
        time.text = data.times
        teacher.text = data.teacher
        room.text = if (data.building == null) data.room else "${data.room}, "
        building.text = ScheduleUtils.shortenBuildingName(data.building)

        moreText?.text = data.moreLessonsText

        teacherLayout.visibility = if (data.teacher.isNullOrEmpty()) View.GONE else View.VISIBLE

        val hideLocation = data.room == null && data.building == null
        locationLayout.visibility = if (hideLocation) View.GONE else View.VISIBLE

        timeLayout.visibility = if (data.times.isNullOrEmpty()) View.GONE else View.VISIBLE
        moreLayout?.visibility = if (data.moreLessonsText.isNullOrEmpty()) View.GONE else View.VISIBLE

        val colorResId = ScheduleUtils.getWorkTypeColor(data.workTypeId)
        val colorInt = ContextCompat.getColor(requireContext(), colorResId)

        typeIndicator.imageTintList = ColorStateList.valueOf(colorInt)
    }
}