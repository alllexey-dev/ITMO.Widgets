package dev.alllexey.itmowidgets.core.debug

/**
 * How a debug preview host renders: font scale, night mode, window width and a dynamic palette seed.
 * A host reads only the fields it supports and ignores the rest; `0` width and a `null` seed keep the defaults.
 */
data class PreviewAppearance(
    val fontScale: Float = 1f,
    val dark: Boolean = false,
    val widthDp: Int = 0,
    val colorSeed: Int? = null
)
