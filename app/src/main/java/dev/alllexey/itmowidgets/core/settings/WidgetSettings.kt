package dev.alllexey.itmowidgets.core.settings

enum class ScheduleWidgetFormat { COMPACT, FULL }

data class CompactScheduleWidgetSettings(
    val showNextLessonEarly: Boolean = true,
    val hideTeacher: Boolean = false
)

data class FullScheduleWidgetSettings(
    val hideTeacher: Boolean = false,
    val hidePastLessons: Boolean = false,
    val showTomorrowWhenTodayIsOver: Boolean = false
)

data class ScheduleWidgetSettings(
    val compact: CompactScheduleWidgetSettings = CompactScheduleWidgetSettings(),
    val full: FullScheduleWidgetSettings = FullScheduleWidgetSettings()
)

data class QrWidgetSettings(
    val dynamicColors: Boolean = true,
    val spoilerEnabled: Boolean = true,
    val animationType: QrAnimationType = QrAnimationType.CIRCLE
)

/** Appearance inputs shared by settings and the real widget preview renderers. */
sealed interface WidgetPreviewSettings {
    data class Qr(val appearance: QrWidgetSettings) : WidgetPreviewSettings
    data class Schedule(val appearance: ScheduleWidgetSettings, val format: ScheduleWidgetFormat) : WidgetPreviewSettings
}
