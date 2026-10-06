package dev.alllexey.itmowidgets.designsystem.preview

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle

/**
 * One entry of the appearance matrix every preview is captured in: night mode, font scale, a colour seed (null: the
 * platform scheme) and a window width (null: the capture size's), and the platform style the kit draws in. [name] is
 * the baseline file suffix (`<Preview>_<state>_<name>.png`). The four Material entries ([All]) equal the View tests'
 * `Appearances.all`, so an XML reference and its Compose port are captured alike; the three iOS entries ([IosAll])
 * draw the kit's iOS variants on the static scheme, as iOS gets it.
 */
@Immutable
data class PreviewAppearance(
    val name: String,
    val dark: Boolean = false,
    val fontScale: Float = 1f,
    val colorSeed: Int? = null,
    val widthDp: Int? = null,
    val platformStyle: ItmoPlatformStyle = ItmoPlatformStyle.Material,
) {
    companion object {
        val Light = PreviewAppearance("light")
        val Dark = PreviewAppearance("dark", dark = true)
        val GreenNarrow = PreviewAppearance(
            "green-narrow",
            fontScale = NARROW_FONT_SCALE,
            colorSeed = 0xFF087F5B.toInt(),
            widthDp = NARROW_WIDTH_DP,
        )
        val DarkNarrow = PreviewAppearance(
            "dark-narrow",
            dark = true,
            fontScale = NARROW_FONT_SCALE,
            colorSeed = 0xFF826C24.toInt(),
            widthDp = NARROW_WIDTH_DP,
        )

        val IosLight = PreviewAppearance("ios-light", platformStyle = ItmoPlatformStyle.Ios)
        val IosDark = PreviewAppearance("ios-dark", dark = true, platformStyle = ItmoPlatformStyle.Ios)
        val IosNarrow = PreviewAppearance(
            "ios-narrow",
            fontScale = NARROW_FONT_SCALE,
            widthDp = NARROW_WIDTH_DP,
            platformStyle = ItmoPlatformStyle.Ios,
        )

        /** The Material matrix in capture order. */
        val All: List<PreviewAppearance> = listOf(Light, Dark, GreenNarrow, DarkNarrow)

        /**
         * The iOS matrix: captured with `-Pshots.appearance=ios`, then always for every preview recorded in it, and
         * always in a module whose screenshot test sets `iosAppearances`.
         */
        val IosAll: List<PreviewAppearance> = listOf(IosLight, IosDark, IosNarrow)

        /** Every appearance a baseline can be recorded in. */
        val Known: List<PreviewAppearance> = All + IosAll

        /** What a screenshot run captures unless `-Pshots.appearance=full` asks for [All]. */
        val Default: List<PreviewAppearance> = listOf(Light, Dark)

        private const val NARROW_FONT_SCALE = 1.3f
        private const val NARROW_WIDTH_DP = 320
    }
}

/** The appearance [ItmoPreview] renders in: the screenshot harness provides it, IDE previews get the light one. */
val LocalPreviewAppearance = staticCompositionLocalOf { PreviewAppearance.Light }
