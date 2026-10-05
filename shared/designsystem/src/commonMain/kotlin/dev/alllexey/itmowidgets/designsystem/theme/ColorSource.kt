package dev.alllexey.itmowidgets.designsystem.theme

/** Where [ItmoTheme] takes its colour scheme from. */
sealed interface ColorSource {

    /** The wallpaper's dynamic colours on Android 12+ (API 31), [Static] below that and on iOS. */
    data object Platform : ColorSource

    /** The Material 3 baseline (`#6750A4` family), what API 26-30 get from `Theme.Material3.DynamicColors.DayNight`. */
    data object Static : ColorSource

    /**
     * A scheme generated from [argb] like MDC's content-based source (the Content variant, not TonalSpot), so tests
     * and narrow appearances see what a seeded View screen sees; later the brand colour.
     */
    data class Seed(val argb: Int) : ColorSource
}
