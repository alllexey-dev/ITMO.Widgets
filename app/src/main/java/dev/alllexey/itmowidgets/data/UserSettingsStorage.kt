package dev.alllexey.itmowidgets.data

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.alllexey.itmowidgets.ui.widgets.data.LessonStyle
import dev.alllexey.itmowidgets.ui.widgets.data.QrAnimationType
import dev.alllexey.itmowidgets.ui.widgets.data.QrWidgetState

class UserSettingsStorage(val prefs: SharedPreferences) {

    companion object KEYS {
        const val CUSTOM_SERVICES_ALLOW_KEY = "custom_services_allow"
        const val SMART_SCHEDULING_KEY = "smart_scheduling"
        const val BEFOREHAND_SCHEDULING_KEY = "beforehand_scheduling"
        const val SINGLE_LESSON_WIDGET_STYLE_KEY = "single_lesson_widget_style"
        const val LIST_LESSON_WIDGET_STYLE_KEY = "list_lesson_widget_style"
        const val HIDE_TEACHER_KEY = "hide_teacher"
        const val HIDE_PREVIOUS_LESSONS_KEY = "hide_previous_lessons"
        const val SHOW_SCHEDULE_FOR_TOMORROW_KEY = "show_schedule_for_tomorrow"
        const val DYNAMIC_QR_COLORS_KEY = "dynamic_qr_colors"
        const val QR_SPOILER_KEY = "qr_spoiler"
        const val QR_SPOILER_ANIMATION_TYPE_KEY = "qr_spoiler_animation_type"
        const val QR_WIDGET_STATE_PREFIX = "qr_widget_state_"
    }

    fun getCustomServicesState(): Boolean {
        return prefs.getBoolean(CUSTOM_SERVICES_ALLOW_KEY, false)
    }

    fun getSmartSchedulingState(): Boolean {
        return prefs.getBoolean(SMART_SCHEDULING_KEY, true)
    }

    fun getBeforehandSchedulingState(): Boolean {
        return prefs.getBoolean(BEFOREHAND_SCHEDULING_KEY, true)
    }

    fun getSingleLessonWidgetStyle(): LessonStyle {
        val str = prefs.getString(SINGLE_LESSON_WIDGET_STYLE_KEY, null)
        return LessonStyle.valueOf(str ?: LessonStyle.DOT.name)
    }

    fun getLessonListWidgetStyle(): LessonStyle {
        val str = prefs.getString(LIST_LESSON_WIDGET_STYLE_KEY, null)
        return LessonStyle.valueOf(str ?: LessonStyle.DOT.name)
    }

    fun getHideTeacherState(): Boolean {
        return prefs.getBoolean(HIDE_TEACHER_KEY, false)
    }

    fun getHidePreviousLessonsState(): Boolean {
        return prefs.getBoolean(HIDE_PREVIOUS_LESSONS_KEY, false)
    }

    fun getShowScheduleForTomorrowState(): Boolean {
        return prefs.getBoolean(SHOW_SCHEDULE_FOR_TOMORROW_KEY, false)
    }

    fun getDynamicQrColorsState(): Boolean {
        return prefs.getBoolean(DYNAMIC_QR_COLORS_KEY, true)
    }

    fun getQrSpoilerState(): Boolean {
        return prefs.getBoolean(QR_SPOILER_KEY, true)
    }

    fun getQrSpoilerAnimationType(): QrAnimationType {
        val str = prefs.getString(QR_SPOILER_ANIMATION_TYPE_KEY, null)
        return QrAnimationType.valueOf(str ?: QrAnimationType.CIRCLE.name)
    }

    fun getQrWidgetState(appWidgetId: Int): QrWidgetState {
        val stateName =
            prefs.getString("$QR_WIDGET_STATE_PREFIX$appWidgetId", QrWidgetState.SPOILER.name)
        return QrWidgetState.valueOf(stateName ?: QrWidgetState.SPOILER.name)
    }

    fun setQrWidgetState(appWidgetId: Int, state: QrWidgetState) {
        prefs.edit(commit = true) {
            putString("$QR_WIDGET_STATE_PREFIX$appWidgetId", state.name)
        }
    }

    fun setCustomServicesState(allow: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(CUSTOM_SERVICES_ALLOW_KEY, allow)
        }
    }

    fun setSmartSchedulingState(smartScheduling: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(SMART_SCHEDULING_KEY, smartScheduling)
        }
    }

    fun setBeforehandSchedulingState(beforehandScheduling: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(BEFOREHAND_SCHEDULING_KEY, beforehandScheduling)
        }
    }

    fun setSingleLessonWidgetStyle(style: LessonStyle) {
        prefs.edit(commit = true) {
            putString(SINGLE_LESSON_WIDGET_STYLE_KEY, style.name)
        }
    }

    fun setListLessonWidgetStyle(style: LessonStyle) {
        prefs.edit(commit = true) {
            putString(LIST_LESSON_WIDGET_STYLE_KEY, style.name)
        }
    }

    fun setHideTeacherState(hideTeacher: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(HIDE_TEACHER_KEY, hideTeacher)
        }
    }

    fun setHidePreviousLessonsState(hidePreviousLessons: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(HIDE_PREVIOUS_LESSONS_KEY, hidePreviousLessons)
        }
    }

    fun setScheduleForTomorrowState(showScheduleForTomorrow: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(SHOW_SCHEDULE_FOR_TOMORROW_KEY, showScheduleForTomorrow)
        }
    }

    fun setDynamicQrColorsState(dynamicQrColors: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(DYNAMIC_QR_COLORS_KEY, dynamicQrColors)
        }
    }

    fun setQrAnimationType(qrAnimationType: QrAnimationType) {
        prefs.edit(commit = true) {
            putString(QR_SPOILER_ANIMATION_TYPE_KEY, qrAnimationType.name)
        }
    }
}