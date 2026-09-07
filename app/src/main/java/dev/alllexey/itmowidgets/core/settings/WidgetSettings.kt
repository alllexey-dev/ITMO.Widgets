package dev.alllexey.itmowidgets.core.settings

data class ScheduleWidgetSettings(
    val showNextLessonEarly: Boolean = true,
    val hideTeacher: Boolean = false,
    val hidePastLessons: Boolean = false,
    val showTomorrowWhenTodayIsOver: Boolean = false
)

data class QrWidgetSettings(
    val dynamicColors: Boolean = true,
    val spoilerEnabled: Boolean = true,
    val animationType: QrAnimationType = QrAnimationType.CIRCLE
)

/** Appearance inputs shared by settings and the real widget preview renderers. */
sealed interface WidgetPreviewSettings {
    data class Qr(val appearance: QrWidgetSettings) : WidgetPreviewSettings
    data class Schedule(val appearance: ScheduleWidgetSettings) : WidgetPreviewSettings
}
