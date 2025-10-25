package dev.alllexey.itmowidgets.data

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.alllexey.itmowidgets.ui.widgets.data.QrWidgetState

class UserSettingsStorage(val prefs: SharedPreferences) {

    companion object KEYS {
        const val CUSTOM_SERVICES_ALLOW_KEY = "custom_services_allow"
        const val SMART_SCHEDULING_KEY = "smart_scheduling"
        const val BEFOREHAND_SCHEDULING_KEY = "beforehand_scheduling"
        const val SINGLE_LESSON_WIDGET_STYLE = "single_lesson_widget_style"
        const val LIST_LESSON_WIDGET_STYLE = "list_lesson_widget_style"
        const val DOT_STYLE = "dot" // todo: better implementation
        const val LINE_STYLE = "line"
        const val HIDE_TEACHER_KEY = "hide_teacher"
        const val HIDE_PREVIOUS_LESSONS_KEY = "hide_previous_lessons"
        const val DYNAMIC_QR_COLORS_KEY = "dynamic_qr_colors"
        const val QR_SPOILER_KEY = "qr_spoiler"
        const val QR_SPOILER_ANIMATION_TYPE_KEY = "qr_spoiler_animation_type"
        const val FADE_ANIMATION = "fade" // todo: better implementation
        const val QR_WIDGET_STATE_PREFIX = "qr_widget_state_"
        const val CIRCLE_ANIMATION = "circle"
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

    fun getSingleLessonWidgetStyle(): String? {
        return prefs.getString(SINGLE_LESSON_WIDGET_STYLE, DOT_STYLE)
    }

    fun getLessonListWidgetStyle(): String? {
        return prefs.getString(LIST_LESSON_WIDGET_STYLE, DOT_STYLE)
    }

    fun getHideTeacherState(): Boolean {
        return prefs.getBoolean(HIDE_TEACHER_KEY, false)
    }

    fun getHidePreviousLessonsState(): Boolean {
        return prefs.getBoolean(HIDE_PREVIOUS_LESSONS_KEY, false)
    }

    fun getDynamicQrColorsState(): Boolean {
        return prefs.getBoolean(DYNAMIC_QR_COLORS_KEY, true)
    }

    fun getQrSpoilerState(): Boolean {
        return prefs.getBoolean(QR_SPOILER_KEY, true)
    }

    fun getQrSpoilerAnimationType(): String? {
        return prefs.getString(QR_SPOILER_ANIMATION_TYPE_KEY, CIRCLE_ANIMATION)
    }

    fun getQrWidgetState(appWidgetId: Int): QrWidgetState {
        val stateName = prefs.getString("$QR_WIDGET_STATE_PREFIX$appWidgetId", QrWidgetState.SPOILER.name)
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

    fun setSingleLessonWidgetStyle(style: String?) {
        prefs.edit(commit = true) {
            putString(SINGLE_LESSON_WIDGET_STYLE, style)
        }
    }

    fun setListLessonWidgetStyle(style: String?) {
        prefs.edit(commit = true) {
            putString(LIST_LESSON_WIDGET_STYLE, style)
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

    fun setDynamicQrColorsState(dynamicQrColors: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(DYNAMIC_QR_COLORS_KEY, dynamicQrColors)
        }
    }
}