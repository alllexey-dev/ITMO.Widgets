package dev.alllexey.itmowidgets.designsystem.theme

/** Where [ItmoTheme] takes its colour scheme from. */
sealed interface ColorSource {

    /** The wallpaper's dynamic colours on Android 12+ (API 31), [Static] below that and on iOS. */
    data object Platform : ColorSource

    /** The app's static scheme: the brand blue `#4984E2`, TonalSpot variant (owner, item 14 Q1). */
    data object Static : ColorSource

    /**
     * A scheme generated from [argb] like MDC's content-based source (the Content variant, not TonalSpot), so tests
     * and narrow appearances see what a seeded View screen sees.
     */
    data class Seed(val argb: Int) : ColorSource

    /** An accent colour preset: the brand scheme's TonalSpot variant from [argb] instead of the brand blue. */
    data class Accent(val argb: Int) : ColorSource
}
