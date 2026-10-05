package dev.alllexey.itmowidgets.core.debug

/**
 * Marks a class that stores debug state or opens the debug tools. Such a class checks `BuildConfig.DEBUG` itself, so
 * a release build neither opens it nor reads an override; the rule `debug code is gated` enforces both directions.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class DebugOnly
