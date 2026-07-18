package dev.alllexey.itmowidgets.core.storage

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.alllexey.itmowidgets.core.util.safeEnumOf
import dev.alllexey.itmowidgets.feature.qr.ui.QrAnimationType
import dev.alllexey.itmowidgets.feature.widget.schedule.LessonStyle
import javax.inject.Inject

class AppSettingsStorage @Inject constructor(val prefs: SharedPreferences) {

    companion object KEYS {
        const val CUSTOM_SERVICES_ENABLED_KEY = "custom_services_enabled"
        const val SINGLE_LESSON_WIDGET_STYLE_KEY = "single_lesson_widget_style"
        const val LIST_LESSON_WIDGET_STYLE_KEY = "list_lesson_widget_style"
        const val WIDGET_SMART_SCHEDULING_ENABLED_KEY = "widget_smart_scheduling_enabled"
        const val WIDGET_FORWARD_SCHEDULING_ENABLED_KEY = "widget_forward_scheduling_enabled"
        const val WIDGET_HIDE_TEACHER_ENABLED_KEY = "widget_hide_teacher_enabled"
        const val WIDGET_HIDE_PREVIOUS_LESSONS_ENABLED_KEY = "widget_hide_previous_lessons_enabled"
        const val WIDGET_FUTURE_SCHEDULE_ENABLED_KEY = "widget_future_schedule_enabled"
        const val QR_DYNAMIC_COLORS_ENABLED_KEY = "qr_dynamic_colors_enabled"
        const val QR_SPOILER_ENABLED_KEY = "qr_spoiler_enabled"
        const val QR_SPOILER_ANIMATION_TYPE_KEY = "qr_spoiler_animation_type"
        const val SPORT_SIGN_TEACHER_SELECTOR_ENABLED_KEY = "sport_sign_teacher_selector_enabled"
        const val SPORT_SIGN_TIME_SELECTOR_ENABLED_KEY = "sport_sign_hide_time_selector_enabled"
    }

    // region getters

    fun getCustomServicesEnabled(): Boolean {
        return prefs.getBoolean(CUSTOM_SERVICES_ENABLED_KEY, false)
    }

    fun getWidgetSmartSchedulingEnabled(): Boolean {
        return prefs.getBoolean(WIDGET_SMART_SCHEDULING_ENABLED_KEY, true)
    }

    fun getWidgetForwardSchedulingEnabled(): Boolean {
        return prefs.getBoolean(WIDGET_FORWARD_SCHEDULING_ENABLED_KEY, true)
    }

    fun getSingleLessonWidgetStyle(): LessonStyle {
        val styleName = prefs.getString(SINGLE_LESSON_WIDGET_STYLE_KEY, null)
        return safeEnumOf(styleName, LessonStyle.DOT)
    }

    fun getLessonListWidgetStyle(): LessonStyle {
        val styleName = prefs.getString(LIST_LESSON_WIDGET_STYLE_KEY, null)
        return safeEnumOf(styleName, LessonStyle.DOT)
    }

    fun getWidgetHideTeacherEnabled(): Boolean {
        return prefs.getBoolean(WIDGET_HIDE_TEACHER_ENABLED_KEY, false)
    }

    fun getWidgetHidePreviousLessonsEnabled(): Boolean {
        return prefs.getBoolean(WIDGET_HIDE_PREVIOUS_LESSONS_ENABLED_KEY, false)
    }

    fun getWidgetFutureScheduleEnabled(): Boolean {
        return prefs.getBoolean(WIDGET_FUTURE_SCHEDULE_ENABLED_KEY, false)
    }

    fun getQrDynamicColorsEnabled(): Boolean {
        return prefs.getBoolean(QR_DYNAMIC_COLORS_ENABLED_KEY, true)
    }

    fun getQrSpoilerEnabled(): Boolean {
        return prefs.getBoolean(QR_SPOILER_ENABLED_KEY, true)
    }

    fun getQrSpoilerAnimationType(): QrAnimationType {
        val animationTypeName = prefs.getString(QR_SPOILER_ANIMATION_TYPE_KEY, null)
        return safeEnumOf(animationTypeName, QrAnimationType.CIRCLE)
    }

    fun getSportSignHideTeacherSelectorEnabled(): Boolean {
        return prefs.getBoolean(SPORT_SIGN_TEACHER_SELECTOR_ENABLED_KEY, true)
    }

    fun getSportSignHideTimeSelectorEnabled(): Boolean {
        return prefs.getBoolean(SPORT_SIGN_TIME_SELECTOR_ENABLED_KEY, true)
    }

    // endregion getters

    // region setters

    fun setCustomServicesEnabled(enabled: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(CUSTOM_SERVICES_ENABLED_KEY, enabled)
        }
    }

    fun setWidgetSmartSchedulingEnabled(smartScheduling: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(WIDGET_SMART_SCHEDULING_ENABLED_KEY, smartScheduling)
        }
    }

    fun setWidgetForwardSchedulingEnabled(forwardScheduling: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(WIDGET_FORWARD_SCHEDULING_ENABLED_KEY, forwardScheduling)
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

    fun setWidgetHideTeacherEnabled(hideTeacher: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(WIDGET_HIDE_TEACHER_ENABLED_KEY, hideTeacher)
        }
    }

    fun setWidgetHidePreviousLessonsEnabled(hidePreviousLessons: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(WIDGET_HIDE_PREVIOUS_LESSONS_ENABLED_KEY, hidePreviousLessons)
        }
    }

    fun setWidgetFutureScheduleEnabled(futureSchedule: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(WIDGET_FUTURE_SCHEDULE_ENABLED_KEY, futureSchedule)
        }
    }

    fun setQrSpoilerEnabled(qrSpoilerEnabled: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(QR_SPOILER_ENABLED_KEY, qrSpoilerEnabled)
        }
    }

    fun setQrDynamicColorsEnabled(qrDynamicColors: Boolean) {
        prefs.edit(commit = true) {
            putBoolean(QR_DYNAMIC_COLORS_ENABLED_KEY, qrDynamicColors)
        }
    }

    fun setQrSpoilerAnimationType(qrAnimationType: QrAnimationType) {
        prefs.edit(commit = true) {
            putString(QR_SPOILER_ANIMATION_TYPE_KEY, qrAnimationType.name)
        }
    }

    // endregion setters
}
