package dev.alllexey.itmowidgets.core.settings

enum class ScheduleWidgetFormat { COMPACT, FULL }

data class CompactScheduleWidgetSettings(
    val showNextLessonEarly: Boolean = true,
    val hideTeacher: Boolean = false,
    val textSize: WidgetTextSize = WidgetTextSize.NORMAL
)

data class FullScheduleWidgetSettings(
    val hideTeacher: Boolean = false,
    val hidePastLessons: Boolean = false,
    val showTomorrowWhenTodayIsOver: Boolean = false,
    val textSize: WidgetTextSize = WidgetTextSize.NORMAL
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

/** Every widget appearance at once, for screens that show more than one preview. */
data class WidgetAppearance(
    val schedule: ScheduleWidgetSettings = ScheduleWidgetSettings(),
    val qr: QrWidgetSettings = QrWidgetSettings()
)

/** Appearance inputs shared by settings and the real widget preview renderers. */
sealed interface WidgetPreviewSettings {
    data class Qr(val appearance: QrWidgetSettings) : WidgetPreviewSettings
    data class Schedule(val appearance: ScheduleWidgetSettings, val format: ScheduleWidgetFormat) : WidgetPreviewSettings
}
