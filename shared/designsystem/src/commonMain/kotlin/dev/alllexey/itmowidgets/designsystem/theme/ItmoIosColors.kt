package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * UIKit's dynamic system colours that the iOS variants of the kit draw with, beside the extended colours: resolved
 * on the pinned simulator (iOS 27.0, DS-IOS-01) with `resolvedColor(with:)` for the light and the dark trait, as
 * `#AARRGGBB`. Separators, secondary labels and fills keep UIKit's alpha. The accent stays `colorScheme.primary`.
 */
@Immutable
data class ItmoIosColors(
    /** `systemGroupedBackground`: behind inset groups. */
    val groupedBackground: Color,
    /** `secondarySystemGroupedBackground`: a cell of an inset group. */
    val groupedCell: Color,
    /** `separator`: between rows. */
    val separator: Color,
    /** `secondaryLabel`: subtitles, values, footers. */
    val secondaryLabel: Color,
    /** `tertiaryLabel`: placeholders, disabled text. */
    val tertiaryLabel: Color,
    /** `systemFill`: a track, an unselected control. */
    val systemFill: Color,
    /** `systemGreen`: a switch that is on, success. */
    val systemGreen: Color,
    /** `systemRed`: destructive actions. */
    val systemRed: Color,
) {
    companion object {
        val Light = ItmoIosColors(
            groupedBackground = Color(0xFFF2F2F7),
            groupedCell = Color(0xFFFFFFFF),
            separator = Color(0x1F3C3C43),
            secondaryLabel = Color(0x993C3C43),
            tertiaryLabel = Color(0x4C3C3C43),
            systemFill = Color(0x33787880),
            systemGreen = Color(0xFF34C759),
            systemRed = Color(0xFFFF383C),
        )

        val Dark = ItmoIosColors(
            groupedBackground = Color(0xFF000000),
            groupedCell = Color(0xFF1C1C1E),
            separator = Color(0x80545458),
            secondaryLabel = Color(0x99EBEBF5),
            tertiaryLabel = Color(0x4CEBEBF5),
            systemFill = Color(0x5C787880),
            systemGreen = Color(0xFF30D158),
            systemRed = Color(0xFFFF4245),
        )

        fun of(dark: Boolean): ItmoIosColors = if (dark) Dark else Light
    }
}

internal val LocalItmoIosColors = staticCompositionLocalOf { ItmoIosColors.Light }
