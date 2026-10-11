package dev.alllexey.itmowidgets.core.settings

/** The palette installed widgets draw with now: the app's scheme while they follow the theme, null otherwise. */
fun interface WidgetPaletteSource {
    suspend fun current(): WidgetPalette?
}
