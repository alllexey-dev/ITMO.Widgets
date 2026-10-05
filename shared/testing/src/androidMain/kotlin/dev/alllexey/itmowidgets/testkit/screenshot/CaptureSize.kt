package dev.alllexey.itmowidgets.testkit.screenshot

import dev.alllexey.itmowidgets.designsystem.preview.PreviewAppearance

/**
 * The window a capture renders in, as Robolectric qualifiers. A preview that wraps its content is captured at its own
 * size; one that fills the window gets this size, narrowed by the appearance's width (320 dp).
 */
data class CaptureSize(
    val widthDp: Int = PHONE_WIDTH_DP,
    val heightDp: Int = PHONE_HEIGHT_DP,
    val density: String = DENSITY,
) {

    /** Russian like the app, the appearance's width and night mode, then the density (qualifier order matters). */
    fun qualifiers(appearance: PreviewAppearance): String {
        val width = appearance.widthDp ?: widthDp
        val night = if (appearance.dark) "night" else "notnight"
        return "ru-w${width}dp-h${heightDp}dp-$night-$density"
    }

    companion object {
        /** A current phone in dp (the SP-13b screen tests). */
        const val PHONE_WIDTH_DP = 411
        const val PHONE_HEIGHT_DP = 891

        /** 1080 x 1920 px at [DENSITY], the store screenshot frame (L17 SH-3a). */
        const val STORE_WIDTH_DP = 360
        const val STORE_HEIGHT_DP = 640

        /** Phone-density PNGs without changing dp sizes (SP-06). */
        const val DENSITY = "xxhdpi"
    }
}
