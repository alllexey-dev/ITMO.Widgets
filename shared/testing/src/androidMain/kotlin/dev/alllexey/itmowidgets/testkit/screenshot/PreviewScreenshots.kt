package dev.alllexey.itmowidgets.testkit.screenshot

/**
 * Optional settings of a [PreviewScreenshotTest] subclass; without it the subclass's package tree is scanned in light
 * and dark at [CaptureSize]'s phone size, with accessibility checks.
 *
 * - [packageTree]: where the previews live, when the subclass sits elsewhere.
 * - [allAppearances]: always all four appearances (the kit), not only with `-Pshots.appearance=full`.
 * - [widthDp], [heightDp], [density]: the capture window ([CaptureSize]); a preview's own `widthDp`/`heightDp`
 *   override them.
 * - [accessibilityChecks]: ATF (Roborazzi accessibility check, error level) on every capture.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PreviewScreenshots(
    val packageTree: String = "",
    val allAppearances: Boolean = false,
    val widthDp: Int = CaptureSize.PHONE_WIDTH_DP,
    val heightDp: Int = CaptureSize.PHONE_HEIGHT_DP,
    val density: String = CaptureSize.DENSITY,
    val accessibilityChecks: Boolean = true,
)
