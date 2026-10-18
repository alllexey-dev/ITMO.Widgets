package dev.alllexey.itmowidgets.core.settings

/** The schedule widgets' looks: the next lesson (W1), the day to scale (W2) and the classic list, which reads W2's settings. */
enum class ScheduleWidgetFormat { COMPACT, FULL, LIST }

/**
 * A widget of the widgets gallery: the next lesson (W1, the compact schedule widget), the day (W2, the full one), the
 * classic list of 2.2 (with W2's settings), the sport score (W3) and the pass. Its settings apply to every placed
 * widget of the format.
 */
enum class WidgetFormat { NEXT, DAY, LIST, SPORT, PASS }

/** The next-lesson widget. It names no teacher: [hideTeacher] is read only by the first-run flow. */
data class CompactScheduleWidgetSettings(
    val showNextLessonEarly: Boolean = true,
    val hideTeacher: Boolean = false,
    val textSize: WidgetTextSize = WidgetTextSize.NORMAL
)

data class FullScheduleWidgetSettings(
    val hideTeacher: Boolean = false,
    val hidePastLessons: Boolean = false,
    val showTomorrowWhenTodayIsOver: Boolean = true,
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

/**
 * Appearance inputs shared by settings and the real widget preview renderers; [theme] is the appearance the widgets
 * draw with while they follow the app's theme, null for their own colours.
 */
sealed interface WidgetPreviewSettings {
    val theme: ThemeSpec?

    data class Qr(
        val appearance: QrWidgetSettings,
        override val theme: ThemeSpec? = null,
    ) : WidgetPreviewSettings

    data class Schedule(
        val appearance: ScheduleWidgetSettings,
        val format: ScheduleWidgetFormat,
        override val theme: ThemeSpec? = null,
    ) : WidgetPreviewSettings

    data class Sport(override val theme: ThemeSpec? = null) : WidgetPreviewSettings
}
